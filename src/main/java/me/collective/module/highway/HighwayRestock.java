package me.collective.module.highway;

import me.collective.util.InventoryUtil;
import me.collective.util.sort.ShulkerDataParser;
import net.minecraft.block.AirBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.function.Predicate;

/**
 * Логика состояния Restock билдера хайвеев: подбор контейнера (шалкер/эндер-сундук),
 * установка, вытягивание предметов и аккуратный разбор контейнера после рестока.
 *
 * <p>Выделено из тела константы {@code State.Restock}: константа остаётся тонкой
 * оболочкой (identity-сравнения {@code lastState != this} внутри машины состояний
 * не меняются), вся логика и изменяемое состояние живут здесь. Экземпляр один —
 * как и прежнее тело константы enum, поэтому время жизни полей идентично.
 */
final class HighwayRestock {
    static final HighwayRestock INSTANCE = new HighwayRestock();

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private final MBlockPos pos = new MBlockPos();
    private int minimumSlots;
    private int stopTimer;
    private int delayTimer;
    private boolean breakContainer;
    private boolean indicateStopping;
    private Predicate<ItemStack> shulkerPredicate;
    private int slot = -1;

    private HighwayRestock() {
    }

    void start(HighwayBuilderHIG b) {
        slot = -1;

        if (shulkerPredicate == null) setShulkerPredicate(b);

        if (b.restockTask.tasksInactive()) {
            b.setState(HighwayBuilderHIG.State.Forward);
            return;
        }

        if (b.lastState != HighwayBuilderHIG.State.Center && b.lastState != HighwayBuilderHIG.State.ThrowOutTrash
            && b.lastState != HighwayBuilderHIG.State.PlaceShulkerBlockade && b.lastState != HighwayBuilderHIG.State.Restock) {
            b.setState(HighwayBuilderHIG.State.Center);
            return;
        } else if (b.lastState == HighwayBuilderHIG.State.Center) {
            b.setState(HighwayBuilderHIG.State.ThrowOutTrash);
            return;
        }

        if (slot == -1 && b.searchShulkers.getValue()) {
            slot = b.findAndMoveToHotbar(shulkerPredicate);

            if (slot != -1 && b.lastState != HighwayBuilderHIG.State.PlaceShulkerBlockade) {
                b.setState(HighwayBuilderHIG.State.PlaceShulkerBlockade);
            }
        }

        if (slot == -1 && b.searchEnderChest.getValue() && b.countItem(stack -> stack.getItem() == Items.ENDER_CHEST) > 0) {
            boolean stop = HighwayBuilderHIG.EChestMemory.isKnown();
            if (HighwayBuilderHIG.EChestMemory.isKnown()) {
                for (ItemStack stack : HighwayBuilderHIG.EChestMemory.ITEMS) {
                    if (b.restockTask.materials && stack.getItem() instanceof BlockItem bi) {
                        if (b.blocksToPlace.getValue().contains(bi.getBlock()) || (b.blocksToPlace.getValue().contains(Blocks.OBSIDIAN) && bi == Items.ENDER_CHEST)) {
                            stop = false;
                            break;
                        }
                    }
                    if (b.restockTask.pickaxes && stack.isIn(ItemTags.PICKAXES)) {
                        stop = false;
                        break;
                    }
                    if (b.restockTask.food && stack.get(DataComponentTypes.FOOD) != null) {
                        stop = false;
                        break;
                    }

                    if (b.searchShulkers.getValue() && shulkerPredicate.test(stack)) {
                        stop = false;
                        break;
                    }
                }
            }

            if (!stop) slot = b.findAndMoveToHotbar(stack -> stack.getItem() == Items.ENDER_CHEST);
        }

        if (slot == -1) {
            boolean restockOccurred = (
                (b.restockTask.materials && (b.hasItem(stack -> stack.getItem() instanceof BlockItem bi && b.blocksToPlace.getValue().contains(bi.getBlock())) || b.blocksToPlace.getValue().contains(Blocks.OBSIDIAN) && b.countItem(stack -> stack.getItem() == Items.ENDER_CHEST) > b.saveEchests.getIntValue())) ||
                    (b.restockTask.pickaxes && b.countItem(stack -> stack.isIn(ItemTags.PICKAXES)) > b.savePickaxes.getIntValue()) ||
                    (b.restockTask.food && b.hasItem(stack -> stack.get(DataComponentTypes.FOOD) != null))
            );

            if (restockOccurred) {
                b.setState(HighwayBuilderHIG.State.ThrowOutTrash, HighwayBuilderHIG.State.Forward);
            } else b.error("Unable to perform restock for '" + b.restockTask.item() + "'.");

            return;
        }

        int restockSlots = -b.minEmpty.getIntValue();
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) restockSlots++;
        }

        if (restockSlots <= 0) {
            b.error("No empty slots for restocking items.");
            return;
        }

        minimumSlots = b.restockTask.materials ? restockSlots : 1;

        HorizontalDirection dir = b.dir.diagonal ? b.dir.rotateLeft().rotateLeftSkipOne() : b.dir.opposite();
        pos.set(mc.player).offset(dir);

        breakContainer = mc.world.getBlockState(pos.getBlockPos()).getBlock() == Blocks.ENDER_CHEST;

        indicateStopping = false;
        delayTimer = b.inventoryDelay.getIntValue();
    }

    void tick(HighwayBuilderHIG b) {
        if (slot == -1) {
            b.error("Invalid restocking action.");
            return;
        }

        if (indicateStopping && !breakContainer) {
            if (stopTimer > 0) stopTimer--;
            else {
                if (b.lastState == HighwayBuilderHIG.State.PlaceShulkerBlockade) {
                    b.setState(HighwayBuilderHIG.State.MineShulkerBlockade);
                } else {
                    b.setState(HighwayBuilderHIG.State.ThrowOutTrash, HighwayBuilderHIG.State.Forward);
                }
            }

            return;
        }

        if (b.restockTask.tasksInactive()) {
            b.setState(HighwayBuilderHIG.State.Forward);
            return;
        }

        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        int slotsPulled = 0;
        if (b.restockTask.materials) {
            slotsPulled += countSlots(stack -> stack.getItem() instanceof BlockItem bi && b.blocksToPlace.getValue().contains(bi.getBlock()));
            if (b.blocksToPlace.getValue().contains(Blocks.OBSIDIAN))
                slotsPulled += ((b.countItem(stack -> stack.getItem() == Items.ENDER_CHEST) - b.saveEchests.getIntValue()) * 8) / 64;
        }
        if (b.restockTask.pickaxes)
            slotsPulled += countSlots(stack -> stack.isIn(ItemTags.PICKAXES)) - b.savePickaxes.getIntValue();
        if (b.restockTask.food)
            slotsPulled += countSlots(stack -> stack.get(DataComponentTypes.FOOD) != null);

        if (slotsPulled >= minimumSlots && !indicateStopping) {
            indicateStopping = true;
            breakContainer = true;
            stopTimer = 12;
            if (mc.currentScreen != null) mc.setScreen(null);
            return;
        }

        BlockPos blockPos = pos.getBlockPos();
        BlockState blockState = mc.world.getBlockState(blockPos);

        switch (blockState.getBlock()) {
            case ShulkerBoxBlock shulker -> {
                if (mc.currentScreen instanceof ShulkerBoxScreen screen) {
                    if (screen.getScreenHandler().syncId != b.containerId) return;

                    if (restockItems(b, screen.getScreenHandler(), 27)) {
                        delayTimer = b.inventoryDelay.getIntValue();
                        return;
                    }

                    mc.setScreen(null);
                    breakContainer = true;
                } else {
                    if (!b.searchShulkers.getValue()) breakContainer = true;
                    handleContainerBlock(b, blockPos);
                }
            }

            case EnderChestBlock echest -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    if (screen.getScreenHandler().syncId != b.containerId) return;

                    HighwayBuilderHIG.EChestMemory.updateFrom(screen.getScreenHandler());

                    if (restockItems(b, screen.getScreenHandler(), screen.getScreenHandler() instanceof GenericContainerScreenHandler handler ? handler.getRows() * 9 : 27)) {
                        delayTimer = b.inventoryDelay.getIntValue();
                        return;
                    }

                    if (b.searchShulkers.getValue()) {
                        ScreenHandler menu = screen.getScreenHandler();
                        for (int i = 0; i < 27 && i < menu.slots.size(); i++) {
                            if (shulkerPredicate.test(menu.slots.get(i).getStack())) {
                                b.quickMove(i);
                                delayTimer = b.inventoryDelay.getIntValue();
                                break;
                            }
                        }
                    }

                    mc.setScreen(null);
                    breakContainer = true;
                } else {
                    if (!b.searchEnderChest.getValue()) breakContainer = true;
                    handleContainerBlock(b, blockPos);
                }
            }

            case AirBlock air -> {
                if (breakContainer) {
                    breakContainer = false;

                    if (indicateStopping) b.restockTask.complete();
                    else start(b);

                    return;
                }

                b.placeBlock(blockPos, slot);
            }

            default -> b.error("Invalid block at container restocking position?");
        }
    }

    private boolean restockItems(HighwayBuilderHIG b, ScreenHandler menu, int containerSize) {
        if (b.restockTask.materials) {
            if (grabFromInventory(b, menu, containerSize, stack -> stack.getItem() instanceof BlockItem bi && b.blocksToPlace.getValue().contains(bi.getBlock())))
                return true;

            if (b.blocksToPlace.getValue().contains(Blocks.OBSIDIAN)) {
                if (grabFromInventory(b, menu, containerSize, stack -> stack.getItem() == Items.ENDER_CHEST)) return true;
            }
        }
        if (b.restockTask.pickaxes) {
            if (grabFromInventory(b, menu, containerSize, stack -> stack.isIn(ItemTags.PICKAXES))) return true;
        }
        if (b.restockTask.food) {
            return grabFromInventory(b, menu, containerSize, stack -> stack.get(DataComponentTypes.FOOD) != null);
        }

        return false;
    }

    private boolean grabFromInventory(HighwayBuilderHIG b, ScreenHandler menu, int containerSize, Predicate<ItemStack> filterItem) {
        for (int i = 0; i < containerSize && i < menu.slots.size(); i++) {
            if (filterItem.test(menu.slots.get(i).getStack())) {
                b.quickMove(i);
                return true;
            }
        }

        return false;
    }

    private void setShulkerPredicate(HighwayBuilderHIG b) {
        shulkerPredicate = itemStack -> {
            if (!HighwayInventory.isShulker(itemStack.getItem())) return false;
            List<ItemStack> contents = ShulkerDataParser.parseShulkerContentsAsList(itemStack);

            for (ItemStack stack : contents) {
                if (b.restockTask.materials && stack.getItem() instanceof BlockItem bi) {
                    if (b.blocksToPlace.getValue().contains(bi.getBlock()) || (b.blocksToPlace.getValue().contains(Blocks.OBSIDIAN) && bi == Items.ENDER_CHEST))
                        return true;
                }
                if (b.restockTask.pickaxes && stack.isIn(ItemTags.PICKAXES)) return true;
                if (b.restockTask.food && stack.get(DataComponentTypes.FOOD) != null) return true;
            }

            return false;
        };
    }

    private void handleContainerBlock(HighwayBuilderHIG b, BlockPos bp) {
        if (breakContainer) {
            BlockState state = mc.world.getBlockState(bp);
            int toolSlot = b.findAndMoveBestToolToHotbar(state, false);
            InventoryUtil.swap(toolSlot);

            if (b.rotation.getValue().mine) {
                float[] yp = b.lookAt(bp);
                b.rotateTo(yp[0], yp[1]);
            }
            b.breakBlockAt(bp);
            Direction dir = HighwayBuilderHIG.getInteractDirection(bp);
            Vec3d hitVec = new Vec3d(bp.getX() + 0.5 + dir.getOffsetX() * 0.45,
                bp.getY() + 0.5 + dir.getOffsetY() * 0.45,
                bp.getZ() + 0.5 + dir.getOffsetZ() * 0.45);
            InventoryUtil.swap(slot);
            if (b.rotation.getValue().place) {
                float[] yp = b.lookAt(mc.player.getEyePos(), hitVec);
                b.rotateTo(yp[0], yp[1]);
            }
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                new BlockHitResult(hitVec, dir, bp, false));
            mc.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.HandSwingC2SPacket(Hand.MAIN_HAND));
            delayTimer = b.inventoryDelay.getIntValue();
        }
    }

    private int countSlots(Predicate<ItemStack> predicate) {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            if (predicate.test(mc.player.getInventory().getStack(i))) count++;
        }

        return count;
    }
}

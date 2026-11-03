package me.collective.module.highway;

import me.collective.util.InventoryUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Логика состояния MineEnderChests билдера: ставит эндер-сундуки и добывает
 * их (без Silk Touch), пока не наберёт минимальный запас обсидиана.
 * Поддерживает rebreak-режим с таймером и паузой на STOP_DESTROY_BLOCK.
 *
 * <p>Выделено из тела константы {@code State.MineEnderChests} по паттерну
 * {@link HighwayRestock}: константа — тонкая оболочка, состояние и логика
 * живут здесь (один экземпляр на JVM — как и прежнее тело константы).
 */
final class HighwayMineEnderChests {
    static final HighwayMineEnderChests INSTANCE = new HighwayMineEnderChests();

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    /** Тиков до таймаута rebreak: суны не применились — пересаживаем сундук. */
    private static final int REBREAK_TIMEOUT_TICKS = 60;
    /** Тиков паузы перед переходом к MineEChestBlockade. */
    private static final int FINISH_PAUSE_TICKS = 12;

    private final MBlockPos pos = new MBlockPos();
    private int minimumObsidian;
    private boolean first;
    private boolean primed;
    private boolean stopTimerEnabled;
    private int stopTimer;
    private int moveTimer;
    private int rebreakTimer;
    private int timeout;

    private HighwayMineEnderChests() {
    }

    void start(HighwayBuilderHIG b) {
        if (b.lastState != HighwayBuilderHIG.State.Center && b.lastState != HighwayBuilderHIG.State.ThrowOutTrash
            && b.lastState != HighwayBuilderHIG.State.PlaceEChestBlockade) {
            b.setState(HighwayBuilderHIG.State.Center);
            return;
        } else if (b.lastState == HighwayBuilderHIG.State.Center) {
            b.setState(HighwayBuilderHIG.State.ThrowOutTrash);
            return;
        } else if (b.lastState == HighwayBuilderHIG.State.ThrowOutTrash) {
            b.setState(HighwayBuilderHIG.State.PlaceEChestBlockade);
            return;
        }

        int emptySlots = 0;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) emptySlots++;
        }

        if (emptySlots == 0) {
            b.error("No empty slots.");
            return;
        }

        int minimumSlots = Math.max(emptySlots - b.minEmpty.getIntValue(), 1);
        minimumObsidian = minimumSlots * 64;
        first = true;
        moveTimer = timeout = 0;

        stopTimerEnabled = false;
        primed = false;
    }

    void tick(HighwayBuilderHIG b) {
        if (stopTimerEnabled) {
            if (stopTimer > 0) stopTimer--;
            else b.setState(HighwayBuilderHIG.State.MineEChestBlockade);

            return;
        }

        HorizontalDirection dir = b.dir.diagonal ? b.dir.rotateLeft().rotateLeftSkipOne() : b.dir.opposite();
        pos.set(mc.player).offset(dir);

        if (moveTimer > 0) {
            b.rotateTo(dir.yaw, mc.player.getPitch());
            b.inFwd = moveTimer > 2;

            moveTimer--;
            return;
        }

        int obsidianCount = 0;

        for (Entity entity : mc.world.getOtherEntities(mc.player,
            new Box(pos.x, pos.y, pos.z, pos.x + 1, pos.y + 2, pos.z + 1))) {
            if (entity instanceof ItemEntity itemEntity && itemEntity.getStack().getItem() == Items.OBSIDIAN) {
                obsidianCount += itemEntity.getStack().getCount();
            }
        }

        for (int i = 0; i < 36; i++) {
            ItemStack itemStack = mc.player.getInventory().getStack(i);
            if (itemStack.getItem() == Items.OBSIDIAN) obsidianCount += itemStack.getCount();
        }

        if (obsidianCount >= minimumObsidian) {
            stopTimerEnabled = true;
            stopTimer = FINISH_PAUSE_TICKS;
            return;
        }

        BlockPos bp = pos.getBlockPos();
        BlockState blockState = mc.world.getBlockState(bp);

        if (blockState.getBlock() == Blocks.ENDER_CHEST) {
            if (mc.currentScreen instanceof GenericContainerScreen screen) {
                if (screen.getScreenHandler().syncId != b.containerId) return;

                mc.setScreen(null);
            }

            if (!HighwayBuilderHIG.EChestMemory.isKnown()) {
                Direction interactDir = HighwayBuilderHIG.getInteractDirection(bp);
                Vec3d hitVec = new Vec3d(bp.getX() + 0.5 + interactDir.getOffsetX() * 0.45,
                    bp.getY() + 0.5 + interactDir.getOffsetY() * 0.45,
                    bp.getZ() + 0.5 + interactDir.getOffsetZ() * 0.45);
                if (b.rotation.getValue().place) {
                    float[] yp = b.lookAt(mc.player.getEyePos(), hitVec);
                    b.rotateTo(yp[0], yp[1]);
                }
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                    new BlockHitResult(hitVec, interactDir, bp, false));
                return;
            }

            if (first) {
                moveTimer = 8;
                first = false;
                return;
            }

            int slot = b.findAndMoveBestToolToHotbar(blockState, true);
            if (slot == -1) {
                b.error("Cannot find pickaxe without silk touch to mine ender chests.");
                return;
            }

            InventoryUtil.swap(slot);

            if (b.rebreakEchests.getValue() && primed) {
                timeout++;
                if (timeout > REBREAK_TIMEOUT_TICKS) {
                    primed = false;
                    timeout = 0;
                    return;
                }

                if (rebreakTimer > 0) {
                    rebreakTimer--;
                    return;
                }

                rebreakTimer = b.rebreakTimer.getIntValue();

                if (b.rotation.getValue().mine) {
                    float[] yp = b.lookAt(bp);
                    b.rotateTo(yp[0], yp[1]);
                }
                b.sendAction(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, bp);
            } else {
                if (b.rotation.getValue().mine) {
                    float[] yp = b.lookAt(bp);
                    b.rotateTo(yp[0], yp[1]);
                }
                b.breakBlockAt(bp);
            }
        } else {
            int slot = b.findAndMoveToHotbar(stack -> stack.getItem() == Items.ENDER_CHEST);
            if (slot == -1 || b.countItem(stack -> stack.getItem() == Items.ENDER_CHEST) <= b.saveEchests.getIntValue()) {
                stopTimerEnabled = true;
                stopTimer = FINISH_PAUSE_TICKS;
                return;
            }

            if (b.countItem(stack -> stack.isIn(ItemTags.PICKAXES)) <= b.savePickaxes.getIntValue()) {
                if (b.searchEnderChest.getValue() || b.searchShulkers.getValue()) {
                    b.restockTask.setPickaxes();
                }
            }

            if (!first) primed = true;

            Direction placeDir = HighwayBuilderHIG.getInteractDirection(bp);
            Vec3d hitVec = new Vec3d(bp.getX() + 0.5 + placeDir.getOffsetX() * 0.45,
                bp.getY() + 0.5 + placeDir.getOffsetY() * 0.45,
                bp.getZ() + 0.5 + placeDir.getOffsetZ() * 0.45);
            BlockHitResult hit = new BlockHitResult(hitVec, placeDir, bp, false);
            InventoryUtil.swap(slot);
            if (b.rotation.getValue().place) {
                float[] yp = b.lookAt(mc.player.getEyePos(), hitVec);
                b.rotateTo(yp[0], yp[1]);
            }
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            mc.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.HandSwingC2SPacket(Hand.MAIN_HAND));
            InventoryUtil.swapBack();
            timeout = 0;
        }
    }
}

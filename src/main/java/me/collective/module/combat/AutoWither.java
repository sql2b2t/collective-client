package me.collective.module.combat;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.ColorSetting;
import me.collective.setting.EnumSetting;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * AutoWither: автоматически строит визера по нажатию ПКМ. 1:1 порт из b2xy.
 */
public class AutoWither extends Module {

    public final BooleanSetting airPlace = add(new BooleanSetting("AirPlace", true));
    public final BooleanSetting placeCenterSkull = add(new BooleanSetting("CenterSkull", true));
    public final BooleanSetting lockRotation = add(new BooleanSetting("LockRotation", false));
    public final EnumSetting<CardinalDirection> chosenDirection = add(new EnumSetting<>("Direction", CardinalDirection.North, (v) -> lockRotation.getValue()));
    public final BooleanSetting renderPreview = add(new BooleanSetting("Preview", true));
    public final BooleanSetting swingHand = add(new BooleanSetting("Swing", true));
    public final ColorSetting previewColor = add(new ColorSetting("PreviewColor", new Color(51, 207, 255, 50)));
    public final ColorSetting previewOutlineColor = add(new ColorSetting("PreviewOutlineColor", new Color(112, 136, 255, 255)));

    private BlockPos previewPos;
    private boolean isBuilding = false;
    private boolean hasMaterials = false;
    private boolean wasUsePressed = false;

    public AutoWither() {
        super("AutoWither", "Автоматически строит визера при нажатии ПКМ.", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        sendClientMessage("Нажмите ПКМ, чтобы построить визера");
        int soulSandCount = 0;
        int skullCount = 0;

        if (mc.player != null) {
            for (int i = 0; i < mc.player.getInventory().size(); i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.isEmpty()) continue;

                if (stack.getItem() == Items.SOUL_SAND) {
                    soulSandCount += stack.getCount();
                } else if (stack.getItem() == Items.WITHER_SKELETON_SKULL) {
                    skullCount += stack.getCount();
                }
            }

            int witherCount = Math.min(soulSandCount / 4, skullCount / 3);
            if (witherCount > 0) {
                sendClientMessage("Вы можете построить " + witherCount + " визера(ов)");
                hasMaterials = true;
            } else {
                sendClientMessage("У вас недостаточно материалов для постройки визера в хотбаре");
                hasMaterials = false;
            }
        }
        wasUsePressed = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        hasMaterials = hasWitherMaterials();

        boolean isUsePressed = mc.options.useKey.isPressed();
        if (mc.currentScreen == null && isUsePressed && !wasUsePressed && !isBuilding) {
            if (!hasMaterials) {
                sendClientMessage("У вас недостаточно материалов для постройки визера в хотбаре");
            } else if (previewPos != null) {
                isBuilding = true;
                placeWither(previewPos, () -> isBuilding = false);
            }
        }
        wasUsePressed = isUsePressed;
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (!renderPreview.getValue() || mc.world == null) return;
        if (mc.crosshairTarget instanceof BlockHitResult hit) {
            BlockPos pos = hit.getBlockPos();
            BlockState state = mc.world.getBlockState(pos);
            if (airPlace.getValue()) {
                if (state.isSolidBlock(mc.world, pos)) {
                    previewPos = pos.up();
                } else {
                    previewPos = pos;
                }
            } else {
                if (state.isSolidBlock(mc.world, pos)) {
                    previewPos = pos.up();
                } else {
                    previewPos = null;
                }
            }
        } else {
            previewPos = null;
        }
    }

    private void placeWither(BlockPos basePos, Runnable onComplete) {
        if (!checkWitherBlocks(basePos)) {
            sendClientMessage("Препятствие помешало постройке визера");
            onComplete.run();
            return;
        }

        int originalSlot = mc.player.getInventory().selectedSlot;
        Direction direction;
        if (lockRotation.getValue()) {
            direction = chosenDirection.getValue().toMcDirection();
        } else {
            direction = mc.player.getHorizontalFacing();
        }
        List<BlockPos> blockPositions = new ArrayList<>();
        List<BlockPos> skullPositions = new ArrayList<>();
        if (direction == Direction.NORTH || direction == Direction.SOUTH) {
            blockPositions.add(basePos.up().west());
            blockPositions.add(basePos.up().east());
            skullPositions.add(basePos.up().west());
            skullPositions.add(basePos.up().east());
        } else {
            blockPositions.add(basePos.up().south());
            blockPositions.add(basePos.up().north());
            skullPositions.add(basePos.up().south());
            skullPositions.add(basePos.up().north());
        }
        blockPositions.add(basePos);
        blockPositions.add(basePos.up());
        if (placeCenterSkull.getValue()) {
            skullPositions.add(basePos.up());
        }

        mc.execute(() -> {
            for (BlockPos pos : blockPositions) {
                placeSoulBlock(pos);
            }
            for (BlockPos pos : skullPositions) {
                placeBlock(pos, Items.WITHER_SKELETON_SKULL);
            }
            mc.player.getInventory().selectedSlot = originalSlot;
            if (mc.interactionManager != null) mc.interactionManager.syncSelectedSlot();
            onComplete.run();
        });
    }

    private boolean checkWitherBlocks(BlockPos basePos) {
        List<BlockPos> blockPositions = new ArrayList<>();
        Direction direction;
        if (lockRotation.getValue()) {
            direction = chosenDirection.getValue().toMcDirection();
        } else {
            direction = mc.player.getHorizontalFacing();
        }
        if (direction == Direction.NORTH || direction == Direction.SOUTH) {
            blockPositions.add(basePos.up().west());
            blockPositions.add(basePos.up().east());
            blockPositions.add(basePos.up(2).west());
            blockPositions.add(basePos.up(2).east());
            blockPositions.add(basePos.west());
            blockPositions.add(basePos.east());
        } else {
            blockPositions.add(basePos.up().south());
            blockPositions.add(basePos.up().north());
            blockPositions.add(basePos.up(2).south());
            blockPositions.add(basePos.up(2).north());
            blockPositions.add(basePos.south());
            blockPositions.add(basePos.north());
        }
        blockPositions.add(basePos);
        blockPositions.add(basePos.up());
        if (placeCenterSkull.getValue()) {
            blockPositions.add(basePos.up(2));
        }
        for (BlockPos pos : blockPositions) {
            if (!checkBlockPlaceable(pos)) {
                return false;
            }
        }
        blockPositions.remove(4);
        blockPositions.remove(5);
        for (BlockPos pos : blockPositions) {
            if (!checkBlockForEntity(pos)) {
                return false;
            }
        }
        return true;
    }

    private boolean checkBlockPlaceable(BlockPos pos) {
        return mc.world.getBlockState(pos).isAir();
    }

    private boolean checkBlockForEntity(BlockPos pos) {
        List<Entity> entities = mc.world.getOtherEntities(
            mc.player,
            new Box(pos),
            e -> !(e instanceof ItemEntity || e instanceof ExperienceOrbEntity)
        );
        return entities.isEmpty();
    }

    private void placeSoulBlock(BlockPos pos) {
        Item soulBlock = hasSoulSandHotbar() ? Items.SOUL_SAND : Items.SOUL_SOIL;
        placeBlock(pos, soulBlock);
    }

    private void placeBlock(BlockPos pos, Item item) {
        int slot = findHotbarSlot(item);
        if (slot == -1) return;
        mc.player.getInventory().selectedSlot = slot;
        mc.interactionManager.syncSelectedSlot();
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false));
        if (!swingHand.getValue()) return;
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private int findHotbarSlot(Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) return i;
        }
        return -1;
    }

    private boolean hasWitherMaterials() {
        int soulBlockCount = 0;
        int skullCount = 0;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            Item item = stack.getItem();

            if (item == Items.SOUL_SAND || item == Items.SOUL_SOIL) {
                soulBlockCount += stack.getCount();
            } else if (item == Items.WITHER_SKELETON_SKULL) {
                skullCount += stack.getCount();
            }

            if (mc.player.isCreative()) {
                if (soulBlockCount >= 1 && skullCount >= 1) return true;
            } else {
                if (soulBlockCount >= 4 && skullCount >= 3) return true;
            }
        }

        return false;
    }

    private boolean hasSoulSandHotbar() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.SOUL_SAND) return true;
        }
        return false;
    }

    private void sendClientMessage(String msg) {
        if (mc.player != null) {
            mc.player.sendMessage(net.minecraft.text.Text.literal("§7[§5AutoWither§7] §f" + msg), false);
        }
    }

    public enum CardinalDirection {
        North(Direction.NORTH),
        South(Direction.SOUTH),
        East(Direction.EAST),
        West(Direction.WEST);

        private final Direction mcDirection;

        CardinalDirection(Direction mcDirection) {
            this.mcDirection = mcDirection;
        }

        public Direction toMcDirection() {
            return mcDirection;
        }
    }
}

package me.collective.module.player;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.util.InventoryUtil;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class AutoTool extends Module {
    public final BooleanSetting switchBack = add(new BooleanSetting("SwitchBack", true));
    public final BooleanSetting saveDurability = add(new BooleanSetting("SaveDurability", true));

    private boolean wasPressed = false;
    private int prevSlot = -1;

    public AutoTool() {
        super("AutoTool", "Автоматически выбирает наиболее эффективный инструмент при копании блоков.", Category.PLAYER);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        boolean isPressed = mc.options.keyAttack.isDown();

        if (isPressed && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            BlockState state = mc.level.getBlockState(hit.getBlockPos());
            if (!state.isAir()) {
                int bestSlot = getBestTool(state);
                if (bestSlot != -1 && bestSlot != mc.player.getInventory().getSelectedSlot()) {
                    if (prevSlot == -1) {
                        prevSlot = mc.player.getInventory().getSelectedSlot();
                    }
                    InventoryUtil.swap(bestSlot);
                }
            }
        } else if (!isPressed && wasPressed) {
            if (switchBack.getValue() && prevSlot != -1) {
                InventoryUtil.swap(prevSlot);
                prevSlot = -1;
            }
        }

        wasPressed = isPressed;
    }

    private int getBestTool(BlockState state) {
        int bestSlot = -1;
        float bestSpeed = 1.0f;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            if (saveDurability.getValue() && stack.isDamageableItem()) {
                if ((stack.getMaxDamage() - stack.getDamageValue()) < 15) continue;
            }

            float speed = stack.getDestroySpeed(state);
            if (state.requiresCorrectToolForDrops() && !stack.isCorrectToolForDrops(state)) {
                continue;
            }

            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }

        return bestSlot;
    }
}
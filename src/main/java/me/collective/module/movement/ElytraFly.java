package me.collective.module.movement;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.FindItemResult;
import me.collective.util.InventoryUtil;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

/**
 * Порт ElytraFly из Meteor Client (упрощённый Vanilla-режим).
 *
 * <p>Классический управляемый полёт: прыжок — буст фейерверком, шифт — спуск,
 * вперёд — разгон по взгляду. Надёжен в лагах, где волна Pitch-40 сбоит.
 */
public class ElytraFly extends Module {
    public final NumberSetting speed = add(new NumberSetting("Скорость", 2.0, 0.5, 5.0, 0.1));
    public final NumberSetting boostPower = add(new NumberSetting("Сила буста", 1.5, 0.5, 3.0, 0.1));
    public final BooleanSetting autoBoost = add(new BooleanSetting("Авто-буст", true));
    public final BooleanSetting fireworks = add(new BooleanSetting("Фейерверки", false));
    public final BooleanSetting hover = add(new BooleanSetting("Зависание", false));

    public ElytraFly() {
        super("ElytraFly", "Управляемый полёт на элитрах.", Category.MOVEMENT);
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || !mc.player.isGliding()) return;

        Vec3d vel = mc.player.getVelocity();
        Vec3d look = mc.player.getRotationVec(1.0f);

        if (hover.getValue()) {
            mc.player.setVelocity(vel.x * 0.9, 0, vel.z * 0.9);
            return;
        }

        boolean forward = mc.options.forwardKey.isPressed();
        boolean jump = mc.options.jumpKey.isPressed();
        boolean sneak = mc.options.sneakKey.isPressed();

        double s = speed.getFloatValue();
        double motionX = vel.x;
        double motionY = vel.y;
        double motionZ = vel.z;

        if (forward) {
            motionX = look.x * s;
            motionZ = look.z * s;
            motionY = look.y * s * 0.8;
        }
        if (sneak) motionY = -1.2;
        if (jump && autoBoost.getValue() && fireworks.getValue()) {
            if (useFirework()) {
                motionX = look.x * s * boostPower.getFloatValue();
                motionZ = look.z * s * boostPower.getFloatValue();
                motionY = Math.max(motionY, 0.6);
            }
        }

        mc.player.setVelocity(motionX, motionY, motionZ);
    }

    private boolean useFirework() {
        FindItemResult hotbar = InventoryUtil.findInHotbarOrOffhand(Items.FIREWORK_ROCKET);
        if (hotbar.isOffhand()) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            return true;
        }
        if (hotbar.isHotbar()) {
            InventoryUtil.swap(hotbar.slot(), true);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            InventoryUtil.swapBack();
            return true;
        }
        return false;
    }
}

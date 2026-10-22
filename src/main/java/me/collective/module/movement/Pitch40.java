package me.collective.module.movement;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.module.ModuleManager;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.InventoryUtil;
import me.collective.util.Utils;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Pitch40: утилита для питч-40 полёта — синхронизация границ высоты,
 * автозапуск фейерверков, авто-рекаст и ускорение RocketBoost. 1:1 порт из b2xy.
 */
public class Pitch40 extends Module {

    public final NumberSetting pitch40LowerBounds = add(new NumberSetting("LowerBounds", 360.0, -128.0, 500.0, 1.0));
    public final NumberSetting pitch40UpperBounds = add(new NumberSetting("UpperBounds", 420.0, -128.0, 500.0, 1.0));
    public final BooleanSetting autoFirework = add(new BooleanSetting("AutoFirework", true));
    public final BooleanSetting ecoMode = add(new BooleanSetting("Эконом", true));
    public final NumberSetting minSpeed = add(new NumberSetting("MinSpeed", 5.0, 0.0, 50.0, 0.5, (v) -> autoFirework.getValue()));
    public final NumberSetting fireworkCooldownTicks = add(new NumberSetting("CooldownTicks", 100, 10, 100, 5, (v) -> autoFirework.getValue()));
    public final BooleanSetting autoRecast = add(new BooleanSetting("AutoRecast", true));
    public final BooleanSetting useBoost = add(new BooleanSetting("UseBoost", false));

    private boolean boostToggledByUs = false;
    private boolean recastToggledByUs = false;
    private int fireworkCooldown = 0;
    private boolean goingUp = false;
    private int elytraSwapSlot = -1;
    private double peakY = 0.0;
    private double troughY = 0.0;
    private ElytraRecast elytraRecast = null;
    private RegistryKey<World> lastDimension = null;
    private boolean waitingNoticeShown = false;

    public Pitch40() {
        super("Pitch40", "Утилита для Pitch40-полёта: синхронизация границ, авто-фейерверк и авто-рекаст.", Category.MOVEMENT);
    }

    private void enableBoost() {
        RocketBoost boost = ModuleManager.INSTANCE.getModule(RocketBoost.class);
        if (boost != null && !boost.isToggled()) {
            boost.toggle();
            this.boostToggledByUs = true;
        }
    }

    private void disableBoost() {
        if (this.boostToggledByUs) {
            this.boostToggledByUs = false;
            RocketBoost boost = ModuleManager.INSTANCE.getModule(RocketBoost.class);
            if (boost != null && boost.isToggled()) {
                boost.toggle();
            }
        }
    }

    private void driveBoost() {
        if (this.useBoost.getValue() && mc.player != null && mc.player.isGliding()) {
            RocketBoost boost = ModuleManager.INSTANCE.getModule(RocketBoost.class);
            if (boost != null && boost.isToggled()) {
                boost.declareTravelling();
            }
        }
    }

    @Override
    public void onEnable() {
        this.fireworkCooldown = 0;
        this.goingUp = false;
        this.peakY = mc.player != null ? mc.player.getY() : 0.0;
        this.troughY = this.peakY;
        this.elytraRecast = ModuleManager.INSTANCE.getModule(ElytraRecast.class);
        this.lastDimension = mc.world != null ? mc.world.getRegistryKey() : null;
        this.waitingNoticeShown = false;

        if (this.useBoost.getValue()) {
            this.enableBoost();
        }
        if (this.autoRecast.getValue() && this.elytraRecast != null && !this.elytraRecast.isToggled()) {
            this.elytraRecast.toggle();
            this.recastToggledByUs = true;
        }
    }

    @Override
    public void onDisable() {
        if (this.recastToggledByUs && this.elytraRecast != null && this.elytraRecast.isToggled()) {
            this.elytraRecast.toggle();
        }
        this.recastToggledByUs = false;
        this.elytraSwapSlot = -1;
        this.disableBoost();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (this.useBoost.getValue()) this.enableBoost();
        else this.disableBoost();
        if (!this.autoRecast.getValue() && this.recastToggledByUs) {
            if (this.elytraRecast != null && this.elytraRecast.isToggled()) this.elytraRecast.toggle();
            this.recastToggledByUs = false;
        } else if (this.autoRecast.getValue() && this.elytraRecast != null && !this.elytraRecast.isToggled()) {
            this.elytraRecast.toggle();
            this.recastToggledByUs = true;
        }
        this.driveBoost();
        RegistryKey<World> currentDimension = mc.world.getRegistryKey();
        if (this.lastDimension != null && !this.lastDimension.equals(currentDimension)) {
            sendClientMessage("Измерение сменилось! Обновляю границы Pitch40.");
            this.lastDimension = currentDimension;
            this.waitingNoticeShown = false;
            this.peakY = mc.player.getY();
            this.troughY = this.peakY;
            this.goingUp = false;
        } else {
            this.lastDimension = currentDimension;

            if (this.fireworkCooldown > 0) {
                this.fireworkCooldown--;
            }
            if (this.elytraSwapSlot != -1) {
                InventoryUtil.swap(this.elytraSwapSlot, true);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                InventoryUtil.swapBack();
                this.elytraSwapSlot = -1;
            }

            double playerY = mc.player.getY();
            double velocityY = mc.player.getVelocity().y;
            boolean wasGoingUp = this.goingUp;
            this.goingUp = velocityY > 0.0;
            if (wasGoingUp && !this.goingUp) {
                this.peakY = playerY;
            } else if (!wasGoingUp && this.goingUp) {
                this.troughY = playerY;
            }

            if (this.goingUp && playerY > this.peakY) {
                this.peakY = playerY;
            } else if (!this.goingUp && playerY < this.troughY) {
                this.troughY = playerY;
            }

            this.checkAndUseFirework();
        }
    }

    public boolean meetsPitch40ModeBounds() {
        if (mc.player == null) return false;
        double y = mc.player.getY();
        return y >= this.pitch40UpperBounds.getValue() && y - 40.0 >= this.pitch40LowerBounds.getValue();
    }

    private double getSpeedBPS() {
        Vec3d velocity = mc.player.getVelocity();
        double speedPerTick = Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y + velocity.z * velocity.z);
        return speedPerTick * 20.0;
    }

    private void checkAndUseFirework() {
        if (this.autoFirework.getValue() && this.fireworkCooldown <= 0) {
            if (mc.player != null && mc.player.isGliding()) {
                if (this.elytraRecast == null || !this.elytraRecast.isToggled() || !this.elytraRecast.isRecovering()) {
                    double currentSpeed = this.getSpeedBPS();
                    double playerY = mc.player.getY();
                    double lowerBounds = this.pitch40LowerBounds.getValue();
                    boolean needsBoost = currentSpeed < this.minSpeed.getValue();
                    boolean emergency = playerY < lowerBounds - 10.0;
                    if (needsBoost || emergency) {
                        int launchStatus = Utils.firework(mc, false);
                        if (launchStatus >= 0) {
                            this.fireworkCooldown = this.fireworkCooldownTicks.getIntValue();
                            if (launchStatus != 200) {
                                this.elytraSwapSlot = launchStatus;
                            }
                            this.peakY = playerY;
                            this.troughY = playerY;
                        }
                    }
                }
            }
        }
    }

    private void sendClientMessage(String msg) {
        if (mc.player != null) {
            mc.player.sendMessage(net.minecraft.text.Text.literal("§7[§bPitch40§7] §f" + msg), false);
        }
    }
}

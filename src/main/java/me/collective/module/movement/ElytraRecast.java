package me.collective.module.movement;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.PacketEvent;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.module.ModuleManager;
import me.collective.setting.BooleanSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.FindItemResult;
import me.collective.util.InventoryUtil;
import me.collective.util.Utils;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;

/**
 * ElytraRecast: следит за полётом на элитрах и автоматически поднимает игрока,
 * если полёт прервался или высота упала ниже границ. 1:1 порт из b2xy.
 */
public class ElytraRecast extends Module {

    public final BooleanSetting disableIfNoRockets = add(new BooleanSetting("DisableIfNoRockets", true));
    public final BooleanSetting debugMessages = add(new BooleanSetting("DebugMessages", false));
    public final NumberSetting activationDelay = add(new NumberSetting("ActivationDelay", 3, 1, 20, 1));
    public final NumberSetting rocketDelay = add(new NumberSetting("RocketDelay", 15, 5, 60, 1));
    public final BooleanSetting smartRockets = add(new BooleanSetting("SmartRockets", true));
    public final NumberSetting minClimbSpeed = add(new NumberSetting("MinClimbSpeed", 0.5, 0.0, 2.0, 0.05, (v) -> smartRockets.getValue()));
    public final NumberSetting wallClearance = add(new NumberSetting("WallClearance", 20, 5, 50, 1));
    public final EnumSetting<WallHitAction> wallHitAction = add(new EnumSetting<>("WallHitAction", WallHitAction.Pause));
    public final NumberSetting pauseDuration = add(new NumberSetting("PauseDuration", 20, 0, 200, 1, (v) -> wallHitAction.getValue() == WallHitAction.Pause));
    public final NumberSetting maxWallHits = add(new NumberSetting("MaxWallHits", 3, 1, 10, 1, (v) -> wallHitAction.getValue() == WallHitAction.Pause));
    public final NumberSetting rotationSpeed = add(new NumberSetting("RotationSpeed", 3.0, 0.5, 15.0, 0.5));
    public final NumberSetting targetPitch = add(new NumberSetting("TargetPitch", -70.0, -89.0, 0.0, 1.0));
    public final NumberSetting netherFallDelay = add(new NumberSetting("NetherFallDelay", 5, 1, 40, 1));
    public final BooleanSetting usePitch40Bounds = add(new BooleanSetting("UsePitch40Bounds", true));
    public final NumberSetting overworldTargetAltitude = add(new NumberSetting("TargetAltitude", 360, 50, 400, 5, (v) -> !usePitch40Bounds.getValue()));
    public final NumberSetting overworldMinAltitude = add(new NumberSetting("MinAltitude", 310, 10, 400, 5, (v) -> !usePitch40Bounds.getValue()));

    private static final int WALL_HIT_RESET_TICKS = 600;
    private State state;
    private int tickCounter;
    private int rocketTickCounter;
    private boolean usedActivationRocket;
    private int notGlidingTicks;
    private boolean netherRocketUsed;
    private Pitch40 pitch40Util;
    private int pauseTicks;
    private int wallHits;
    private int ticksSinceWallHit;
    private volatile boolean pendingWallHit;

    public ElytraRecast() {
        super("ElytraRecast", "Восстановление полёта: следит за полётом на элитрах и поднимает при падении или остановке.", Category.MOVEMENT);
    }

    public boolean isRecovering() {
        return this.state == State.JUMPING || this.state == State.ACTIVATING || this.state == State.ASCENDING;
    }

    @Override
    public void onEnable() {
        if (mc.player != null) {
            if (!this.hasElytraEquipped()) {
                sendClientMessage("§cЭлитры не надеты!");
                this.toggle();
            } else {
                this.tickCounter = 0;
                this.rocketTickCounter = 0;
                this.usedActivationRocket = false;
                this.notGlidingTicks = 0;
                this.netherRocketUsed = false;
                this.pitch40Util = ModuleManager.INSTANCE.getModule(Pitch40.class);
                this.pauseTicks = 0;
                this.wallHits = 0;
                this.ticksSinceWallHit = 0;
                this.pendingWallHit = false;
                this.state = State.MONITORING;
                if (this.debugMessages.getValue()) {
                    sendClientMessage("Слежу за полётом...");
                }
            }
        }
    }

    @Override
    public void onDisable() {
        this.state = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        if (this.state == null) return;
        if (!this.hasElytraEquipped()) return;

        if (this.pendingWallHit) {
            this.pendingWallHit = false;
            this.handleWallHit();
            if (!this.isToggled()) return;
        }

        this.ticksSinceWallHit++;
        if (this.wallHits > 0 && this.ticksSinceWallHit > WALL_HIT_RESET_TICKS) {
            this.wallHits = 0;
        }

        this.tickCounter++;
        this.rocketTickCounter++;
        switch (this.state) {
            case MONITORING -> this.handleMonitoring();
            case JUMPING -> this.handleJumping();
            case ACTIVATING -> this.handleActivating();
            case ASCENDING -> this.handleAscending();
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof ClientboundHurtAnimationPacket packet) {
            // Receive стреляет на нетти-треде: raycast и состояние игрока
            // разрешено трогать только на основном, планируем через mc.execute.
            int id = packet.id();
            mc.execute(() -> {
                if (mc.player == null || mc.level == null || this.state == null) return;
                if (id == mc.player.getId() && this.looksLikeWallHit()) {
                    this.pendingWallHit = true;
                }
            });
        }
    }

    private boolean looksLikeWallHit() {
        if (!mc.player.isFallFlying()) return false;
        Vec3 vel = mc.player.getDeltaMovement();
        if (Math.abs(vel.x) + Math.abs(vel.z) < 0.1) return false;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 dir = new Vec3(vel.x, 0.0, vel.z).normalize();
        HitResult hit = mc.level.clip(
            new ClipContext(eye, eye.add(dir.scale(1.5)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player)
        );
        return hit.getType() == HitResult.Type.BLOCK;
    }

    private void handleWallHit() {
        this.wallHits++;
        this.ticksSinceWallHit = 0;
        if (this.wallHitAction.getValue() == WallHitAction.Disable) {
            sendClientMessage("§cУдар о стену! Отключаюсь.");
            this.toggle();
        } else if (this.wallHits >= this.maxWallHits.getIntValue()) {
            sendClientMessage("§cУдар о стену " + this.wallHits + " раз! Отключаюсь.");
            this.toggle();
        } else {
            sendClientMessage(
                "§eУдар о стену (" + this.wallHits + "/" + this.maxWallHits.getIntValue() + ")! Пауза восстановления на " + this.pauseDuration.getIntValue() + " тиков."
            );
            this.state = State.MONITORING;
            this.pauseTicks = this.pauseDuration.getIntValue();
            this.tickCounter = 0;
            this.notGlidingTicks = 0;
            this.usedActivationRocket = false;
            this.netherRocketUsed = false;
        }
    }

    private void handleMonitoring() {
        if (this.pauseTicks > 0) {
            this.pauseTicks--;
            this.notGlidingTicks = 0;
        } else {
            boolean currentlyGliding = mc.player.isFallFlying();
            double currentY = mc.player.getY();
            if (this.isInNether()) {
                if (!currentlyGliding) {
                    this.notGlidingTicks++;
                    if (this.notGlidingTicks >= this.netherFallDelay.getIntValue()) {
                        if (this.debugMessages.getValue()) {
                            sendClientMessage("Ад: полёт остановлен " + this.notGlidingTicks + " тиков! Восстанавливаюсь...");
                        }
                        this.triggerRecovery();
                        this.notGlidingTicks = 0;
                    }
                } else {
                    this.notGlidingTicks = 0;
                }
            } else {
                double minAlt = this.getEffectiveMinAltitude();
                if (currentY < minAlt) {
                    if (!currentlyGliding) {
                        if (this.debugMessages.getValue()) {
                            sendClientMessage("Ниже мин. высоты (Y=" + (int) currentY + " < " + (int) minAlt + ") и не в полёте! Восстанавливаюсь...");
                        }
                        this.triggerRecovery();
                        return;
                    }
                    if (this.debugMessages.getValue()) {
                        sendClientMessage("Высота слишком мала (Y=" + (int) currentY + " < " + (int) minAlt + ")! Набираю высоту...");
                    }
                    this.triggerRecovery();
                }
            }
        }
    }

    private double getEffectiveMinAltitude() {
        if (this.pitch40Util == null) this.pitch40Util = ModuleManager.INSTANCE.getModule(Pitch40.class);
        return this.usePitch40Bounds.getValue() && this.pitch40Util != null && this.pitch40Util.isToggled()
            ? this.pitch40Util.pitch40LowerBounds.getValue() - 10.0
            : this.overworldMinAltitude.getIntValue();
    }

    private double getEffectiveTargetAltitude() {
        if (this.pitch40Util == null) this.pitch40Util = ModuleManager.INSTANCE.getModule(Pitch40.class);
        return this.usePitch40Bounds.getValue() && this.pitch40Util != null && this.pitch40Util.isToggled()
            ? Math.max(this.pitch40Util.pitch40UpperBounds.getValue(), this.pitch40Util.pitch40LowerBounds.getValue() + 40.0) + 2.0
            : this.overworldTargetAltitude.getIntValue();
    }

    private void triggerRecovery() {
        if (!this.hasRockets()) {
            if (this.disableIfNoRockets.getValue()) {
                sendClientMessage("§cНет фейерверков! Отключаю ElytraRecast.");
                this.toggle();
                return;
            }
            sendClientMessage("§eНет фейерверков! Восстановление может не сработать.");
        }

        this.tickCounter = 0;
        this.rocketTickCounter = this.rocketDelay.getIntValue();
        this.usedActivationRocket = false;
        this.netherRocketUsed = false;
        if (mc.player.isFallFlying()) {
            this.state = State.ASCENDING;
        } else if (mc.player.onGround()) {
            this.state = State.JUMPING;
        } else {
            this.state = State.ACTIVATING;
        }
    }

    private void handleJumping() {
        if (mc.player.onGround()) {
            mc.player.jumpFromGround();
            this.tickCounter = 0;
        } else if (this.tickCounter >= this.activationDelay.getIntValue()) {
            this.state = State.ACTIVATING;
            this.tickCounter = 0;
        }
    }

    private void handleActivating() {
        if (mc.player.isFallFlying()) {
            this.state = State.ASCENDING;
            this.rocketTickCounter = this.rocketDelay.getIntValue();
            this.tickCounter = 0;
        } else if (mc.player.onGround()) {
            this.state = State.JUMPING;
            this.tickCounter = 0;
            this.usedActivationRocket = false;
        } else {
            if (this.tickCounter % 2 == 0) {
                this.sendElytraPacket();
            }
            if (this.tickCounter > 15 && !this.usedActivationRocket) {
                if (!this.hasRocketClearance()) {
                    this.adjustPitchUp();
                } else if (this.useRocket()) {
                    this.usedActivationRocket = true;
                } else if (this.disableIfNoRockets.getValue()) {
                    sendClientMessage("§cНет ракет для активации! Отключаюсь.");
                    this.toggle();
                    return;
                }
            }
            if (this.tickCounter > 40) {
                if (!this.hasRockets() && this.disableIfNoRockets.getValue()) {
                    sendClientMessage("§cНет ракет и активация не удалась! Отключаюсь.");
                    this.toggle();
                    return;
                }
                this.state = State.JUMPING;
                this.tickCounter = 0;
                this.usedActivationRocket = false;
            }
        }
    }

    private void handleAscending() {
        if (!mc.player.isFallFlying()) {
            if (!this.hasRockets() && this.disableIfNoRockets.getValue()) {
                sendClientMessage("§cПолёт потерян и ракет нет! Отключаюсь.");
                this.toggle();
            } else {
                this.state = State.ACTIVATING;
                this.tickCounter = 0;
                this.usedActivationRocket = false;
            }
        } else {
            if (this.isInNether()) {
                this.adjustPitchUp();
                if (!this.netherRocketUsed && this.hasRocketClearance()) {
                    if (this.useRocket()) {
                        this.netherRocketUsed = true;
                    } else if (this.disableIfNoRockets.getValue()) {
                        sendClientMessage("§cНет ракет для восстановления в Аду! Отключаюсь.");
                        this.toggle();
                        return;
                    }
                }
                if (this.netherRocketUsed ? this.tickCounter > 10 : this.tickCounter > 40) {
                    if (this.debugMessages.getValue()) {
                        sendClientMessage(
                            this.netherRocketUsed
                                ? "Восстановление в Аду завершено — дальше баритон."
                                : "Ад: нет свободного пути для ракеты, возвращаюсь к мониторингу."
                        );
                    }
                    this.state = State.MONITORING;
                    this.tickCounter = 0;
                    this.netherRocketUsed = false;
                }
            } else {
                double currentY = mc.player.getY();
                double target = this.getEffectiveTargetAltitude();
                if (currentY >= target) {
                    if (this.debugMessages.getValue()) {
                        sendClientMessage("Целевая высота Y=" + (int) target + " достигнута! Возвращаюсь к мониторингу.");
                    }
                    this.state = State.MONITORING;
                    this.tickCounter = 0;
                    return;
                }
                this.adjustPitchUp();
                if (this.rocketTickCounter >= this.rocketDelay.getIntValue() && this.shouldBoost()) {
                    if (this.useRocket()) {
                        this.rocketTickCounter = 0;
                    } else if (this.disableIfNoRockets.getValue()) {
                        sendClientMessage("§cРакеты кончились во время подъёма! Отключаюсь.");
                        this.toggle();
                    }
                }
            }
        }
    }

    private boolean shouldBoost() {
        if (!this.hasRocketClearance()) return false;
        return !this.smartRockets.getValue() || !this.isBoosted() && mc.player.getDeltaMovement().y < this.minClimbSpeed.getValue();
    }

    private boolean hasRocketClearance() {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 end = eye.add(mc.player.getViewVector(1.0f).scale(this.wallClearance.getIntValue()));
        return mc.level.clip(
            new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player)
        ).getType() == HitResult.Type.MISS;
    }

    private boolean isBoosted() {
        return !mc.level.getEntities(
            mc.player,
            mc.player.getBoundingBox().inflate(2.0),
            e -> e instanceof FireworkRocketEntity rocket && !rocket.isShotAtAngle()
        ).isEmpty();
    }

    private void adjustPitchUp() {
        float currentPitch = mc.player.getXRot();
        float target = this.targetPitch.getFloatValue();
        float speed = this.rotationSpeed.getFloatValue();
        float diff = target - currentPitch;
        if (!(Math.abs(diff) < 0.5F)) {
            float adjustment;
            if (Math.abs(diff) <= speed) {
                adjustment = diff;
            } else {
                adjustment = diff > 0.0F ? speed : -speed;
            }
            float newPitch = currentPitch + adjustment;
            newPitch = Math.max(-89.0F, Math.min(89.0F, newPitch));
            mc.player.setXRot(newPitch);
        }
    }

    private void sendElytraPacket() {
        if (mc.player != null && mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
        }
    }

    private boolean hasRockets() {
        FindItemResult hotbar = InventoryUtil.findInHotbar(Items.FIREWORK_ROCKET);
        if (hotbar.found()) return true;
        return InventoryUtil.find(Items.FIREWORK_ROCKET).found();
    }

    private boolean useRocket() {
        FindItemResult hotbar = InventoryUtil.findInHotbar(Items.FIREWORK_ROCKET);
        if (!hotbar.found()) {
            FindItemResult inv = InventoryUtil.find(Items.FIREWORK_ROCKET);
            if (!inv.found()) return false;
            int hotbarSlot = this.findEmptyHotbarSlot();
            if (hotbarSlot != -1) {
                InventoryUtil.move().from(inv.slot()).to(hotbarSlot);
            }
        }
        Utils.firework(mc, false);
        return true;
    }

    private int findEmptyHotbarSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) return i;
        }
        return -1;
    }

    private boolean hasElytraEquipped() {
        return mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;
    }

    private boolean isInNether() {
        return mc.level != null && mc.level.dimension() == Level.NETHER;
    }

    private void sendClientMessage(String msg) {
        if (mc.player != null) {
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7[§bElytraRecast§7] §f" + msg));
        }
    }

    private enum State {
        MONITORING,
        JUMPING,
        ACTIVATING,
        ASCENDING
    }

    public enum WallHitAction {
        Pause,
        Disable
    }
}
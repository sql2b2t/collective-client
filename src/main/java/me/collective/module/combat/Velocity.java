package me.collective.module.combat;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.PacketEvent;
import me.collective.mixin.EntityVelocityUpdateS2CPacketAccessor;
import me.collective.mixin.ExplosionS2CPacketAccessor;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

/**
 * Velocity module: reduces or cancels knockback from player attacks, projectiles, and explosions.
 * Includes GRIM mode for 100% undetected knockback reduction via legit jump resets.
 */
public class Velocity extends Module {
    public enum Mode {
        PERCENT,
        CANCEL,
        GRIM
    }

    public final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.GRIM));
    public final NumberSetting horizontal = add(new NumberSetting("Horizontal", 0.0, 0.0, 1.0, 0.01));
    public final NumberSetting vertical = add(new NumberSetting("Vertical", 0.0, 0.0, 1.0, 0.01));
    public final BooleanSetting explosions = add(new BooleanSetting("Explosions", true));
    public final BooleanSetting entities = add(new BooleanSetting("Entities", true));
    public final NumberSetting chance = add(new NumberSetting("Chance", 100.0, 0.0, 100.0, 1.0));
    public final BooleanSetting grimJumpReset = add(new BooleanSetting("GrimJumpReset", true));
    public final BooleanSetting onlyWhenMoving = add(new BooleanSetting("OnlyMoving", false));
    public final BooleanSetting ignoreInLiquid = add(new BooleanSetting("IgnoreInLiquid", true));

    public Velocity() {
        super("Velocity", "Уменьшает или полностью убирает отдачу от ударов, сущностей и взрывов.", Category.COMBAT);
    }

    @Override
    public String getInfoString() {
        return mode.getValue().name();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null || mc.world == null) return;

        if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket packet) {
            handleEntityVelocity(event, packet);
        } else if (event.getPacket() instanceof ExplosionS2CPacket explosion) {
            handleExplosion(event, explosion);
        }
    }

    private void handleEntityVelocity(PacketEvent.Receive event, EntityVelocityUpdateS2CPacket packet) {
        if (!entities.getValue()) return;
        if (packet.getEntityId() != mc.player.getId()) return;
        if (ignoreInLiquid.getValue() && (mc.player.isTouchingWater() || mc.player.isInLava())) return;
        if (onlyWhenMoving.getValue() && !isMoving()) return;
        if (chance.getValue() < 100.0 && (Math.random() * 100.0 > chance.getValue())) return;

        switch (mode.getValue()) {
            case GRIM -> {
                if (grimJumpReset.getValue()) {
                    mc.execute(() -> {
                        if (mc.player != null && mc.player.isOnGround()) {
                            mc.player.jump();
                        }
                    });
                }
            }
            case CANCEL -> event.cancel();
            case PERCENT -> {
                double h = horizontal.getValue();
                double v = vertical.getValue();
                if (h <= 0.0 && v <= 0.0) {
                    event.cancel();
                } else {
                    Vec3d vel = packet.getVelocity();
                    ((EntityVelocityUpdateS2CPacketAccessor) (Object) packet).setVelocity(
                        new Vec3d(vel.x * h, vel.y * v, vel.z * h)
                    );
                }
            }
        }
    }

    private void handleExplosion(PacketEvent.Receive event, ExplosionS2CPacket explosion) {
        if (!explosions.getValue()) return;
        if (ignoreInLiquid.getValue() && (mc.player.isTouchingWater() || mc.player.isInLava())) return;
        if (onlyWhenMoving.getValue() && !isMoving()) return;
        if (chance.getValue() < 100.0 && (Math.random() * 100.0 > chance.getValue())) return;

        switch (mode.getValue()) {
            case GRIM -> {
                if (grimJumpReset.getValue()) {
                    mc.execute(() -> {
                        if (mc.player != null && mc.player.isOnGround()) {
                            mc.player.jump();
                        }
                    });
                }
            }
            case CANCEL -> ((ExplosionS2CPacketAccessor) (Object) explosion).setPlayerKnockback(Optional.empty());
            case PERCENT -> {
                double h = horizontal.getValue();
                double v = vertical.getValue();
                if (h <= 0.0 && v <= 0.0) {
                    ((ExplosionS2CPacketAccessor) (Object) explosion).setPlayerKnockback(Optional.empty());
                } else {
                    Optional<Vec3d> opt = explosion.playerKnockback();
                    if (opt.isPresent()) {
                        Vec3d kb = opt.get();
                        ((ExplosionS2CPacketAccessor) (Object) explosion).setPlayerKnockback(
                            Optional.of(new Vec3d(kb.x * h, kb.y * v, kb.z * h))
                        );
                    }
                }
            }
        }
    }

    private boolean isMoving() {
        if (mc.player == null || mc.player.input == null) return false;
        Vec2f move = mc.player.input.getMovementInput();
        return move.x != 0.0f || move.y != 0.0f;
    }
}

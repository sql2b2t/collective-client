package me.collective.module.movement;

import me.collective.category.Category;
import me.collective.command.ChatHelper;
import me.collective.event.EventHandler;
import me.collective.event.events.PacketEvent;
import me.collective.event.events.PlayerMoveEvent;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.ViaProtocolUtil;
import net.minecraft.entity.EntityPosition;
import net.minecraft.entity.MovementType;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.util.math.Vec3d;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class GrimGlide extends Module {
    public enum Mode {
        Envelope("Огибающая"),
        Report("Отчёт");

        private final String title;
        Mode(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }

    public enum Protocol {
        Auto("Авто"),
        Legacy("Легаси"),
        Modern("Современный");

        private final String title;
        Protocol(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }

    public final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.Envelope));
    public final EnumSetting<Protocol> protocol = add(new EnumSetting<>("Protocol", Protocol.Auto, (v) -> mode.getValue() == Mode.Envelope));
    public final NumberSetting safety = add(new NumberSetting("Safety", 0.85, 0.1, 1.0, 0.05, (v) -> mode.getValue() == Mode.Envelope, false));
    public final NumberSetting climb = add(new NumberSetting("Climb", 0.0, -0.1, 0.1, 0.01, (v) -> mode.getValue() == Mode.Envelope, false));
    public final BooleanSetting steer = add(new BooleanSetting("Steer", true, (v) -> mode.getValue() == Mode.Envelope));

    public final NumberSetting forward = add(new NumberSetting("Forward", 0.087, 0.0, 1.0, 0.005, (v) -> mode.getValue() == Mode.Report, false));
    public final BooleanSetting randomize = add(new BooleanSetting("Randomize", true, (v) -> mode.getValue() == Mode.Report));
    public final NumberSetting randomMin = add(new NumberSetting("RandomMin", 1.1, 0.0, 2.0, 0.05, (v) -> mode.getValue() == Mode.Report && randomize.getValue(), false));
    public final NumberSetting randomMax = add(new NumberSetting("RandomMax", 1.21, 0.0, 2.0, 0.05, (v) -> mode.getValue() == Mode.Report && randomize.getValue(), false));
    public final NumberSetting fall = add(new NumberSetting("Fall", 0.02, 0.0, 0.1, 0.005, (v) -> mode.getValue() == Mode.Report, false));
    public final NumberSetting lift = add(new NumberSetting("Lift", 0.016, 0.0, 0.1, 0.005, (v) -> mode.getValue() == Mode.Report, false));
    public final NumberSetting speedLimit = add(new NumberSetting("SpeedLimit", 48.0, 0.0, 120.0, 1.0, (v) -> mode.getValue() == Mode.Report, false));
    public final BooleanSetting teleport = add(new BooleanSetting("Teleport", true, (v) -> mode.getValue() == Mode.Report));
    public final BooleanSetting reportSetbacks = add(new BooleanSetting("ReportSetbacks", true));

    private int setbacks;
    private double bps;
    private Vec3d lastPos;

    public GrimGlide() {
        super("GrimGlide", "Elytra hover using GrimAC flight uncertainty. Requires pre-1.18.2 protocol for buffer.", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        setbacks = 0;
        bps = 0.0;
        lastPos = mc.player == null ? null : mc.player.getEntityPos();
        if (mode.getValue() == Mode.Envelope) {
            reportEnvelope();
        }
    }

    private void reportEnvelope() {
        int detected = ViaProtocolUtil.targetProtocol();
        if (protocol.getValue() == Protocol.Auto) {
            if (!ViaProtocolUtil.isPresent()) {
                ChatHelper.sendMessage("ViaFabricPlus не загружен — считается нативный протокол, запаса почти нет.");
            } else if (detected == -1) {
                ChatHelper.sendMessage("Не удалось прочитать цель ViaFabricPlus — считается нативный. Задайте протокол вручную.");
            } else {
                ChatHelper.sendMessage(String.format("Цель Via %s (протокол %d).", ViaProtocolUtil.targetName(), detected));
            }
        } else if (detected != -1 && isLegacy() != ViaProtocolUtil.isLegacyBand(detected)) {
            ChatHelper.sendMessage(String.format("Протокол принудительно %s, но Via на %s — бюджет будет неверным.", protocol.getValue(), ViaProtocolUtil.targetName()));
        }
        if (!isLegacy()) {
            ChatHelper.sendMessage(String.format("Протокол 1.18.2+: порог Grim равен 0.0002, так что бюджет %.4f б/с — ниже его собственной линии флага 0.001. Переключите цель на 1.9-1.18.1.", horizontalBudget() * 20.0));
        } else {
            ChatHelper.sendMessage(String.format("Огибающая активна: %.2f б/с по горизонтали, %.3f блоков/тик по вертикали.", horizontalBudget() * 20.0, verticalBudget()));
        }
    }

    private boolean isLegacy() {
        if (protocol.getValue() == Protocol.Legacy) return true;
        if (protocol.getValue() == Protocol.Modern) return false;
        return ViaProtocolUtil.isLegacyBand(ViaProtocolUtil.targetProtocol());
    }

    private double threshold() {
        return isLegacy() ? 0.03 : 2.0E-4;
    }

    private double horizontalBudget() {
        double t = threshold();
        return (0.99 * (t * 2.0) + t) * safety.getValue();
    }

    private double verticalBudget() {
        return threshold() * 2.0 * safety.getValue();
    }

    private double gravity() {
        double cos = Math.cos(Math.toRadians(mc.player.getPitch()));
        double vertCosRotation = cos * cos;
        return 0.08 * (-1.0 + vertCosRotation * 0.75);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (lastPos != null) {
            Vec3d delta = mc.player.getEntityPos().subtract(lastPos);
            bps = Math.sqrt(delta.x * delta.x + delta.z * delta.z) * 20.0;
        }
        lastPos = mc.player.getEntityPos();
        if (!mc.player.isGliding()) return;

        if (mode.getValue() == Mode.Report) {
            tickReport();
        } else {
            mc.player.setVelocity(envelope());
        }
    }

    @EventHandler
    private void onPlayerMove(PlayerMoveEvent event) {
        if (mode.getValue() != Mode.Envelope || mc.player == null || mc.world == null) return;
        if (event.type != MovementType.SELF || !mc.player.isGliding()) return;
        event.movement = envelope();
    }

    private Vec3d envelope() {
        double h = horizontalBudget();
        double v = verticalBudget();
        double gravity = gravity();
        double y = Math.max(gravity - v, Math.min(gravity + v, climb.getValue()));
        if (steer.getValue() && !isMoveKeyDown()) {
            return new Vec3d(0.0, y, 0.0);
        }
        double yawRad = Math.toRadians(mc.player.getYaw());
        return new Vec3d(-Math.sin(yawRad) * h, y, Math.cos(yawRad) * h);
    }

    private boolean isMoveKeyDown() {
        return mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed() || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed();
    }

    private void tickReport() {
        double step = bps >= speedLimit.getValue() ? 0.0 : forward.getValue();
        double yawRad = Math.toRadians(mc.player.getYaw());
        double dx = -Math.sin(yawRad) * step;
        double dz = Math.cos(yawRad) * step;
        Vec3d velocity = mc.player.getVelocity();
        mc.player.setVelocity(dx * factor(), velocity.y - fall.getValue(), dz * factor());
        if (teleport.getValue()) {
            mc.player.setPosition(mc.player.getX() + dx, mc.player.getY(), mc.player.getZ() + dz);
        }
        mc.player.setVelocity(dx * factor(), mc.player.getVelocity().y + lift.getValue(), dz * factor());
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!reportSetbacks.getValue() || mc.player == null) return;
        Packet<?> packet = event.getPacket();
        if (!(packet instanceof PlayerPositionLookS2CPacket positionLook)) return;

        EntityPosition changePacket = positionLook.change();
        Vec3d change = changePacket.position();
        Vec3d current = mc.player.getEntityPos();
        Set<PositionFlag> relatives = positionLook.relatives();
        Vec3d target = new Vec3d(
            relatives.contains(PositionFlag.X) ? current.x + change.x : change.x,
            relatives.contains(PositionFlag.Y) ? current.y + change.y : change.y,
            relatives.contains(PositionFlag.Z) ? current.z + change.z : change.z
        );
        String message = String.format("Откат #%d — оттянуто на %.2f блоков.", ++setbacks, target.distanceTo(current));
        // Пакеты приходят на netty-треде: чат трогаем только из главного потока.
        mc.execute(() -> ChatHelper.sendMessage(message));
    }

    private double factor() {
        if (!randomize.getValue()) return 1.0;
        double min = Math.min(randomMin.getValue(), randomMax.getValue());
        double max = Math.max(randomMin.getValue(), randomMax.getValue());
        return min == max ? min : ThreadLocalRandom.current().nextDouble(min, max);
    }
}

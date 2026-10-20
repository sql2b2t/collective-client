package me.collective.module.combat;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.PacketEvent;
import me.collective.util.IPlayerInteractEntity;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.MaceItem;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public class Criticals extends Module {
    public enum Mode {
        PACKET, JUMP, MINI_JUMP
    }

    public final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.PACKET));
    public final BooleanSetting mace = add(new BooleanSetting("MaceSmash", true));
    public final NumberSetting extraHeight = add(new NumberSetting("ExtraHeight", 0.0, 0.0, 10.0, 0.5));

    public Criticals() {
        super("Criticals", "Always perform critical hits when attacking targets.", Category.COMBAT);
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null || mc.world == null) return;

        if (event.getPacket() instanceof PlayerInteractEntityC2SPacket && event.getPacket() instanceof IPlayerInteractEntity packet) {
            if (packet.collective$getType() == PlayerInteractEntityC2SPacket.InteractType.ATTACK) {
                Entity entity = packet.collective$getEntity();
                if (!(entity instanceof LivingEntity)) return;

                if (mace.getValue() && mc.player.getMainHandStack().getItem() instanceof MaceItem) {
                    if (mc.player.isGliding()) return;
                    sendPos(0.0);
                    sendPos(1.501 + extraHeight.getValue());
                    sendPos(0.0);
                    return;
                }

                if (skipCrit()) return;

                switch (mode.getValue()) {
                    case PACKET -> {
                        sendPos(0.0625);
                        sendPos(0.0);
                    }
                    case JUMP -> {
                        mc.player.jump();
                    }
                    case MINI_JUMP -> {
                        mc.player.setVelocity(mc.player.getVelocity().x, 0.25, mc.player.getVelocity().z);
                    }
                }
            }
        }
    }

    private void sendPos(double offset) {
        if (mc.player == null) return;
        me.collective.util.PacketUtil.sendSilently(new PlayerMoveC2SPacket.PositionAndOnGround(
            mc.player.getX(),
            mc.player.getY() + offset,
            mc.player.getZ(),
            false,
            false
        ));
    }

    private boolean skipCrit() {
        if (mc.player == null) return true;
        return !mc.player.isOnGround() || mc.player.isSubmergedInWater() || mc.player.isInLava() || mc.player.isClimbing();
    }
}

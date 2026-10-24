package me.collective.module.combat;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.PacketEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.MaceItem;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class Criticals extends Module {
    public enum Mode {
        PACKET, GRIM, JUMP, MINI_JUMP
    }

    public final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.GRIM));
    public final BooleanSetting mace = add(new BooleanSetting("MaceSmash", true));
    public final NumberSetting extraHeight = add(new NumberSetting("ExtraHeight", 0.0, 0.0, 10.0, 0.5));

    public Criticals() {
        super("Criticals", "Всегда наносит критические удары при атаке целей.", Category.COMBAT);
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null || mc.level == null) return;

        if (event.getPacket() instanceof ServerboundAttackPacket attackPacket) {
            Entity entity = mc.level.getEntity(attackPacket.entityId());
            if (!(entity instanceof LivingEntity)) return;

            if (mace.getValue() && mc.player.getMainHandItem().getItem() instanceof MaceItem) {
                    if (mc.player.isFallFlying()) return;
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
                    case GRIM -> {
                        if (mc.player.onGround()) {
                            mc.player.jumpFromGround();
                        }
                    }
                    case JUMP -> {
                        mc.player.jumpFromGround();
                    }
                    case MINI_JUMP -> {
                        mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, 0.25, mc.player.getDeltaMovement().z);
                    }
                }
        }
    }

    private void sendPos(double offset) {
        if (mc.player == null) return;
        me.collective.util.PacketUtil.sendSilently(new ServerboundMovePlayerPacket.Pos(
            mc.player.getX(),
            mc.player.getY() + offset,
            mc.player.getZ(),
            false,
            false
        ));
    }

    private boolean skipCrit() {
        if (mc.player == null) return true;
        return !mc.player.onGround() || mc.player.isUnderWater() || mc.player.isInLava() || mc.player.onClimbable();
    }
}
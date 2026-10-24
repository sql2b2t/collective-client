package me.collective.module.combat;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.manager.FriendManager;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.InventoryUtil;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;

public class AutoLog extends Module {
    public final NumberSetting health = add(new NumberSetting("Health", 8.0, 1.0, 36.0, 1.0));
    public final BooleanSetting noTotems = add(new BooleanSetting("NoTotems", true));
    public final NumberSetting minTotems = add(new NumberSetting("MinTotems", 1.0, 0.0, 5.0, 1.0));
    public final BooleanSetting crystalDanger = add(new BooleanSetting("CrystalDanger", true));
    public final BooleanSetting playerNear = add(new BooleanSetting("PlayerNear", false));
    public final NumberSetting playerRange = add(new NumberSetting("PlayerRange", 15.0, 5.0, 64.0, 1.0));

    public AutoLog() {
        super("AutoLog", "Автоматически выходит с сервера при критическом здоровье или опасности, спасая вещи.", Category.COMBAT);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return;

        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();

        // 1. Порог здоровья
        if (hp <= health.getValue().floatValue()) {
            disconnect(String.format("Health fell below threshold: %.1f HP", hp));
            return;
        }

        // 2. Проверка тотемов
        if (noTotems.getValue()) {
            int totems = InventoryUtil.find(Items.TOTEM_OF_UNDYING).count();
            if (mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) totems++;
            if (totems <= minTotems.getValue().intValue()) {
                disconnect(String.format("Totems remaining (%d) <= threshold (%d)!", totems, minTotems.getValue().intValue()));
                return;
            }
        }

        // 3. Опасность от кристаллов при неполном HP
        if (crystalDanger.getValue() && hp <= 16.0f) {
            for (var entity : mc.level.entitiesForRendering()) {
                if (entity instanceof EndCrystal crystal) {
                    if (crystal.distanceTo(mc.player) <= 4.0f) {
                        disconnect(String.format("Lethal crystal nearby (%.1fm) with HP %.1f!", crystal.distanceTo(mc.player), hp));
                        return;
                    }
                }
            }
        }

        // 4. Защита от врагов (AFK Log)
        if (playerNear.getValue()) {
            double rangeSq = playerRange.getValue() * playerRange.getValue();
            for (Player player : mc.level.players()) {
                if (player == mc.player || player.isSpectator() || player.isCreative()) continue;
                if (FriendManager.getInstance().isFriend(player)) continue;

                if (mc.player.distanceToSqr(player) <= rangeSq) {
                    disconnect(String.format("Hostile player %s detected within %.1fm!", player.getName().getString(), mc.player.distanceTo(player)));
                    return;
                }
            }
        }
    }

    private void disconnect(String reason) {
        setToggled(false);
        me.collective.module.misc.AutoReconnect.markAfkLogout();
        if (mc.getConnection() != null && mc.getConnection().getConnection() != null) {
            mc.getConnection().getConnection().disconnect(Component.literal("§c[AutoLog] §f" + reason));
        }
    }
}
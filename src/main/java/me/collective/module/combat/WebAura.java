package me.collective.module.combat;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.ModeSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.FindItemResult;
import me.collective.util.InventoryUtil;
import me.collective.util.PlacementUtils;
import me.collective.manager.FriendManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;

/**
 * Порт WebAura из ThunderHack.
 *
 * <p>Кидает паутину в ноги/на голову цели — дешёвый контроль убегающих.
 */
public class WebAura extends Module {
    public final NumberSetting range = add(new NumberSetting("Радиус", 4.5, 1.0, 7.0, 0.1));
    public final NumberSetting delay = add(new NumberSetting("Задержка", 5.0, 0.0, 20.0, 1.0));
    public final ModeSetting targetMode = add(new ModeSetting("Куда", "Both", "Feet", "Head", "Both"));
    public final BooleanSetting rotate = add(new BooleanSetting("Ротация", true));

    private int delayTimer = 0;

    public WebAura() {
        super("WebAura", "Ставит паутину в ноги и на голову врага.", Category.COMBAT);
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.world == null) return;
        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        Player target = getTarget();
        if (target == null) return;

        FindItemResult web = InventoryUtil.findInHotbarOrOffhand(Items.COBWEB);
        if (!web.found()) return;

        BlockPos feet = target.getBlockPos();
        boolean placed = false;
        String m = targetMode.compute().trim();

        if ((m.equals("Feet") || m.equals("Both")) && PlacementUtils.canPlace(feet, false)) {
            placed = PlacementUtils.placeBlock(feet, web, rotate.getValue(), true, false);
        }
        if (!placed && (m.equals("Head") || m.equals("Both")) && PlacementUtils.canPlace(feet.up(1), false)) {
            placed = PlacementUtils.placeBlock(feet.up(1), web, rotate.getValue(), true, false);
        }

        if (placed) delayTimer = delay.getIntValue();
    }

    private Player getTarget() {
        Player best = null;
        double bestDist = range.getFloatValue();
        for (Player p : mc.world.getPlayers()) {
            if (p == mc.player || !p.isAlive() || p.isSpectator() || p.isCreative()) continue;
            if (FriendManager.getInstance().isFriend(p)) continue;
            double d = mc.player.distanceTo(p);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }
}
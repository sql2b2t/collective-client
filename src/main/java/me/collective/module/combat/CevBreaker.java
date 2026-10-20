package me.collective.module.combat;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.module.player.SpeedMine;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import me.collective.manager.FriendManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

/**
 * Порт CevBreaker из Meteor Client.
 *
 * <p>Выламывает блок над закопавшимся врагом (cev) через SpeedMine и бьёт
 * появившиеся кристаллы. Техника против зарывшихся в сурраунд.
 */
public class CevBreaker extends Module {
    public final NumberSetting range = add(new NumberSetting("Радиус", 5.0, 1.0, 7.0, 0.5));
    public final NumberSetting crystalRange = add(new NumberSetting("Радиус кристаллов", 4.0, 1.0, 6.0, 0.5));
    public final NumberSetting attackDelay = add(new NumberSetting("Задержка удара", 3.0, 0.0, 10.0, 1.0));
    public final BooleanSetting rotate = add(new BooleanSetting("Ротация", true));

    private int delayTimer = 0;

    public CevBreaker() {
        super("CevBreaker", "Ломает cev над врагом и бьёт кристаллы.", Category.COMBAT);
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        PlayerEntity target = getTarget();
        if (target == null) return;

        if (SpeedMine.INSTANCE != null && !SpeedMine.INSTANCE.isToggled()) {
            SpeedMine.INSTANCE.setToggled(true);
        }

        // Ломаем блок над головой цели
        BlockPos cev = target.getBlockPos().up(2);
        if (!mc.world.getBlockState(cev).isAir() && SpeedMine.INSTANCE != null) {
            if (!SpeedMine.INSTANCE.isMiningBlock(cev)) {
                SpeedMine.INSTANCE.mineBlock(cev, Direction.UP);
            }
        }

        // Бьём кристаллы рядом с целью
        if (delayTimer > 0) {
            delayTimer--;
            return;
        }
        Box box = new Box(target.getBlockPos()).expand(crystalRange.getFloatValue());
        for (Entity e : mc.world.getOtherEntities(mc.player, box)) {
            if (!(e instanceof EndCrystalEntity crystal)) continue;
            if (mc.player.distanceTo(crystal) > crystalRange.getFloatValue()) continue;
            mc.interactionManager.attackEntity(mc.player, crystal);
            mc.player.swingHand(Hand.MAIN_HAND);
            delayTimer = attackDelay.getIntValue();
            break;
        }
    }

    private PlayerEntity getTarget() {
        PlayerEntity best = null;
        double bestDist = range.getFloatValue();
        for (PlayerEntity p : mc.world.getPlayers()) {
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

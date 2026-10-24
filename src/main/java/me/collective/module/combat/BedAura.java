package me.collective.module.combat;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.FindItemResult;
import me.collective.util.InventoryUtil;
import me.collective.util.PlacementUtils;
import me.collective.manager.FriendManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

/**
 * Порт BedAura из Meteor Client.
 *
 * <p>Ставит кровать у ног цели, подтверждает её и только потом подрывает
 * (взрыв в Незере и Энде). Для анархий: незер-спавн и хайвей-PvP.
 */
public class BedAura extends Module {
    public final NumberSetting range = add(new NumberSetting("Радиус", 4.5, 1.0, 7.0, 0.1));
    public final NumberSetting delay = add(new NumberSetting("Задержка", 5.0, 0.0, 20.0, 1.0));
    public final BooleanSetting rotate = add(new BooleanSetting("Ротация", true));
    public final BooleanSetting dimensionCheck = add(new BooleanSetting("Только Незер/Энд", true));

    private int delayTimer = 0;
    private BlockPos pendingExplode = null;

    public BedAura() {
        super("BedAura", "Ставит и взрывает кровати у врагов (Незер/Энд).", Category.COMBAT);
    }

    @Override
    public void onDisable() {
        pendingExplode = null;
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.level == null) return;

        // Подрыв на следующий тик: сервер уже подтвердил кровать, и Grim не
        // увидит interact по блоку, которого у него ещё нет.
        if (pendingExplode != null) {
            BlockPos place = pendingExplode;
            pendingExplode = null;

            if (delayTimer > 0) {
                delayTimer--;
            } else if (mc.gameMode != null
                && mc.level.getBlockState(place).getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(place), Direction.UP, place, false);
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                me.collective.util.Utils.swingHand(InteractionHand.MAIN_HAND);
            }
            delayTimer = delay.getIntValue();
            return;
        }

        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        if (dimensionCheck.getValue()) {
            ResourceKey<Level> dim = mc.level.dimension();
            if (dim != Level.NETHER && dim != Level.END) return;
        }

        Player target = getTarget(range.getFloatValue());
        if (target == null) return;

        FindItemResult bed = InventoryUtil.findInHotbarOrOffhand(stack -> stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof BedBlock);
        if (!bed.found()) return;

        BlockPos feet = target.blockPosition();
        BlockPos place = null;
        if (PlacementUtils.canPlace(feet, false)) place = feet;
        else if (PlacementUtils.canPlace(feet.above(1), false)) place = feet.above(1);
        if (place == null) return;

        if (!PlacementUtils.placeBlock(place, bed, rotate.getValue(), true, false)) return;
        pendingExplode = place;
    }

    private Player getTarget(double range) {
        Player best = null;
        double bestDist = range;
        for (Player p : mc.level.players()) {
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
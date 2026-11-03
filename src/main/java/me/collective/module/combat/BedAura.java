package me.collective.module.combat;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.FindItemResult;
import me.collective.util.InventoryUtil;
import me.collective.util.PlacementUtils;
import me.collective.manager.FriendManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BedItem;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

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
        if (mc.player == null || mc.world == null) return;

        // Подрыв на следующий тик: сервер уже подтвердил кровать, и Grim не
        // увидит interact по блоку, которого у него ещё нет.
        if (pendingExplode != null) {
            BlockPos place = pendingExplode;
            pendingExplode = null;

            if (delayTimer > 0) {
                delayTimer--;
            } else if (mc.interactionManager != null
                && mc.world.getBlockState(place).getBlock() instanceof net.minecraft.block.BedBlock) {
                BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(place), Direction.UP, place, false);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
            delayTimer = delay.getIntValue();
            return;
        }

        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        if (dimensionCheck.getValue()) {
            RegistryKey<World> dim = mc.world.getRegistryKey();
            if (dim != World.NETHER && dim != World.END) return;
        }

        PlayerEntity target = getTarget(range.getFloatValue());
        if (target == null) return;

        FindItemResult bed = InventoryUtil.findInHotbarOrOffhand(stack -> stack.getItem() instanceof BedItem);
        if (!bed.found()) return;

        BlockPos feet = target.getBlockPos();
        BlockPos place = null;
        if (PlacementUtils.canPlace(feet, false)) place = feet;
        else if (PlacementUtils.canPlace(feet.up(1), false)) place = feet.up(1);
        if (place == null) return;

        if (!PlacementUtils.placeBlock(place, bed, rotate.getValue(), true, false)) return;
        pendingExplode = place;
    }

    private PlayerEntity getTarget(double range) {
        PlayerEntity best = null;
        double bestDist = range;
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

package me.collective.module.combat;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.ModeSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.FindItemResult;
import me.collective.util.InventoryUtil;
import me.collective.util.PlacementUtils;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Порт HoleFiller из Meteor Client.
 *
 * <p>Заливает дыры 1x1 вокруг тебя, чтобы враг не ушёл в сейф.
 */
public class HoleFiller extends Module {
    public final NumberSetting range = add(new NumberSetting("Радиус", 4.0, 1.0, 8.0, 0.5));
    public final NumberSetting blocksPerTick = add(new NumberSetting("Блоков/тик", 3.0, 1.0, 8.0, 1.0));
    public final NumberSetting delay = add(new NumberSetting("Задержка", 2.0, 0.0, 10.0, 1.0));
    public final ModeSetting fillWith = add(new ModeSetting("Чем", "Obsidian", "Obsidian", "Web"));
    public final BooleanSetting rotate = add(new BooleanSetting("Ротация", true));
    public final BooleanSetting doubles = add(new BooleanSetting("Двойные дыры", false));

    private int delayTimer = 0;

    public HoleFiller() {
        super("HoleFiller", "Заливает дыры вокруг, лишая врага укрытия.", Category.COMBAT);
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.level == null) return;
        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        FindItemResult mat = fillWith.process("Web")
            ? InventoryUtil.findInHotbarOrOffhand(Items.COBWEB)
            : InventoryUtil.findInHotbarOrOffhand(Items.OBSIDIAN, Items.CRYING_OBSIDIAN, Items.NETHERITE_BLOCK);
        if (!mat.found()) return;

        BlockPos self = mc.player.blockPosition();
        int r = range.getIntValue();

        // Собираем дыры, сортируем по близости: сначала закрываем то,
        // что рядом с игроком, чтобы враг не просочился под бок.
        List<BlockPos> holes = new ArrayList<>();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = -2; y <= 1; y++) {
                    BlockPos pos = self.offset(x, y, z);
                    if (pos.equals(self) || pos.equals(self.above(1))) continue;
                    if (isHole(pos)) holes.add(pos.immutable());
                }
            }
        }

        holes.sort(Comparator.comparingDouble(
            p -> mc.player.distanceToSqr(Vec3.atCenterOf(p))));

        int placed = 0;
        for (BlockPos pos : holes) {
            if (placed >= blocksPerTick.getIntValue()) break;
            if (isOccupedByEntity(pos)) continue;
            if (!PlacementUtils.canPlace(pos, false)) continue;
            if (PlacementUtils.placeBlock(pos, mat, rotate.getValue(), true, false)) {
                placed++;
            }
        }

        if (placed > 0) delayTimer = delay.getIntValue();
    }

    /** Дыру, внутри которой уже кто-то стоит, не заливаем — блоки тратить зря. */
    private boolean isOccupedByEntity(BlockPos pos) {
        AABB box = new AABB(pos);
        for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player) continue;
            if (entity.getBoundingBox().intersects(box)) return true;
        }
        return false;
    }

    private boolean isHole(BlockPos pos) {
        if (!mc.level.getBlockState(pos).isAir()) return false;
        if (!mc.level.getBlockState(pos.above(1)).isAir()) return false;
        if (!isSolid(pos.below())) return false;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(dir);
            if (!isSolid(side)) {
                if (!doubles.getValue()) return false;
                // Двойная: второй блок тоже должен быть дырой
                if (!mc.level.getBlockState(side).isAir()
                    || !mc.level.getBlockState(side.above(1)).isAir()
                    || !isSolid(side.below())) return false;
            }
        }
        return true;
    }

    private boolean isSolid(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        return !state.isAir() && state.isSolid();
    }
}
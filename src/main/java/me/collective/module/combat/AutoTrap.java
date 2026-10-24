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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Порт AutoTrap из ThunderHack (логика TrapModule: Full/Legs/Head).
 *
 * <p>Застраивает цель обсидианом: ноги, голова или полный ящик.
 */
public class AutoTrap extends Module {
    public final NumberSetting range = add(new NumberSetting("Радиус", 5.0, 1.0, 8.0, 0.5));
    public final NumberSetting blocksPerTick = add(new NumberSetting("Блоков/тик", 4.0, 1.0, 8.0, 1.0));
    public final NumberSetting placeDelay = add(new NumberSetting("Задержка", 3.0, 0.0, 10.0, 1.0));
    public final ModeSetting trapMode = add(new ModeSetting("Режим", "Full", "Full", "Legs", "Head"));
    public final BooleanSetting rotate = add(new BooleanSetting("Ротация", true));
    public final BooleanSetting targetMoving = add(new BooleanSetting("Движущиеся цели", false));

    private int delay = 0;

    public AutoTrap() {
        super("AutoTrap", "Закрывает врага в обсидиановую ловушку.", Category.COMBAT);
    }

    @Override
    public void onDisable() {
        delay = 0;
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.world == null) return;
        if (delay > 0) {
            delay--;
            return;
        }

        PlayerEntity target = getTarget();
        if (target == null) return;

        FindItemResult obby = InventoryUtil.findInHotbarOrOffhand(Items.OBSIDIAN, Items.CRYING_OBSIDIAN, Items.NETHERITE_BLOCK);
        if (!obby.found()) return;

        List<BlockPos> blocks = getBlocks(target);
        blocks.sort(Comparator.comparing(b -> mc.player.squaredDistanceTo(b.toCenterPos())));

        int placed = 0;
        for (BlockPos pos : blocks) {
            if (placed >= blocksPerTick.getIntValue()) break;
            if (pos.getSquaredDistance(mc.player.getEntityPos()) > range.getFloatValue() * range.getFloatValue()) continue;
            if (!PlacementUtils.canPlace(pos, false)) continue;
            if (PlacementUtils.placeBlock(pos, obby, rotate.getValue(), true, false)) {
                placed++;
                delay = placeDelay.getIntValue();
            }
        }
    }

    private PlayerEntity getTarget() {
        PlayerEntity best = null;
        double bestDist = range.getFloatValue();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || !p.isAlive() || p.isSpectator() || p.isCreative()) continue;
            if (p.getHealth() + p.getAbsorptionAmount() <= 0) continue;
            if (FriendManager.getInstance().isFriend(p)) continue;
            if (!targetMoving.getValue() && p.getVelocity().lengthSquared() > 0.08) continue;
            double d = mc.player.distanceTo(p);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    private List<BlockPos> getBlocks(PlayerEntity target) {
        List<BlockPos> offsets = new ArrayList<>();
        BlockPos base = target.getBlockPos();
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        switch (trapMode.compute().trim()) {
            case "Legs" -> {
                for (int[] s : sides) offsets.add(base.add(s[0], 0, s[1]));
            }
            case "Head" -> {
                for (int[] s : sides) {
                    offsets.add(base.add(s[0], 1, s[1]));
                    offsets.add(base.add(s[0], 2, s[1]));
                }
            }
            default -> {
                for (int[] s : sides) {
                    offsets.add(base.add(s[0], 0, s[1]));
                    offsets.add(base.add(s[0], 1, s[1]));
                    offsets.add(base.add(s[0], 2, s[1]));
                }
                offsets.add(base.add(0, 3, 0));
            }
        }
        return offsets;
    }
}

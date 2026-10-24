package me.collective.module.combat;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.module.player.SpeedMine;
import me.collective.setting.NumberSetting;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.StreamSupport;

public class AutoCity extends Module {
    public final NumberSetting targetRange = add(new NumberSetting("TargetRange", 5.5, 2.0, 8.0, 0.5));
    public final NumberSetting breakRange = add(new NumberSetting("BreakRange", 4.5, 2.0, 6.0, 0.5));

    private BlockPos cityPos = null;

    public AutoCity() {
        super("AutoCity", "Автоматически копает блоки сурраунда вокруг цели для установки кристаллов в лицо.", Category.COMBAT);
    }

    @Override
    public void onDisable() {
        cityPos = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        if (cityPos != null) {
            BlockState state = mc.level.getBlockState(cityPos);
            if (state.isAir() || !isBlastResistant(state)) {
                cityPos = null;
                setToggled(false);
                return;
            }
            return;
        }

        double r = targetRange.getValue();
        double rSq = r * r;

        Player target = StreamSupport.stream(mc.level.players().spliterator(), false)
            .filter(p -> p != mc.player && p.isAlive() && !p.isCreative() && !p.isSpectator())
            .filter(p -> !me.collective.manager.FriendManager.getInstance().isFriend(p))
            .filter(p -> mc.player.distanceToSqr(p) <= rSq)
            .min(Comparator.comparingDouble(p -> mc.player.distanceToSqr(p)))
            .orElse(null);

        if (target == null) return;

        BlockPos targetPos = target.blockPosition();
        Direction[] sides = { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };

        double maxReachSq = breakRange.getValue() * breakRange.getValue();

        List<BlockPos> candidates = new ArrayList<>();
        for (Direction side : sides) {
            BlockPos pos = targetPos.relative(side);
            if (mc.player.distanceToSqr(Vec3.atCenterOf(pos)) > maxReachSq) continue;
            BlockState state = mc.level.getBlockState(pos);
            if (isBlastResistant(state)) {
                candidates.add(pos);
            }
        }

        // Приоритет блокам, над которыми воздух (чтобы сразу поставить кристалл)
        candidates.sort((a, b) -> {
            boolean aAir = mc.level.getBlockState(a.above()).isAir();
            boolean bAir = mc.level.getBlockState(b.above()).isAir();
            if (aAir != bAir) return aAir ? -1 : 1;
            return Double.compare(mc.player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(a)), mc.player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(b)));
        });

        if (!candidates.isEmpty()) {
            BlockPos pos = candidates.get(0);
            cityPos = pos;
            mc.player.sendSystemMessage(Component.literal("§7[§cAutoCity§7] §aMining city block at: §e" + pos.toShortString()));

            if (SpeedMine.INSTANCE != null) {
                if (!SpeedMine.INSTANCE.isToggled()) {
                    SpeedMine.INSTANCE.setToggled(true);
                    mc.player.sendSystemMessage(Component.literal("§7[§cAutoCity§7] §6Сам включил SpeedMine — её настройки (скорость/обход/дальность) теперь копают твои блоки и могут флагать Grim."));
                }
                SpeedMine.INSTANCE.mineBlock(cityPos, Direction.UP);
            }
        }
    }

    private boolean isBlastResistant(BlockState state) {
        return state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN)
            || state.is(Blocks.NETHERITE_BLOCK) || state.is(Blocks.RESPAWN_ANCHOR)
            || state.is(Blocks.ENDER_CHEST);
    }
}
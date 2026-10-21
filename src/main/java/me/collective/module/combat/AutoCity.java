package me.collective.module.combat;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.module.player.SpeedMine;
import me.collective.setting.NumberSetting;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Comparator;
import java.util.stream.StreamSupport;

public class AutoCity extends Module {
    public final NumberSetting targetRange = add(new NumberSetting("TargetRange", 5.5, 2.0, 8.0, 0.5));
    public final NumberSetting breakRange = add(new NumberSetting("BreakRange", 4.5, 2.0, 6.0, 0.5));

    private BlockPos cityPos = null;

    public AutoCity() {
        super("AutoCity", "Automatically mines your opponent's surround blocks to allow crystal face-placing.", Category.COMBAT);
    }

    @Override
    public void onDisable() {
        cityPos = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (cityPos != null) {
            BlockState state = mc.world.getBlockState(cityPos);
            if (state.isAir() || !isBlastResistant(state)) {
                cityPos = null;
                setToggled(false);
                return;
            }
            return;
        }

        double r = targetRange.getValue();
        double rSq = r * r;

        PlayerEntity target = StreamSupport.stream(mc.world.getPlayers().spliterator(), false)
            .filter(p -> p != mc.player && p.isAlive() && !p.isCreative() && !p.isSpectator())
            .filter(p -> !me.collective.manager.FriendManager.getInstance().isFriend(p))
            .filter(p -> mc.player.squaredDistanceTo(p) <= rSq)
            .min(Comparator.comparingDouble(p -> mc.player.squaredDistanceTo(p)))
            .orElse(null);

        if (target == null) return;

        BlockPos targetPos = target.getBlockPos();
        Direction[] sides = { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };

        double maxReachSq = breakRange.getValue() * breakRange.getValue();

        for (Direction side : sides) {
            BlockPos pos = targetPos.offset(side);
            if (mc.player.squaredDistanceTo(pos.toCenterPos()) > maxReachSq) continue;

            BlockState state = mc.world.getBlockState(pos);
            if (isBlastResistant(state)) {
                cityPos = pos;
                mc.player.sendMessage(Text.literal("§7[§cAutoCity§7] §aMining city block at: §e" + pos.toShortString()), false);

                if (SpeedMine.INSTANCE != null) {
                    if (!SpeedMine.INSTANCE.isToggled()) {
                        SpeedMine.INSTANCE.setToggled(true);
                        mc.player.sendMessage(Text.literal("§7[§cAutoCity§7] §6Сам включил SpeedMine — её настройки (скорость/обход/дальность) теперь копают твои блоки и могут флагать Grim."), false);
                    }
                    SpeedMine.INSTANCE.mineBlock(cityPos, Direction.UP);
                }
                break;
            }
        }
    }

    private boolean isBlastResistant(BlockState state) {
        return state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.CRYING_OBSIDIAN) || state.isOf(Blocks.NETHERITE_BLOCK) || state.isOf(Blocks.RESPAWN_ANCHOR);
    }
}

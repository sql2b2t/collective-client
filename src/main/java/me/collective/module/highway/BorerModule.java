package me.collective.module.highway;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.EnumSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.HighwayUtils;
import me.collective.util.PacketUtil;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.stream.IntStream;

import static me.collective.util.HighwayUtils.backward;
import static me.collective.util.HighwayUtils.forward;
import static me.collective.util.HighwayUtils.left;
import static me.collective.util.HighwayUtils.right;

/**
 * Порт BorerModule: пакетный майнер тоннелей (START+STOP пакеты).
 * Конкретные направления — в наследниках.
 */
public abstract class BorerModule extends Module {
    public enum Shape {
        THIN,
        HIGHWAY
    }

    public final EnumSetting<Shape> mode = add(new EnumSetting<>("Shape", Shape.HIGHWAY));

    public final NumberSetting extForward;
    public final NumberSetting extBackward;
    public final NumberSetting xOffset;
    public final NumberSetting zOffset;

    public final NumberSetting keepY = add(new NumberSetting("KeepY", 119, -1, 255, 1));

    protected int packets = 0;
    protected long lastUpdateTime = 0;
    protected BlockPos playerPos = BlockPos.ORIGIN;

    protected BorerModule(String name, String description, int extForwards, int extBackwards, int xOff, int zOff) {
        super(name, description, Category.HIGHWAY);

        extForward = add(new NumberSetting("ExtForward", extForwards, 1, 6, 1));
        extBackward = add(new NumberSetting("ExtBackward", extBackwards, 1, 6, 1));
        this.xOffset = add(new NumberSetting("XOffset", xOff, -2, 2, 1));
        this.zOffset = add(new NumberSetting("ZOffset", zOff, -2, 2, 1));
    }

    @EventHandler
    public abstract void onTick(TickEvent.Pre event);

    protected void do2x3(BlockPos playerPos) {
        IntStream.rangeClosed(-extBackward.getIntValue(), extForward.getIntValue()).forEach(i -> {
            breakBlock(forward(playerPos, i));
            breakBlock(forward(playerPos, i).up());
            breakBlock(forward(playerPos, i).up(2));
            breakBlock(left(forward(playerPos, i), 1));
            breakBlock(left(forward(playerPos, i), 1).up());
            breakBlock(left(forward(playerPos, i), 1).up(2));
        });
    }

    protected void doHighway4(BlockPos playerPos) {
        IntStream.rangeClosed(-extBackward.getIntValue(), extForward.getIntValue()).forEach(i -> {
            breakBlock(forward(playerPos, i));
            breakBlock(forward(playerPos, i).up());
            breakBlock(forward(playerPos, i).up(2));
            breakBlock(forward(playerPos, i).up(3));

            breakBlock(right(forward(playerPos, i), 1));
            breakBlock(right(forward(playerPos, i), 1).up());
            breakBlock(right(forward(playerPos, i), 1).up(2));
            breakBlock(right(forward(playerPos, i), 1).up(3));

            breakBlock(right(forward(playerPos, i), 2).up());
            breakBlock(right(forward(playerPos, i), 2).up(2));
            breakBlock(right(forward(playerPos, i), 2).up(3));

            breakBlock(left(forward(playerPos, i), 1));
            breakBlock(left(forward(playerPos, i), 1).up());
            breakBlock(left(forward(playerPos, i), 1).up(2));
            breakBlock(left(forward(playerPos, i), 1).up(3));

            breakBlock(left(forward(playerPos, i), 2));
            breakBlock(left(forward(playerPos, i), 2).up());
            breakBlock(left(forward(playerPos, i), 2).up(2));
            breakBlock(left(forward(playerPos, i), 2).up(3));

            breakBlock(left(forward(playerPos, i), 3).up());
            breakBlock(left(forward(playerPos, i), 3).up(2));
            breakBlock(left(forward(playerPos, i), 3).up(3));
        });
    }

    protected void breakBlock(BlockPos blockPos) {
        if (mc.player == null || mc.world == null) return;

        if (packets >= 130 || mc.world.getBlockState(blockPos).isReplaceable()) {
            return;
        }

        PacketUtil.sendSilently(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, blockPos, Direction.UP));
        PacketUtil.sendSilently(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, blockPos, Direction.UP));
        packets += 2;

        mc.player.getInventory().updateItems();
    }

    protected BlockPos flooredPlayerPos() {
        return new BlockPos(
            (int) Math.floor(mc.player.getX()),
            keepY.getIntValue() != -1 ? keepY.getIntValue() : (int) Math.floor(mc.player.getY()),
            (int) Math.floor(mc.player.getZ()));
    }
}

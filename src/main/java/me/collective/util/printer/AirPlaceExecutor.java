package me.collective.util.printer;

import me.collective.util.GrimUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Исполнитель установки блоков (порт из BepHax NEW-SRC).
 *
 * Адаптация под 1.21.11: {@code BlockStatePredictionHandler} удалён из клиента,
 * поэтому вместо {@code prediction.currentSequence()} используем собственный
 * монотонный счётчик {@link GrimUtils#nextSequence()} (семантика сохранена,
 * сервер игнорирует последовательность для ACK).
 */
public final class AirPlaceExecutor {
    private AirPlaceExecutor() {
    }

    public static void place(BlockHitResult hit, BlockPos placePos, InteractionHand hand, BlockState predicted, Method method) {
        if (hit.isInsideBlock()) {
            airPlace(hit, predicted, method);
        } else {
            silentPlace(hit, placePos, predicted, hand, true);
        }
    }

    public static void airPlace(BlockHitResult hit, BlockState predicted, Method method) {
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().world != null && Minecraft.getInstance().getNetworkHandler() != null) {
            BlockHitResult wireHit = new BlockHitResult(hit.getPos(), hit.getSide(), hit.getBlockPos(), false);
            if (method == Method.Grim) {
                grimAirPlace(wireHit, predicted);
            } else {
                silentPlace(wireHit, hit.getBlockPos(), predicted, InteractionHand.MAIN_HAND, true);
            }
        }
    }

    private static void grimAirPlace(BlockHitResult wireHit, BlockState predicted) {
        ClientPlayNetworkHandler connection = Minecraft.getInstance().getNetworkHandler();

        // 1.21.11: BlockStatePredictionHandler нет — шлём пакеты напрямую с собственным sequence.
        connection.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
        connection.sendPacket(new PlayerInteractBlockC2SPacket(InteractionHand.OFF_HAND, wireHit, GrimUtils.nextSequence()));
        connection.sendPacket(new HandSwingC2SPacket(InteractionHand.OFF_HAND));
        connection.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
        if (predicted != null) {
            Minecraft.getInstance().world.setBlockState(wireHit.getBlockPos(), predicted, 11);
        }
    }

    public static void silentPlace(BlockHitResult hit, BlockPos placePos, BlockState predicted, InteractionHand hand, boolean visualSwing) {
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().world != null && Minecraft.getInstance().getNetworkHandler() != null) {
            ClientPlayNetworkHandler connection = Minecraft.getInstance().getNetworkHandler();

            // 1.21.11: BlockStatePredictionHandler удалён — sequence берём из GrimUtils.
            connection.sendPacket(new PlayerInteractBlockC2SPacket(hand, hit, GrimUtils.nextSequence()));
            if (predicted != null) {
                Minecraft.getInstance().world.setBlockState(placePos, predicted, 11);
            }

            if (visualSwing && hand == InteractionHand.MAIN_HAND) {
                Minecraft.getInstance().player.swingHand(InteractionHand.MAIN_HAND);
            } else {
                connection.sendPacket(new HandSwingC2SPacket(hand));
            }
        }
    }

    public enum Method {
        Default,
        Grim;
    }
}
package me.collective.util.printer;

import me.collective.util.GrimUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
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
        if (hit.isInside()) {
            airPlace(hit, predicted, method);
        } else {
            silentPlace(hit, placePos, predicted, hand, true);
        }
    }

    public static void airPlace(BlockHitResult hit, BlockState predicted, Method method) {
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().level != null && Minecraft.getInstance().getConnection() != null) {
            BlockHitResult wireHit = new BlockHitResult(hit.getLocation(), hit.getDirection(), hit.getBlockPos(), false);
            if (method == Method.Grim) {
                grimAirPlace(wireHit, predicted);
            } else {
                silentPlace(wireHit, hit.getBlockPos(), predicted, InteractionHand.MAIN_HAND, true);
            }
        }
    }

    private static void grimAirPlace(BlockHitResult wireHit, BlockState predicted) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();

        // 1.21.11: BlockStatePredictionHandler нет — шлём пакеты напрямую с собственным sequence.
        connection.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
        connection.send(new ServerboundUseItemOnPacket(InteractionHand.OFF_HAND, wireHit, GrimUtils.nextSequence()));
        connection.send(ServerboundPunchPacket.INSTANCE);
        connection.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
        if (predicted != null) {
            Minecraft.getInstance().level.setBlock(wireHit.getBlockPos(), predicted, 11, 3);
        }
    }

    public static void silentPlace(BlockHitResult hit, BlockPos placePos, BlockState predicted, InteractionHand hand, boolean visualSwing) {
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().level != null && Minecraft.getInstance().getConnection() != null) {
            ClientPacketListener connection = Minecraft.getInstance().getConnection();

            // 1.21.11: BlockStatePredictionHandler удалён — sequence берём из GrimUtils.
            connection.send(new ServerboundUseItemOnPacket(hand, hit, GrimUtils.nextSequence()));
            if (predicted != null) {
                Minecraft.getInstance().level.setBlock(placePos, predicted, 11, 3);
            }

            if (visualSwing && hand == InteractionHand.MAIN_HAND) {
                me.collective.util.Utils.swingHand(InteractionHand.MAIN_HAND);
            } else {
                connection.send(ServerboundPunchPacket.INSTANCE);
            }
        }
    }

    public enum Method {
        Default,
        Grim;
    }
}
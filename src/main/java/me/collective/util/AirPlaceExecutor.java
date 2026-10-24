package me.collective.util;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class AirPlaceExecutor {
    private static final Minecraft mc = Minecraft.getInstance();

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
        airPlace(hit, hit.getBlockPos(), predicted, method);
    }

    public static void airPlace(BlockHitResult hit, BlockPos placePos, BlockState predicted, Method method) {
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return;

        BlockHitResult wireHit = new BlockHitResult(hit.getLocation(), hit.getDirection(), hit.getBlockPos(), false);

        if (method == Method.Grim) {
            grimAirPlace(wireHit, placePos, predicted);
        } else {
            silentPlace(wireHit, placePos, predicted, InteractionHand.MAIN_HAND, true);
        }
    }

    private static void grimAirPlace(BlockHitResult wireHit, BlockPos placePos, BlockState predicted) {
        ClientPacketListener connection = mc.getConnection();
        if (connection == null || mc.level == null) return;

        int seq = GrimUtils.nextSequence();
        connection.send(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
        connection.send(new ServerboundUseItemOnPacket(
            InteractionHand.OFF_HAND, wireHit, seq));
        connection.send(ServerboundPunchPacket.INSTANCE);
        connection.send(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));

        if (predicted != null) {
            mc.level.setBlock(placePos, predicted, 11, 3);
        }
    }

    public static void silentPlace(BlockHitResult hit, BlockPos placePos, BlockState predicted, InteractionHand hand, boolean visualSwing) {
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return;

        ClientPacketListener connection = mc.getConnection();

        int seq = GrimUtils.nextSequence();
        connection.send(new ServerboundUseItemOnPacket(hand, hit, seq));

        if (predicted != null) {
            mc.level.setBlock(placePos, predicted, 11, 3);
        }

        if (visualSwing && hand == InteractionHand.MAIN_HAND) {
            me.collective.util.Utils.swingHand(InteractionHand.MAIN_HAND);
        } else {
            connection.send(ServerboundPunchPacket.INSTANCE);
        }
    }

    public enum Method {
        Default,
        Grim
    }
}
package me.collective.util;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
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
        if (hit.isInsideBlock()) {
            airPlace(hit, predicted, method);
        } else {
            silentPlace(hit, placePos, predicted, hand, true);
        }
    }

    public static void airPlace(BlockHitResult hit, BlockState predicted, Method method) {
        airPlace(hit, hit.getBlockPos(), predicted, method);
    }

    public static void airPlace(BlockHitResult hit, BlockPos placePos, BlockState predicted, Method method) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;

        BlockHitResult wireHit = new BlockHitResult(hit.getPos(), hit.getSide(), hit.getBlockPos(), false);

        if (method == Method.Grim) {
            grimAirPlace(wireHit, placePos, predicted);
        } else {
            silentPlace(wireHit, placePos, predicted, InteractionHand.MAIN_HAND, true);
        }
    }

    private static void grimAirPlace(BlockHitResult wireHit, BlockPos placePos, BlockState predicted) {
        ClientPlayNetworkHandler connection = mc.getNetworkHandler();
        if (connection == null || mc.world == null) return;

        try (PendingUpdateManager prediction = mc.world.getPendingUpdateManager().incrementSequence()) {
            connection.sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
            connection.sendPacket(new PlayerInteractBlockC2SPacket(
                InteractionHand.OFF_HAND, wireHit, prediction.getSequence()));
            connection.sendPacket(new HandSwingC2SPacket(InteractionHand.OFF_HAND));
            connection.sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));

            if (predicted != null) {
                mc.world.setBlockState(placePos, predicted, 11);
            }
        }
    }

    public static void silentPlace(BlockHitResult hit, BlockPos placePos, BlockState predicted, InteractionHand hand, boolean visualSwing) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;

        ClientPlayNetworkHandler connection = mc.getNetworkHandler();

        try (PendingUpdateManager prediction = mc.world.getPendingUpdateManager().incrementSequence()) {
            connection.sendPacket(new PlayerInteractBlockC2SPacket(hand, hit, prediction.getSequence()));

            if (predicted != null) {
                mc.world.setBlockState(placePos, predicted, 11);
            }
        }

        if (visualSwing && hand == InteractionHand.MAIN_HAND) {
            mc.player.swingHand(InteractionHand.MAIN_HAND);
        } else {
            connection.sendPacket(new HandSwingC2SPacket(hand));
        }
    }

    public enum Method {
        Default,
        Grim
    }
}
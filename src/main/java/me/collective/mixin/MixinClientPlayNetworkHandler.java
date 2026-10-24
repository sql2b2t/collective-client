package me.collective.mixin;

import me.collective.CollectiveClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Оповещение всех модулей о смене мира: вход на сервер (game join),
 * респавн и смена измерения через PlayerRespawn.
 */
@Mixin(ClientPacketListener.class)
public abstract class MixinClientPlayNetworkHandler {

    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void collective$onGameJoin(ClientboundLoginPacket packet, CallbackInfo ci) {
        CollectiveClient.onWorldChange();
    }

    @Inject(method = "handleRespawn", at = @At("TAIL"))
    private void collective$onPlayerRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        CollectiveClient.onWorldChange();
    }
}
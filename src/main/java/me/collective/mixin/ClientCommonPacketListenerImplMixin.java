package me.collective.mixin;

import me.collective.client.ClientContext;
import me.collective.client.modules.misc.NoPacketKickModule;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerImplMixin implements PacketListener {
   private static NoPacketKickModule noPacketKickModule;
   private static NoPacketKickModule noPacketKickModule() {
      if (noPacketKickModule == null) {
         noPacketKickModule = ClientContext.getModule(NoPacketKickModule.class);
      }
      return noPacketKickModule;
   }

   @Inject(method = "onDisconnect", at = @At("HEAD"), cancellable = true)
   public void suppressDecoderExceptionDisconnect(DisconnectionDetails details, CallbackInfo callback) {
      if (noPacketKickModule().isToggled()
         && this.protocol() == ConnectionProtocol.PLAY
         && details.reason().getString().contains("io.netty.handler.codec.DecoderException")) {
         callback.cancel();
      }
   }
}

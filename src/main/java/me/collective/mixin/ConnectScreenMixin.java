package me.collective.mixin;

import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.events.ConnectScreenConnectEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerData.Type;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public class ConnectScreenMixin {

   @Inject(method = "connect", at = @At("HEAD"))
   private void recordServerConnection(
      Minecraft minecraft, ServerAddress address, ServerData serverData, TransferState transferState, CallbackInfo callback
   ) {
      ServerData resolvedServerData = serverData;
      if (resolvedServerData == null) {
         resolvedServerData = new ServerData("Collective", address.getHost(), Type.OTHER);
      }

      ClientAccess.eventBus
         .post(new ConnectScreenConnectEvent(resolvedServerData.ip));
      ClientContext.serverStateManager
         .setServerData(resolvedServerData);
   }
}

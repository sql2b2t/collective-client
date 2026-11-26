package me.collective.mixin;

import io.netty.channel.Channel;
import me.collective.client.access.BotConnectionAccess;
import me.collective.client.bots.proxy.ProxyConfig;
import me.collective.client.bots.proxy.ProxyManager;
import me.collective.client.bots.proxy.SocksProxyConnector;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net/minecraft/network/Connection$1")
public class ConnectionAnonymousMixin {
   @Shadow(remap = false)
   @Final
   Connection val$connection;

   @Inject(method = "initChannel(Lio/netty/channel/Channel;)V", at = @At("HEAD"))
   private void collective$installProxy(Channel channel, CallbackInfo callback) {
      BotConnectionAccess access = (BotConnectionAccess) this.val$connection;
      if (access != null) {
         ProxyConfig proxy = access.isBotConnection() ? access.getProxy() : ProxyManager.INSTANCE.getDefaultProxy();
         if (proxy != null) {
            SocksProxyConnector.INSTANCE.install(channel, proxy);
         }
      }
   }
}

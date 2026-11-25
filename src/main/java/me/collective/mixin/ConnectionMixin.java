package me.collective.mixin;

import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.access.BotConnectionAccess;
import me.collective.client.bots.proxy.ProxyConfig;
import me.collective.client.events.PacketEvent;
import me.collective.client.modules.exploit.PingSpoofModule;
import me.collective.client.modules.misc.NoPacketKickModule;
import me.collective.client.utils.network.PingSpoofDelayHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ConnectionMixin implements BotConnectionAccess {
   private static NoPacketKickModule noPacketKickModule;
   private static PingSpoofModule pingSpoofModule;
   private static NoPacketKickModule noPacketKickModule() {
      if (noPacketKickModule == null) {
         noPacketKickModule = ClientContext.getModule(NoPacketKickModule.class);
      }
      return noPacketKickModule;
   }
   private static PingSpoofModule pingSpoofModule() {
      if (pingSpoofModule == null) {
         pingSpoofModule = ClientContext.getModule(PingSpoofModule.class);
      }
      return pingSpoofModule;
   }
   @Shadow
   private Channel channel;
   @Shadow
   @Final
   private PacketFlow receiving;

   @Shadow
   public abstract void send(Packet<?> packet);

   @Unique
   private boolean collective$isBot = false;
   @Unique
   private ProxyConfig collective$proxyConfig = null;

   @Override
   public boolean isBotConnection() {
      return this.collective$isBot;
   }

   @Override
   public void setBotConnection(boolean value) {
      this.collective$isBot = value;
   }

   @Override
   public ProxyConfig getProxy() {
      return this.collective$proxyConfig;
   }

   @Override
   public void setProxy(ProxyConfig proxy) {
      this.collective$proxyConfig = proxy;
   }

   @Override
   public void sendPacket(Packet<?> packet) {
      this.send(packet);
   }

   @Inject(method = "channelActive", at = @At("TAIL"))
   private void installPingSpoofDelayHandler(ChannelHandlerContext context, CallbackInfo callback) {
      if (this.collective$isBot || this.receiving != PacketFlow.CLIENTBOUND) {
         return;
      }
      ChannelPipeline pipeline = context.pipeline();
      if (pipeline.get(PingSpoofDelayHandler.NAME) == null) {
         pipeline.addFirst(PingSpoofDelayHandler.NAME, new PingSpoofDelayHandler(
            pingSpoofModule(), () -> ((Connection)(Object)this).getPacketListener() instanceof ClientGamePacketListener
         ));
      }
   }

   @Inject(method = "channelRead0*", at = @At("HEAD"), cancellable = true)
   public void onPacketReceive(
      ChannelHandlerContext context, Packet packet, CallbackInfo callback
   ) {
      if (this.collective$isBot) {
         return;
      }
      if (this.channel.isOpen() && packet != null) {
         try {
            PacketEvent.Receive event = new PacketEvent.Receive(packet);
            ClientAccess.eventBus
               .post(event);
            if (event.isCancelled()) {
               callback.cancel();
            }
         } catch (Exception ignoredException) {
         }
      }
   }

   @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
   private void onPacketSend(
      Packet packet, PacketSendListener listener, boolean flush, CallbackInfo callback
   ) {
      if (this.collective$isBot) {
         return;
      }
      if (this.receiving == PacketFlow.CLIENTBOUND) {
         try {
            PacketEvent.Send event = new PacketEvent.Send(packet);
            ClientAccess.eventBus
               .post(event);
            if (event.isCancelled()) {
               callback.cancel();
            }
         } catch (Exception ignoredException) {
         }
      }
   }

   @Inject(method = "doSendPacket", at = @At("TAIL"))
   private void onPacketPostSend(
      Packet packet, PacketSendListener listener, boolean flush, CallbackInfo callback
   ) {
      if (this.collective$isBot) {
         return;
      }
      if (this.receiving == PacketFlow.CLIENTBOUND) {
         try {
            PacketEvent.PostSend event = new PacketEvent.PostSend(packet);
            ClientAccess.eventBus
               .post(event);
         } catch (Exception ignoredException) {
         }
      }
   }

   @Inject(method = "exceptionCaught", at = @At("HEAD"), cancellable = true)
   private void onConnectionException(
      ChannelHandlerContext context, Throwable failure, CallbackInfo callback
   ) {
      if (this.collective$isBot) {
         return;
      }
      if (noPacketKickModule().isToggled()) {
         noPacketKickModule().logInterceptedException(failure);
         callback.cancel();
      }
   }
}

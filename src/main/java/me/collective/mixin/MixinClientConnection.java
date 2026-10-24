package me.collective.mixin;

import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.channel.ChannelHandlerContext;
import me.collective.event.EventBus;
import me.collective.event.events.PacketEvent;
import me.collective.proxy.ProxyEntry;
import me.collective.proxy.ProxyManager;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * Подключение через выбранный прокси (SOCKS5/HTTP).
 *
 * <p>Перехватывает финальный connect: вместо сервера цепляемся к прокси,
 * а целевой адрес отдаём в рукопожатии (без локального DNS-резолва).
 */
@Mixin(targets = "net.minecraft.network.ClientConnection")
public abstract class MixinClientConnection {

    private static final String CONNECT_METHOD = "connect(Ljava/net/InetSocketAddress;"
        + "Lnet/minecraft/network/NetworkingBackend;"
        + "Lnet/minecraft/network/ClientConnection;)Lio/netty/channel/ChannelFuture;";

    /** Ванильный хендлер пайплайна — читаем рефлексией, второго инжекта не надо. */
    @Unique
    private static ChannelHandler collective$originalHandler(Bootstrap bootstrap) {        try {
            java.lang.reflect.Field f = AbstractBootstrap.class.getDeclaredField("handler");
            f.setAccessible(true);
            return (ChannelHandler) f.get(bootstrap);
        } catch (Exception e) {
            return null;
        }
    }

    @Redirect(
        method = CONNECT_METHOD,
        at = @At(value = "INVOKE",
            target = "Lio/netty/bootstrap/Bootstrap;connect(Ljava/net/InetAddress;I)Lio/netty/channel/ChannelFuture;")
    )
    private static ChannelFuture collective$proxyConnect(Bootstrap bootstrap, InetAddress address, int port) {
        ProxyEntry proxy = ProxyManager.INSTANCE.getActive();
        if (proxy == null || !ProxyManager.INSTANCE.isEnabled()) {
            return bootstrap.connect(address, port);
        }

        ChannelHandler original = collective$originalHandler(bootstrap);
        if (original == null) {
            return bootstrap.connect(address, port);
        }
        InetSocketAddress proxyAddr = new InetSocketAddress(proxy.host(), proxy.port());
        bootstrap.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                // Хендлер сам цепляется к прокси и пробрасывает цель из connect()
                if (proxy.type() == ProxyEntry.ProxyType.SOCKS5) {
                    if (proxy.hasAuth()) {
                        ch.pipeline().addLast(new Socks5ProxyHandler(proxyAddr, proxy.user(), proxy.pass()));
                    } else {
                        ch.pipeline().addLast(new Socks5ProxyHandler(proxyAddr));
                    }
                } else {
                    if (proxy.hasAuth()) {
                        ch.pipeline().addLast(new HttpProxyHandler(proxyAddr, proxy.user(), proxy.pass()));
                    } else {
                        ch.pipeline().addLast(new HttpProxyHandler(proxyAddr));
                    }
                }
                ch.pipeline().addLast(original);
            }
        });
        // Цель остаётся серверной — хендлер завернёт TCP на прокси сам
        return bootstrap.connect(address, port);
    }

    /** Исходящие пакеты в шину (HEAD, отмена глушит отправку). */
    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void collective$onSend(Packet<?> packet, CallbackInfo ci) {
        PacketEvent.Send event = new PacketEvent.Send(packet);
        EventBus.INSTANCE.post(event);
        if (event.isCancelled()) ci.cancel();
    }

    /** Входящие пакеты в шину (HEAD, отмена глушит обработку). */
    @Inject(
        method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V",
        at = @At("HEAD"), cancellable = true
    )
    private void collective$onReceive(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        PacketEvent.Receive event = new PacketEvent.Receive(packet);
        EventBus.INSTANCE.post(event);
        if (event.isCancelled()) ci.cancel();
    }
}
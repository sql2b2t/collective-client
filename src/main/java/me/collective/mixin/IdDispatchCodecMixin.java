package me.collective.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.collective.client.ClientContext;
import me.collective.client.modules.misc.NoPacketKickModule;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.IdDispatchCodec;
import net.minecraft.network.codec.StreamCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(IdDispatchCodec.class)
public class IdDispatchCodecMixin {
   private static NoPacketKickModule noPacketKickModule;
   private static NoPacketKickModule noPacketKickModule() {
      if (noPacketKickModule == null) {
         noPacketKickModule = ClientContext.getModule(NoPacketKickModule.class);
      }
      return noPacketKickModule;
   }

   @WrapOperation(method = "decode", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/codec/StreamCodec;decode(Ljava/lang/Object;)Ljava/lang/Object;"))
   private Object decodeWithPacketKickHandling(
      StreamCodec codec, Object input, Operation original
   ) {
      if (!noPacketKickModule().isToggled()) {
         return original.call(codec, input);
      }

      try {
         return original.call(codec, input);
      } catch (DecoderException decoderFailure) {
         noPacketKickModule().logInterceptedException(decoderFailure);
         if (input instanceof ByteBuf buffer) {
            buffer.skipBytes(buffer.readableBytes());
         }

         throw decoderFailure;
      }
   }
}

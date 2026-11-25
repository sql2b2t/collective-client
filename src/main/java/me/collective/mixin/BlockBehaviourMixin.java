package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.Util;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.apache.commons.codec.digest.MurmurHash3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockBehaviour.BlockStateBase.class)
public class BlockBehaviourMixin {
   @Unique
   private static long seedHashSalt;

   @Inject(method = "<clinit>", at = @At("TAIL"))
   private static void initializeSeedHashSalt(CallbackInfo callback) {
      seedHashSalt = Util.getNanos() ^ -7046029254386353131L;
   }

   @ModifyReturnValue(method = "getSeed", at = @At("RETURN"))
   private long hashBlockSeed(long originalSeed) {
      return MurmurHash3.hash32(originalSeed, seedHashSalt);
   }
}

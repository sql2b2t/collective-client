package me.collective.mixin;

import com.mojang.authlib.GameProfile;
import me.collective.client.access.PlayerInfoJoinTime;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerInfo.class)
public class PlayerInfoMixin implements PlayerInfoJoinTime {
   @Unique
   private long joinTimeMillis;

   @Inject(method = "<init>", at = @At("TAIL"))
   private void captureJoinTime(
      GameProfile profile, boolean secureChatEnforced, CallbackInfo callback
   ) {
      this.joinTimeMillis = System.currentTimeMillis();
   }

   @Override
   public long collective$getJoinTime() {
      return this.joinTimeMillis;
   }
}

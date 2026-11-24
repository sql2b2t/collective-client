package me.collective.mixin;

import com.mojang.authlib.minecraft.UserApiService.UserFlag;
import com.mojang.authlib.minecraft.UserApiService.UserProperties;
import com.mojang.authlib.yggdrasil.YggdrasilUserApiService;
import java.util.Map;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(YggdrasilUserApiService.class)
public class YggdrasilUserApiServiceMixin {

   @Inject(method = "fetchProperties()Lcom/mojang/authlib/minecraft/UserApiService$UserProperties;", at = @At("HEAD"), cancellable = true)
   private static void returnPermittedUserProperties(CallbackInfoReturnable<UserProperties> callback) {
      callback.setReturnValue(new UserProperties(Set.of(UserFlag.CHAT_ALLOWED, UserFlag.REALMS_ALLOWED, UserFlag.SERVERS_ALLOWED), Map.of()));
   }
}

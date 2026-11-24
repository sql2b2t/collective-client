package me.collective.mixin;

import com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslator;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.collective.client.integrations.ProtocolVersionAccess;
import me.collective.client.integrations.ProtocolCompatibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(ProtocolTranslator.class)
public class ProtocolTranslatorMixin {
   @Shadow(remap = false)
   private static ProtocolVersion targetVersion;

   @Inject(method = "setTargetVersion(Lcom/viaversion/viaversion/api/protocol/version/ProtocolVersion;Z)V", at = @At("HEAD"), remap = false)
   private static void syncTargetProtocolVersion(
      ProtocolVersion version, boolean ignoredProtocolFlag, CallbackInfo callback
   ) {
      ProtocolCompatibility.setTargetVersion((ProtocolVersionAccess)version);
   }

   @Inject(method = "<clinit>", at = @At("TAIL"))
   private static void initializeTargetProtocolVersion(CallbackInfo callback) {
      ProtocolCompatibility.setTargetVersion((ProtocolVersionAccess)targetVersion);
   }
}

package me.collective.mixin;

import net.minecraft.CrashReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CrashReport.class)
public class CrashReportMixin {

   @Inject(method = "getDetails(Ljava/lang/StringBuilder;)V", at = @At("TAIL"))
   public void appendClientCrashDetails(StringBuilder details, CallbackInfo callback) {
      details.append("\n\n-- https://github.com/spectrumdevs/collective --\n");
      details.append("Details:\n");
      details.append("\tMagic: b9edAc3b3Ba4eD721B4efaEe\n\n");
   }
}

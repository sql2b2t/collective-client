package me.collective.mixin;

import me.collective.client.ClientContext;
import me.collective.client.modules.player.FreecamModule;
import me.collective.client.modules.render.XrayModule;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VisGraph.class)
public class VisGraphMixin {
   private static FreecamModule freecam;
   private static FreecamModule freecam() {
      if (freecam == null) {
         freecam = ClientContext.getModule(FreecamModule.class);
      }
      return freecam;
   }

   @Inject(method = "setOpaque", at = @At("HEAD"), cancellable = true)
   private void suppressOpaqueBlockMarking(BlockPos blockPos, CallbackInfo callback) {
      XrayModule xray = XrayModule.getInstance();
      FreecamModule freecamModule = freecam();
      if ((xray != null && xray.isToggled())
         || (freecamModule != null && freecamModule.isToggled())) {
         callback.cancel();
      }
   }
}

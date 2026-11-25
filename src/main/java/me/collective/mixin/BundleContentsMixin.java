package me.collective.mixin;

import me.collective.client.ClientContext;
import me.collective.client.managers.ModuleRegistry;
import me.collective.client.modules.render.TooltipsModule;
import net.minecraft.world.item.component.BundleContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BundleContents.class)
public abstract class BundleContentsMixin {
   private static TooltipsModule tooltipsModule;
   private static TooltipsModule tooltipsModule() {
      if (tooltipsModule == null) {
         tooltipsModule = ClientContext.getModule(TooltipsModule.class);
      }
      return tooltipsModule;
   }

   @Shadow
   public abstract int size();

   @Inject(method = "getNumberOfItemsToShow", at = @At("HEAD"), cancellable = true)
   private void overrideBundlePreviewItemCount(CallbackInfoReturnable<Integer> callback) {
      if (tooltipsModule().isToggled()
         && tooltipsModule().bundleContentSetting.getValue()) {
         callback.setReturnValue(this.size());
      }
   }

   @Unique
   private static void collective$bindBundleContentsModules(ModuleRegistry moduleRegistry) {
      tooltipsModule = (TooltipsModule)moduleRegistry.getModule(
         TooltipsModule.class
      );
   }
}

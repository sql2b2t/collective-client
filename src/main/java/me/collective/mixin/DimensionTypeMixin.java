package me.collective.mixin;

import me.collective.client.ClientContext;
import me.collective.client.modules.render.SkyboxModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.timeline.Timeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DimensionType.class)
public class DimensionTypeMixin {
   @Inject(method = "timelines", at = @At("HEAD"), cancellable = true)
   private void useOverworldTimelinesForSkybox(CallbackInfoReturnable<HolderSet<Timeline>> callback) {
      SkyboxModule skybox = ClientContext.getModule(SkyboxModule.class);
      if (!skybox.isToggled() || skybox.typeSetting.getValue() != SkyboxModule.SkyType.OVERWORLD) {
         return;
      }
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.getConnection() == null) {
         return;
      }
      Registry<DimensionType> dimensions = minecraft.getConnection().registryAccess()
         .lookup(Registries.DIMENSION_TYPE).orElse(null);
      if (dimensions == null) {
         return;
      }
      DimensionType overworld = dimensions.getValue(BuiltinDimensionTypes.OVERWORLD);
      if ((Object)this != overworld && overworld != null) {
         callback.setReturnValue(overworld.timelines());
      }
   }
}

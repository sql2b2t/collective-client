package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import me.collective.client.modules.render.AnimationsModule;
import me.collective.client.settings.options.AutoCrystalInstantChoice;
import me.collective.client.modules.combat.AutoCrystalModule;
import me.collective.client.ClientContext;
import me.collective.client.utils.combat.EndCrystalState;
import me.collective.client.render.EntityRenderStateAccess;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.EndCrystalModel;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EndCrystalModel.class)
public class EndCrystalModelMixin {
   private static AutoCrystalModule autoCrystalModule;
   private static AnimationsModule animationsModule;
   private static AutoCrystalModule autoCrystalModule() {
      if (autoCrystalModule == null) {
         autoCrystalModule = ClientContext.getModule(AutoCrystalModule.class);
      }
      return autoCrystalModule;
   }
   private static AnimationsModule animationsModule() {
      if (animationsModule == null) {
         animationsModule = ClientContext.getModule(AnimationsModule.class);
      }
      return animationsModule;
   }
   @Shadow
   @Final
   public ModelPart base;
   @Shadow
   @Final
   public ModelPart innerGlass;
   @Shadow
   @Final
   public ModelPart outerGlass;
   @Shadow
   @Final
   public ModelPart cube;

   @ModifyExpressionValue(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;ageInTicks:F", ordinal = 0), require = 0)
   private float applyCrystalRotationSpeed(
      float originalRotation, @Local(argsOnly = true) EndCrystalRenderState renderState
   ) {
      return originalRotation * this.updateCrystalAnimationAndGetSpeed(renderState);
   }

   @Unique
   private float updateCrystalAnimationAndGetSpeed(EndCrystalRenderState renderState) {
      Entity entity = EntityRenderStateAccess.getEntity(renderState);
      float speedMultiplier = 1.0F;
      if (entity != null
         && entity.tickCount < 10
         && autoCrystalModule().isToggled()
         && (autoCrystalModule().instantSetting
               .getValue())
            .isEnabled()
         && ((EndCrystalState)entity).isClientAttacked()) {
         speedMultiplier *= 1.5F;
      }

      if (animationsModule().isToggled()
         && animationsModule().crystalsSetting.getValue()) {
         speedMultiplier *= animationsModule().rotationSpeedSetting.getValue();
         this.base.skipDraw = animationsModule().shouldHideCrystalPart(0);
         this.outerGlass.skipDraw = animationsModule().shouldHideCrystalPart(1);
         this.innerGlass.skipDraw = animationsModule().shouldHideCrystalPart(2);
         this.cube.skipDraw = animationsModule().shouldHideCrystalPart(3);
      }
      return speedMultiplier;
   }
}

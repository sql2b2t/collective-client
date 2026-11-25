package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import me.collective.client.modules.render.ChamsModule;
import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.events.EntityRenderEvent;
import me.collective.client.render.EntityRenderStateAccess;
import me.collective.client.modules.render.NoRenderModule;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
   private static NoRenderModule noRenderModule;
   private static ChamsModule chamsModule;
   private static NoRenderModule noRenderModule() {
      if (noRenderModule == null) {
         noRenderModule = ClientContext.getModule(NoRenderModule.class);
      }
      return noRenderModule;
   }
   private static ChamsModule chamsModule() {
      if (chamsModule == null) {
         chamsModule = ClientContext.getModule(ChamsModule.class);
      }
      return chamsModule;
   }

   @ModifyExpressionValue(
      method = "render(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/EntityRenderer;)V",
      at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;shouldRenderShadow:Z")
   )
   private static boolean applyEntityGlintAndShadow(
      boolean originalShouldRenderShadow, @Local(argsOnly = true) Entity entity
   ) {
      if (entity != null && chamsModule().shouldRenderGlintForEntity(entity)) {
         return false;
      }
      return !ClientContext.shaderRegistry.renderingIntoTarget
         && !noRenderModule().shouldHideEntityShadow()
         && originalShouldRenderShadow;
   }

   @Inject(
      method = "render(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/EntityRenderer;)V",
      at = @At("HEAD"),
      cancellable = true
   )
   public <E extends Entity, S extends EntityRenderState> void onEntityRenderPre(
      E entity, double x, double y, double z, float partialTick, PoseStack poses, MultiBufferSource bufferSource, int packedLight, EntityRenderer<? super E, S> renderer, CallbackInfo callback
   ) {
      if (entity == null) {
         return;
      }
      EntityRenderEvent.Pre event = EntityRenderEvent.Pre.create(
         entity, poses, bufferSource
      );
      ClientAccess.eventBus.post(event);
      if (event.isCancelled()) {
         callback.cancel();
      }
   }

   @Inject(
      method = "render(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/EntityRenderer;)V",
      at = @At("RETURN")
   )
   public <E extends Entity, S extends EntityRenderState> void onEntityRenderPost(
      E entity, double x, double y, double z, float partialTick, PoseStack poses, MultiBufferSource bufferSource, int packedLight, EntityRenderer<? super E, S> renderer, CallbackInfo callback
   ) {
      if (entity == null) {
         return;
      }
      EntityRenderEvent.Post event = EntityRenderEvent.Post.create(
         entity, poses, bufferSource
      );
      ClientAccess.eventBus
         .post(event);
   }

   @ModifyExpressionValue(
      method = "render(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/EntityRenderer;)V",
      at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/state/EntityRenderState;displayFireAnimation:Z")
   )
   public boolean applyEntityGlintAndFire(
      boolean originalDisplayFire, @Local(argsOnly = true) Entity entity
   ) {
      if (entity != null && chamsModule().shouldRenderGlintForEntity(entity)) {
         return false;
      }

      if (noRenderModule().isToggled()
         && noRenderModule().fireSetting.getValue()
         && (
            noRenderModule().othersSetting.getValue()
               || entity instanceof LocalPlayer
               || entity instanceof EndCrystal
         )) {
         return false;
      }
      return originalDisplayFire;
   }
}

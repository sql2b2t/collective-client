package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import me.collective.client.ClientContext;
import me.collective.client.render.ColorUtils;
import me.collective.client.modules.render.NoRenderModule;
import java.awt.Color;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.EquipmentClientInfo.LayerType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(EquipmentLayerRenderer.class)
public class EquipmentLayerRendererMixin {
   @Unique
   private static NoRenderModule noRenderModule;
   private static NoRenderModule noRenderModule() {
      if (noRenderModule == null) {
         noRenderModule = ClientContext.getModule(NoRenderModule.class);
      }
      return noRenderModule;
   }
   @Unique
   private LayerType currentLayerType;

   @Inject(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/resources/ResourceLocation;)V", at = @At("HEAD"))
   private void captureEquipmentLayerType(
      LayerType layerType,
      ResourceKey<EquipmentAsset> assetId,
      Model model,
      ItemStack stack,
      PoseStack poses,
      MultiBufferSource bufferSource,
      int packedLight,
      @Nullable ResourceLocation texture,
      CallbackInfo callback
   ) {
      this.currentLayerType = layerType;
   }

   @ModifyArgs(
      method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/resources/ResourceLocation;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/model/Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"
      )
   )
   private void applyEquipmentSubmissionOpacity(Args arguments) {
      if (!this.isNonHumanoidEquipmentLayer()) {
         Color originalColor = new Color(
            arguments.<Integer>get(4)
         );
         float opacity = noRenderModule().getArmorOpacity()
            * noRenderModule().getEntityAlpha();
         arguments.set(
            4,
            ColorUtils.withAlpha(originalColor, opacity).hashCode()
         );
      }
   }

   @ModifyExpressionValue(
      method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/resources/ResourceLocation;)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderType;armorCutoutNoCull(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;")
   )
   private RenderType applyEquipmentColorOpacity(
      RenderType originalType, @Local(ordinal = 1) ResourceLocation texture
   ) {
      if (this.isNonHumanoidEquipmentLayer()) {
         return originalType;
      }

      float opacity = noRenderModule().getArmorOpacity()
         * noRenderModule().getEntityAlpha();
      return opacity == 1.0F ? originalType : RenderType.entityTranslucent(texture);
   }

   @ModifyExpressionValue(
      method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/resources/ResourceLocation;)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;")
   )
   private Object suppressArmorTrim(Object originalTrim) {
      return noRenderModule().isToggled()
            && noRenderModule().armorTrimSetting.getValue()
         ? null
         : originalTrim;
   }

   @Unique
   private boolean isNonHumanoidEquipmentLayer() {
      return this.currentLayerType == null
         ? false
         : this.currentLayerType != LayerType.HUMANOID
            && this.currentLayerType != LayerType.HUMANOID_LEGGINGS;
   }
}

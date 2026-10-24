package me.collective.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.collective.module.render.HandsShader;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemRenderer.class)
public class MixinItemRenderer {

    @WrapOperation(method = "getItemGlintConsumer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/VertexConsumerProvider;getBuffer(Lnet/minecraft/client/render/RenderLayer;)Lnet/minecraft/client/render/VertexConsumer;"))
    private static VertexConsumer collective$shadeItemQuads(VertexConsumerProvider consumers, RenderLayer layer, Operation<VertexConsumer> original) {
        return HandsShader.decorateItem(original.call(consumers, layer));
    }

    @WrapOperation(method = "getSpecialItemGlintConsumer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/VertexConsumerProvider;getBuffer(Lnet/minecraft/client/render/RenderLayer;)Lnet/minecraft/client/render/VertexConsumer;"))
    private static VertexConsumer collective$shadeSpecialItemQuads(VertexConsumerProvider consumers, RenderLayer layer, Operation<VertexConsumer> original) {
        return HandsShader.decorateItem(original.call(consumers, layer));
    }
}

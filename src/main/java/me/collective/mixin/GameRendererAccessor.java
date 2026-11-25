package me.collective.mixin;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
   @Accessor("resourcePool")
   CrossFrameResourcePool getPool();

   @Invoker("bobView")
   void collective$bobView(PoseStack poses, float partialTick);

   @Invoker("bobHurt")
   void collective$bobViewHurt(PoseStack poses, float partialTick);
}

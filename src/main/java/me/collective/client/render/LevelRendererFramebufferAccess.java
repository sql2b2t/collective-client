package me.collective.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;

public interface LevelRendererFramebufferAccess {
   void collective$popFramebuffer();

   void collective$pushFramebuffer(RenderTarget target);
}

package me.collective.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import java.util.function.Supplier;
import net.minecraft.client.renderer.texture.AbstractTexture;

public final class MipmapFontTexture extends AbstractTexture {

   private NativeImage[] mipImages;
   private final int mipLevelCount;

   public MipmapFontTexture(Supplier<String> label, NativeImage[] mipImages) {
      this.mipImages = mipImages;
      this.mipLevelCount = mipImages.length;
      this.createTexture(label);
      this.upload();
   }

   private void createTexture(Supplier<String> label) {
      GpuDevice device = RenderSystem.getDevice();
      NativeImage baseImage = this.mipImages[0];
      this.texture = device.createTexture(label, 5,
         TextureFormat.RGBA8, baseImage.getWidth(), baseImage.getHeight(), 1, this.mipLevelCount);
      this.sampler = RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
         FilterMode.LINEAR, FilterMode.LINEAR, true);
      this.textureView = device.createTextureView(this.texture, 0, this.mipLevelCount);
   }

   public void upload() {
      if (this.mipImages == null || this.texture == null) {
         return;
      }
      CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
      for (int level = 0; level < this.mipImages.length; level++) {
         NativeImage image = this.mipImages[level];
         encoder.writeToTexture(this.texture, image, level, 0, 0, 0, image.getWidth(), image.getHeight(), 0, 0);
      }
   }

   public NativeImage getPixels() {
      return this.mipImages != null ? this.mipImages[0] : null;
   }

   public void setPixels(NativeImage image) {
      if (this.mipImages != null) {
         for (NativeImage mipImage : this.mipImages) {
            mipImage.close();
         }
      }
      this.mipImages = new NativeImage[]{image};
   }

   @Override
   public void close() {
      if (this.mipImages != null) {
         for (NativeImage image : this.mipImages) {
            image.close();
         }
         this.mipImages = null;
      }
      super.close();
   }
}

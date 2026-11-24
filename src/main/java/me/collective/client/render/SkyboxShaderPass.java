package me.collective.client.render;

import me.collective.client.managers.ConfigurationManager;

import me.collective.client.ClientContext;
import me.collective.client.utils.UncheckedExceptions;
import me.collective.client.modules.render.SkyboxModule;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;

public class SkyboxShaderPass extends BaseShaderPass implements ReloadableShaderPass {
   private static SkyboxModule skyboxModule;
   private static SkyboxModule skyboxModule() {
      if (skyboxModule == null) {
         skyboxModule = (SkyboxModule)ClientContext.getModule(SkyboxModule.class);
      }
      return skyboxModule;
   }

   @Override
   public void updateUniforms() {
      SkyboxModule skyboxModule = skyboxModule();
      if (skyboxModule == null) {
         return;
      }
      Minecraft clientInstance = Minecraft.getInstance();
      int screenWidth = (clientInstance.getWindow()).getScreenWidth();
      int screenHeight = (clientInstance.getWindow()).getScreenHeight();
      float aspectRatio = (float)screenWidth / Math.max(1, screenHeight);
      Camera pitchCamera = clientInstance.gameRenderer.getMainCamera();
      float cameraRotation = -pitchCamera.xRot();

      float normalizedPitch = cameraRotation / 90.0F;
      cameraRotation = (clientInstance.gameRenderer.getMainCamera()).yRot();

      float normalizedYaw = cameraRotation / 180.0F * aspectRatio;
      ShaderUniforms scaleUniformsCapture = this.uniforms;

      scaleUniformsCapture.set("u_Scale", 1.0F);
      ShaderUniforms mouseUniformsCapture = this.uniforms;

      SkyboxModule yawModuleCapture = skyboxModule;

      float mouseXCapture = normalizedYaw * yawModuleCapture.getParallax();
      SkyboxModule pitchModuleCapture = skyboxModule;

      float mouseY = normalizedPitch * pitchModuleCapture.getParallax();
      mouseUniformsCapture.set("u_Mouse", mouseXCapture, mouseY);
      ShaderUniforms resolutionUniformsCapture = this.uniforms;

      resolutionUniformsCapture.set("u_Resolution", screenWidth, screenHeight);
      ShaderUniforms timeUniformsCapture = this.uniforms;

      float elapsedMillis = (float)(System.currentTimeMillis() - START_TIME_MILLIS);

      float elapsedTime = elapsedMillis / 1000.0F;
      timeUniformsCapture.set("u_Time", elapsedTime);
      ShaderUniforms colorUniformsCapture = this.uniforms;

      SkyboxModule colorModuleCapture = skyboxModule;

      Color color = colorModuleCapture.getShaderColor();
      colorUniformsCapture.set("u_Color", color);
   }

   @Override
   public CompiledShaderProgram compileProgram() {
      Path shaderDirectory = ConfigurationManager.SHADERS_DIRECTORY;

      Path fragmentPath = shaderDirectory.resolve("sky.frag");
      String fragmentSource;
      try {
         fragmentSource = Files.readString(fragmentPath);
      } catch (Throwable failure) {
         throw UncheckedExceptions.rethrow(failure);
      }
      String vertexSource = RenderTargetUtils.readShaderSource(
         this.vertexResource
      );

      String vertexSourceForCompile = vertexSource;
      return CompiledShaderProgram.compile(vertexSourceForCompile, fragmentSource);
   }

   public SkyboxShaderPass() {
      super("sky/base.vert", "sky/sky.frag");
      this.reload(this);
   }

}

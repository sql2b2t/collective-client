package me.collective.client.render

import me.collective.client.ClientContext
import me.collective.client.managers.ConfigurationManager
import me.collective.client.modules.render.SkyboxModule
import me.collective.client.utils.UncheckedExceptions
import net.minecraft.client.Minecraft
import java.nio.file.Files
import kotlin.math.max

open class SkyboxShaderPass : BaseShaderPass("sky/base.vert", "sky/sky.frag"), ReloadableShaderPass {
   init {
      this.reload(this)
   }

   override fun updateUniforms() {
      val skyboxModule = skyboxModule() ?: return
      val clientInstance = Minecraft.getInstance()
      val screenWidth = clientInstance.window.screenWidth
      val screenHeight = clientInstance.window.screenHeight
      val aspectRatio = screenWidth.toFloat() / max(1, screenHeight)
      val mainCamera = clientInstance.gameRenderer.mainCamera()
      val normalizedPitch = -mainCamera.xRot() / 90.0f
      val normalizedYaw = mainCamera.yRot() / 180.0f * aspectRatio

      this.uniforms.set("u_Scale", 1.0f)
      this.uniforms.set(
         "u_Mouse",
         normalizedYaw * skyboxModule.getParallax(),
         normalizedPitch * skyboxModule.getParallax()
      )
      this.uniforms.set("u_Resolution", screenWidth.toFloat(), screenHeight.toFloat())
      val elapsedTime = (System.currentTimeMillis() - ShaderPass.START_TIME_MILLIS).toFloat() / 1000.0f
      this.uniforms.set("u_Time", elapsedTime)
      this.uniforms.set("u_Color", skyboxModule.getShaderColor())
   }

   override fun compileProgram(): CompiledShaderProgram {
      val shaderDirectory = ConfigurationManager.SHADERS_DIRECTORY
      val fragmentPath = shaderDirectory.resolve("sky.frag")
      val fragmentSource = try {
         Files.readString(fragmentPath)
      } catch (failure: Throwable) {
         throw UncheckedExceptions.rethrow<Throwable>(failure)
      }
      val vertexSource = RenderTargetUtils.readShaderSource(this.vertexResource)
      return CompiledShaderProgram.compile(vertexSource, fragmentSource)
   }

   companion object {
      private var skyboxModule: SkyboxModule? = null

      private fun skyboxModule(): SkyboxModule? {
         if (skyboxModule == null) {
            skyboxModule = ClientContext.getModule(SkyboxModule::class.java)
         }
         return skyboxModule
      }
   }
}

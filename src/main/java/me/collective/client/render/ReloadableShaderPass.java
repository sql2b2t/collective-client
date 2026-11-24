package me.collective.client.render;

import me.collective.client.utils.chat.ChatUtils;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;

public interface ReloadableShaderPass {
   String RELOAD_FAILURE_MESSAGE = "Failed to compile shader, check Minecraft logs for more details.";

   CompiledShaderProgram compileProgram();

   default void reload(BaseShaderPass pass) {
      CompiledShaderProgram newProgram = null;

      try {
         newProgram = this.compileProgram();
      } catch (Throwable failure) {
         String errorMessage = RELOAD_FAILURE_MESSAGE;
         String failureMessage = failure.getMessage();
         ShaderDiagnostics.logError(failureMessage);
         MutableComponent plainNotification = Component.literal(errorMessage);
         ChatFormatting errorColor = ChatFormatting.RED;
         MutableComponent coloredNotification = plainNotification.withStyle(errorColor);
         MessageSignature signature = ChatUtils.createSignature(-99);
         ChatUtils.MessagePriority priority = ChatUtils.MessagePriority.HIGH;
         ChatUtils.printChat(coloredNotification, signature, priority);
         try {
            String fallbackVertexRead = RenderTargetUtils.readShaderSource(
               pass.vertexResource
            );
            String fallbackFragmentRead = RenderTargetUtils.readShaderSource(
               pass.fragmentResource
            );
            newProgram = CompiledShaderProgram.compile(fallbackVertexRead, fallbackFragmentRead);
         } catch (Throwable fallbackFailure) {
            ShaderDiagnostics.logError(fallbackFailure.getMessage());
         }
      }

      if (newProgram == null) {
         if (pass.program == null) {
            throw new IllegalStateException("Shader program compilation failed and no previous program is available.");
         }

         return;
      }

      CompiledShaderProgram oldProgram = pass.program;
      pass.program = newProgram;
      pass.uniforms = new ShaderUniforms(newProgram.programId());
      if (oldProgram != null) {
         oldProgram.delete();
      }
   }

}

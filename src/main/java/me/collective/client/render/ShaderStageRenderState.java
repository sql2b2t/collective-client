package me.collective.client.render;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL20C;

public class ShaderStageRenderState implements RenderState {
   public static final ShaderStageRenderState INSTANCE;
   private ShaderStage stage;
   private int inputTextureId = -1;
   private int savedTextureBinding;
   private int savedProgram;
   private int savedMinFilter;
   private int savedMagFilter;
   private int savedWrapS;
   private int savedWrapT;

   private ShaderStageRenderState() {
   }

   static {
      INSTANCE = new ShaderStageRenderState();
   }

   public void configure(ShaderStage stage, int inputTextureId) {
      this.stage = stage;
      this.inputTextureId = inputTextureId;
   }

   @Override
   public void apply(boolean entering) {
      if (entering) {

         this.savedProgram = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);

         GL13C.glActiveTexture(GL13C.GL_TEXTURE15);

         this.savedTextureBinding = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);

         int textureIdToBind = this.inputTextureId;

         GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, textureIdToBind);
         this.savedMinFilter = GL11C.glGetTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER);
         this.savedMagFilter = GL11C.glGetTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER);
         this.savedWrapS = GL11C.glGetTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S);
         this.savedWrapT = GL11C.glGetTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T);
         GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
         GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);

         GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL13C.GL_CLAMP_TO_EDGE);

         GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL13C.GL_CLAMP_TO_EDGE);
         ShaderStage stageForProgram = this.stage;
         CompiledShaderProgram capturedProgram = stageForProgram.getProgram();

         int programId = capturedProgram.programId();
         GL20C.glUseProgram(programId);

         GL11C.glDisable(GL11C.GL_BLEND);
         ShaderStage stageForUniforms = this.stage;
         stageForUniforms.updateUniforms();
      } else {

         GL13C.glActiveTexture(GL13C.GL_TEXTURE15);

         GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, this.inputTextureId);
         GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, this.savedMinFilter);
         GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, this.savedMagFilter);
         GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, this.savedWrapS);
         GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, this.savedWrapT);
         int textureBindingToRestore = this.savedTextureBinding;
         GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, textureBindingToRestore);
         int programToRestore = this.savedProgram;
         GL20C.glUseProgram(programToRestore);

         GL13C.glActiveTexture(GL13C.GL_TEXTURE0);

         GL11C.glEnable(GL11C.GL_BLEND);
      }
   }

}

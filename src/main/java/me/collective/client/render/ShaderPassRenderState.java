package me.collective.client.render;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL20C;

public class ShaderPassRenderState implements RenderState {

   public static final ShaderPassRenderState INSTANCE = new ShaderPassRenderState();
   private ShaderPass pass;
   private int inputTextureId = -1;
   private int savedTextureBinding;
   private int savedProgram;
   private int savedMinFilter;
   private int savedMagFilter;
   private int savedWrapS;
   private int savedWrapT;

   private ShaderPassRenderState() {
   }

   public void configure(ShaderPass pass) {
      this.configure(pass, -1);
   }

   public void configure(ShaderPass pass, int inputTextureId) {
      this.pass = pass;
      this.inputTextureId = inputTextureId;
   }

   @Override
   public void apply(boolean entering) {
      if (entering) {

         this.savedProgram = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);

         GL13C.glActiveTexture(GL13C.GL_TEXTURE15);

         this.savedTextureBinding = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
         if (this.inputTextureId != -1) {

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
         }

         ShaderPass passForProgram = this.pass;
         CompiledShaderProgram capturedProgram = passForProgram.getProgram();
         int programId = capturedProgram.programId();
         GL20C.glUseProgram(programId);
         ShaderPass passForUniforms = this.pass;
         passForUniforms.updateUniforms();
      } else {

         GL13C.glActiveTexture(GL13C.GL_TEXTURE15);
         if (this.inputTextureId != -1) {

            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, this.inputTextureId);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, this.savedMinFilter);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, this.savedMagFilter);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, this.savedWrapS);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, this.savedWrapT);
            int textureBindingToRestore = this.savedTextureBinding;
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, textureBindingToRestore);
         }
         int programToRestore = this.savedProgram;
         GL20C.glUseProgram(programToRestore);

         GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
      }
   }

}

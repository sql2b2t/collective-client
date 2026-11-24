package me.collective.client.render;

import org.lwjgl.opengl.GL20;

public class BaseShaderPass implements ShaderPass, ReloadableShaderPass {
   protected final String vertexResource;
   protected final String fragmentResource;
   protected CompiledShaderProgram program;
   protected ShaderUniforms uniforms;

   @Override
   public CompiledShaderProgram getProgram() {
      return this.program;
   }

   @Override
   public void updateUniforms() {
   }

   protected void initializeProgram() {
      this.program = this.compileProgram();
      this.uniforms = new ShaderUniforms(this.program.programId());
   }

   @Override
   public CompiledShaderProgram compileProgram() {
      return CompiledShaderProgram.fromResources(
         this.vertexResource, this.fragmentResource
      );
   }

   public BaseShaderPass(String vertexResource, String fragmentResource) {
      this.vertexResource = vertexResource;
      this.fragmentResource = fragmentResource;
   }

   @Override
   public void render(Runnable draw) {
      CompiledShaderProgram capturedProgram = this.program;

      int programId = capturedProgram.programId();
      GL20.glUseProgram(programId);
      draw.run();
   }

}

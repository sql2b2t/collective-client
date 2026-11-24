package me.collective.client.render;

public interface ShaderPass {
   long START_TIME_MILLIS = System.currentTimeMillis();

   CompiledShaderProgram getProgram();

   void updateUniforms();

   void render(Runnable draw);

}

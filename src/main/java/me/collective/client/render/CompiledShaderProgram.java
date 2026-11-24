package me.collective.client.render;

import org.lwjgl.opengl.GL20;

public record CompiledShaderProgram(int programId) {

   public void delete() {
      int programId = this.programId;
      GL20.glDeleteProgram(programId);
   }

   public static CompiledShaderProgram fromResources(String vertexResource, String fragmentResource) {
      String vertexSource = RenderTargetUtils.readShaderSource(vertexResource);
      String fragmentSource = RenderTargetUtils.readShaderSource(fragmentResource);
      return compile(vertexSource, fragmentSource);
   }

   private static int compileShader(String source, int shaderType) {
      int shaderId = GL20.glCreateShader(shaderType);
      StringBuilder sourceBuilder = new StringBuilder(source);
      if (shaderType == GL20.GL_FRAGMENT_SHADER) {
         int main = sourceBuilder.indexOf("void main(");
         if (main < 0) main = sourceBuilder.indexOf("void main (");
         if (main >= 0) {
            String utilitySource = RenderTargetUtils.readShaderSource("util/util.frag");
            if (source.contains("u_Overlay")) {
               utilitySource += RenderTargetUtils.readShaderSource("util/blur_util.frag");
            }
            sourceBuilder.insert(main, utilitySource + "\n");
         }
      }
      GL20.glShaderSource(shaderId, sourceBuilder);
      GL20.glCompileShader(shaderId);
      if (GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == 0) {
         String message = formatCompileFailure(sourceBuilder, shaderId);
         GL20.glDeleteShader(shaderId);
         throw new ShaderCompilationException(message);
      }
      return shaderId;
   }

   private static String formatCompileFailure(StringBuilder sourceBuilder, int shaderId) {
      String diagnosticSource = sourceBuilder.toString();
      String diagnostic = ShaderDiagnostics.formatCompileLog(diagnosticSource, shaderId);
      return "Failed to compile shader:\n" + diagnostic;
   }

   private static void linkProgram(int programId, int vertexShader, int fragmentShader) {
      GL20.glAttachShader(programId, vertexShader);
      GL20.glAttachShader(programId, fragmentShader);
      GL20.glLinkProgram(programId);
      if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == 0) {
         throw new ShaderCompilationException("Failed to link shader program:\n" + GL20.glGetProgramInfoLog(programId));
      }
      GL20.glDeleteShader(vertexShader);
      GL20.glDeleteShader(fragmentShader);
   }

   public static CompiledShaderProgram compile(String vertexSource, String fragmentSource) {
      int programId = GL20.glCreateProgram();
      if (programId == 0) {
         throw new RuntimeException("Failed to create program.");
      }

      int vertexShader = -1;
      int fragmentShader = -1;
      try {
         vertexShader = compileShader(vertexSource, 35633);

         fragmentShader = compileShader(fragmentSource, 35632);
         linkProgram(programId, vertexShader, fragmentShader);
      } catch (RuntimeException | Error failure) {
         if (vertexShader != -1) {
            GL20.glDeleteShader(vertexShader);
         }
         if (fragmentShader != -1) {
            GL20.glDeleteShader(fragmentShader);
         }
         GL20.glDeleteProgram(programId);
         throw failure;
      }
      return new CompiledShaderProgram(programId);
   }
}

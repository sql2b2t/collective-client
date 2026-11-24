package me.collective.client.render;

import me.collective.client.ClientAccess;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.lwjgl.opengl.GL20;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public final class ShaderDiagnostics implements ClientAccess {
   private static final Pattern SOURCE_LINE_PATTERN;

   private ShaderDiagnostics() {
   }

   public static String formatCompileLog(String source, int shaderId) {
      String[] sourceLines = source.split("\n");
      StringBuilder diagnostics = new StringBuilder();
      String driverLog = GL20.glGetShaderInfoLog(shaderId);
      if (driverLog.isEmpty()) {
         return "No log provided by driver.";
      }

      for (String logLine : driverLog.split("\n")) {
         Pattern linePattern = SOURCE_LINE_PATTERN;
         Matcher lineMatcher = linePattern.matcher(logLine);
         if (!lineMatcher.find()) {
            diagnostics.append("[INTERNAL] ").append(logLine).append("\n");
         } else {
            String lineNumber = lineMatcher.group(1);
            try {
               int sourceLineIndex = Integer.parseInt(lineNumber) - 1;
               if (sourceLineIndex >= 0 && sourceLineIndex < sourceLines.length) {
                  String trimmedSourceLine = sourceLines[sourceLineIndex].trim();
                  if (!trimmedSourceLine.isEmpty()) {
                     diagnostics.append("Line ").append(lineNumber).append(": ").append(trimmedSourceLine).append("\n");
                     int indicatorLength = trimmedSourceLine.length();
                     String indicator = "^".repeat(indicatorLength);
                     diagnostics.append(indicator).append("\n");
                  }
               }
               diagnostics.append(logLine).append("\n\n");
            } catch (NumberFormatException invalidLineNumber) {
               diagnostics.append(logLine).append("\n");
            }
         }
      }
      return diagnostics.toString();
   }

   public static void logError(String message) {
      Logger logger = ClientAccess.logger;
      Marker marker = MarkerFactory.getMarker("Shaders");
      logger.error(marker, message);
   }

   static {
      SOURCE_LINE_PATTERN = Pattern.compile("0\\((\\d+)\\)");
   }
}

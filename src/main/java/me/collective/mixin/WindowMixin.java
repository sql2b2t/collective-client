package me.collective.mixin;

import com.mojang.blaze3d.platform.DisplayData;
import com.mojang.blaze3d.platform.MonitorManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.WindowEventHandler;
import com.mojang.blaze3d.systems.GpuBackend;
import org.apache.commons.lang3.SystemUtils;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class WindowMixin {
   @Inject(
      method = "<init>",
      at = @At(value = "INVOKE_ASSIGN", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J", shift = Shift.BEFORE, remap = false)
   )
   private void configureWindowHints(
      WindowEventHandler eventHandler, DisplayData displayData, String preferredFullscreenMode, boolean isFullscreen, String title, MonitorManager monitorManager, GpuBackend backend, CallbackInfo callback
   ) {
      // На macOS ванильный Minecraft включает GLFW_OPENGL_FORWARD_COMPAT для
      // core-контекста 3.2+; без него создание контекста падает. Сбрасываем хинт
      // только на остальных ОС.
      if (!SystemUtils.IS_OS_MAC) {
         GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_FALSE);
      }
   }
}

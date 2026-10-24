package me.collective.render;

import net.minecraft.client.gui.GuiGraphics;

public class ScissorUtil {
    public static void enableScissor(GuiGraphics context, int x, int y, int width, int height) {
        context.enableScissor(x, y, x + width, y + height);
    }

    public static void disableScissor(GuiGraphics context) {
        context.disableScissor();
    }
}
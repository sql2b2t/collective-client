package me.collective.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public class ScissorUtil {
    public static void enableScissor(GuiGraphicsExtractor context, int x, int y, int width, int height) {
        context.enableScissor(x, y, x + width, y + height);
    }

    public static void disableScissor(GuiGraphicsExtractor context) {
        context.disableScissor();
    }
}
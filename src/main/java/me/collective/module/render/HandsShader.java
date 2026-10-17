package me.collective.module.render;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.render.ColorUtil;
import me.collective.render.HandShaderBuffer;
import me.collective.setting.ColorSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

import java.awt.Color;

public class HandsShader extends Module {
    public static HandsShader INSTANCE;

    /** True only while the deferred first-person hand command batch is being executed. */
    private static boolean handPass;

    public enum Mode {
        COLOR, WAVE, RAINBOW, PLASMA
    }

    public final EnumSetting<Mode> mode = add(new EnumSetting<>("Режим", Mode.WAVE));
    public final ColorSetting firstColor = add(new ColorSetting("Основной цвет", new Color(189, 153, 255, 255)));
    public final ColorSetting secondColor = add(new ColorSetting("Второй цвет", new Color(110, 220, 255, 255)));
    public final NumberSetting speed = add(new NumberSetting("Скорость анимации", 1.0, 0.1, 4.0, 0.1));
    public final NumberSetting opacity = add(new NumberSetting("Непрозрачность", 1.0, 0.1, 1.0, 0.05));

    public HandsShader() {
        super("HandsShader", "Накладывает анимированный шейдер на руки и предметы от первого лица", Category.RENDER);
        INSTANCE = this;
    }

    /** Marks the start of the deferred hand command execution window. */
    public static void beginHandPass() {
        handPass = true;
    }

    public static void endHandPass() {
        handPass = false;
    }

    /** Whether vertex data fetched right now belongs to the hand pass and should be shaded. */
    public static boolean isShading() {
        return handPass && INSTANCE != null && INSTANCE.isToggled();
    }

    /** Wraps a vertex consumer produced during the hand pass, or returns the original. */
    public static VertexConsumer decorate(VertexConsumer consumer) {
        return isShading() ? new HandShaderBuffer(consumer) : consumer;
    }

    /** Computes the shaded ARGB color for one vertex of the hand model. */
    public static int shade(float x, float y, float z, float u, float v) {
        HandsShader module = INSTANCE;
        if (module == null || !handPass) return 0xFFFFFFFF;

        float time = Util.getMeasuringTimeMs() * 0.001f * module.speed.getFloatValue();
        int alpha = Math.round(255.0f * module.opacity.getFloatValue());
        int rgb = module.firstColor.getValue().getRGB() & 0xFFFFFF;

        switch (module.mode.getValue()) {
            case COLOR -> rgb = module.firstColor.getValue().getRGB() & 0xFFFFFF;
            case WAVE -> {
                float mix = 0.5f + 0.5f * MathHelper.sin(u * 6.28318f + x * 0.7f + y * 0.5f - time * 1.8f);
                Color c = ColorUtil.gradient(module.firstColor.getValue(), module.secondColor.getValue(), mix);
                rgb = c.getRGB() & 0xFFFFFF;
            }
            case RAINBOW -> {
                float hue = (time * 0.12f + u + (x + z) * 0.08f) % 1.0f;
                Color c = Color.getHSBColor(hue, 0.85f, 1.0f);
                rgb = c.getRGB();
            }
            case PLASMA -> {
                float f = MathHelper.sin(x * 1.7f - time * 2.2f) + MathHelper.sin(u * 5.0f + (y + z) * 1.1f - time * 1.3f);
                float mix = 0.5f + 0.25f * f;
                Color c = ColorUtil.gradient(module.firstColor.getValue(), module.secondColor.getValue(), mix);
                rgb = c.getRGB() & 0xFFFFFF;
            }
        }

        return ((alpha & 0xFF) << 24) | rgb;
    }
}

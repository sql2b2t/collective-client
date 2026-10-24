package me.collective.module.render;

import com.mojang.blaze3d.buffers.Std140Builder;
import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.ColorSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.world.level.Level;
import org.joml.Vector4f;

import java.awt.Color;
import java.nio.ByteBuffer;

/**
 * Настройка окружения: фиксация времени суток, погоды и полное управление туманом (включая Ад).
 */
public class Ambience extends Module {
    public static Ambience INSTANCE;

    public enum TimeMode {
        VANILLA, DAY, NIGHT, SUNSET, CUSTOM, FAST
    }

    public enum WeatherMode {
        VANILLA, CLEAR, RAIN, THUNDER
    }

    public final EnumSetting<TimeMode> timeMode = add(new EnumSetting<>("TimeMode", TimeMode.DAY));
    public final NumberSetting customTime = add(new NumberSetting("Time", 6000.0, 0.0, 24000.0, 500.0));
    public final NumberSetting fastSpeed = add(new NumberSetting("FastSpeed", 5.0, 1.0, 20.0, 1.0));

    public final EnumSetting<WeatherMode> weather = add(new EnumSetting<>("Weather", WeatherMode.CLEAR));

    public final BooleanSetting clearFog = add(new BooleanSetting("ClearFog", true));
    public final BooleanSetting netherOnly = add(new BooleanSetting("NetherOnly", false));
    public final BooleanSetting customFogColor = add(new BooleanSetting("CustomFogColor", false));
    public final ColorSetting fogColor = add(new ColorSetting("FogColor", new Color(170, 120, 255, 255)));

    public Ambience() {
        super("Ambience", "Кастомизация времени суток, погоды и отключение/перекрашивание тумана.", Category.RENDER);
        INSTANCE = this;
    }

    public Long getModifiedTime() {
        return switch (timeMode.getValue()) {
            case DAY -> 6000L;
            case NIGHT -> 18000L;
            case SUNSET -> 12500L;
            case CUSTOM -> customTime.getValue().longValue();
            case FAST -> (System.currentTimeMillis() * fastSpeed.getValue().longValue() / 5L) % 24000L;
            case VANILLA -> null;
        };
    }

    public Float getModifiedRain() {
        return switch (weather.getValue()) {
            case CLEAR -> 0.0f;
            case RAIN, THUNDER -> 1.0f;
            case VANILLA -> null;
        };
    }

    public Float getModifiedThunder() {
        return switch (weather.getValue()) {
            case CLEAR, RAIN -> 0.0f;
            case THUNDER -> 1.0f;
            case VANILLA -> null;
        };
    }

    public boolean modifyFogBuffer(ByteBuffer buffer, int offset, Vector4f color, float envStart, float envEnd, float rdStart, float rdEnd, float skyEnd, float cloudEnd) {
        if (mc.level == null) return false;

        boolean isNether = mc.level.dimension() == Level.NETHER;
        if (netherOnly.getValue() && !isNether) return false;

        boolean clearing = clearFog.getValue();
        boolean customColor = customFogColor.getValue();

        if (!clearing && !customColor) return false;

        Vector4f finalColor = color;
        if (customColor) {
            Color c = fogColor.getValue();
            finalColor = new Vector4f(c.getRed() / 255.0f, c.getGreen() / 255.0f, c.getBlue() / 255.0f, 1.0f);
        }

        float sStart = envStart;
        float sEnd = envEnd;
        float rStart = rdStart;
        float rEnd = rdEnd;

        if (clearing) {
            sStart = 100000.0f;
            sEnd = 100000.0f;
            rStart = 100000.0f;
            rEnd = 100000.0f;
        }

        buffer.position(offset);
        Std140Builder.intoBuffer(buffer)
            .putVec4(finalColor)
            .putFloat(sStart)
            .putFloat(sEnd)
            .putFloat(rStart)
            .putFloat(rEnd)
            .putFloat(skyEnd)
            .putFloat(cloudEnd);

        return true;
    }
}
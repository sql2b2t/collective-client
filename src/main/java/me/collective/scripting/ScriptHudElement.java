package me.collective.scripting;

import me.collective.module.hud.HudModule;
import me.collective.setting.*;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Color;
import java.util.function.BiConsumer;

public class ScriptHudElement extends HudModule {
    private BiConsumer<GuiGraphics, Float> renderAction;
    private ScriptContext scriptContext;
    private int errorCount;

    public ScriptHudElement(String name, String description, float defaultX, float defaultY) {
        super(name, description, defaultX, defaultY);
        this.width = 100;
        this.height = 18;
    }

    /** Привязка к скрипту-владельцу (для отчётов об ошибках). */
    public void setScriptContext(ScriptContext ctx) {
        this.scriptContext = ctx;
    }

    // --- Fluent Settings API ---

    public BooleanSetting boolSetting(String name, boolean defaultValue) {
        return add(new BooleanSetting(name, defaultValue));
    }

    public NumberSetting numberSetting(String name, double defaultValue, double min, double max, double step) {
        return add(new NumberSetting(name, defaultValue, min, max, step));
    }

    public ColorSetting colorSetting(String name, Color defaultValue) {
        return add(new ColorSetting(name, defaultValue));
    }

    public StringSetting stringSetting(String name, String defaultValue) {
        return add(new StringSetting(name, defaultValue));
    }

    public ModeSetting modeSetting(String name, String defaultMode, String... modes) {
        return add(new ModeSetting(name, defaultMode, modes));
    }

    public <T extends Enum<T>> EnumSetting<T> enumSetting(String name, T defaultValue) {
        return add(new EnumSetting<>(name, defaultValue));
    }

    public void onRender(BiConsumer<GuiGraphics, Float> renderAction) {
        this.renderAction = renderAction;
    }

    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
    }

    public float getWidth() {
        return this.width;
    }

    public float getHeight() {
        return this.height;
    }

    public float getX() {
        return this.x;
    }

    public float getY() {
        return this.y;
    }

    public void setX(float x) {
        this.x = x;
    }

    public void setY(float y) {
        this.y = y;
    }

    @Override
    public void render(GuiGraphics context, float delta) {
        if (renderAction != null) {
            // Встроенные HUD-модули (Watermark/Bps) рисуют в абсолютных x,y.
            // Скрипты рисуют в локальных 0,0 — сдвигаем матрицы, иначе картинка
            // висит в углу экрана, а хитбокс для перетаскивания в другом месте.
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(x, y);
            try {
                renderAction.accept(context, delta);
            } catch (Throwable t) {
                // Ошибка отрисовки не должна ломать кадр — сообщаем и после лимита выключаем элемент
                errorCount++;
                int reported = scriptContext != null ? scriptContext.reportError(getName(), t) : errorCount;
                if ((errorCount >= ScriptContext.MAX_ERRORS || reported >= ScriptContext.MAX_ERRORS) && isToggled()) {
                    setToggled(false);
                }
            } finally {
                context.getMatrices().popMatrix();
            }
        }
    }
}
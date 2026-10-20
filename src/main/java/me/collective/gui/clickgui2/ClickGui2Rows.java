package me.collective.gui.clickgui2;

import me.collective.font.FontManager;
import me.collective.module.client.UI;
import me.collective.render.SoundUtil;
import me.collective.setting.BooleanSetting;
import me.collective.setting.ColorSetting;
import me.collective.setting.KeybindSetting;
import me.collective.setting.ModeSetting;
import me.collective.setting.NumberSetting;
import me.collective.setting.Setting;
import me.collective.setting.StringSetting;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

import static me.collective.gui.GuiGfx.fillRounded;
import static me.collective.gui.GuiGfx.outlineRounded;

/**
 * Строки настроек внутри карточек — перенос поведения исходника.
 * Общие размеры строк: 22px (тумблер/цвет/бинд/текст), 34px (число —
 * подпись сверху, трек снизу), 24px (режим). По кликам ЛКМ тумблит,
 * ПКМ «выбирает»/циклит в обратную сторону; числа — Ctrl+колесо и
 * инлайн-редактирование значения с мигающей кареткой (мод0444/мод0367);
 * текстовые настройки — поле с прокруткой текста и лимитом 32 кодпоинта
 * (мод0585); бинд открывает диалог (мод0312), цвет — пикер (мод0365).
 */
final class ClickGui2Rows {
    static final float TOGGLE_H = 22f;
    static final float NUMBER_H = 34f;
    static final float MODE_H = 24f;
    static final float INPUT_H = 22f;

    private ClickGui2Rows() {
    }

    interface Row {
        float height();

        void layout(float x, float y, float w);

        void render(DrawContext ctx, float mx, float my, float dt, float alpha);

        /** Возвращает true, если клик обработан внутри строки. */
        boolean mouseClicked(float mx, float my, int button, ClickGui2Popups popups);

        boolean mouseDragging(float mx, float my);

        void mouseReleased();

        /** Колесо, когда курсор над строкой (Ctrl — пошаговые числа). */
        boolean scrolled(double deltaY, boolean ctrl);

        void charTyped(String character);

        boolean keyPressed(int key);

        /** Идёт инлайн-ввод (строкаClaims клавиатуру экрана). */
        boolean wantsKeys();

        void clearFocus();
    }

    private static boolean soundOk(boolean hover) {
        UI ui = UI.INSTANCE;
        if (ui == null) return false;
        return hover ? ui.hoverSound.getValue() : ui.sounds.getValue();
    }

    private static void clickSound() {
        if (soundOk(false)) SoundUtil.playClick(0.22f, 1.15f);
    }

    private static void hoverSound() {
        if (soundOk(true)) SoundUtil.playHover(0.16f, 1.5f);
    }

    // ── Локальные утилиты текста ──

    private static float textWidth(String s, float size) {
        return FontManager.INSTANCE.getStringWidthScaled(s, size);
    }

    private static void drawText(DrawContext ctx, String s, float x, float y, float size, int color) {
        FontManager.INSTANCE.drawStringScaled(ctx, s, x, y, color, Math.max(0.55f, size / 9f));
    }

    private abstract static class Base implements Row {
        float x, y, w;
        boolean hovered;
        float hoverT;
        private boolean hoverLatch;

        @Override
        public void layout(float x, float y, float w) {
            this.x = x;
            this.y = y;
            this.w = w;
        }

        protected boolean trackHover(float mx, float my, float dt) {
            hovered = ClickGui2Widgets.inRect(mx, my, x, y, w, height());
            hoverT = ClickGui2Anim.to01(hoverT, hovered, dt, 10f);
            if (hovered && !hoverLatch) {
                hoverLatch = true;
                hoverSound();
            } else if (!hovered) {
                hoverLatch = false;
            }
            return hovered;
        }

        protected int labelColor(float brightness) {
            int de = ClickGui2Theme.rgb(ClickGui2Theme.Role.DEOPEN_SETTING_TEXT);
            int on = ClickGui2Theme.rgb(ClickGui2Theme.Role.OPEN_SETTING_TEXT);
            return ClickGui2Theme.blend(de, on, brightness);
        }
    }

    // ── Тумблер (BooleanSetting) ──

    static final class ToggleRow extends Base {
        private final BooleanSetting setting;
        private float activeT;

        ToggleRow(BooleanSetting setting) {
            this.setting = setting;
        }

        @Override
        public float height() {
            return TOGGLE_H;
        }

        @Override
        public void render(DrawContext ctx, float mx, float my, float dt, float alpha) {
            trackHover(mx, my, dt);
            activeT = ClickGui2Anim.to01(activeT, setting.getValue(), dt, 11f);
            int label = labelColor(activeT);
            drawText(ctx, setting.getName(), x, y + 5f, 10f, alphaApply(label, alpha));
            ClickGui2Widgets.togglePill(ctx, x + w - 36f, y + 2f, activeT, hovered && alpha > 0.5f);
        }

        @Override
        public boolean mouseClicked(float mx, float my, int button, ClickGui2Popups popups) {
            if (!ClickGui2Widgets.inRect(mx, my, x, y, w, height())) return false;
            if (button == 0) {
                setting.setValue(!setting.getValue());
                clickSound();
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseDragging(float mx, float my) {
            return false;
        }

        @Override
        public void mouseReleased() {
        }

        @Override
        public boolean scrolled(double deltaY, boolean ctrl) {
            return false;
        }

        @Override
        public void charTyped(String character) {
        }

        @Override
        public boolean keyPressed(int key) {
            return false;
        }

        @Override
        public boolean wantsKeys() {
            return false;
        }

        @Override
        public void clearFocus() {
        }

        private static int alphaApply(int color, float alpha) {
            return ClickGui2Theme.fade(color, alpha);
        }
    }

    // ── Число (NumberSetting): подпись + значение ( можно печатать) + трек ──

    static final class NumberRow extends Base {
        private final NumberSetting setting;
        private boolean editing;
        private String buffer = "";
        private boolean dragging;
        private double wheelAccum;

        NumberRow(NumberSetting setting) {
            this.setting = setting;
        }
        @Override
        public float height() {
            return NUMBER_H;
        }

        private String display() {
            return setting.isInteger()
                    ? String.valueOf(setting.getIntValue())
                    : String.format(Locale.ROOT, "%.1f", setting.getFloatValue());
        }

        private String formatted() {
            return setting.isInteger()
                    ? String.valueOf(setting.getIntValue())
                    : String.format(Locale.ROOT, "%.2f", setting.getFloatValue());
        }

        private void startEdit() {
            editing = true;
            buffer = formatted();
        }

        private void commit() {
            try {
                double parsed = Double.parseDouble(buffer.replace(',', '.'));
                setting.setValue(parsed);
            } catch (NumberFormatException ignored) {
            }
            editing = false;
        }

        @Override
        public void render(DrawContext ctx, float mx, float my, float dt, float alpha) {
            trackHover(mx, my, dt);

            float valueX = x + w;
            int label = ClickGui2Theme.blend(
                    ClickGui2Theme.rgb(ClickGui2Theme.Role.DEOPEN_SETTING_TEXT),
                    ClickGui2Theme.rgb(ClickGui2Theme.Role.ACTIVE_TEXT), hoverT);
            drawText(ctx, setting.getName(), x, y + 3f, 10f, ClickGui2Theme.fade(label, alpha));

            String shown = editing ? buffer : display();
            float tw = textWidth(shown, 9f);
            int valueColor = editing
                    ? ClickGui2Theme.rgb(ClickGui2Theme.Role.ACTIVE_TEXT)
                    : ClickGui2Theme.blend(
                            ClickGui2Theme.rgb(ClickGui2Theme.Role.DEACTIVE_TEXT),
                            ClickGui2Theme.rgb(ClickGui2Theme.Role.ACTIVE_TEXT), hoverT);
            drawText(ctx, shown, valueX - tw, y + 3f, 9f, ClickGui2Theme.fade(valueColor, alpha));
            if (editing && ClickGui2Anim.caretBlink()) {
                ClickGui2Widgets.caret(ctx, valueX - tw + tw + 1f, y + 3.5f, 9f);
            }

            float min = (float) setting.getMin();
            float max = (float) setting.getMax();
            float t = (setting.getFloatValue() - min) / Math.max(0.0001f, max - min);
            ClickGui2Widgets.sliderTrack(ctx, x, y + 19f, w, t, hovered && !editing,
                    dragging || editing);

            if (editing) {
                // рамка активного редактирования значения
                outlineRounded(ctx, valueX - tw - 4f, y + 1f, tw + 8f, 13f, 3f,
                        ClickGui2Theme.alpha(ClickGui2Theme.Role.MAIN, 150));
            }
        }

        @Override
        public boolean mouseClicked(float mx, float my, int button, ClickGui2Popups popups) {
            if (!ClickGui2Widgets.inRect(mx, my, x, y, w, height())) {
                if (editing) editing = false;
                return false;
            }
            if (button == 0) {
                float valueZone = Math.max(64f, w * 0.3f);
                boolean onValue = ClickGui2Widgets.inRect(mx, my, x + w - valueZone, y, valueZone, 14f);
                boolean onTrack = my >= y + 14f;
                if (editing) {
                    commit();
                    return true;
                }
                if (onValue && !onTrack) {
                    startEdit();
                    clickSound();
                    return true;
                }
                if (onTrack) {
                    dragging = true;
                    applyFromMouse(mx);
                    clickSound();
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean mouseDragging(float mx, float my) {
            if (dragging) {
                applyFromMouse(mx);
                return true;
            }
            return false;
        }

        @Override
        public void mouseReleased() {
            dragging = false;
        }

        private void applyFromMouse(float mx) {
            float min = (float) setting.getMin();
            float max = (float) setting.getMax();
            double v = min + Math.max(0f, Math.min(1f, (mx - x) / w)) * (max - min);
            setting.setValue(v);
        }

        @Override
        public boolean scrolled(double deltaY, boolean ctrl) {
            if (!ctrl) return false;
            wheelAccum += (deltaY > 0 ? 1.0 : -1.0) * setting.getIncrement();
            int steps = (int) wheelAccum;
            if (steps != 0) {
                wheelAccum -= steps;
                setting.setValue(setting.getValue() + steps * setting.getIncrement());
                clickSound();
            }
            return true;
        }

        @Override
        public void charTyped(String character) {
            if (!editing) return;
            char c = character.isEmpty() ? '\0' : character.charAt(0);
            if (c == '-' || c == '.' || c == ',' || (c >= '0' && c <= '9')) {
                if (buffer.length() < 12) {
                    buffer += c;
                }
                try {
                    double parsed = Double.parseDouble(buffer.replace(',', '.'));
                    setting.setValue(parsed);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        @Override
        public boolean keyPressed(int key) {
            if (!editing) return false;
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                commit();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                editing = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE && !buffer.isEmpty()) {
                buffer = buffer.substring(0, buffer.length() - 1);
                try {
                    double parsed = Double.parseDouble(buffer.replace(',', '.'));
                    setting.setValue(parsed);
                } catch (NumberFormatException ignored) {
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean wantsKeys() {
            return editing;
        }

        @Override
        public void clearFocus() {
            editing = false;
            dragging = false;
        }
    }

    // ── Режим (ModeSetting): чип «< значение >» + выпадающий список ──

    static final class ModeRow extends Base {
        private final ModeSetting setting;

        ModeRow(ModeSetting setting) {
            this.setting = setting;
        }

        @Override
        public float height() {
            return MODE_H;
        }

        private String value() {
            try {
                return setting.compute();
            } catch (Exception e) {
                return String.valueOf(setting.getValue());
            }
        }

        private java.util.List<String> modes() {
            java.util.List<String> list = setting.getModes();
            return list == null ? java.util.List.of() : list;
        }

        @Override
        public void render(DrawContext ctx, float mx, float my, float dt, float alpha) {
            trackHover(mx, my, dt);
            drawText(ctx, setting.getName(), x, y + 3.5f, 10f,
                    ClickGui2Theme.fade(labelColor(hoverT), alpha));

            String v = value();
            float chipH = 16f;
            float textW = textWidth(v, 9f);
            float chipW = Math.max(44f, Math.min(w * 0.62f, textW + 30f));
            float chipX = x + w - chipW;
            float chipY = y + (height() - chipH) / 2f;
            boolean chipHover = hovered && ClickGui2Widgets.inRect(mx, my, chipX, chipY, chipW, chipH);

            int bg = chipHover
                    ? ClickGui2Theme.fade(ClickGui2Theme.Role.SELECTED, 2.0f)
                    : ClickGui2Theme.alpha(ClickGui2Theme.Role.LINE, 90);
            fillRounded(ctx, chipX, chipY, chipW, chipH, 4f, ClickGui2Theme.fade(bg, alpha));
            outlineRounded(ctx, chipX, chipY, chipW, chipH, 4f, ClickGui2Theme.fade(ClickGui2Theme.Role.OUTLINE, alpha));

            drawText(ctx, v, chipX + 8f, chipY + (chipH - 9f) / 2f + 1f, 9f,
                    ClickGui2Theme.fade(ClickGui2Theme.rgb(ClickGui2Theme.Role.ACTIVE_TEXT), alpha));
            ClickGui2Widgets.chevronDown(ctx, chipX + chipW - 9f, chipY + chipH / 2f - 2.5f, 1f,
                    ClickGui2Theme.fade(ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT), alpha));
        }

        @Override
        public boolean mouseClicked(float mx, float my, int button, ClickGui2Popups popups) {
            if (!ClickGui2Widgets.inRect(mx, my, x, y, w, height())) return false;
            java.util.List<String> list = modes();
            if (list.isEmpty()) return false;

            // кликnard по чипу — выпадающий список, вне чипа — быстрый цикл
            float chipH = 16f;
            float chipW = Math.max(44f, Math.min(w * 0.62f, textWidth(value(), 9f) + 30f));
            boolean onChip = ClickGui2Widgets.inRect(mx, my, x + w - chipW, y + (height() - chipH) / 2f, chipW, chipH);

            if (onChip && popups != null) {
                popups.openMode(x + w - chipW, y + height() + 4f, setting);
                clickSound();
                return true;
            }
            if (button == 0) {
                setting.setValue(next(list, 1));
                clickSound();
                return true;
            }
            if (button == 1) {
                setting.setValue(next(list, -1));
                clickSound();
                return true;
            }
            return false;
        }

        private String next(java.util.List<String> list, int dir) {
            int idx = Math.max(0, list.indexOf(value()));
            return list.get((idx + dir + list.size()) % list.size());
        }

        @Override
        public boolean mouseDragging(float mx, float my) {
            return false;
        }

        @Override
        public void mouseReleased() {
        }

        @Override
        public boolean scrolled(double deltaY, boolean ctrl) {
            return false;
        }

        @Override
        public void charTyped(String character) {
        }

        @Override
        public boolean keyPressed(int key) {
            return false;
        }

        @Override
        public boolean wantsKeys() {
            return false;
        }

        @Override
        public void clearFocus() {
        }
    }

    // ── Цвет (ColorSetting): свотч открывает пикер ──

    static final class ColorRow extends Base {
        private final ColorSetting setting;

        ColorRow(ColorSetting setting) {
            this.setting = setting;
        }

        @Override
        public float height() {
            return TOGGLE_H;
        }

        @Override
        public void render(DrawContext ctx, float mx, float my, float dt, float alpha) {
            trackHover(mx, my, dt);
            drawText(ctx, setting.getName(), x, y + 5f, 10f,
                    ClickGui2Theme.fade(labelColor(hovered ? 0.85f : 0.35f), alpha));

            float sw = 14f;
            ClickGui2Widgets.colorSwatch(ctx, x + w - sw - 2f, y + 4f,
                    setting.getValue() == null ? java.awt.Color.WHITE : setting.getValue(),
                    hovered && alpha > 0.5f);

            if (hovered) {
                java.awt.Color c = setting.getValue() != null ? setting.getValue() : java.awt.Color.WHITE;
                String hex = String.format("#%06X%02X", c.getRGB() & 0xFFFFFF, c.getAlpha());
                float hw = textWidth(hex, 9f);
                drawText(ctx, hex, x + w - sw - 6f - hw, y + 5f, 9f,
                        ClickGui2Theme.fade(ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT), alpha));
            }
        }

        @Override
        public boolean mouseClicked(float mx, float my, int button, ClickGui2Popups popups) {
            if (!ClickGui2Widgets.inRect(mx, my, x, y, w, height())) return false;
            if (popups != null && button == 0) {
                popups.openColor(x + w - 16f, y - 2f, setting);
                clickSound();
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseDragging(float mx, float my) {
            return false;
        }

        @Override
        public void mouseReleased() {
        }

        @Override
        public boolean scrolled(double deltaY, boolean ctrl) {
            return false;
        }

        @Override
        public void charTyped(String character) {
        }

        @Override
        public boolean keyPressed(int key) {
            return false;
        }

        @Override
        public boolean wantsKeys() {
            return false;
        }

        @Override
        public void clearFocus() {
        }
    }

    // ── Бинд (KeybindSetting): чип с клавишей и диалогом захвата ──

    static final class BindRow extends Base {
        private final KeybindSetting setting;

        BindRow(KeybindSetting setting) {
            this.setting = setting;
        }

        @Override
        public float height() {
            return TOGGLE_H;
        }

        private String keyName() {
            try {
                return setting.getKeyName();
            } catch (Exception e) {
                return "None";
            }
        }

        @Override
        public void render(DrawContext ctx, float mx, float my, float dt, float alpha) {
            trackHover(mx, my, dt);
            drawText(ctx, setting.getName(), x, y + 5f, 10f,
                    ClickGui2Theme.fade(labelColor(hoverT), alpha));

            String name = keyName();
            float tw = textWidth(name, 9f);
            float chipW = Math.max(44f, tw + 18f);
            float chipX = x + w - chipW;
            float chipY = y + 3f;
            boolean chipHover = hovered && ClickGui2Widgets.inRect(mx, my, chipX, chipY, chipW, 16f);
            ClickGui2Widgets.chip(ctx, chipX, chipY, chipW, 16f, chipHover && alpha > 0.5f, name);
        }

        @Override
        public boolean mouseClicked(float mx, float my, int button, ClickGui2Popups popups) {
            if (!ClickGui2Widgets.inRect(mx, my, x, y, w, height())) return false;
            if (popups != null && button == 0) {
                popups.openKeybind(x + w - 6f, y - 4f, setting);
                clickSound();
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseDragging(float mx, float my) {
            return false;
        }

        @Override
        public void mouseReleased() {
        }

        @Override
        public boolean scrolled(double deltaY, boolean ctrl) {
            return false;
        }

        @Override
        public void charTyped(String character) {
        }

        @Override
        public boolean keyPressed(int key) {
            return false;
        }

        @Override
        public boolean wantsKeys() {
            return false;
        }

        @Override
        public void clearFocus() {
        }
    }

    // ── Текст (StringSetting): поле 96px с правым входом, как в мод0585 ──

    static final class TextRow extends Base {
        private static final float BOX_W = 96f;
        private static final int LIMIT = 32;

        private final StringSetting setting;
        private boolean editing;
        private float scrollOffset;
        private long lastScrollFrame;

        TextRow(StringSetting setting) {
            this.setting = setting;
        }

        @Override
        public float height() {
            return TOGGLE_H;
        }

        @Override
        public void render(DrawContext ctx, float mx, float my, float dt, float alpha) {
            trackHover(mx, my, dt);

            drawText(ctx, setting.getName(), x, y + 5f, 10f,
                    ClickGui2Theme.fade(labelColor(hoverT), alpha));

            float boxH = 16f;
            float boxX = x + w - BOX_W;
            float boxY = y + 3f;
            int border = editing
                    ? ClickGui2Theme.alpha(ClickGui2Theme.Role.MAIN, 170)
                    : ClickGui2Theme.rgb(ClickGui2Theme.Role.OUTLINE);
            fillRounded(ctx, boxX, boxY, BOX_W, boxH, 4f, ClickGui2Theme.alpha(ClickGui2Theme.Role.BACKGROUND, 190));
            outlineRounded(ctx, boxX, boxY, BOX_W, boxH, 4f, ClickGui2Theme.fade(border, alpha));

            String value = setting.getValue() == null ? "" : setting.getValue();
            boolean marquee = textWidth(value, 9f) > BOX_W - 10f;
            boolean scrolled = hovered || editing;
            long now = System.nanoTime();
            float frameDt = lastScrollFrame == 0L ? 0.016f
                    : Math.min(0.05f, (now - lastScrollFrame) / 1_000_000_000f);
            lastScrollFrame = now;

            float innerX = boxX + 5f;
            float textY = boxY + (boxH - 9f) / 2f + 1f;
            int textCol = editing || value.isEmpty()
                    ? ClickGui2Theme.rgb(ClickGui2Theme.Role.ACTIVE_TEXT)
                    : ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT);

            if (!marquee || !scrolled) {
                scrollOffset = 0f;
                String shown = value.isEmpty() ? "..." : value;
                drawText(ctx, shown, innerX, textY, 9f, ClickGui2Theme.fade(textCol, alpha));
            } else {
                float step = textWidth(value, 9f) + 12f;
                scrollOffset += frameDt * 18f;
                float startX = innerX - scrollOffset % step;
                ctx.enableScissor((int) (boxX + 4f), (int) (boxY + 2f),
                        (int) (boxX + BOX_W - 4f), (int) (boxY + boxH - 2f));
                drawText(ctx, value, startX, textY, 9f, ClickGui2Theme.fade(textCol, alpha));
                drawText(ctx, value, startX + step, textY, 9f, ClickGui2Theme.fade(textCol, alpha));
                ctx.disableScissor();
            }

            if (editing && ClickGui2Anim.caretBlink()) {
                float caretX = innerX + textWidth(editPreview(), 9f);
                ClickGui2Widgets.caret(ctx, Math.min(boxX + BOX_W - 3f, caretX), textY, 9f);
            }
        }

        private String editPreview() {
            return setting.getValue() == null ? "" : setting.getValue();
        }

        @Override
        public boolean mouseClicked(float mx, float my, int button, ClickGui2Popups popups) {
            if (!ClickGui2Widgets.inRect(mx, my, x, y, w, height())) {
                if (editing) editing = false;
                return false;
            }
            if (button == 0) {
                editing = ClickGui2Widgets.inRect(mx, my, x + w - BOX_W, y + 3f, BOX_W, 16f);
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseDragging(float mx, float my) {
            return false;
        }

        @Override
        public void mouseReleased() {
        }

        @Override
        public boolean scrolled(double deltaY, boolean ctrl) {
            return false;
        }

        @Override
        public void charTyped(String character) {
            if (!editing) return;
            String current = setting.getValue() == null ? "" : setting.getValue();
            if (current.length() < LIMIT) {
                setting.setValue(current + character);
            }
        }

        @Override
        public boolean keyPressed(int key) {
            if (!editing) return false;
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                editing = false;
                clickSound();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                editing = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                String current = setting.getValue() == null ? "" : setting.getValue();
                if (!current.isEmpty()) {
                    setting.setValue(current.substring(0, current.length() - 1));
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean wantsKeys() {
            return editing;
        }

        @Override
        public void clearFocus() {
            editing = false;
        }
    }

    /** Фабрика: привести настройку к строке в карточке. */
    static Row of(Setting<?> setting) {
        if (setting instanceof BooleanSetting bool) return new ToggleRow(bool);
        if (setting instanceof NumberSetting num) return new NumberRow(num);
        if (setting instanceof ModeSetting mode) return new ModeRow(mode);
        if (setting instanceof ColorSetting color) return new ColorRow(color);
        if (setting instanceof KeybindSetting bind) return new BindRow(bind);
        if (setting instanceof StringSetting str) return new TextRow(str);
        return null;
    }
}

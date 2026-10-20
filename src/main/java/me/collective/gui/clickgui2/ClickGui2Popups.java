package me.collective.gui.clickgui2;

import me.collective.font.FontManager;
import me.collective.gui.GuiGfx;
import me.collective.render.RenderUtil;
import me.collective.render.SoundUtil;
import me.collective.setting.ColorSetting;
import me.collective.setting.KeybindSetting;
import me.collective.setting.ModeSetting;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static me.collective.gui.GuiGfx.fillRounded;
import static me.collective.gui.GuiGfx.outlineRounded;

/**
 * Менеджер всплывашек второго стиля (по духу mod0699): экземпляры
 * привязываются к точке якоря, въезжают альфой и лёгким сдвигом,
 * закрываются по клику мимо или по ESC. Три типа:
 * dropdown списка режимов, колор-пикер (mod0365: SV-область, полоса
 * оттенка 5px, полоса альфы с шахматкой, hex-поле "#AARRGGBB" с вводом,
 * лимитом и кареткой) и диалог бинда (mod0312: кнопка захвата).
 */
public final class ClickGui2Popups {
    private final List<Popup> stack = new ArrayList<>();

    public void openMode(float anchorX, float anchorY, ModeSetting setting) {
        stack.clear();
        stack.add(new ModeDropdown(anchorX, anchorY, setting));
    }

    public void openColor(float anchorX, float anchorY, ColorSetting setting) {
        stack.clear();
        stack.add(new ColorPicker(anchorX, anchorY, setting));
    }

    public void openKeybind(float anchorX, float anchorY, KeybindSetting setting) {
        stack.clear();
        stack.add(new KeybindDialog(anchorX, anchorY, setting));
    }

    public boolean isOpen() {
        return !stack.isEmpty();
    }

    public void closeAll() {
        stack.clear();
    }

    public void render(DrawContext ctx, float mx, float my, float dt, float viewportX, float viewportY, float viewportW, float viewportH) {
        if (stack.isEmpty()) return;
        Popup top = stack.get(stack.size() - 1);
        top.update(dt);
        top.moveTo(viewportX, viewportY, viewportW, viewportH);
        if (top.finishedClosing()) {
            stack.clear();
            return;
        }
        top.render(ctx, mx, my);
    }

    public boolean mouseClicked(float mx, float my, int button) {
        if (stack.isEmpty()) return false;
        Popup top = stack.get(stack.size() - 1);
        if (top.inBounds(mx, my)) {
            top.mouseClicked(mx, my, button);
            return true;
        }
        popCloseSound();
        stack.clear();
        return false;
    }

    public boolean mouseDragging(float mx, float my) {
        if (stack.isEmpty()) return false;
        return stack.get(stack.size() - 1).mouseDragging(mx, my);
    }

    public void mouseReleased() {
        if (!stack.isEmpty()) stack.get(stack.size() - 1).mouseReleased();
    }

    public boolean mouseScrolled(double deltaY) {
        if (stack.isEmpty()) return false;
        return stack.get(stack.size() - 1).mouseScrolled(deltaY);
    }

    public boolean charTyped(String character) {
        if (stack.isEmpty()) return false;
        return stack.get(stack.size() - 1).charTyped(character);
    }

    /** true — ESC закрыл всплывашку (не экран). */
    public boolean keyPressed(int key) {
        if (stack.isEmpty()) return false;
        Popup top = stack.get(stack.size() - 1);
        if (key == GLFW.GLFW_KEY_ESCAPE && !(top instanceof KeybindDialog dialog && dialog.capturing)) {
            popCloseSound();
            stack.clear();
            return true;
        }
        return top.keyPressed(key);
    }

    private static void popCloseSound() {
        me.collective.module.client.UI ui = me.collective.module.client.UI.INSTANCE;
        if (ui != null && ui.sounds.getValue()) SoundUtil.playClick(0.16f, 0.9f);
    }

    // ── База ──

    private abstract static class Popup {
        float x, y, w, h;
        float alpha;
        boolean closing;

        void update(float dt) {
            alpha = ClickGui2Anim.to01(alpha, !closing, dt, closing ? 16f : 11f);
        }

        boolean finishedClosing() {
            return closing && alpha <= 0.004f;
        }

        void moveTo(float vx, float vy, float vw, float vh) {
            x = Math.max(vx + 6f, Math.min(vx + vw - w - 6f, x));
            y = Math.max(vy + 6f, Math.min(vy + vh - h - 6f, y));
        }

        boolean inBounds(float mx, float my) {
            return ClickGui2Widgets.inRect(mx, my, x, y, w, h);
        }

        abstract void render(DrawContext ctx, float mx, float my);

        abstract void mouseClicked(float mx, float my, int button);

        abstract boolean mouseDragging(float mx, float my);

        abstract void mouseReleased();

        abstract boolean mouseScrolled(double deltaY);

        abstract boolean charTyped(String character);

        boolean keyPressed(int key) {
            return false;
        }
    }

    // ── Выпадающий список режимов ──

    private static final class ModeDropdown extends Popup {
        private final ModeSetting setting;
        float hoverRow = -1f;

        ModeDropdown(float anchorX, float anchorY, ModeSetting setting) {
            List<String> modes = setting.getModes() == null ? List.of() : setting.getModes();
            float widest = 0f;
            for (String m : modes) {
                widest = Math.max(widest, FontManager.INSTANCE.getStringWidthScaled(m, 9f));
            }
            this.w = Math.max(120f, widest + 34f);
            this.h = 26f + modes.size() * 20f;
            this.x = anchorX;
            this.y = anchorY;
            this.setting = setting;
        }

        private List<String> modes() {
            return setting.getModes() == null ? List.of() : setting.getModes();
        }

        @Override
        void render(DrawContext ctx, float mx, float my) {
            float a = alpha;
            GuiGfx.shadow(ctx, (int) x, (int) y, (int) w, (int) h, 8);
            fillRounded(ctx, x, y, w, h, 9f, ClickGui2Theme.rgb(ClickGui2Theme.Role.BACKGROUND));
            outlineRounded(ctx, x, y, w, h, 9f, ClickGui2Theme.rgb(ClickGui2Theme.Role.OUTLINE));

            FontManager fm = FontManager.INSTANCE;
            fm.drawStringScaled(ctx, "Выбрать", x + 10f, y + 6f,
                    ClickGui2Theme.fade(ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT), a), 1f);
            ClickGui2Widgets.hairline(ctx, x + 10f, y + 18f, w - 20f);
            ctx.enableScissor((int) x, (int) (y + 20f), (int) (x + w), (int) (y + h));
            float rowY = y + 22f;
            String current = current();
            for (String mode : modes()) {
                boolean active = mode.equals(current);
                boolean hover = !closing && ClickGui2Widgets.inRect(mx, my, x + 6f, rowY - 2f, w - 12f, 18f);
                if (hover) {
                    fillRounded(ctx, x + 6f, rowY - 2f, w - 12f, 18f, 5f,
                            ClickGui2Theme.fade(ClickGui2Theme.Role.SELECTED, a));
                }
                if (active) {
                    GuiGfx.dotRing(ctx, x + 8f, rowY + 7f, 2.6f,
                            ClickGui2Theme.fade(ClickGui2Theme.rgb(ClickGui2Theme.Role.MAIN), a));
                }
                int col = active ? ClickGui2Theme.rgb(ClickGui2Theme.Role.ACTIVE_TEXT)
                        : hover ? ClickGui2Theme.rgb(ClickGui2Theme.Role.OPEN_SETTING_TEXT)
                        : ClickGui2Theme.rgb(ClickGui2Theme.Role.DEOPEN_SETTING_TEXT);
                fm.drawStringScaled(ctx, mode, x + 16f, rowY + 2f, ClickGui2Theme.fade(col, a), 1f);
                rowY += 20f;
            }
            ctx.disableScissor();
        }

        private String current() {
            try {
                return setting.compute();
            } catch (Exception e) {
                return String.valueOf(setting.getValue());
            }
        }

        @Override
        void mouseClicked(float mx, float my, int button) {
            if (button != 0 || closing) return;
            float rowY = y + 22f;
            for (String mode : modes()) {
                if (ClickGui2Widgets.inRect(mx, my, x + 6f, rowY - 2f, w - 12f, 18f)) {
                    setting.setValue(mode);
                    SoundUtil.playClick(0.22f, 1.2f);
                    closing = true;
                    return;
                }
                rowY += 20f;
            }
        }

        @Override
        boolean mouseDragging(float mx, float my) {
            return false;
        }

        @Override
        void mouseReleased() {
        }

        @Override
        boolean mouseScrolled(double deltaY) {
            return false;
        }

        @Override
        boolean charTyped(String character) {
            return false;
        }
    }

    // ── Колор-пикер (порт mod0365) ──

    private static final class ColorPicker extends Popup {
        private static final float PAD = 10f;
        private static final float SV_W = 118f;
        private static final float SV_H = 86f;
        private static final float STRIP_W = 5f;
        private static final float HEX_H = 16f;

        private final ColorSetting setting;
        private float hue, sat, val;
        private boolean dragSV, dragHue, dragAlpha, editingHex;
        private String hexBuffer = "";

        ColorPicker(float anchorX, float anchorY, ColorSetting setting) {
            this.setting = setting;
            Color c = setting.getValue() != null ? setting.getValue() : Color.WHITE;
            float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
            this.hue = hsb[0];
            this.sat = hsb[1];
            this.val = hsb[2];
            this.w = PAD * 2f + SV_W + 6f + STRIP_W + 10f;
            this.h = PAD + SV_H + 8f + STRIP_W + 8f + HEX_H + PAD;
            this.x = anchorX - w;
            this.y = anchorY;
        }

        private float svX() {
            return x + PAD;
        }

        private float svY() {
            return y + PAD;
        }

        private float hueX() {
            return x + PAD + SV_W + 6f;
        }

        private float alphaY() {
            return svY() + SV_H + 8f;
        }

        private float hexY() {
            return alphaY() + STRIP_W + 10f;
        }

        private void apply() {
            int packed = Color.HSBtoRGB(hue, sat, val);
            Color c = setting.getValue() != null ? setting.getValue() : Color.WHITE;
            setting.setValue(new Color((packed >> 16) & 0xFF, (packed >> 8) & 0xFF, packed & 0xFF, c.getAlpha()));
        }

        private void applyAlpha(int alpha) {
            Color base = setting.getValue() != null ? setting.getValue() : Color.WHITE;
            setting.setValue(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
        }

        @Override
        void render(DrawContext ctx, float mx, float my) {
            float a = alpha;
            GuiGfx.shadow(ctx, (int) x - 3, (int) y - 3, (int) w + 6, (int) h + 6, 6);
            fillRounded(ctx, x, y, w, h, 10f, ClickGui2Theme.rgb(ClickGui2Theme.Role.BACKGROUND));
            outlineRounded(ctx, x, y, w, h, 10f, ClickGui2Theme.rgb(ClickGui2Theme.Role.OUTLINE));

            FontManager fm = FontManager.INSTANCE;

            // SV-область: базовый оттенок столбцами насыщенности + затухание яркости строками
            float sx = svX();
            float sy = svY();
            for (int col = 0; col < (int) SV_W; col++) {
                float s = col / SV_W;
                int packed = Color.HSBtoRGB(hue, s, 1f);
                RenderUtil.drawRect(ctx, sx + col, sy, sx + col + 1f, sy + SV_H, (packed & 0xFFFFFF) | (0xFF << 24));
            }
            int rows = 16;
            for (int j = 0; j < rows; j++) {
                float t = j / (float) rows;
                int rowAlpha = Math.round(255f * t * a);
                RenderUtil.drawRect(ctx, sx, sy + t * SV_H, sx + SV_W, sy + (t + 1f / rows) * SV_H,
                        rowAlpha << 24);
            }
            float knobX = sx + sat * SV_W;
            float knobY = sy + (1f - val) * SV_H;
            if (a > 0.4f) {
                GuiGfx.dotRing(ctx, knobX, knobY, 3.4f, ClickGui2Theme.rgb(ClickGui2Theme.Role.CIRCLE));
            }
            outlineRounded(ctx, sx, sy, SV_W, SV_H, 6f, ClickGui2Theme.fade(ClickGui2Theme.Role.OUTLINE, a));

            // Полоса оттенка: 1px сегменты по кругу тона
            float hx = hueX();
            for (int row = 0; row < (int) SV_H; row++) {
                float t = row / SV_H;
                int packed = Color.HSBtoRGB(t, 1f, 1f) & 0xFFFFFF;
                RenderUtil.drawRect(ctx, hx, sy + row, hx + STRIP_W, sy + row + 1f,
                        (packed) | (Math.round(255f * a) << 24));
            }
            float hueKnobY = sy + hue * SV_H;
            RenderUtil.drawRect(ctx, hx - 2f, hueKnobY - 2f, hx + STRIP_W + 2f, hueKnobY + 2f,
                    ClickGui2Theme.fade(ClickGui2Theme.rgb(ClickGui2Theme.Role.CIRCLE), a));
            outlineRounded(ctx, hx, sy, STRIP_W, SV_H, 2.5f, ClickGui2Theme.fade(ClickGui2Theme.Role.OUTLINE, a));

            // Полоса альфы с шахматной подложкой
            float ay = alphaY();
            for (int i = 0; i < (int) SV_W; i += 4) {
                boolean light = (i / 4) % 2 == 0;
                RenderUtil.drawRect(ctx, sx + i, ay, sx + Math.min(i + 4, SV_W), ay + STRIP_W,
                        (light ? (28 << 24) | 0xFFFFFF : (52 << 24) | 0xFFFFFF));
            }
            Color cur = currentColor();
            RenderUtil.drawRect(ctx, sx, ay, sx + SV_W, ay + STRIP_W,
                    (cur.getRGB() & 0x00FFFFFF) | (Math.round(cur.getAlpha() / 255f * 255f * a) << 24));
            float alphaKnobX = sx + SV_W * (cur.getAlpha() / 255f);
            GuiGfx.dot(ctx, alphaKnobX, ay + STRIP_W / 2f, 3.4f,
                    ClickGui2Theme.fade(ClickGui2Theme.rgb(ClickGui2Theme.Role.CIRCLE), a));

            // HEX-поле
            float tx = sx;
            float ty = hexY();
            fillRounded(ctx, tx, ty, SV_W + STRIP_W + 6f, HEX_H, 4f,
                    ClickGui2Theme.alpha(ClickGui2Theme.Role.BACKGROUND, 235));
            outlineRounded(ctx, tx, ty, SV_W + STRIP_W + 6f, HEX_H, 4f,
                    editingHex ? ClickGui2Theme.alpha(ClickGui2Theme.Role.MAIN, 180)
                            : ClickGui2Theme.fade(ClickGui2Theme.Role.OUTLINE, a));
            String display = editingHex || !hexBuffer.isEmpty() ? hexBuffer : hexOf(currentColor());
            drawText(ctx, display, tx + 6f, ty + 4f, ClickGui2Theme.fade(ClickGui2Theme.rgb(ClickGui2Theme.Role.TEXT), a));
            if (editingHex && ClickGui2Anim.caretBlink()) {
                float cw = FontManager.INSTANCE.getStringWidthScaled(display, 8f);
                ClickGui2Widgets.caret(ctx, tx + 6f + cw + 1f, ty + 4f, 9f);
            }

            // подпись формата
            drawText(ctx, "Цвет", tx + SV_W + STRIP_W + 8f, ty + 4f,
                    ClickGui2Theme.fade(ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT), a));
        }

        private static float hNearest(float ob) {
            return ob;
        }

        private Color currentColor() {
            return setting.getValue() != null ? setting.getValue() : Color.WHITE;
        }

        private static String hexOf(Color c) {
            return String.format(Locale.ROOT, "#%02X%02X%02X%02X", c.getAlpha(), c.getRed(), c.getGreen(), c.getBlue());
        }

        private static void drawText(DrawContext ctx, String s, float x, float y, int color) {
            FontManager.INSTANCE.drawStringScaled(ctx, s, x, y, color, 1f);
        }

        @Override
        void mouseClicked(float mx, float my, int button) {
            if (button != 0 || closing) return;
            float sx = svX();
            float sy = svY();

            if (ClickGui2Widgets.inRect(mx, my, sx, sy, SV_W, SV_H)) {
                dragSV = true;
                moveSV(mx, my);
                return;
            }
            if (ClickGui2Widgets.inRect(mx, my, hueX() - 2f, sy, STRIP_W + 4f, SV_H)) {
                dragHue = true;
                moveHue(my);
                return;
            }
            if (ClickGui2Widgets.inRect(mx, my, sx, alphaY() - 3f, SV_W, STRIP_W + 6f)) {
                dragAlpha = true;
                moveAlpha(mx);
                return;
            }
            // поле hex
            if (ClickGui2Widgets.inRect(mx, my, sx, hexY(), SV_W + STRIP_W + 6f, HEX_H)) {
                if (!editingHex) {
                    hexBuffer = hexOf(currentColor());
                    editingHex = true;
                }
                return;
            }
            if (editingHex) {
                editingHex = false;
            }
        }

        private void moveSV(float mx, float my) {
            sat = Math.max(0f, Math.min(1f, (mx - svX()) / SV_W));
            val = Math.max(0f, Math.min(1f, 1f - (my - svY()) / SV_H));
            apply();
        }

        private void moveHue(float my) {
            hue = Math.max(0f, Math.min(0.9999f, (my - svY()) / SV_H));
            apply();
        }

        private void moveAlpha(float mx) {
            float t = Math.max(0f, Math.min(1f, (mx - svX()) / SV_W));
            applyAlpha(Math.round(t * 255f));
        }

        @Override
        boolean mouseDragging(float mx, float my) {
            if (closing) return false;
            if (dragSV) {
                moveSV(mx, my);
                return true;
            }
            if (dragHue) {
                moveHue(my);
                return true;
            }
            if (dragAlpha) {
                moveAlpha(mx);
                return true;
            }
            return false;
        }

        @Override
        void mouseReleased() {
            dragSV = dragHue = dragAlpha = false;
        }

        @Override
        boolean mouseScrolled(double deltaY) {
            return false;
        }

        @Override
        boolean charTyped(String character) {
            if (!editingHex || character.isEmpty()) return false;
            char c = character.charAt(0);
            if (hexBuffer.length() < 9 && "#0123456789abcdefABCDEF".indexOf(c) >= 0) {
                hexBuffer += c;
                tryApplyHex();
                return true;
            }
            return false;
        }

        @Override
        boolean keyPressed(int key) {
            if (!editingHex) return false;
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                tryApplyHex();
                editingHex = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                editingHex = false;
                hexBuffer = "";
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE && !hexBuffer.isEmpty()) {
                hexBuffer = hexBuffer.substring(0, hexBuffer.length() - 1);
                tryApplyHex();
                return true;
            }
            return true; // редактирование потребляет клавиши
        }

        private void tryApplyHex() {
            String cleaned = hexBuffer.replace("#", "");
            if (cleaned.length() == 8) {
                try {
                    long v = Long.parseLong(cleaned, 16);
                    int argb = (int) v;
                    setting.setValue(new Color(argb, true));
                } catch (NumberFormatException ignored) {
                }
            } else if (cleaned.length() == 6) {
                try {
                    long v = Long.parseLong(cleaned, 16) & 0xFFFFFF;
                    Color base = currentColor();
                    setting.setValue(new Color((int) (v >> 16) & 0xFF, (int) (v >> 8) & 0xFF, (int) v & 0xFF, base.getAlpha()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    // ── Диалог бинда (порт mod0312) ──

    private static final class KeybindDialog extends Popup {
        private static final float W = 167f;
        private static final float H = 84f;

        private final KeybindSetting setting;
        boolean capturing;

        KeybindDialog(float anchorX, float anchorY, KeybindSetting setting) {
            this.setting = setting;
            this.w = W;
            this.h = H;
            this.x = anchorX - W;
            this.y = anchorY;
        }

        @Override
        void render(DrawContext ctx, float mx, float my) {
            float a = alpha;
            GuiGfx.shadow(ctx, (int) x, (int) y, (int) w, (int) h, 10);
            fillRounded(ctx, x, y, w, h, 10f, ClickGui2Theme.rgb(ClickGui2Theme.Role.BACKGROUND));
            outlineRounded(ctx, x, y, w, h, 10f, ClickGui2Theme.rgb(ClickGui2Theme.Role.OUTLINE));

            FontManager fm = FontManager.INSTANCE;
            fm.drawStringScaled(ctx, "Кнопка", x + 10f, y + 8f,
                    ClickGui2Theme.fade(ClickGui2Theme.rgb(ClickGui2Theme.Role.TEXT), a), 1f);

            boolean btnHover = !closing && ClickGui2Widgets.inRect(mx, my, x + 10f, y + 24f, w - 20f, 22f);
            String label = capturing ? "..." : keyDisplay();
            fillRounded(ctx, x + 10f, y + 24f, w - 20f, 22f, 5f,
                    ClickGui2Theme.fade(btnHover ? ClickGui2Theme.Role.SELECTED : ClickGui2Theme.Role.LINE, a * (capturing ? 1.8f : 1f)));
            outlineRounded(ctx, x + 10f, y + 24f, w - 20f, 22f, 5f,
                    capturing ? ClickGui2Theme.alpha(ClickGui2Theme.Role.MAIN, 190)
                            : ClickGui2Theme.fade(ClickGui2Theme.Role.OUTLINE, a));
            float tw = fm.getStringWidthScaled(label, 9f);
            fm.drawStringScaled(ctx, label, x + (w - tw) / 2f, y + 24f + 7f,
                    ClickGui2Theme.fade(btnHover || capturing
                            ? ClickGui2Theme.rgb(ClickGui2Theme.Role.TEXT)
                            : ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT), a), 1f);

            boolean resetHover = !closing && ClickGui2Widgets.inRect(mx, my, x + 10f, y + 54f, 47f, 18f);
            boolean doneHover = !closing && ClickGui2Widgets.inRect(mx, my, x + w - 57f, y + 54f, 47f, 18f);
            ClickGui2Widgets.chip(ctx, x + 10f, y + 54f, 47f, 18f, resetHover, "Сбросить");
            ClickGui2Widgets.chip(ctx, x + w - 57f, y + 54f, 47f, 18f, doneHover, "Готово");
        }

        private String keyDisplay() {
            try {
                return setting.getKeyName();
            } catch (Exception e) {
                return "None";
            }
        }

        @Override
        void mouseClicked(float mx, float my, int button) {
            if (button != 0 || closing) return;
            if (ClickGui2Widgets.inRect(mx, my, x + 10f, y + 24f, w - 20f, 22f)) {
                capturing = !capturing;
                return;
            }
            if (ClickGui2Widgets.inRect(mx, my, x + 10f, y + 54f, 47f, 18f)) {
                setting.setValue(-1);
                SoundUtil.playClick(0.2f, 0.9f);
                return;
            }
            if (ClickGui2Widgets.inRect(mx, my, x + w - 57f, y + 54f, 47f, 18f)) {
                closing = true;
                SoundUtil.playClick(0.2f, 1.1f);
            }
        }

        @Override
        boolean mouseDragging(float mx, float my) {
            return false;
        }

        @Override
        void mouseReleased() {
        }

        @Override
        boolean mouseScrolled(double deltaY) {
            return false;
        }

        @Override
        boolean charTyped(String character) {
            return false;
        }

        @Override
        boolean keyPressed(int key) {
            if (!capturing) return false;
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                capturing = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                capturing = false;
                return true;
            }
            setting.setValue(key);
            capturing = false;
            SoundUtil.playClick(0.24f, 1.3f);
            return true;
        }
    }
}

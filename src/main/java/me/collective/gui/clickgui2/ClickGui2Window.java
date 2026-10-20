package me.collective.gui.clickgui2;

import me.collective.font.FontManager;
import me.collective.module.Module;
import me.collective.module.client.UI;
import me.collective.setting.BooleanSetting;
import me.collective.setting.ColorSetting;
import me.collective.setting.ModeSetting;
import me.collective.setting.NumberSetting;
import me.collective.setting.Setting;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static me.collective.gui.GuiGfx.fillRounded;
import static me.collective.gui.GuiGfx.outlineRounded;
import static me.collective.gui.GuiGfx.shadow;

/**
 * Плавающее окно категории ClickGui2 — перенос структуры исходника:
 * скруглённое стекло BACKGROUND + рамка OUTLINE + тень SHADOW, HEADER
 * с именем категории (CATEGORY_TEXT, у неактивного — DESELECT_CATEGORY_TEXT),
 * строки модулей (клик — вкл/выкл, ПКМ — раскрыть настройки), ховером
 * SELECTED (0.8 при наведении / 0.55 исходно — как в mod0004), настройки
 * внутри раскрытой строки: тумблер (ACTIVE/DEACTIVE_STATE), слайдер (круг
 * CIRCLE по треку LINE), циклер режима "< значение >", свотч цвета.
 * Прокрутка — полоса SCROLL, перетаскивание — за HEADER.
 */
public class ClickGui2Window {
    public static final float WIDTH = 150f;
    private static final float HEADER_H = 20f;
    private static final float ROW_H = 16f;
    private static final float SETTING_H = 15f;
    private static final float PAD = 5f;
    private static final float RADIUS = 10f;
    private static final float VIEWPORT_MAX = 170f;

    private final String title;
    private final List<Module> modules;

    private float x, y;
    private float scroll = 0f;
    private boolean dragging = false;
    private float dragOffsetX, dragOffsetY;

    private Module expanded;
    private NumberSetting activeSlider;
    private ColorSetting colorTarget;

    private static final class Anim {
        float hover;
        float active;
    }

    private final Map<Module, Anim> moduleAnims = new IdentityHashMap<>();
    private final Map<Setting<?>, float[]> settingAnims = new IdentityHashMap<>();

    public ClickGui2Window(String title, List<Module> modules, float x, float y) {
        this.title = title;
        this.modules = modules;
        this.x = x;
        this.y = y;
    }

    public ColorSetting getColorTarget() {
        return colorTarget;
    }

    public void clearColorTarget() {
        this.colorTarget = null;
    }

    // ── Геометрия ──

    private float contentHeight(String query) {
        float total = 0f;
        for (Module m : modules) {
            if (!matches(m, query)) continue;
            total += ROW_H;
            if (m == expanded) {
                for (Setting<?> s : m.getSettings()) {
                    if (s.isVisible() && renderable(s)) total += SETTING_H;
                }
            }
        }
        return total;
    }

    private float bodyTop() {
        return y + HEADER_H + PAD - scroll;
    }

    private float fullHeight(String query) {
        float total = contentHeight(query);
        return total <= 0.001f ? HEADER_H + PAD : HEADER_H + Math.min(total, VIEWPORT_MAX) + PAD;
    }

    public boolean inWindow(float mx, float my) {
        return mx >= x && mx <= x + WIDTH && my >= y && my <= y + fullHeight(currentSearch);
    }

    public boolean headerHit(float mx, float my) {
        return mx >= x && mx <= x + WIDTH && my >= y && my <= y + HEADER_H;
    }

    /** Прямоугольник окна для блюра: {x1, y1, x2, y2}. */
    public float[] bounds() {
        return new float[]{x, y, x + WIDTH, y + fullHeight(currentSearch)};
    }

    // ── Состояние поиска и скролла ──

    private String currentSearch = "";

    public void setSearch(String query) {
        this.currentSearch = query == null ? "" : query.toLowerCase();
    }

    public void scrollBy(float amount) {
        float total = contentHeight(currentSearch);
        float max = Math.max(0f, total - Math.min(total, VIEWPORT_MAX));
        this.scroll = Math.max(0f, Math.min(max, this.scroll + amount));
    }

    private boolean matches(Module m, String query) {
        return query.isEmpty() || m.getName().toLowerCase().contains(query);
    }

    private boolean renderable(Setting<?> s) {
        return s instanceof BooleanSetting || s instanceof NumberSetting
                || s instanceof ModeSetting || s instanceof ColorSetting;
    }

    // ── Перетаскивание ──

    public void startDrag(float mx, float my) {
        dragging = headerHit(mx, my);
        dragOffsetX = mx - x;
        dragOffsetY = my - y;
    }

    public boolean isDragging() {
        return dragging;
    }

    public void endDrag() {
        dragging = false;
        activeSlider = null;
    }

    public boolean dragTo(float mx, float my) {
        if (dragging) {
            x = mx - dragOffsetX;
            y = my - dragOffsetY;
            return true;
        }
        return false;
    }

    // ── Клик ──

    /**
     * Клик по окну: true — обработано.
     * ЛКМ по модулю — вкл/выкл; ПКМ по модулю — раскрыть/свернуть настройки.
     * Клик по свотчу цвета вызывает {@code onColor}.
     */
    public boolean mouseClicked(float mx, float my, int button, Consumer<ColorSetting> onColor) {
        if (!inWindow(mx, my)) return false;

        float rowY = bodyTop();
        for (Module m : modules) {
            if (!matches(m, currentSearch)) continue;

            if (my >= rowY && my < rowY + ROW_H) {
                if (button == 0) {
                    m.toggle();
                } else if (button == 1) {
                    expanded = m == expanded ? null : m;
                }
                return true;
            }

            if (m == expanded && my >= rowY + ROW_H) {
                float sy = rowY + ROW_H;
                for (Setting<?> s : m.getSettings()) {
                    if (!s.isVisible() || !renderable(s)) continue;
                    if (my >= sy && my < sy + SETTING_H) {
                        if (clickSetting(s, mx, my, button, onColor)) return true;
                    }
                    sy += SETTING_H;
                }
                // клик по пустому месту раскрытой области — просто «съедаем»
                return true;
            }

            rowY += ROW_H;
            if (m == expanded) {
                for (Setting<?> s : m.getSettings()) {
                    if (s.isVisible() && renderable(s)) rowY += SETTING_H;
                }
            }
        }
        return true;
    }

    private boolean clickSetting(Setting<?> s, float mx, float my, int button, Consumer<ColorSetting> onColor) {
        // свотч цвета открывает пикер кликом на строке (любой кнопкой)
        if (s instanceof ColorSetting color && onColor != null) {
            onColor.accept(color);
            return true;
        }

        if (button == 0) {
            if (s instanceof BooleanSetting bool) {
                bool.setValue(!bool.getValue());
                return true;
            }
            if (s instanceof NumberSetting num) {
                activeSlider = num;
                applySlider(num, mx);
                return true;
            }
            if (s instanceof ModeSetting mode) {
                java.util.List<String> variants = mode.getModes();
                if (variants != null && !variants.isEmpty()) {
                    int idx = Math.max(0, variants.indexOf(mode.compute()));
                    mode.setValue(variants.get((idx + 1) % variants.size()));
                    return true;
                }
            }
        } else if (button == 1) {
            if (s instanceof ModeSetting mode) {
                java.util.List<String> variants = mode.getModes();
                if (variants != null && !variants.isEmpty()) {
                    int idx = Math.max(0, variants.indexOf(mode.compute()));
                    mode.setValue(variants.get((idx - 1 + variants.size()) % variants.size()));
                    return true;
                }
            }
        }
        return false;
    }

    public boolean mouseDragging(float mx, float my) {
        if (activeSlider != null) {
            applySlider(activeSlider, mx);
            return true;
        }
        return dragging && dragTo(mx, my);
    }

    private void applySlider(NumberSetting num, float mx) {
        float usable = WIDTH - PAD * 2f - 6f;
        float t = Math.max(0f, Math.min(1f, (mx - (x + PAD + 2f)) / usable));
        double range = Math.max(0.0000001, num.getMax() - num.getMin());
        double v = num.getMin() + t * range;
        num.setValue(Math.round(v * 100) / 100.0);
    }

    private Anim anim(Module m) {
        return moduleAnims.computeIfAbsent(m, ignored -> new Anim());
    }

    private float[] settingAnim(Setting<?> s) {
        return settingAnims.computeIfAbsent(s, ignored -> new float[]{0f});
    }

    // ── Рендер ──

    public void render(DrawContext ctx, float mx, float my, float dt) {
        float total = contentHeight(currentSearch);
        float viewable = Math.min(total, VIEWPORT_MAX);
        float h = total <= 0.001f ? HEADER_H + PAD : HEADER_H + viewable + PAD;

        shadow(ctx, (int) x - 3, (int) y - 3, (int) WIDTH + 6, (int) h + 6, 5);
        fillRounded(ctx, x, y, WIDTH, h, RADIUS, ClickGui2Theme.rgb(ClickGui2Theme.Role.BACKGROUND));
        outlineRounded(ctx, x, y, WIDTH, h, RADIUS, ClickGui2Theme.rgb(ClickGui2Theme.Role.OUTLINE));

        FontManager fm = FontManager.INSTANCE;
        float headScale = 8f;

        boolean headHover = headerHit(mx, my);
        int headColor = headHover
                ? ClickGui2Theme.rgb(ClickGui2Theme.Role.CATEGORY_TEXT)
                : ClickGui2Theme.rgb(ClickGui2Theme.Role.DESELECT_CATEGORY_TEXT);
        fm.drawStringWithShadow(ctx, title, x + PAD + 2f,
                y + (HEADER_H - fm.getFontHeightScaled(headScale)) / 2f, headColor);

        float rowY = bodyTop();
        float clipTop = y + HEADER_H;
        float clipH = Math.max(0f, h - HEADER_H - PAD - 3f);
        for (Module m : modules) {
            if (!matches(m, currentSearch)) continue;

            boolean open = m == expanded;
            int extra = open ? countSettings(m) : 0;
            float rowH = ROW_H + extra * SETTING_H;
            if (rowY + rowH >= clipTop && rowY <= clipTop + clipH) {
                renderModuleRow(ctx, m, open, rowY, mx, my, dt);
            }
            rowY += rowH;
        }

        if (total > viewable && viewable > 0) {
            float track = clipH;
            float thumbH = Math.max(8f, track * viewable / total);
            float thumbT = Math.max(0f, track - thumbH);
            float thumbY = (scroll / Math.max(0.001f, total - viewable)) * thumbT;
            ClickGui2Widgets.scrollBar(ctx, x + WIDTH - 3f, clipTop + 2f, Math.max(0f, track - 4f), thumbY, thumbH);
        }
    }

    private int countSettings(Module m) {
        int count = 0;
        for (Setting<?> s : m.getSettings()) {
            if (s.isVisible() && renderable(s)) count++;
        }
        return count;
    }

    private void renderModuleRow(DrawContext ctx, Module m, boolean open, float rowY,
                                 float mx, float my, float dt) {
        Anim a = anim(m);
        FontManager fm = FontManager.INSTANCE;
        float scale = 7f;

        boolean hovered = mx >= x && mx <= x + WIDTH && my >= rowY && my < rowY + ROW_H;
        a.hover = ClickGui2Widgets.ease(a.hover, hovered ? 0.8f : 0.55f, dt, 10f);

        float textY = rowY + (ROW_H - fm.getFontHeightScaled(scale)) / 2f;

        // строка: имя + статусная полоска + bind
        int stateColor = m.isToggled()
                ? ClickGui2Theme.rgb(ClickGui2Theme.Role.MAIN)
                : ClickGui2Theme.rgb(ClickGui2Theme.Role.LINE);
        int label = m.isToggled()
                ? ClickGui2Theme.rgb(ClickGui2Theme.Role.OPEN_SETTING_TEXT)
                : ClickGui2Theme.rgb(ClickGui2Theme.Role.DEOPEN_SETTING_TEXT);
        if (a.hover > 0.001f) {
            fillRounded(ctx, x + PAD - 1f, rowY, WIDTH - PAD * 2f + 2f, ROW_H, 5f,
                    ClickGui2Theme.alpha(ClickGui2Theme.Role.SELECTED, Math.round(a.hover * 120f)));
        }
        fillRounded(ctx, x + PAD - 1f, rowY + 2f, 2.5f, ROW_H - 4f, 1.2f, stateColor);

        fm.drawStringWithShadow(ctx, m.getName(), x + PAD + 4f, textY, label);
        String bind = bindText(m);
        if (!bind.isEmpty()) {
            float bw = fm.getStringWidthScaled(bind, scale);
            fm.drawStringWithShadow(ctx, bind, x + WIDTH - PAD - bw, textY,
                    ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT));
        }

        if (open) {
            float sy = rowY + ROW_H;
            for (Setting<?> s : m.getSettings()) {
                if (!s.isVisible() || !renderable(s)) continue;
                renderSetting(ctx, s, sy, mx, my, dt, scale);
                sy += SETTING_H;
            }
        }
    }

    private String bindText(Module m) {
        UI ui = UI.INSTANCE;
        if (ui == null || !ui.binds.getValue()) return "";
        try {
            int key = m.getKeybind().getValue();
            if (key == -1) return "";
            String name = GLFW.glfwGetKeyName(key, 0);
            return name == null ? "k" + key : name;
        } catch (Throwable t) {
            return "";
        }
    }

    private void renderSetting(DrawContext ctx, Setting<?> s, float y, float mx, float my,
                               float dt, float scale) {
        FontManager fm = FontManager.INSTANCE;
        float textY = y + (SETTING_H - fm.getFontHeightScaled(scale)) / 2f;
        int labelColor = ClickGui2Theme.rgb(ClickGui2Theme.Role.DEOPEN_SETTING_TEXT);
        int valueColor = ClickGui2Theme.rgb(ClickGui2Theme.Role.ACTIVE_TEXT);

        if (s instanceof BooleanSetting bool) {
            float[] a = settingAnim(s);
            a[0] = ClickGui2Widgets.ease(a[0], bool.getValue() ? 1f : 0f, dt, 12f);
            fm.drawStringWithShadow(ctx, s.getName(), x + PAD + 6f, textY,
                    bool.getValue() ? valueColor : labelColor);
            ClickGui2Widgets.toggle(ctx, x + WIDTH - PAD - 20f, textY + 1f, a[0], false);
        } else if (s instanceof NumberSetting num) {
            fm.drawStringWithShadow(ctx, s.getName(), x + PAD + 6f, y, labelColor);
            String value = fmtValue(num);
            float vw = fm.getStringWidthScaled(value, scale);
            fm.drawStringWithShadow(ctx, value, x + WIDTH - PAD - vw - 1f, y, valueColor);
            float progress = (float) ((num.getFloatValue() - num.getMin())
                    / Math.max(0.0001, num.getMax() - num.getMin()));
            ClickGui2Widgets.slider(ctx, x + PAD + 6f, y + SETTING_H - 5f, WIDTH - PAD * 2f - 8f,
                    progress, activeSlider == num, scale);
        } else if (s instanceof ModeSetting mode) {
            fm.drawStringWithShadow(ctx, s.getName(), x + PAD + 6f, textY, labelColor);
            String v = mode.compute();
            float vw = fm.getStringWidthScaled(v, scale);
            fm.drawStringWithShadow(ctx, v, x + WIDTH - PAD - 24f - vw, textY, valueColor);
            ClickGui2Widgets.modeArrows(ctx, textY, SETTING_H - 2f, x + WIDTH - PAD - 3f,
                    ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT), scale);
        } else if (s instanceof ColorSetting color) {
            fm.drawStringWithShadow(ctx, s.getName(), x + PAD + 6f, textY, labelColor);
            ClickGui2Widgets.swatch(ctx, x + WIDTH - PAD - 14f, y + 3.5f, color.getValue(),
                    colorTarget == color);
        }
    }

    private String fmtValue(NumberSetting num) {
        double v = num.getFloatValue();
        if (Math.abs(v) >= 1000) return String.valueOf((int) Math.round(v));
        return String.format("%.1f", v);
    }
}

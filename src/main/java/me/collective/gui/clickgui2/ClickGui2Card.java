package me.collective.gui.clickgui2;

import me.collective.font.FontManager;
import me.collective.gui.GuiGfx;
import me.collective.module.Module;
import me.collective.category.Category;
import me.collective.module.client.UI;
import me.collective.render.SoundUtil;
import me.collective.setting.Setting;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

import static me.collective.gui.GuiGfx.fillRounded;
import static me.collective.gui.GuiGfx.outlineRounded;

/**
 * Карточка модуля — перенос mod0093: шапка (имя + описание бегущей
 * строкой), звезда избранного (восьмиконечная искра, зона 20×22 у правого
 * края, канарейный цвет 255,210,64), тумблер-пилюля 30×18, раскрытие
 * настроек правым кликом со ступенчатым проявлением строк, плавная высота.
 */
public final class ClickGui2Card {
    static final float CARD_W = 296f;
    static final float HEADER_H = 40f;
    static final float SET_GAP = 5f;
    static final float BOTTOM_PAD = 8f;

    private final Module module;
    private final Category category;
    private final List<ClickGui2Rows.Row> rows = new ArrayList<>();
    private String rowsKey = "";

    private float x, y;
    private boolean expanded;
    private float expandT;
    private float toggleT;
    private float favoriteT;
    private float headerHoverT;
    private float focusFlash;
    private boolean hoverLatch;

    public ClickGui2Card(Module module, Category category) {
        this.module = module;
        this.category = category;
    }

    public Category category() {
        return category;
    }

    public Module module() {
        return module;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean value) {
        if (expanded == value) return;
        expanded = value;
        if (expanded) {
            ensureRows();
        } else {
            for (ClickGui2Rows.Row row : rows) {
                row.clearFocus();
            }
        }
    }

    public void focus() {
        focusFlash = 1f;
    }

    // ── Актуализация строк (сигнатурный ключ состава, как кл() в исходнике) ──

    private void ensureRows() {
        StringBuilder key = new StringBuilder();
        List<ClickGui2Rows.Row> fresh = new ArrayList<>();
        for (Setting<?> s : module.getSettings()) {
            if (!s.isVisible()) continue;
            ClickGui2Rows.Row row = ClickGui2Rows.of(s);
            if (row == null) continue;
            fresh.add(row);
            key.append(s.getName()).append('\u0000').append(s.getClass().getName()).append('\n');
        }
        if (!key.toString().equals(rowsKey) || rows.size() != fresh.size()) {
            rowsKey = key.toString();
            boolean[] expandedState = new boolean[1];
            rows.clear();
            rows.addAll(fresh);
            expandedState[0] = expanded;
        }
    }

    float rowsHeight() {
        ensureRows();
        float total = 0f;
        for (ClickGui2Rows.Row row : rows) {
            total += row.height() + SET_GAP;
        }
        return total > 0f ? total - SET_GAP + BOTTOM_PAD : 0f;
    }

    /** Полная высота карточки (с раскрытыми строками). */
    public float height() {
        float rh = rowsHeight();
        return HEADER_H + ClickGui2Anim.smooth(expandT) * rh;
    }

    void layout(float x, float y) {
        this.x = x;
        this.y = y;
    }

    float cardX() {
        return x;
    }

    float cardY() {
        return y;
    }

    boolean cardContains(float mx, float my) {
        return mx >= x && mx <= x + CARD_W && my >= y && my <= y + height();
    }

    // ── Взаимодействие ──

    public void mouseClicked(float mx, float my, int button, ClickGui2Popups popups) {
        boolean header = mx >= x && mx <= x + CARD_W && my >= y && my <= y + HEADER_H;
        if (header) {
            if (starZone(mx, my)) {
                if (button == 0) {
                    FavoriteStore.toggle(module.getName());
                    SoundUtil.playClick(0.2f, 1.35f);
                }
                return;
            }
            if (button == 0) {
                module.toggle();
                SoundUtil.playClick(0.2f, module.isToggled() ? 1.2f : 0.85f);
                return;
            }
            if (button == 1) {
                setExpanded(!expanded);
                SoundUtil.playClick(0.16f, expanded ? 1.25f : 0.95f);
                return;
            }
            return;
        }

        ensureRows();
        float rowY = y + HEADER_H;
        for (ClickGui2Rows.Row row : rows) {
            if (ClickGui2Widgets.inRect(mx, my, x + 12f, rowY, CARD_W - 24f, row.height())) {
                row.mouseClicked(mx, my, button, popups);
                return;
            }
            rowY += row.height() + SET_GAP;
        }
    }

    public boolean mouseDragging(float mx, float my) {
        for (ClickGui2Rows.Row row : rows) {
            if (row.mouseDragging(mx, my)) {
                return true;
            }
        }
        return false;
    }

    public void mouseReleased() {
        for (ClickGui2Rows.Row row : rows) {
            row.mouseReleased();
        }
    }

    /** Ctrl+скролл над строкой числа меняет значение мелкими шагами. */
    public boolean scrolled(float mx, float my, double deltaY, boolean ctrl) {
        if (!expanded) return false;
        ensureRows();
        float rowY = y + HEADER_H;
        for (ClickGui2Rows.Row row : rows) {
            if (ClickGui2Widgets.inRect(mx, my, x + 12f, rowY, CARD_W - 24f, row.height())) {
                return row.scrolled(deltaY, ctrl);
            }
            rowY += row.height() + SET_GAP;
        }
        return false;
    }

    void charTyped(String character) {
        for (ClickGui2Rows.Row row : rows) {
            row.charTyped(character);
        }
    }

    boolean keyPressed(int key) {
        for (ClickGui2Rows.Row row : rows) {
            if (row.keyPressed(key)) {
                return true;
            }
        }
        return false;
    }

    boolean wantsKeys() {
        for (ClickGui2Rows.Row row : rows) {
            if (row.wantsKeys()) {
                return true;
            }
        }
        return false;
    }

    /** Гасить фокусы инлайн-редактирования (смена категории/скролл). */
    public void blurRows() {
        for (ClickGui2Rows.Row row : rows) {
            row.clearFocus();
        }
    }

    private boolean starZone(float mx, float my) {
        return mx >= x + CARD_W - 68f && mx <= x + CARD_W - 48f
                && my >= y + 8f && my <= y + 30f;
    }

    // ── Рендер ──

    public void render(DrawContext ctx, float mx, float my, float dt) {
        float h = height();
        boolean favorite = FavoriteStore.isFavorite(module.getName());

        boolean hover = ClickGui2Widgets.inRect(mx, my, x, y, CARD_W, HEADER_H);
        headerHoverT = ClickGui2Anim.to01(headerHoverT, hover, dt, 9f);
        if (hover && !hoverLatch) {
            hoverLatch = true;
            UI ui = UI.INSTANCE;
            if (ui != null && ui.hoverSound.getValue()) {
                SoundUtil.playHover(0.14f, 1.5f);
            }
        } else if (!hover) {
            hoverLatch = false;
        }

        toggleT = ClickGui2Anim.to01(toggleT, module.isToggled(), dt, 9f);
        favoriteT = ClickGui2Anim.to01(favoriteT, favorite, dt, 7f);
        expandT = ClickGui2Anim.to01(expandT, expanded, dt, expanded ? 14f : 18f);
        focusFlash = Math.max(0f, focusFlash - dt * 0.8f);

        // подложка: тень, вспышка фокуса, стеклянная заливка, обводка
        GuiGfx.shadow(ctx, (int) x + 1, (int) y + 2, (int) CARD_W - 2, (int) h, 7);
        if (focusFlash > 0.01f) {
            float pulse = (float) Math.sin(Math.min(1f, focusFlash) * Math.PI);
            fillRounded(ctx, x, y, CARD_W, h, 12f,
                    ClickGui2Theme.alpha(ClickGui2Theme.Role.MAIN, Math.round(pulse * 70)));
        }
        int fillAlpha = Math.round(60 + 36 * headerHoverT);
        fillRounded(ctx, x, y, CARD_W, h, 12f,
                ClickGui2Theme.alpha(ClickGui2Theme.Role.BACKGROUND, fillAlpha));
        outlineRounded(ctx, x, y, CARD_W, h, 12f, ClickGui2Theme.rgb(ClickGui2Theme.Role.OUTLINE));

        renderHeader(ctx, favorite);

        float revealBase = ClickGui2Anim.smooth(expandT);
        if (revealBase > 0.01f) {
            ensureRows();
            ctx.enableScissor((int) x, (int) (y + HEADER_H - 2f), (int) (x + CARD_W), (int) (y + h + 2f));
            float rowY = y + HEADER_H + 4f * (1f - revealBase);
            int idx = 0;
            for (ClickGui2Rows.Row row : rows) {
                float reveal = ClickGui2Anim.stagger(revealBase, idx, Math.max(1, rows.size()));
                row.layout(x + 12f, rowY + (1f - reveal) * 6f, CARD_W - 24f);
                row.render(ctx, mx, my, dt, reveal);
                rowY += row.height() + SET_GAP;
                idx++;
            }
            ctx.disableScissor();
        }
    }

    private void renderHeader(DrawContext ctx, boolean favorite) {
        FontManager fm = FontManager.INSTANCE;

        int nameColor = ClickGui2Theme.blend(
                ClickGui2Theme.rgb(ClickGui2Theme.Role.DEACTIVE_TEXT),
                ClickGui2Theme.rgb(ClickGui2Theme.Role.TEXT), toggleT);
        fm.drawStringScaled(ctx, module.getName(), x + 12f, y + 7f, nameColor, 12f / 9f);

        String desc = module.getDescription() == null ? "" : module.getDescription();
        if (!desc.isEmpty()) {
            renderMarquee(ctx, desc, x + 12f, y + 24f, CARD_W - 100f);
        }

        float starX = x + CARD_W - 58f;
        float starY = y + 19f;
        int starColor = ClickGui2Theme.blend(
                ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT),
                (255 << 16) | (210 << 8) | 64, favoriteT);
        if (favorite || favoriteT > 0.01f) {
            ClickGui2Widgets.starIcon(ctx, starX, starY, 6.2f + favoriteT * 1.8f,
                    ClickGui2Theme.fade(starColor, 0.4f + 0.6f * favoriteT));
        } else {
            int ghost = ClickGui2Theme.fade(ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT),
                    0.55f * headerHoverT);
            if (headerHoverT > 0.03f) {
                ClickGui2Widgets.starIcon(ctx, starX, starY, 6.2f, ghost);
            }
        }

        ClickGui2Widgets.togglePill(ctx, x + CARD_W - 42f, y + 11f, toggleT, headerHoverT > 0.5f);
    }

    private void renderMarquee(DrawContext ctx, String text, float px, float py, float maxW) {
        FontManager fm = FontManager.INSTANCE;
        float textW = fm.getStringWidthScaled(text, 8f);
        ctx.enableScissor((int) px, (int) py - 2, (int) (px + maxW), (int) py + 12);
        int color = ClickGui2Theme.fade(ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT),
                0.7f + 0.3f * headerHoverT);
        if (textW <= maxW) {
            fm.drawStringScaled(ctx, text, px, py, color, 8f / 9f);
        } else {
            float step = textW + 14f;
            float speed = headerHoverT > 0.3f ? 26f : 12f;
            float scroll = (float) ((System.currentTimeMillis() / 1000.0 * speed) % step);
            fm.drawStringScaled(ctx, text, px - scroll, py, color, 8f / 9f);
            fm.drawStringScaled(ctx, text, px - scroll + step, py, color, 8f / 9f);
        }
        ctx.disableScissor();
    }
}

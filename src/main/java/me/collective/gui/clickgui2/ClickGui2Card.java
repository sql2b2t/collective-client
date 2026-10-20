package me.collective.gui.clickgui2;

import me.collective.font.FontManager;
import me.collective.gui.GuiGfx;
import me.collective.module.Module;
import me.collective.category.Category;
import me.collective.module.client.UI;
import me.collective.render.RenderUtil;
import me.collective.render.SoundUtil;
import me.collective.setting.Setting;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

import static me.collective.gui.GuiGfx.fillRounded;
import static me.collective.gui.GuiGfx.outlineRounded;

/**
 * Карточка модуля — «premium glass»: стеклянная подложка с бликом,
 * включённое состояние ловит акцентный ореол с медленным дыханием,
 * тумблер 32×18, звезда избранного, шеврон в круглой подложке,
 * раскрытие строк правым кликом со ступенчатым проявлением.
 */
public final class ClickGui2Card {
    static final float CARD_W = 296f;
    static final float HEADER_H = 44f;
    static final float SET_GAP = 5f;
    static final float BOTTOM_PAD = 8f;

    private final Module module;
    private final Category category;
    private final String lowName;
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
        this.lowName = module.getName().toLowerCase();
    }

    public Category category() {
        return category;
    }

    String lowName() {
        return lowName;
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

    // ── Актуализация строк: сверка по ссылкам, без аллокаций в кадре ──

    private void ensureRows() {
        visibleScratch.clear();
        boolean touch = false;
        for (Setting<?> s : module.getSettings()) {
            if (!s.isVisible()) continue;
            if (ClickGui2Rows.of(s) == null) continue;
            visibleScratch.add(s);
            touch = true;
        }
        boolean same = visibleScratch.size() == rows.size();
        if (same) {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).setting() != visibleScratch.get(i)) {
                    same = false;
                    break;
                }
            }
        }
        if (same || !touch) {
            return;
        }
        rows.clear();
        StringBuilder key = new StringBuilder();
        for (Setting<?> s : visibleScratch) {
            rows.add(ClickGui2Rows.of(s));
            key.append(s.getClass().getName()).append('\n');
        }
        rowsKey = key.toString();
        cachedRowsH = -1f;
    }

    private final ArrayList<Setting<?>> visibleScratch = new ArrayList<>();
    private float cachedRowsH = -1f;

    float rowsHeight() {
        ensureRows();
        if (cachedRowsH < 0f) {
            float total = 0f;
            for (ClickGui2Rows.Row row : rows) {
                total += row.height() + SET_GAP;
            }
            cachedRowsH = total > 0f ? total - SET_GAP + BOTTOM_PAD : 0f;
        }
        return cachedRowsH;
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
            if (toggleZone(mx, my)) {
                if (button == 0) {
                    module.toggle();
                    SoundUtil.playClick(0.2f, module.isToggled() ? 1.2f : 0.85f);
                } else if (button == 1) {
                    setExpanded(!expanded);
                    SoundUtil.playClick(0.16f, expanded ? 1.25f : 0.95f);
                }
                return;
            }
            if (starZone(mx, my)) {
                if (button == 0) {
                    FavoriteStore.toggle(module.getName());
                    SoundUtil.playClick(0.2f, 1.35f);
                }
                return;
            }
            if (button == 0 && expandChipZone(mx, my)) {
                setExpanded(!expanded);
                SoundUtil.playClick(0.16f, expanded ? 1.25f : 0.95f);
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
            float rowH = ClickGui2Anim.smooth(expandT) * row.height();
            if (rowH < 1f) continue;
            if (ClickGui2Widgets.inRect(mx, my, x + 14f, rowY, CARD_W - 28f, rowH)) {
                row.mouseClicked(mx, my, button, popups);
                return;
            }
            rowY += rowH + SET_GAP;
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
            if (ClickGui2Widgets.inRect(mx, my, x + 14f, rowY, CARD_W - 28f, row.height())) {
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

    private boolean toggleZone(float mx, float my) {
        return ClickGui2Widgets.inRect(mx, my, x + CARD_W - 44f, y + 11f, 36f, 22f);
    }

    private boolean starZone(float mx, float my) {
        return ClickGui2Widgets.inRect(mx, my, x + CARD_W - 64f, y + 8f, 20f, 28f);
    }

    private boolean expandChipZone(float mx, float my) {
        return ClickGui2Widgets.inRect(mx, my, x + CARD_W - 88f, y + 8f, 20f, 28f);
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

        // подложка: глубина → свечение включённого (с дыханием) → вспышка → стекло
        GuiGfx.shadow(ctx, (int) x + 2, (int) y + 3, (int) CARD_W - 4, (int) h - 6, 8);
        if (toggleT > 0.02f) {
            float breathe = 0.75f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 700.0);
            GuiGfx.softGlow(ctx, (int) x + 1, (int) y + 1, (int) CARD_W - 2, (int) h - 2, 10,
                    ClickGui2Theme.rgb(ClickGui2Theme.Role.MAIN) & 0xFFFFFF,
                    Math.round((13f + 4f * breathe) * toggleT), 1, 0);
        }
        if (focusFlash > 0.01f) {
            float pulse = (float) Math.sin(Math.min(1f, focusFlash) * Math.PI);
            fillRounded(ctx, x, y, CARD_W, h, 14f,
                    ClickGui2Theme.alpha(ClickGui2Theme.Role.MAIN, Math.round(pulse * 70)));
        }
        float expandSmooth = ClickGui2Anim.smooth(expandT);
        int fillAlpha = Math.round(62 + 30 * headerHoverT + 30 * expandSmooth);
        fillRounded(ctx, x, y, CARD_W, h, 14f,
                ClickGui2Theme.alpha(ClickGui2Theme.Role.BACKGROUND, fillAlpha));
        ClickGui2Widgets.edgeHighlight(ctx, x, y, CARD_W, navOf(hover));
        outlineRounded(ctx, x, y, CARD_W, h, 14f, ClickGui2Theme.rgb(ClickGui2Theme.Role.OUTLINE));
        if (toggleT > 0.02f) {
            outlineRounded(ctx, x, y, CARD_W, h, 14f,
                    ClickGui2Theme.alpha(ClickGui2Theme.Role.MAIN, Math.round(85f * toggleT)));
        }

        renderHeader(ctx, favorite);

        if (expandSmooth > 0.05f) {
            // разделитель шапки и строк
            RenderUtil.drawRect(ctx, x + 14f, y + HEADER_H - 3f, x + CARD_W - 14f, y + HEADER_H - 2f,
                    ClickGui2Theme.alpha(ClickGui2Theme.Role.LINE, Math.round(90f * expandSmooth)));
        }
        if (expandSmooth > 0.01f) {
            ensureRows();
            ctx.enableScissor((int) x, (int) (y + HEADER_H - 2f), (int) (x + CARD_W), (int) (y + h + 2f));
            float rowY = y + HEADER_H + 5f * (1f - expandSmooth);
            int idx = 0;
            for (ClickGui2Rows.Row row : rows) {
                float reveal = ClickGui2Anim.stagger(expandSmooth, idx, Math.max(1, rows.size()));
                float rowH = reveal * row.height();
                row.layout(x + 14f, rowY + (1f - reveal) * 6f, CARD_W - 28f);
                row.render(ctx, mx, my, dt, reveal);
                rowY += rowH + SET_GAP;
                idx++;
            }
            ctx.disableScissor();
        }
    }

    private float navOf(boolean hovered) {
        return hovered ? Math.max(0.55f, headerHoverT) : headerHoverT * 0.6f;
    }

    private void renderHeader(DrawContext ctx, boolean favorite) {
        FontManager fm = FontManager.INSTANCE;

        int nameColor = ClickGui2Theme.blend(
                ClickGui2Theme.rgb(ClickGui2Theme.Role.DEACTIVE_TEXT),
                ClickGui2Theme.rgb(ClickGui2Theme.Role.TEXT), toggleT);
        fm.drawStringWithShadowScaled(ctx, module.getName(), x + 14f, y + 8f, nameColor, 1.2f);

        String desc = module.getDescription() == null ? "" : module.getDescription();
        if (!desc.isEmpty()) {
            renderMarquee(ctx, desc, x + 14f, y + 26f, CARD_W - 116f);
        }

        // индикатор настроек: шеврон в круглой подложке (пойнтер раскрытия)
        if (rowsHeight() > 0f) {
            float cxv = x + CARD_W - 78f;
            float cyv = y + 22f;
            float chipA = Math.max(0.35f * headerHoverT, expandT * 0.85f);
            if (chipA > 0.02f) {
                ClickGui2Widgets.circleChip(ctx, cxv, cyv, 9.5f,
                        ClickGui2Theme.alpha(ClickGui2Theme.Role.SELECTED,
                                Math.round(chipA * (ClickGui2Theme.c(ClickGui2Theme.Role.SELECTED).getAlpha() * 2.6f))));
            }
            int chevCol = ClickGui2Theme.blend(
                    ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT),
                    ClickGui2Theme.rgb(ClickGui2Theme.Role.MAIN), expandT);
            ClickGui2Widgets.chevronSide(ctx, cxv, cyv, expandT < 0.5f, chevCol,
                    Math.max(0.45f * headerHoverT, expandT * 0.9f));
        }

        float starX = x + CARD_W - 54f;
        float starY = y + 22f;
        int starColor = ClickGui2Theme.blend(
                ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT),
                ClickGui2Theme.SPARK, favoriteT);
        if (favorite || favoriteT > 0.01f) {
            ClickGui2Widgets.starIcon(ctx, starX, starY, 6.2f + favoriteT * 2f,
                    ClickGui2Theme.fade(starColor, 0.4f + 0.6f * favoriteT));
            if (favoriteT > 0.6f) {
                GuiGfx.softGlow(ctx, (int) starX - 8, (int) starY - 8, 16, 16, 6,
                        ClickGui2Theme.SPARK, Math.round(14f * favoriteT), 2, 0);
            }
        } else {
            int ghost = ClickGui2Theme.fade(ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT),
                    0.55f * headerHoverT);
            if (headerHoverT > 0.03f) {
                ClickGui2Widgets.starIcon(ctx, starX, starY, 6.2f, ghost);
            }
        }

        ClickGui2Widgets.togglePill(ctx, x + CARD_W - 42f, y + 13f, toggleT, headerHoverT > 0.5f);
    }

    private void renderMarquee(DrawContext ctx, String text, float px, float py, float maxW) {
        FontManager fm = FontManager.INSTANCE;
        float textW = fm.getStringWidthScaled(text, 8f);
        ctx.enableScissor((int) px, (int) py - 2, (int) (px + maxW), (int) py + 12);
        int color = ClickGui2Theme.fade(ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT),
                (0.7f + 0.3f * headerHoverT));
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

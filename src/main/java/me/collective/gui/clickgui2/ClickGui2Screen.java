package me.collective.gui.clickgui2;

import me.collective.category.Category;
import me.collective.font.FontManager;
import me.collective.gui.GuiGfx;
import me.collective.module.Module;
import me.collective.module.ModuleManager;
import me.collective.module.client.UI;
import me.collective.module.render.Blur;
import me.collective.render.BlurEngine;
import me.collective.render.RenderUtil;
import me.collective.render.SoundUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static me.collective.gui.GuiGfx.fillRounded;
import static me.collective.gui.GuiGfx.outlineRounded;

/**
 * Второй стиль клик-гуи — перенос панели исходника (mod0165): панель
 * 800×504 с мягким введением, слева рельс категорий 148px с активной
 * подсветкой и избранным, справа breadcrumb + поиск с выпадающими
 * результатами, портал модулей — две колонки карточек 296px с каскадным
 * появлением, плавный скролл и полоса прокрутки; всплывашки настроек
 * закрываются при сильном прокручивании (порог 0.25, как в источнике).
 */
public class ClickGui2Screen extends Screen {
    private static ClickGui2Screen instance;

    public static ClickGui2Screen getInstance() {
        if (instance == null) {
            instance = new ClickGui2Screen();
        }
        return instance;
    }

    // ── Геометрия панели (логические размеры исходника) ──

    private static final float PANEL_W = 800f;
    private static final float PANEL_H = 504f;
    private static final float RAIL_W = 148f;
    private static final float VIEW_Y = 74f;
    private static final float CARD_GAP = 8f;
    private static final float COL_STEP = ClickGui2Card.CARD_W + 16f;
    private static final float COL_STAGGER = 10f;
    private static final float SCROLL_W = 3f;
    private static final float VIEW_PAD = 12f;

    private static final Category[] ORDER = new Category[] {
        Category.COMBAT, Category.MOVEMENT, Category.RENDER, Category.PLAYER,
        Category.MISC, Category.EXPLOIT, Category.CLIENT, Category.HIGHWAY
    };

    // ── Состояние ──

    private final Map<Category, List<ClickGui2Card>> cardCache = new EnumMap<>(Category.class);
    private final Map<Category, Float> catAnims = new EnumMap<>(Category.class);
    private final List<RailButton> railButtons = new ArrayList<>();
    private final ClickGui2Popups popups = new ClickGui2Popups();
    private final List<SearchResult> searchResults = new ArrayList<>();

    private Category category = ORDER[0];
    private boolean favoritesView;
    private float favoritesAnim;

    private float scrollTarget;
    private float scrollCurrent;
    private float contentHeight;

    private boolean searchActive;
    private String searchQuery = "";

    private float introT;
    private float panelX;
    private float panelY;

    private boolean draggingThumb;
    private float dragGrabOffset;
    private float thumbX, thumbY, thumbW, thumbH;

    private long lastNano = System.nanoTime();
    private long lastHoverSound;
    private float lastMouseX;
    private float lastMouseY;
    private boolean built;

    // адаптивный масштаб панели: подгон под текущее GUI-разрешение
    private float fitScale = 1f;
    private float drawnScale = 1f;

    // кэш избранного
    private List<ClickGui2Card> favoritesCache;
    private long favoritesVersion = -1L;

    // кэш сканирования поиска
    private String scannedQuery;

    private static final class RailButton {
        final Category category;
        final boolean favorites;
        float x, y;
        final float w = RAIL_W - 26f;
        final float h = 30f;

        RailButton(Category category, boolean favorites) {
            this.category = category;
            this.favorites = favorites;
        }
    }

    private record SearchResult(ClickGui2Card card, float rowY) {
    }

    private ClickGui2Screen() {
        super(Text.literal("Collective"));
    }

    // ── Жизненный цикл ──

    @Override
    public void init() {
        super.init();
        lastNano = System.nanoTime();
        introT = 0f;
        scrollTarget = 0f;
        scrollCurrent = 0f;
        searchActive = false;
        searchResults.clear();
        popups.closeAll();

        if (!built) {
            for (Category cat : ORDER) {
                List<ClickGui2Card> cards = new ArrayList<>();
                List<Module> modules = ModuleManager.INSTANCE.getModulesByCategory(cat);
                if (modules != null) {
                    for (Module module : modules) {
                        cards.add(new ClickGui2Card(module, cat));
                    }
                }
                cardCache.put(cat, cards);
                catAnims.put(cat, 0f);
            }
            built = true;
        }

        railButtons.clear();
        for (Category cat : ORDER) {
            railButtons.add(new RailButton(cat, false));
        }
        railButtons.add(new RailButton(null, true));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, Math.max(0.001f, (now - lastNano) / 1_000_000_000f));
        lastNano = now;

        introT = ClickGui2Anim.to01(introT, true, dt, 6f);

        // фон: затемнение + мягкие свечения (аппроксимация шейдеров исходника)
        RenderUtil.drawRect(context, 0f, 0f, (float) width, (float) height,
                Math.round(150f * introT) << 24);
        int mainRgb = ClickGui2Theme.rgb(ClickGui2Theme.Role.MAIN) & 0xFFFFFF;
        int twoRgb = ClickGui2Theme.rgb(ClickGui2Theme.Role.MAIN_TWO) & 0xFFFFFF;
        float glowA = 0.10f * introT;
        GuiGfx.softGlow(context, (int) (width * 0.18f) - 320, (int) (height * 0.9f) - 260, 640, 520, 240,
                mainRgb, Math.round(30f * glowA * 4f), 2, 0);
        GuiGfx.softGlow(context, (int) (width * 0.85f) - 300, (int) (height * 0.12f) - 220, 600, 440, 220,
                twoRgb, Math.round(22f * glowA * 4f), 2, 0);

        // мышь и геометрия в логических координатах панели
        float centerX = width / 2f;
        float centerY = height / 2f;
        float uiScale = 1f;
        UI uiMod = UI.INSTANCE;
        if (uiMod != null && uiMod.gui2Scale != null) {
            uiScale = uiMod.gui2Scale.getFloatValue();
        }
        fitScale = Math.max(0.5f, Math.min(1.15f,
                Math.min(width * 0.84f / PANEL_W, height * 0.74f / PANEL_H) * uiScale));
        drawnScale = fitScale * (0.94f + 0.06f * ClickGui2Anim.spring(introT));
        panelX = (width - PANEL_W) / 2f;
        panelY = (height - PANEL_H) / 2f;
        float mx = toPanelX(mouseX);
        float my = toPanelY(mouseY);
        lastMouseX = mx;
        lastMouseY = my;
        float navAlpha = ClickGui2Anim.easeOut(Math.min(1f, introT * 1.35f));

        // блюр — ровно по области панели (в реальных координатах), не весь экран
        if (Blur.INSTANCE != null && Blur.INSTANCE.isToggled() && Blur.INSTANCE.clickGui.getValue()) {
            float pad = 10f * drawnScale;
            float bw = PANEL_W * drawnScale + pad * 2f;
            float bh = PANEL_H * drawnScale + pad * 2f;
            List<float[]> regions = new ArrayList<>();
            regions.add(new float[]{centerX - bw / 2f, centerY - bh / 2f,
                    centerX + bw / 2f, centerY + bh / 2f});
            BlurEngine.INSTANCE.renderBlur(context, regions, Blur.INSTANCE.radius.getFloatValue());
        }

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(centerX, centerY);
        context.getMatrices().scale(drawnScale, drawnScale);
        context.getMatrices().translate(-centerX, -centerY);

        renderPanel(context, mx, my, dt, navAlpha);

        context.getMatrices().popMatrix();
        super.render(context, mouseX, mouseY, delta);
    }

    private float toPanelX(float raw) {
        return width / 2f + (raw - width / 2f) / Math.max(0.05f, drawnScale);
    }

    private float toPanelY(float raw) {
        return height / 2f + (raw - height / 2f) / Math.max(0.05f, drawnScale);
    }

    private void renderPanel(DrawContext ctx, float mx, float my, float dt, float navAlpha) {
        boolean interact = introT > 0.92f;

        GuiGfx.shadow(ctx, (int) panelX - 4, (int) panelY - 4, (int) PANEL_W + 8, (int) PANEL_H + 8, 12);
        fillRounded(ctx, panelX, panelY, PANEL_W, PANEL_H, 14f,
                ClickGui2Theme.alpha(ClickGui2Theme.Role.BACKGROUND, 246));
        outlineRounded(ctx, panelX, panelY, PANEL_W, PANEL_H, 14f, ClickGui2Theme.rgb(ClickGui2Theme.Role.OUTLINE));

        renderRail(ctx, mx, my, dt, navAlpha, interact);
        renderHeaderRow(ctx, mx, my, navAlpha, interact);
        renderModules(ctx, mx, my, dt, navAlpha, interact);
        renderSearchDropdown(ctx, navAlpha, interact);
        popups.render(ctx, mx, my, dt,
                panelX + RAIL_W + 4f, panelY + 4f, PANEL_W - RAIL_W - 8f, PANEL_H - 8f);
    }

    // ── Рельс категорий ──

    private void renderRail(DrawContext ctx, float mx, float my, float dt, float navAlpha, boolean interact) {
        FontManager fm = FontManager.INSTANCE;

        RenderUtil.drawRect(ctx, panelX + RAIL_W, panelY + 18f, panelX + RAIL_W + 1f, panelY + PANEL_H - 18f,
                ClickGui2Theme.fade(ClickGui2Theme.Role.LINE, navAlpha));

        fm.drawStringScaled(ctx, "Collective", panelX + 16f, panelY + 15f,
                ClickGui2Theme.fade(ClickGui2Theme.rgb(ClickGui2Theme.Role.CATEGORY_TEXT), navAlpha), 1.22f);
        fm.drawStringScaled(ctx, "GUI / v1.2", panelX + 16f, panelY + 30f,
                ClickGui2Theme.fade(ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT), 0.9f * navAlpha), 1f);
        ClickGui2Widgets.hairline(ctx, panelX + 12f, panelY + 48f, RAIL_W - 24f);

        favoritesAnim = ClickGui2Anim.to01(favoritesAnim, favoritesView, dt, 10f);
        float by = panelY + 60f;
        for (RailButton button : railButtons) {
            button.x = panelX + 12f;
            button.y = by;
            float anim;
            if (button.favorites) {
                anim = favoritesAnim;
            } else {
                float target = (!favoritesView && button.category == category) ? 1f : 0f;
                float prev = catAnims.get(button.category);
                anim = ClickGui2Anim.to01(prev, target > 0.5f, dt, 10f);
                catAnims.put(button.category, anim);
            }

            boolean hover = interact && ClickGui2Widgets.inRect(mx, my, button.x, button.y, button.w, button.h);
            if (hover) {
                pingHoverSound();
            }
            float reaction = Math.max(anim, hover ? 0.55f : 0f);

            if (reaction > 0.02f) {
                fillRounded(ctx, button.x, button.y, button.w, button.h, 8f,
                        ClickGui2Theme.alpha(ClickGui2Theme.Role.SELECTED,
                                Math.round(reaction * (ClickGui2Theme.c(ClickGui2Theme.Role.SELECTED).getAlpha() * 2.4f))));
            }

            int label = ClickGui2Theme.blend(
                    ClickGui2Theme.rgb(ClickGui2Theme.Role.DESELECT_CATEGORY_TEXT),
                    hover && anim < 0.05f
                            ? ClickGui2Theme.rgb(ClickGui2Theme.Role.OPEN_SETTING_TEXT)
                            : ClickGui2Theme.rgb(ClickGui2Theme.Role.CATEGORY_TEXT), anim);
            if (button.favorites) {
                int starColor = ClickGui2Theme.blend(
                        ClickGui2Theme.rgb(ClickGui2Theme.Role.DESELECT_CATEGORY_TEXT),
                        (255 << 16) | (210 << 8) | 64, anim);
                ClickGui2Widgets.starIcon(ctx, button.x + 14f, button.y + button.h / 2f,
                        7f + anim * 1.5f, ClickGui2Theme.fade(starColor, navAlpha));
                fm.drawStringScaled(ctx, "Избранное", button.x + 26f, button.y + button.h / 2f - 5f,
                        ClickGui2Theme.fade(label, navAlpha), 1f);
            } else {
                if (anim > 0.02f) {
                    fillRounded(ctx, button.x - 5f, button.y + button.h / 2f - 7f, 2.5f, 14f, 1.2f,
                            ClickGui2Theme.fade(ClickGui2Theme.Role.MAIN, 0.95f * anim * navAlpha));
                }
                fm.drawStringScaled(ctx, button.category.getName(), button.x + 12f,
                        button.y + button.h / 2f - 5f, ClickGui2Theme.fade(label, navAlpha), 1f);
            }
            by += button.h + 6f;
        }

        // подвал рельса: имя игрока (как блок профиля исходника, без портрета)
        float footerY = panelY + PANEL_H - 44f;
        ClickGui2Widgets.hairline(ctx, panelX + 12f, footerY, RAIL_W - 24f);
        MinecraftClient mc = MinecraftClient.getInstance();
        String player = mc != null && mc.getSession() != null
                ? mc.getSession().getUsername() : "-";
        fm.drawStringScaled(ctx, player, panelX + 16f, footerY + 10f,
                ClickGui2Theme.fade(ClickGui2Theme.rgb(ClickGui2Theme.Role.TEXT), navAlpha), 1f);
        fm.drawStringScaled(ctx, "sql build", panelX + 16f, footerY + 22f,
                ClickGui2Theme.fade(ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT), 0.85f * navAlpha), 1f);
    }

    private void pingHoverSound() {
        long now = System.currentTimeMillis();
        if (now - lastHoverSound > 220L) {
            lastHoverSound = now;
            UI ui = UI.INSTANCE;
            if (ui != null && ui.hoverSound.getValue()) {
                SoundUtil.playHover(0.12f, 1.6f);
            }
        }
    }

    // ── Шапка: breadcrumb + поиск + дропдаун результатов ──

    private float searchBoxX() {
        return panelX + PANEL_W - 226f;
    }

    private float searchBoxY() {
        return panelY + 44f;
    }

    private void renderHeaderRow(DrawContext ctx, float mx, float my, float navAlpha, boolean interact) {
        FontManager fm = FontManager.INSTANCE;

        String crumbs = favoritesView ? "Избранное" : category.getName();
        fm.drawStringScaled(ctx, crumbs, panelX + RAIL_W + 16f, panelY + 18f,
                ClickGui2Theme.fade(ClickGui2Theme.rgb(ClickGui2Theme.Role.CATEGORY_TEXT), navAlpha), 1.28f);

        // пересканирование только при изменении запроса (без аллокаций в кадре)
        if (!searchQuery.equals(scannedQuery)) {
            searchResults.clear();
            scannedQuery = searchQuery;
            if (!searchQuery.isEmpty()) {
                String q = searchQuery.toLowerCase();
                float resultY = searchBoxY() + 24f + 36f;
                outer:
                for (Category cat : ORDER) {
                    for (ClickGui2Card card : cardCache.get(cat)) {
                        if (card.lowName().contains(q)) {
                            searchResults.add(new SearchResult(card, resultY));
                            resultY += 20f;
                            if (searchResults.size() >= 6) break outer;
                        }
                    }
                }
            }
        }

        int count = favoritesView ? countFavorites() : cardCache.get(category).size();
        String sub = count + pluralModules(count);
        float sw = fm.getStringWidthScaled(sub, 8f);
        fm.drawStringScaled(ctx, sub, panelX + PANEL_W - 16f - sw, panelY + 22f,
                ClickGui2Theme.fade(ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT), 0.9f * navAlpha), 1f);

        // бокс поиска
        float boxW = 210f;
        float boxH = 24f;
        float boxX = searchBoxX();
        float boxY = searchBoxY();
        int border = searchActive
                ? ClickGui2Theme.alpha(ClickGui2Theme.Role.MAIN, 170)
                : ClickGui2Theme.rgb(ClickGui2Theme.Role.OUTLINE);
        fillRounded(ctx, boxX, boxY, boxW, boxH, 8f,
                ClickGui2Theme.alpha(ClickGui2Theme.Role.BACKGROUND, 255));
        outlineRounded(ctx, boxX, boxY, boxW, boxH, 8f, border);

        if (!searchActive && searchQuery.isEmpty()) {
            float hw = fm.getStringWidthScaled("CTRL + F", 8f);
            fm.drawStringScaled(ctx, "CTRL + F", boxX + boxW - hw - 10f, boxY + boxH / 2f - 4f,
                    ClickGui2Theme.fade(ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT), 0.65f * navAlpha), 1f);
        }
        String display = searchQuery.isEmpty()
                ? "Поиск..."
                : searchQuery + (searchActive && ClickGui2Anim.caretBlink() ? "|" : "");
        fm.drawStringScaled(ctx, display, boxX + 10f, boxY + boxH / 2f - 4f,
                ClickGui2Theme.fade(searchQuery.isEmpty()
                        ? ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT)
                        : ClickGui2Theme.rgb(ClickGui2Theme.Role.TEXT), navAlpha), 1f);
    }

    private void renderSearchDropdown(DrawContext ctx, float navAlpha, boolean interact) {
        if (searchResults.isEmpty()) {
            return;
        }
        FontManager fm = FontManager.INSTANCE;
        float boxW = 210f;
        float boxH = 24f;
        float boxX = searchBoxX();
        float dropY = searchBoxY() + boxH + 6f;
        float dropH = 26f + searchResults.size() * 20f;

        GuiGfx.shadow(ctx, (int) boxX, (int) dropY, (int) boxW, (int) dropH, 8);
        fillRounded(ctx, boxX, dropY, boxW, dropH, 9f, ClickGui2Theme.rgb(ClickGui2Theme.Role.BACKGROUND));
        outlineRounded(ctx, boxX, dropY, boxW, dropH, 9f, ClickGui2Theme.rgb(ClickGui2Theme.Role.OUTLINE));
        fm.drawStringScaled(ctx, "Результаты поиска", boxX + 10f, dropY + 6f,
                ClickGui2Theme.fade(ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT), 0.85f * navAlpha), 1f);

        for (SearchResult result : searchResults) {
            boolean hoverRow = interact
                    && ClickGui2Widgets.inRect(lastMouseX, lastMouseY, boxX + 6f, result.rowY() - 1f, boxW - 12f, 18f);
            if (hoverRow) {
                fillRounded(ctx, boxX + 6f, result.rowY() - 1f, boxW - 12f, 18f, 5f,
                        ClickGui2Theme.rgb(ClickGui2Theme.Role.SELECTED));
            }
            fm.drawStringScaled(ctx, result.card().module().getName(), boxX + 14f, result.rowY() + 3f,
                    ClickGui2Theme.fade(hoverRow
                            ? ClickGui2Theme.rgb(ClickGui2Theme.Role.ACTIVE_TEXT)
                            : ClickGui2Theme.rgb(ClickGui2Theme.Role.OPEN_SETTING_TEXT), navAlpha), 1f);
            String catName = result.card().category().getName();
            float cw = fm.getStringWidthScaled(catName, 8f);
            fm.drawStringScaled(ctx, catName, boxX + boxW - 14f - cw, result.rowY() + 4f,
                    ClickGui2Theme.fade(ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT), 0.8f * navAlpha), 1f);
        }
    }

    private static String pluralModules(int count) {
        if (count % 10 == 1 && count % 100 != 11) return " модуль";
        return " модулей";
    }

    private int countFavorites() {
        int n = 0;
        for (Category cat : ORDER) {
            for (ClickGui2Card card : cardCache.get(cat)) {
                if (FavoriteStore.isFavorite(card.module().getName())) n++;
            }
        }
        return n;
    }

    private List<ClickGui2Card> visibleCards() {
        if (favoritesView) {
            long v = FavoriteStore.version();
            if (favoritesCache == null || favoritesVersion != v) {
                favoritesCache = new ArrayList<>();
                for (Category cat : ORDER) {
                    for (ClickGui2Card card : cardCache.get(cat)) {
                        if (FavoriteStore.isFavorite(card.module().getName())) {
                            favoritesCache.add(card);
                        }
                    }
                }
                favoritesVersion = v;
            }
            return favoritesCache;
        }
        return cardCache.get(category);
    }

    // ── Портал модулей ──

    private float viewportX() {
        return panelX + RAIL_W + 2f;
    }

    private float viewportY() {
        return panelY + VIEW_Y;
    }

    private float viewportW() {
        return PANEL_W - RAIL_W - 8f;
    }

    private float viewportH() {
        return PANEL_H - VIEW_Y - 14f;
    }

    private void renderModules(DrawContext ctx, float mx, float my, float dt, float navAlpha, boolean interact) {
        FontManager fm = FontManager.INSTANCE;
        List<ClickGui2Card> visible = visibleCards();

        float cx0 = viewportX() + VIEW_PAD;
        float cx1 = cx0 + COL_STEP;

        // Проход 1: высоты и контент
        float colA = viewportY();
        float colB = viewportY();
        contentHeight = 0f;
        for (int i = 0; i < visible.size(); i++) {
            ClickGui2Card card = visible.get(i);
            if (i % 2 == 0) {
                colA += card.height() + CARD_GAP;
            } else {
                colB += card.height() + CARD_GAP;
            }
        }
        contentHeight = Math.max(0f, Math.max(colA, colB) - viewportY() - CARD_GAP);

        // скролл с плавным ходом; сильный сдвиг гасит всплывашки (как в исходнике)
        float minScroll = Math.min(0f, viewportH() - contentHeight);
        scrollTarget = Math.max(minScroll, Math.min(0f, scrollTarget));
        float before = scrollCurrent;
        scrollCurrent = ClickGui2Anim.to(scrollCurrent, scrollTarget, dt, 14f);
        if (popups.isOpen() && Math.abs(scrollCurrent - before) > 0.55f) {
            popups.closeAll();
        }
        float offset = scrollCurrent;

        // Проход 2: раскладка и рисование (те же формулы колонок)
        ctx.enableScissor((int) viewportX(), (int) viewportY(),
                (int) (viewportX() + viewportW()), (int) (viewportY() + viewportH()));
        colA = viewportY();
        colB = viewportY();
        float revealBase = ClickGui2Anim.easeOut(Math.min(1f, introT * 1.6f));
        for (int i = 0; i < visible.size(); i++) {
            ClickGui2Card card = visible.get(i);
            boolean left = i % 2 == 0;
            float baseY = left ? colA : colB;
            float baseX = left ? cx0 : cx1;
            float entrance = interact ? ClickGui2Anim.stagger(revealBase, i, Math.max(1, visible.size())) : 0f;
            float slide = (1f - entrance) * 10f;
            float jitter = (left ? -COL_STAGGER : COL_STAGGER) * (1f - entrance);
            card.layout(baseX + jitter, baseY + offset + slide);

            if (entrance * navAlpha > 0.02f
                    && card.cardY() + card.height() > viewportY()
                    && card.cardY() < viewportY() + viewportH()) {
                card.render(ctx, mx, my, dt);
            }
            if (left) colA += card.height() + CARD_GAP;
            else colB += card.height() + CARD_GAP;
        }
        ctx.disableScissor();

        // пустое состояние избранного
        if (visible.isEmpty()) {
            String empty = "Нет избранных. Нажми на звезду на карточке модуля.";
            float ew = fm.getStringWidthScaled(empty, 9f);
            fm.drawStringScaled(ctx, empty, viewportX() + (viewportW() - ew) / 2f,
                    viewportY() + viewportH() / 2f - 4f,
                    ClickGui2Theme.fade(ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT), 0.9f * navAlpha), 1f);
        }

        // полоса прокрутки
        if (contentHeight > viewportH()) {
            float trackH = viewportH();
            float th = Math.max(22f, trackH * trackH / contentHeight);
            float trackRun = trackH - th;
            float progress = -scrollCurrent / Math.max(1f, contentHeight - trackH);
            thumbX = viewportX() + viewportW() - SCROLL_W - 2f;
            thumbY = viewportY() + trackRun * progress;
            thumbW = SCROLL_W;
            thumbH = th;
            fillRounded(ctx, thumbX, thumbY, thumbW, thumbH, 1.5f,
                    ClickGui2Theme.alpha(ClickGui2Theme.Role.SCROLL,
                            Math.round(255 * (draggingThumb ? 1f : 0.55f) * navAlpha)));
        } else {
            thumbH = 0f; // вне экрана — не кликабельно
        }
    }

    private float cardBaseY(List<ClickGui2Card> cards, int targetIndex) {
        float colA = viewportY();
        float colB = viewportY();
        for (int i = 0; i < cards.size(); i++) {
            boolean left = i % 2 == 0;
            if (i == targetIndex) return left ? colA : colB;
            float h = cards.get(i).height() + CARD_GAP;
            if (left) colA += h;
            else colB += h;
        }
        return viewportY();
    }

    // ── Мышь ──

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        float mx = toPanelX((float) click.x());
        float my = toPanelY((float) click.y());
        int button = click.button();
        if (introT <= 0.92f) {
            return true;
        }

        // всплывашки: клик внутри потребляется; мимо — закрывают и проходят дальше
        if (popups.mouseClicked(mx, my, button)) {
            return true;
        }

        // поле поиска
        float boxX = searchBoxX();
        float boxY = searchBoxY();
        if (button == 0 && ClickGui2Widgets.inRect(mx, my, boxX, boxY, 210f, 24f)) {
            searchActive = true;
            SoundUtil.playClick(0.18f, 1.3f);
            return true;
        }
        if (searchActive) {
            searchActive = false;
        }

        // дропдаун результатов
        if (!searchResults.isEmpty()) {
            for (SearchResult result : searchResults) {
                if (ClickGui2Widgets.inRect(mx, my, boxX + 6f, result.rowY() - 1f, 210f - 12f, 18f)) {
                    favoritesView = false;
                    category = result.card().category();
                    scrollTarget = 0f;
                    showCard(result.card());
                    searchQuery = "";
                    SoundUtil.playClick(0.2f, 1.25f);
                    return true;
                }
            }
        }

        // рельс
        for (RailButton railButton : railButtons) {
            if (ClickGui2Widgets.inRect(mx, my, railButton.x, railButton.y, railButton.w, railButton.h)) {
                if (railButton.favorites) {
                    favoritesView = true;
                } else if (!favoritesView && railButton.category == category) {
                    // повторный клик — свернуть всё
                    for (ClickGui2Card card : cardCache.get(category)) {
                        card.setExpanded(false);
                        card.blurRows();
                    }
                } else {
                    favoritesView = false;
                    category = railButton.category;
                    scrollTarget = 0f;
                    collapseAll();
                    popups.closeAll();
                }
                SoundUtil.playClick(0.2f, 1.15f);
                return true;
            }
        }

        // полоса прокрутки
        if (thumbH > 0f && ClickGui2Widgets.inRect(mx, my, thumbX, thumbY, thumbW, Math.max(14f, thumbH + 14f))) {
            draggingThumb = true;
            dragGrabOffset = my - thumbY;
            return true;
        }

        // карточки (в обратном порядке; раскрытая первая по колонкам)
        List<ClickGui2Card> visible = visibleCards();
        for (ClickGui2Card card : visible) {
            if (card.cardContains(mx, my)) {
                card.mouseClicked(mx, my, button, popups);
                return true;
            }
        }

        // клик по пустому месту — снять фокусы инлайн-ввода
        blurAllRows();
        return super.mouseClicked(click, bl);
    }

    private void blurAllRows() {
        for (List<ClickGui2Card> list : cardCache.values()) {
            for (ClickGui2Card card : list) {
                card.blurRows();
            }
        }
    }

    private void collapseAll() {
        for (List<ClickGui2Card> list : cardCache.values()) {
            for (ClickGui2Card card : list) {
                card.setExpanded(false);
                card.blurRows();
            }
        }
    }

    /** Перейти к карточке из поиска: включить категорию и подвесть скролл. */
    private void showCard(ClickGui2Card card) {
        card.focus();
        for (Category cat : ORDER) {
            List<ClickGui2Card> cards = cardCache.get(cat);
            int idx = cards.indexOf(card);
            if (idx < 0) continue;
            if (cat != category) {
                category = cat;
                catAnims.put(cat, 0.999f); // мгновенно подсветить
            }
            float baseY = cardBaseY(cards, idx);
            float minScroll = Math.min(0f, viewportH() - contentHeight);
            scrollTarget = Math.max(minScroll, Math.min(0f, viewportY() - baseY));
            return;
        }
    }

    @Override
    public boolean mouseReleased(Click click) {
        draggingThumb = false;
        for (List<ClickGui2Card> list : cardCache.values()) {
            for (ClickGui2Card card : list) {
                card.mouseReleased();
            }
        }
        popups.mouseReleased();
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (popups.mouseScrolled(verticalAmount)) {
            return true;
        }

        float mx = toPanelX((float) mouseX);
        float my = toPanelY((float) mouseY);

        // Ctrl + колесо — пошаговые числа внутри строк
        if (hasCtrl()) {
            for (ClickGui2Card card : visibleCards()) {
                if (card.cardContains(mx, my)
                        && card.scrolled(mx, my, verticalAmount, true)) {
                    return true;
                }
            }
        }

        if (mx >= panelX + RAIL_W && mx <= panelX + PANEL_W
                && my >= viewportY() && my <= panelY + PANEL_H) {
            scrollTarget -= (float) (verticalAmount * 16.0);
            float minScroll = Math.min(0f, viewportH() - contentHeight);
            scrollTarget = Math.max(minScroll, Math.min(0f, scrollTarget));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private boolean hasCtrl() {
        long handle = MinecraftClient.getInstance().getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    @Override
    public boolean mouseDragged(Click click, double mouseX, double mouseY) {
        float mx = toPanelX((float) mouseX);
        float my = toPanelY((float) mouseY);
        if (draggingThumb) {
            float trackH = viewportH();
            float minScroll = Math.min(0f, trackH - contentHeight);
            float content = Math.max(1f, contentHeight - trackH);
            float th = Math.max(22f, trackH * trackH / Math.max(1f, contentHeight));
            float usable = Math.max(1f, trackH - th);
            float progress = (my - dragGrabOffset - viewportY()) / usable;
            scrollTarget = Math.max(minScroll, Math.min(0f, -progress * content));
            return true;
        }
        if (popups.mouseDragging(mx, my)) {
            return true;
        }
        return super.mouseDragged(click, mouseX, mouseY);
    }

    // ── Клавиатура ──

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();

        // всплывашки первыми: пикер hex, диалог бинда
        if (popups.keyPressed(key)) {
            return true;
        }

        // инлайн-редакторы строк
        for (List<ClickGui2Card> list : cardCache.values()) {
            for (ClickGui2Card card : list) {
                if (card.wantsKeys() && card.keyPressed(key)) {
                    return true;
                }
            }
        }

        if (input.hasCtrl() && key == GLFW.GLFW_KEY_F) {
            searchActive = !searchActive;
            if (!searchActive) searchQuery = "";
            return true;
        }

        if (searchActive) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                searchActive = false;
                searchQuery = "";
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                searchActive = false;
                return true;
            }
            return true;
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        // hex-поле пикера
        if (popups.charTyped(input.isValidChar() ? input.asString() : "")) {
            return true;
        }

        // инлайн-редакторы строк
        for (List<ClickGui2Card> list : cardCache.values()) {
            for (ClickGui2Card card : list) {
                if (card.wantsKeys()) {
                    card.charTyped(input.asString());
                    return true;
                }
            }
        }

        if (searchActive && input.isValidChar() && searchQuery.length() < 32) {
            searchQuery += input.asString();
            return true;
        }

        return super.charTyped(input);
    }

    @Override
    public void close() {
        if (UI.INSTANCE != null) {
            UI.INSTANCE.setToggled(false);
        }
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

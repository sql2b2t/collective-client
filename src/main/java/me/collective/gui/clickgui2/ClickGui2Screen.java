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
 * Второй стиль клик-гуи — «premium glass»: мерцающие ambient-свечения за
 * стеклянной панелью 820×524, рельс категорий 172px с пилюлями, акцентной
 * полоской и счётчиками, шапка с breadcrumb/поиском-стеклом и выпадающими
 * результатами, портал модулей — две колонки стеклянных карточек 296px с
 * каскадным появлением, масками затухания по краям и мягкой полосой
 * прокрутки. Все роли — через {@link ClickGui2Theme}, акцент — настройка UI.
 */
public class ClickGui2Screen extends Screen {
    private static ClickGui2Screen instance;

    public static ClickGui2Screen getInstance() {
        if (instance == null) {
            instance = new ClickGui2Screen();
        }
        return instance;
    }

    // ── Геометрия панели (логические размеры) ──

    private static final float PANEL_W = 820f;
    private static final float PANEL_H = 524f;
    private static final float RAIL_W = 172f;
    private static final float VIEW_Y = 84f;
    private static final float CARD_GAP = 10f;
    private static final float COL_STEP = ClickGui2Card.CARD_W + 12f;
    private static final float COL_STAGGER = 12f;
    private static final float SCROLL_W = 5f;
    private static final float VIEW_PAD = 14f;

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
        final float w = RAIL_W - 28f;
        final float h = 34f;

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
        syncTheme();

        // фон: затемнение + два дышащих ambient-свечения по диагонали
        RenderUtil.drawRect(context, 0f, 0f, (float) width, (float) height,
                Math.round(150f * introT) << 24);
        int mainRgb = ClickGui2Theme.rgb(ClickGui2Theme.Role.MAIN) & 0xFFFFFF;
        int twoRgb = ClickGui2Theme.rgb(ClickGui2Theme.Role.MAIN_TWO) & 0xFFFFFF;
        // «дыхание»: медленная пульсация яркости — панельFeels alive, но тихо
        float breathe = 0.72f + 0.28f * (float) Math.sin(now / 900.0);
        float glowA = 0.12f * introT * breathe;
        GuiGfx.softGlow(context, (int) (width * 0.18f) - 340, (int) (height * 0.9f) - 270, 680, 540, 260,
                mainRgb, Math.round(34f * glowA * 4f), 2, 0);
        GuiGfx.softGlow(context, (int) (width * 0.86f) - 310, (int) (height * 0.1f) - 230, 620, 460, 240,
                twoRgb, Math.round(26f * glowA * 4f), 2, 0);

        // мышь и геометрия в логических координатах панели
        float centerX = width / 2f;
        float centerY = height / 2f;
        float uiScale = 1f;
        UI uiMod = UI.INSTANCE;
        if (uiMod != null && uiMod.gui2Scale != null) {
            uiScale = uiMod.gui2Scale.getFloatValue();
        }
        fitScale = Math.max(0.5f, Math.min(1.15f,
                Math.min(width * 0.84f / PANEL_W, height * 0.76f / PANEL_H) * uiScale));
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
            float pad = 12f * drawnScale;
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

        renderPanel(context, mx, my, dt, navAlpha, breathe);

        context.getMatrices().popMatrix();
        super.render(context, mouseX, mouseY, delta);
    }

    private float toPanelX(float raw) {
        return width / 2f + (raw - width / 2f) / Math.max(0.05f, drawnScale);
    }

    private float toPanelY(float raw) {
        return height / 2f + (raw - height / 2f) / Math.max(0.05f, drawnScale);
    }

    /** Общий акцент/фон берём из настроек UI — оба стиля держат одну палитру. */
    private void syncTheme() {
        UI uiMod = UI.INSTANCE;
        if (uiMod == null) return;
        try {
            java.awt.Color accent = uiMod.color.getValue();
            if (accent != null) {
                int rgb = accent.getRGB() & 0x00FFFFFF;
                ClickGui2Theme.setRole(ClickGui2Theme.Role.MAIN, new java.awt.Color(rgb));
                ClickGui2Theme.setRole(ClickGui2Theme.Role.MAIN_TWO, new java.awt.Color(mixe(rgb, 0.72f)));
                ClickGui2Theme.setRole(ClickGui2Theme.Role.ACTIVE_STATE, new java.awt.Color(rgb));
            }
            java.awt.Color bg = uiMod.bgColor.getValue();
            if (bg != null) {
                ClickGui2Theme.setRole(ClickGui2Theme.Role.BACKGROUND,
                        brightenFloor(bg, 0.20f));
            }
        } catch (RuntimeException ignored) {
        }
    }

    /**
     * Повышает яркость фонового цвета до нижнего порога, сохраняя оттенок:
     * каналы масштабируются, пока максимум не достигнет
     * {@code targetFraction * 255}. Светлым цветам ничего не делает.
     */
    private static java.awt.Color brightenFloor(java.awt.Color color, float targetFraction) {
        int max = Math.max(color.getRed(), Math.max(color.getGreen(), color.getBlue()));
        int target = Math.round(255f * targetFraction);
        if (max >= target) return color;
        float k = target / Math.max(1, max);
        return new java.awt.Color(
                clamp255(Math.round(color.getRed() * k)),
                clamp255(Math.round(color.getGreen() * k)),
                clamp255(Math.round(color.getBlue() * k)));
    }

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static int mixe(int rgb, float k) {
        int r = Math.round(((rgb >> 16) & 0xFF) * k);
        int g = Math.round(((rgb >> 8) & 0xFF) * k);
        int b = Math.round((rgb & 0xFF) * k);
        return (r << 16) | (g << 8) | b;
    }

    // ── Панель ──

    private void renderPanel(DrawContext ctx, float mx, float my, float dt, float navAlpha, float breathe) {
        boolean interact = introT > 0.92f;

        // глубина: мягкая тень + узкий акцентный ореол за панелью
        GuiGfx.shadow(ctx, (int) panelX - 6, (int) panelY - 6, (int) PANEL_W + 12, (int) PANEL_H + 12, 14);
        if (breathe > 0f) {
            GuiGfx.softGlow(ctx, (int) panelX + 6, (int) panelY + 6,
                    (int) PANEL_W - 12, (int) PANEL_H - 12, 12,
                    ClickGui2Theme.rgb(ClickGui2Theme.Role.MAIN) & 0xFFFFFF,
                    Math.round(7f * navAlpha * breathe), 2, 0);
        }

        // стеклянная плоскость: заливка, верхний блик, двойная обводка
        fillRounded(ctx, panelX, panelY, PANEL_W, PANEL_H, 18f,
                ClickGui2Theme.alpha(ClickGui2Theme.Role.BACKGROUND, 196));
        ctx.enableScissor((int) panelX, (int) panelY, (int) (panelX + PANEL_W), (int) (panelY + 44));
        fillRounded(ctx, panelX, panelY, PANEL_W, 44f, 18f,
                ClickGui2Theme.alpha(ClickGui2Theme.Role.TEXT, Math.round(9f * navAlpha)));
        ctx.disableScissor();
        outlineRounded(ctx, panelX, panelY, PANEL_W, PANEL_H, 18f,
                ClickGui2Theme.rgb(ClickGui2Theme.Role.OUTLINE));
        outlineRounded(ctx, panelX + 2f, panelY + 2f, PANEL_W - 4f, PANEL_H - 4f, 16f,
                ClickGui2Theme.alpha(ClickGui2Theme.Role.LINE, Math.round(60f * navAlpha)));

        renderRail(ctx, mx, my, dt, navAlpha, interact);
        renderHeaderRow(ctx, mx, my, navAlpha, interact);
        // разделитель шапки и портала
        RenderUtil.drawRect(ctx, panelX + RAIL_W + 14f, panelY + 74f, panelX + PANEL_W - 14f, panelY + 75f,
                ClickGui2Theme.alpha(ClickGui2Theme.Role.LINE, Math.round(100f * navAlpha)));
        renderModules(ctx, mx, my, dt, navAlpha, interact);
        renderSearchDropdown(ctx, navAlpha, interact);
        popups.render(ctx, mx, my, dt,
                panelX + RAIL_W + 4f, panelY + 4f, PANEL_W - RAIL_W - 8f, PANEL_H - 8f);
    }

    // ── Рельс категорий ──

    private void renderRail(DrawContext ctx, float mx, float my, float dt, float navAlpha, boolean interact) {
        FontManager fm = FontManager.INSTANCE;

        RenderUtil.drawRect(ctx, panelX + RAIL_W, panelY + 20f, panelX + RAIL_W + 1f, panelY + PANEL_H - 20f,
                ClickGui2Theme.fade(ClickGui2Theme.Role.LINE, navAlpha));

        // бренд: искра с мягким ореолом + имя
        float brandX = panelX + 18f;
        float brandY = panelY + 18f;
        ClickGui2Widgets.starIcon(ctx, brandX + 5f, brandY + 6f, 11f,
                ClickGui2Theme.fade(ClickGui2Theme.Role.MAIN, navAlpha));
        GuiGfx.softGlow(ctx, (int) brandX - 6, (int) brandY - 7, 22, 22, 8,
                ClickGui2Theme.rgb(ClickGui2Theme.Role.MAIN) & 0xFFFFFF,
                Math.round(26f * navAlpha), 2, 0);
        fm.drawStringScaled(ctx, "Collective", brandX + 16f, brandY,
                ClickGui2Theme.fade(ClickGui2Theme.rgb(ClickGui2Theme.Role.CATEGORY_TEXT), navAlpha), 1.22f);
        fm.drawStringScaled(ctx, "premium glass", brandX + 17f, brandY + 15f,
                ClickGui2Theme.fade(ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT), 0.85f * navAlpha), 1f);
        ClickGui2Widgets.hairline(ctx, panelX + 14f, panelY + 50f, RAIL_W - 28f);

        favoritesAnim = ClickGui2Anim.to01(favoritesAnim, favoritesView, dt, 10f);
        float by = panelY + 58f;
        float revealBase = ClickGui2Anim.easeOut(Math.min(1f, introT * 1.5f));
        for (int buttonIndex = 0; buttonIndex < railButtons.size(); buttonIndex++) {
            RailButton button = railButtons.get(buttonIndex);
            button.x = panelX + 14f;
            button.y = by;
            float anim;
            if (button.favorites) {
                anim = favoritesAnim;
            } else {
                float target = (!favoritesView && button.category == category) ? 1f : 0f;
                float prev = catAnims.get(button.category);
                anim = ClickGui2Anim.to01(prev, target > 0.5f, dt, 11f);
                catAnims.put(button.category, anim);
            }

            // каскадное появление пунктов рельса
            float entrance = ClickGui2Anim.stagger(revealBase, buttonIndex, railButtons.size());
            float slideX = (1f - entrance) * 10f;
            float alpha = navAlpha * entrance;
            if (alpha < 0.02f) {
                by += button.h + 6f;
                continue;
            }
            float bx = button.x + slideX;

            boolean hover = interact && ClickGui2Widgets.inRect(mx, my, button.x, button.y, button.w, button.h);
            if (hover) {
                pingHoverSound();
            }
            float reaction = Math.max(anim, hover ? 0.55f : 0f);

            // подложка-пилюля
            if (reaction > 0.02f) {
                fillRounded(ctx, bx, button.y, button.w, button.h, 10f,
                        ClickGui2Theme.alpha(ClickGui2Theme.Role.SELECTED,
                                Math.round(reaction
                                        * (ClickGui2Theme.c(ClickGui2Theme.Role.SELECTED).getAlpha() * 2.3f)
                                        * alpha)));
            }
            // активной — акцентная полоска + мягкое свечение внутри
            if (anim > 0.02f) {
                if (!button.favorites) {
                    GuiGfx.softGlow(ctx, (int) bx, (int) button.y, (int) button.w, (int) button.h, 9,
                            ClickGui2Theme.rgb(ClickGui2Theme.Role.MAIN) & 0xFFFFFF,
                            Math.round(11f * anim * alpha), 2, 0);
                }
                float barX = bx - 5f;
                float barH = 6f + 10f * anim;
                fillRounded(ctx, barX, button.y + button.h / 2f - barH / 2f, 3f, barH, 1.5f,
                        ClickGui2Theme.fade(ClickGui2Theme.Role.MAIN, 0.95f * anim * alpha));
            }

            // счётчик модулей (постоянно, ненавязчиво)
            if (!button.favorites) {
                int n = cardCache.get(button.category).size();
                String num = String.valueOf(n);
                float nw = fm.getStringWidthScaled(num, 8f);
                int tone = anim > 0.05f
                        ? ClickGui2Theme.blend(
                                ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT),
                                ClickGui2Theme.rgb(ClickGui2Theme.Role.MAIN), anim)
                        : ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT);
                fm.drawStringScaled(ctx, num, bx + button.w - nw - 12f,
                        button.y + button.h / 2f - 4f,
                        ClickGui2Theme.fade(tone, 0.8f * alpha), 1f);
            }

            int label = ClickGui2Theme.blend(
                    ClickGui2Theme.rgb(ClickGui2Theme.Role.DESELECT_CATEGORY_TEXT),
                    hover && anim < 0.05f
                            ? ClickGui2Theme.rgb(ClickGui2Theme.Role.OPEN_SETTING_TEXT)
                            : ClickGui2Theme.rgb(ClickGui2Theme.Role.CATEGORY_TEXT), anim);
            if (button.favorites) {
                int starColor = ClickGui2Theme.blend(
                        ClickGui2Theme.rgb(ClickGui2Theme.Role.DESELECT_CATEGORY_TEXT),
                        ClickGui2Theme.SPARK, anim);
                ClickGui2Widgets.starIcon(ctx, bx + 15f, button.y + button.h / 2f,
                        7f + anim * 1.5f, ClickGui2Theme.fade(starColor, alpha));
                fm.drawStringScaled(ctx, "Избранное", bx + 27f, button.y + button.h / 2f - 5f,
                        ClickGui2Theme.fade(label, alpha), 1.05f);
            } else {
                fm.drawStringScaled(ctx, button.category.getName(), bx + 13f,
                        button.y + button.h / 2f - 5f, ClickGui2Theme.fade(label, alpha), 1.05f);
            }
            by += button.h + 6f;
        }

        // подвал рельса: статус игры + ник в стеклянном чипе
        float footerY = panelY + PANEL_H - 50f;
        RenderUtil.drawRect(ctx, panelX + 14f, footerY, panelX + RAIL_W - 14f, footerY + 1f,
                ClickGui2Theme.fade(ClickGui2Theme.Role.LINE, navAlpha));
        MinecraftClient mc = MinecraftClient.getInstance();
        String player = mc != null && mc.getSession() != null
                ? mc.getSession().getUsername() : "-";
        boolean inGame = mc != null && mc.player != null;
        int statusColor = inGame
                ? ClickGui2Theme.ALIVE
                : ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT) | (150 << 24);
        GuiGfx.dot(ctx, panelX + 20f, footerY + 14f, 2.6f, (statusColor & 0xFFFFFF) | (Math.round(190f * navAlpha) << 24));
        fm.drawStringScaled(ctx, player, panelX + 28f, footerY + 9f,
                ClickGui2Theme.fade(ClickGui2Theme.rgb(ClickGui2Theme.Role.TEXT), navAlpha), 1.05f);
        fm.drawStringScaled(ctx, inGame ? "в игре" : "в меню", panelX + 28f, footerY + 22f,
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

    private static final float SEARCH_W = 240f;
    private static final float SEARCH_H = 26f;

    private float searchBoxX() {
        return panelX + PANEL_W - SEARCH_W - 22f;
    }

    private float searchBoxY() {
        return panelY + 40f;
    }

    private void renderHeaderRow(DrawContext ctx, float mx, float my,
                                 float navAlpha, boolean interact) {
        FontManager fm = FontManager.INSTANCE;

        // breadcrumb: акцентная искра + путь
        float crumbX = panelX + RAIL_W + 16f;
        if (navAlpha > 0.05f) {
            int crumbSpark = favoritesView ? ClickGui2Theme.SPARK : ClickGui2Theme.rgb(ClickGui2Theme.Role.MAIN);
            ClickGui2Widgets.starIcon(ctx, crumbX + 3f, panelY + 40f, 6f,
                    ClickGui2Theme.fade(crumbSpark, navAlpha));
        }
        String crumbs = favoritesView ? "Избранное" : category.getName();
        fm.drawStringWithShadowScaled(ctx, crumbs, crumbX + 10f, panelY + 19f,
                ClickGui2Theme.fade(ClickGui2Theme.rgb(ClickGui2Theme.Role.CATEGORY_TEXT), navAlpha), 1.3f);

        // пересканирование только при изменении запроса (без аллокаций в кадре)
        if (!searchQuery.equals(scannedQuery)) {
            searchResults.clear();
            scannedQuery = searchQuery;
            if (!searchQuery.isEmpty()) {
                String q = searchQuery.toLowerCase();
                float resultY = searchBoxY() + SEARCH_H + 6f + 36f;
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
        fm.drawStringScaled(ctx, sub, searchBoxX() - 12f - sw, panelY + 25f,
                ClickGui2Theme.fade(ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT), 0.9f * navAlpha), 1f);

        // бокс поиска — стеклянное поле
        float boxX = searchBoxX();
        float boxY = searchBoxY();
        int border = searchActive
                ? ClickGui2Theme.alpha(ClickGui2Theme.Role.MAIN, 170)
                : ClickGui2Theme.rgb(ClickGui2Theme.Role.OUTLINE);
        fillRounded(ctx, boxX, boxY, SEARCH_W, SEARCH_H, 9f,
                ClickGui2Theme.alpha(ClickGui2Theme.Role.BACKGROUND, 235));
        outlineRounded(ctx, boxX, boxY, SEARCH_W, SEARCH_H, 9f, border);
        ClickGui2Widgets.edgeHighlight(ctx, boxX, boxY, SEARCH_W, navAlpha);

        if (!searchActive && searchQuery.isEmpty()) {
            float hw = fm.getStringWidthScaled("CTRL + F", 8f);
            fm.drawStringScaled(ctx, "CTRL + F", boxX + SEARCH_W - hw - 10f, boxY + SEARCH_H / 2f - 4f,
                    ClickGui2Theme.fade(ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT), 0.6f * navAlpha), 1f);
        }
        String display = searchQuery.isEmpty()
                ? "Поиск..."
                : searchQuery + (searchActive && ClickGui2Anim.caretBlink() ? "|" : "");
        fm.drawStringScaled(ctx, display, boxX + 11f, boxY + SEARCH_H / 2f - 4f,
                ClickGui2Theme.fade(searchQuery.isEmpty()
                        ? ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT)
                        : ClickGui2Theme.rgb(ClickGui2Theme.Role.TEXT), navAlpha), 1f);
    }

    private void renderSearchDropdown(DrawContext ctx, float navAlpha, boolean interact) {
        FontManager fm = FontManager.INSTANCE;
        float boxX = searchBoxX();
        float dropY = searchBoxY() + SEARCH_H + 6f;
        if (searchResults.isEmpty()) {
            // после сканирования с пустым результатом — вежливая заглушка
            if (!searchQuery.isEmpty() && searchQuery.equals(scannedQuery)) {
                float dropH = 30f;
                GuiGfx.shadow(ctx, (int) boxX, (int) dropY, (int) SEARCH_W, (int) dropH, 10);
                fillRounded(ctx, boxX, dropY, SEARCH_W, dropH, 10f,
                        ClickGui2Theme.rgb(ClickGui2Theme.Role.BACKGROUND));
                outlineRounded(ctx, boxX, dropY, SEARCH_W, dropH, 10f,
                        ClickGui2Theme.rgb(ClickGui2Theme.Role.OUTLINE));
                String stub = "Ничего не найдено";
                int tone = ClickGui2Theme.blend(
                        ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT),
                        ClickGui2Theme.rgb(ClickGui2Theme.Role.DEACTIVE_TEXT), 0.45f);
                fm.drawStringScaled(ctx, stub, boxX + 12f, dropY + 10f,
                        ClickGui2Theme.fade(tone, 0.9f * navAlpha), 1f);
            }
            return;
        }
        float dropH = 26f + searchResults.size() * 20f;

        GuiGfx.shadow(ctx, (int) boxX, (int) dropY, (int) SEARCH_W, (int) dropH, 10);
        fillRounded(ctx, boxX, dropY, SEARCH_W, dropH, 10f, ClickGui2Theme.rgb(ClickGui2Theme.Role.BACKGROUND));
        outlineRounded(ctx, boxX, dropY, SEARCH_W, dropH, 10f, ClickGui2Theme.rgb(ClickGui2Theme.Role.OUTLINE));
        ClickGui2Widgets.edgeHighlight(ctx, boxX, dropY, SEARCH_W, navAlpha);
        fm.drawStringScaled(ctx, "Результаты поиска", boxX + 12f, dropY + 7f,
                ClickGui2Theme.fade(ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT), 0.85f * navAlpha), 1f);
        RenderUtil.drawRect(ctx, boxX + 12f, dropY + 23f, boxX + SEARCH_W - 12f, dropY + 24f,
                ClickGui2Theme.fade(ClickGui2Theme.Role.LINE, 0.8f * navAlpha));

        for (SearchResult result : searchResults) {
            boolean hoverRow = interact
                    && ClickGui2Widgets.inRect(lastMouseX, lastMouseY, boxX + 6f, result.rowY() - 1f, SEARCH_W - 12f, 18f);
            if (hoverRow) {
                fillRounded(ctx, boxX + 6f, result.rowY() - 1f, SEARCH_W - 12f, 18f, 5f,
                        ClickGui2Theme.rgb(ClickGui2Theme.Role.SELECTED));
            }
            boolean on = result.card().module().isToggled();
            float dotX = boxX + 13f;
            GuiGfx.dot(ctx, dotX, result.rowY() + 8f, on ? 2.4f : 1.8f,
                    ClickGui2Theme.fade(on ? ClickGui2Theme.Role.ACTIVE_STATE : ClickGui2Theme.Role.DEACTIVE_TEXT,
                            navAlpha));
            fm.drawStringScaled(ctx, result.card().module().getName(), dotX + 7f, result.rowY() + 3f,
                    ClickGui2Theme.fade(hoverRow
                            ? ClickGui2Theme.rgb(ClickGui2Theme.Role.ACTIVE_TEXT)
                            : ClickGui2Theme.rgb(ClickGui2Theme.Role.OPEN_SETTING_TEXT), navAlpha), 1f);
            String catName = result.card().category().getName();
            float cw = fm.getStringWidthScaled(catName, 8f);
            fm.drawStringScaled(ctx, catName, boxX + SEARCH_W - 12f - cw, result.rowY() + 4f,
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
        return PANEL_W - RAIL_W - 6f;
    }

    private float viewportH() {
        return PANEL_H - VIEW_Y - 16f;
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

        // скролл с плавным ходом; сильный сдвиг гасит всплывашки
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
            float slide = (1f - entrance) * 12f;
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

        // маски затухания по краям портала (стекло «уходит» в панель)
        fadeBand(ctx, viewportY(), 1f, navAlpha);
        fadeBand(ctx, viewportY() + viewportH() - 14f, -1f, navAlpha);

        // пустое состояние избранного: искра + подсказка
        if (visible.isEmpty()) {
            float midX = viewportX() + viewportW() / 2f;
            float midY = viewportY() + viewportH() / 2f;
            float bob = 2.5f * (float) Math.sin(System.currentTimeMillis() / 600.0);
            ClickGui2Widgets.starIcon(ctx, midX, midY - 12f + bob, 15f,
                    ClickGui2Theme.fade(ClickGui2Theme.SPARK, 0.5f * navAlpha));
            GuiGfx.softGlow(ctx, (int) midX - 16, (int) midY - 28 + (int) bob, 32, 32, 10,
                    (255 << 16) | (210 << 8) | 64, Math.round(22f * navAlpha), 2, 0);
            String empty = "Нет избранных. Нажми на звезду на карточке модуля.";
            float ew = fm.getStringWidthScaled(empty, 9f);
            fm.drawStringScaled(ctx, empty, midX - ew / 2f, midY + 8f,
                    ClickGui2Theme.fade(ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT), 0.9f * navAlpha), 1f);
        }

        // полоса прокрутки: тонкий трек + мягкий бегунок
        if (contentHeight > viewportH()) {
            float trackX = viewportX() + viewportW() - SCROLL_W - 3f;
            RenderUtil.drawRect(ctx, trackX, viewportY(),
                    trackX + SCROLL_W, viewportY() + viewportH(),
                    ClickGui2Theme.alpha(ClickGui2Theme.Role.LINE,
                            Math.round((draggingThumb ? 90f : 50f) * navAlpha)));
            float trackH = viewportH();
            float th = Math.max(26f, trackH * trackH / contentHeight);
            float trackRun = trackH - th;
            float progress = -scrollCurrent / Math.max(1f, contentHeight - trackH);
            thumbX = trackX - 6f;
            thumbY = viewportY() + trackRun * progress;
            thumbW = SCROLL_W + 12f;
            thumbH = th;
            boolean thumbHover = ClickGui2Widgets.inRect(mx, my, thumbX, thumbY, thumbW, thumbH);
            int scrollAlpha = Math.round(navAlpha * (draggingThumb ? 255f : thumbHover ? 170f : 110f));
            fillRounded(ctx, trackX, thumbY, SCROLL_W, thumbH, SCROLL_W / 2f,
                    ClickGui2Theme.alpha(ClickGui2Theme.Role.SCROLL, scrollAlpha));
        } else {
            thumbH = 0f; // вне экрана — не кликабельно
        }
    }

    /** Маска затухания: три полосы цвета фона с гаснущей альфой. */
    private void fadeBand(DrawContext ctx, float y, float dir, float navAlpha) {
        if (navAlpha < 0.02f) return;
        for (int i = 0; i < 3; i++) {
            float a = (i == 0 ? 110f : i == 1 ? 70f : 34f) * navAlpha;
            float bandY = y + dir * i * 5f;
            if (bandY < viewportY() - 1f || bandY > viewportY() + viewportH()) continue;
            RenderUtil.drawRect(ctx, viewportX(), bandY, viewportX() + viewportW(), bandY + 5f,
                    ClickGui2Theme.alpha(ClickGui2Theme.Role.BACKGROUND, Math.round(a)));
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
        if (button == 0 && ClickGui2Widgets.inRect(mx, my, boxX, boxY, SEARCH_W, SEARCH_H)) {
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
                if (ClickGui2Widgets.inRect(mx, my, boxX + 6f, result.rowY() - 1f, SEARCH_W - 12f, 18f)) {
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

        // полоса прокрутки (увеличенная зона клика)
        if (thumbH > 0f && ClickGui2Widgets.inRect(mx, my, thumbX, thumbY - 6f, thumbW, thumbH + 12f)) {
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
            float th = Math.max(26f, trackH * trackH / Math.max(1f, contentHeight));
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

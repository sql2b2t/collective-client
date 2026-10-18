package me.collective.gui.settings;

import me.collective.font.FontManager;
import me.collective.module.client.UI;
import me.collective.module.render.Blur;
import me.collective.render.BlurEngine;
import me.collective.render.RenderUtil;
import me.collective.render.SoundUtil;
import me.collective.setting.Setting;
import me.collective.util.SearchUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Порт экрана выбора из Meteor Client (CollectionListSettingScreen + SortingHelper):
 * строка нечёткого поиска сверху и две колонки под ней — слева все значения
 * (клик/«+» добавляет), справа выбранные (клик/«-» убирает). Поиск повторяет
 * поведение Meteor: поиск по словам + расстояние Левенштейна (веса 1/8/8),
 * сортировка результатов по релевантности.
 *
 * Визуально выполнен в языке ClickGUI клиента (палитра UI, снег, blur, рамка),
 * вся геометрия привязана к целым пикселям.
 */
@Environment(EnvType.CLIENT)
public abstract class CollectionListSettingScreen<T> extends Screen {

    private static final float HEADER_H = 14;
    private static final float PAD = 4;
    private static final float SEARCH_H = 16;
    private static final float HDR_TXT_H = 11;
    private static final float ROW_H = 20;
    private static final float ICON = 16;
    private static final float BTN = 14;
    private static final float SCROLL_STEP = ROW_H;

    private static final Color FALLBACK_ACCENT = new Color(189, 153, 255, 115);
    private static final Color FALLBACK_BG = new Color(2, 0, 8, 119);
    private static final Color FALLBACK_TEXT = new Color(249, 244, 255);
    private static final Color PLACEHOLDER = new Color(150, 150, 175, 140);
    private static final Color CLOSE_HOVER = new Color(255, 105, 110, 255);
    private static final Color ROW_HOVER = new Color(255, 255, 255, 25);
    private static final Color BTN_IDLE = new Color(255, 255, 255, 18);
    private static final Color BTN_HOVER = new Color(255, 255, 255, 50);
    private static final Color SEPARATOR = new Color(255, 255, 255, 25);

    protected final Screen parent;
    protected final Setting<List<T>> setting;
    protected final String titleText;

    /** Текущий выбранный набор (копия значения настройки, мутируется при кликах). */
    protected final List<T> selected = new ArrayList<>();

    private final List<T> leftItems = new ArrayList<>();
    private final List<T> rightItems = new ArrayList<>();
    private boolean listDirty = true;

    private String query = "";

    private float scrollLeft, scrollLeftSmooth;
    private float scrollRight, scrollRightSmooth;

    private float winX = Float.NaN;
    private float winY = Float.NaN;
    private float winW = 360;
    private float winH = 320;

    private final Random random = new Random();
    private final List<float[]> snow = new ArrayList<>();

    protected CollectionListSettingScreen(Screen parent, Setting<List<T>> setting, String title) {
        super(Text.literal(title));
        this.parent = parent;
        this.setting = setting;
        this.titleText = title;
    }

    // ------------------------------------------------------------- Абстракция

    /** Все возможные значения в порядке регистрации (как Iterable реестра Meteor). */
    protected abstract Iterable<T> allValues();

    /** Значение вообще показывается в пикере (аналог includeValue у Meteor). */
    protected abstract boolean includeValue(T value);

    /** Имена для нечёткого поиска и сортировки (название + id). */
    protected abstract String[] getValueNames(T value);

    /** Текст строки в списке. */
    protected abstract String labelFor(T value);

    /** Иконка строки. */
    protected abstract ItemStack iconFor(T value);

    /** Парное значение, добавляемое/удаляемое вместе с основным (баннеры → стеновые баннеры). */
    protected T getAdditionalValue(T value) {
        return null;
    }

    // ------------------------------------------------------------- Инициализация

    @Override
    protected void init() {
        selected.clear();
        if (setting.getValue() != null) {
            selected.addAll(setting.getValue());
        }
        listDirty = true;
        scrollLeft = scrollLeftSmooth = 0;
        scrollRight = scrollRightSmooth = 0;

        winW = Math.min(360, width - 16);
        winH = Math.min(320, height - 16);
        winX = (width - winW) / 2f;
        winY = (height - winH) / 2f;

        snow.clear();
        for (int i = 0; i < 80; i++) {
            float[] flake = new float[4];
            flake[0] = random.nextFloat() * width;
            flake[1] = random.nextFloat() * height;
            flake[2] = 0.4f + random.nextFloat() * 1.2f;
            flake[3] = 1.0f + random.nextFloat() * 2.0f;
            snow.add(flake);
        }
    }

    // ------------------------------------------------------------- Геометрия

    /** Окно всегда рисуется и обрабатывается по целым пикселям — кромки чёткие. */
    private float bx() {
        return Math.round(winX);
    }

    private float by() {
        return Math.round(winY);
    }

    private float searchY() {
        return by() + HEADER_H + 5;
    }

    private float hdrY() {
        return searchY() + SEARCH_H + 6;
    }

    private float listTop() {
        return hdrY() + HDR_TXT_H;
    }

    private float listBottom() {
        return by() + winH - PAD;
    }

    private float colWidth() {
        float inner = winW - PAD * 2;
        return (float) Math.floor((inner - 5) / 2f);
    }

    private float leftX() {
        return bx() + PAD;
    }

    private float sepX() {
        return leftX() + colWidth() + 2;
    }

    private float rightX() {
        return sepX() + 3;
    }

    private int visibleRows() {
        float area = listBottom() - listTop();
        return Math.max(1, (int) (area / ROW_H));
    }

    private float maxScroll(int count) {
        return Math.max(0, count - visibleRows());
    }

    private int rowIndexAt(double my, float scroll) {
        float top = listTop();
        if (my < top || my >= listBottom()) return -1;
        int idx0 = (int) Math.floor(scroll);
        float frac = scroll - idx0;
        return idx0 + (int) Math.floor((my - top) / ROW_H + frac);
    }

    // ------------------------------------------------------------- Цвета UI

    private Color accent() {
        return UI.INSTANCE != null ? UI.INSTANCE.color.getValue() : FALLBACK_ACCENT;
    }

    private Color bg() {
        return UI.INSTANCE != null ? UI.INSTANCE.bgColor.getValue() : FALLBACK_BG;
    }

    private Color rowBg() {
        return UI.INSTANCE != null ? UI.INSTANCE.bgButton.getValue() : new Color(159, 128, 219, 42);
    }

    private Color text() {
        return UI.INSTANCE != null ? UI.INSTANCE.textColor.getValue() : FALLBACK_TEXT;
    }

    private Color enabledText() {
        return UI.INSTANCE != null ? UI.INSTANCE.enabledColor.getValue() : new Color(221, 201, 255);
    }

    private boolean sounds() {
        return UI.INSTANCE != null && UI.INSTANCE.sounds.getValue();
    }

    private String trim(String value, float maxWidth) {
        FontManager fm = FontManager.INSTANCE;
        if (fm.getStringWidth(value) <= maxWidth) return value;
        String s = value;
        while (fm.getStringWidth(s + "..") > maxWidth && s.length() > 2) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "..";
    }

    // ------------------------------------------------------------- Рендер

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        if (Blur.INSTANCE != null && Blur.INSTANCE.isToggled() && Blur.INSTANCE.clickGui.getValue()) {
            List<float[]> regions = List.of(new float[]{bx(), by(), bx() + winW, by() + winH});
            BlurEngine.INSTANCE.renderBlur(ctx, regions, Blur.INSTANCE.radius.getFloatValue());
        }
        ctx.fill(0, 0, width, height, new Color(0, 0, 0, 100).getRGB());
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        FontManager fm = FontManager.INSTANCE;
        long now = System.currentTimeMillis();

        if (listDirty) {
            rebuild();
        }

        // Плавные скроллы колонок
        scrollLeft = Math.max(0, Math.min(scrollLeft, maxScroll(leftItems.size())));
        scrollRight = Math.max(0, Math.min(scrollRight, maxScroll(rightItems.size())));
        scrollLeftSmooth += (scrollLeft - scrollLeftSmooth) * 0.35f;
        scrollRightSmooth += (scrollRight - scrollRightSmooth) * 0.35f;
        if (Math.abs(scrollLeftSmooth - scrollLeft) < 0.01f) scrollLeftSmooth = scrollLeft;
        if (Math.abs(scrollRightSmooth - scrollRight) < 0.01f) scrollRightSmooth = scrollRight;

        float wx = bx();
        float wy = by();

        // Снежинки (как в ClickGUI)
        if (UI.INSTANCE != null && UI.INSTANCE.snow.getValue()) {
            for (float[] s : snow) {
                s[1] += s[2];
                if (s[1] > height) {
                    s[1] = 0;
                    s[0] = random.nextFloat() * width;
                }
                RenderUtil.drawRect(ctx, s[0], s[1], s[0] + s[3], s[1] + s[3], new Color(255, 255, 255, 160));
            }
        }

        // Тень окна
        if (UI.INSTANCE != null && UI.INSTANCE.windowShadow.getValue()) {
            float s = UI.INSTANCE.shadowSize.getFloatValue();
            Color sc = UI.INSTANCE.shadowColor.getValue();
            RenderUtil.drawRect(ctx, wx + s, wy + HEADER_H + s, wx + winW + s, wy + winH + s, sc);
        }

        // Фон панели
        RenderUtil.drawRect(ctx, wx, wy, wx + winW, wy + winH, bg());

        // Хедер
        RenderUtil.drawRect(ctx, wx, wy, wx + winW, wy + HEADER_H, accent());
        fm.drawStringWithShadow(ctx, titleText, wx + 2,
                wy + (HEADER_H - fm.getFontHeight()) / 2f + 0.5f, Color.WHITE);

        // Закрыть (✕)
        float closeX = wx + winW - HEADER_H;
        boolean hovClose = inBounds(mx, my, closeX, wy, HEADER_H, HEADER_H);
        if (hovClose) {
            RenderUtil.drawRect(ctx, closeX, wy, closeX + HEADER_H, wy + HEADER_H, new Color(255, 105, 110, 70));
        }
        String xTxt = "✕";
        fm.drawStringWithShadow(ctx, xTxt,
                Math.round(closeX + (HEADER_H - fm.getStringWidth(xTxt)) / 2f),
                wy + (HEADER_H - fm.getFontHeight()) / 2f + 0.5f,
                hovClose ? Color.WHITE : DELETE_IDLE_TEXT());

        // Поле поиска (всегда в фокусе, как у Meteor)
        float fieldX = wx + PAD;
        float fieldW = winW - PAD * 2;
        float fy = searchY();
        RenderUtil.drawRect(ctx, fieldX, fy, fieldX + fieldW, fy + SEARCH_H, rowBg());
        RenderUtil.drawOutline(ctx, fieldX, fy, fieldW, SEARCH_H, 1, accent().getRGB());
        float fieldTextY = fy + (SEARCH_H - fm.getFontHeight()) / 2f + 0.5f;
        if (query.isEmpty()) {
            fm.drawStringWithShadow(ctx, "Поиск...", fieldX + 5, fieldTextY, PLACEHOLDER);
        } else {
            fm.drawStringWithShadow(ctx, query, fieldX + 5, fieldTextY, text());
        }
        if (now % 1000 < 560) {
            float caretX = fieldX + 4 + fm.getStringWidth(query);
            RenderUtil.drawRect(ctx, caretX, fy + 4, caretX + 1, fy + SEARCH_H - 4, text());
        }

        // Подписи колонок
        float hy = hdrY() + 0.5f;
        String leftHdr = "Все (" + leftItems.size() + ")";
        fm.drawStringWithShadow(ctx, leftHdr, leftX() + 1, hy, PLACEHOLDER);
        String rightHdr = "Выбрано (" + rightItems.size() + ")";
        fm.drawStringWithShadow(ctx, rightHdr, rightX() + 1, hy, PLACEHOLDER);

        // Разделитель колонок
        RenderUtil.drawRect(ctx, sepX(), listTop(), sepX() + 1, listBottom(), SEPARATOR);

        // Колонки
        renderColumn(ctx, mx, my, leftItems, leftX(), scrollLeftSmooth, false);
        renderColumn(ctx, mx, my, rightItems, rightX(), scrollRightSmooth, true);
        renderEmptyHints(fm, ctx);

        // Рамка окна
        if (UI.INSTANCE != null && UI.INSTANCE.line.getValue()) {
            RenderUtil.drawOutline(ctx, wx - 1, wy - 1, winW + 2, winH + 2, 1, accent().getRGB());
        }

        super.render(ctx, mx, my, delta);
    }

    private static Color DELETE_IDLE_TEXT() {
        return new Color(255, 255, 255, 200);
    }

    private void renderColumn(DrawContext ctx, int mx, int my, List<T> items, float cx, float scroll, boolean selectedCol) {
        FontManager fm = FontManager.INSTANCE;
        float colW = colWidth();
        float top = listTop();
        float bottom = listBottom();

        ctx.enableScissor((int) cx, (int) top, (int) (cx + colW), (int) bottom);
        {
            int idx0 = (int) Math.floor(scroll);
            float frac = scroll - idx0;

            for (int i = idx0; i < items.size(); i++) {
                float rowY = Math.round(top + (i - idx0 - frac) * ROW_H);
                if (rowY >= bottom) break;

                T value = items.get(i);
                boolean hover = mx >= cx && mx < cx + colW && my >= rowY && my < rowY + ROW_H
                        && my >= top && my < bottom;

                if (hover) {
                    RenderUtil.drawRect(ctx, cx, rowY, cx + colW, rowY + ROW_H, ROW_HOVER);
                }

                // Иконка предмета
                ItemStack icon = iconFor(value);
                if (icon != null && !icon.isEmpty()) {
                    ctx.drawItem(icon, (int) cx + 2, Math.round(rowY + (ROW_H - ICON) / 2f));
                }

                // Название
                float textY = rowY + (ROW_H - fm.getFontHeight()) / 2f + 0.5f;
                float maxLabelW = colW - 2 - ICON - 2 - BTN - 6;
                fm.drawStringWithShadow(ctx, trim(labelFor(value), maxLabelW), cx + ICON + 4, textY,
                        selectedCol ? enabledText() : text());

                // Кнопка +/- (как WPlus/WMinus у Meteor)
                float btnX = cx + colW - BTN - 2;
                float btnY = rowY + (ROW_H - BTN) / 2f;
                boolean hoverBtn = hover && mx >= btnX;
                RenderUtil.drawRect(ctx, btnX, btnY, btnX + BTN, btnY + BTN,
                        hoverBtn ? BTN_HOVER : BTN_IDLE);
                String sym = selectedCol ? "-" : "+";
                fm.drawStringWithShadow(ctx, sym,
                        Math.round(btnX + (BTN - fm.getStringWidth(sym)) / 2f), textY,
                        hoverBtn ? enabledText() : text());
            }
        }
        ctx.disableScissor();

        // Скроллбар колонки (ровно 2px)
        if (items.size() > visibleRows()) {
            float trackH = bottom - top;
            float barH = Math.max(12, Math.round(trackH * (visibleRows() / (float) items.size())));
            float travel = trackH - barH;
            float ms = maxScroll(items.size());
            float ratio = ms <= 0 ? 0 : scroll / ms;
            float barY = Math.round(top + travel * Math.max(0, Math.min(1, ratio)));
            float barX = cx + colW - 2;
            RenderUtil.drawRect(ctx, barX, barY, barX + 2, barY + barH, new Color(255, 255, 255, 130));
        }
    }

    private void renderEmptyHints(FontManager fm, DrawContext ctx) {
        float top = listTop();
        float bottom = listBottom();
        float centerY = (top + bottom) / 2f - fm.getFontHeight() / 2f;

        if (leftItems.isEmpty()) {
            String hint = query.isEmpty() ? "Все блоки выбраны" : "Ничего не найдено";
            fm.drawStringWithShadow(ctx, hint,
                    Math.round(leftX() + (colWidth() - fm.getStringWidth(hint)) / 2f), centerY, PLACEHOLDER);
        }
        if (rightItems.isEmpty()) {
            String hint = "Ничего не выбрано";
            fm.drawStringWithShadow(ctx, hint,
                    Math.round(rightX() + (colWidth() - fm.getStringWidth(hint)) / 2f), centerY, PLACEHOLDER);
        }
    }

    // ------------------------------------------------------------- Логика

    private void rebuild() {
        listDirty = false;
        leftItems.clear();
        rightItems.clear();

        for (T value : allValues()) {
            if (value == null || !includeValue(value) || selected.contains(value)) continue;
            leftItems.add(value);
        }
        for (T value : selected) {
            if (value == null || !includeValue(value)) continue;
            rightItems.add(value);
        }

        String filter = query.trim();
        if (!filter.isEmpty()) {
            filterByRelevance(leftItems, filter);
            filterByRelevance(rightItems, filter);
        }
    }

    /** Точный порт SortingHelper.sortInternal: слова + Левенштейн, сортировка по дистанции. */
    private void filterByRelevance(List<T> list, String filter) {
        List<Entry<T>> entries = new ArrayList<>(list.size());

        for (T value : list) {
            int bestWords = 0;
            int bestDistance = Integer.MAX_VALUE;
            float relevancy = 0f;

            for (String name : getValueNames(value)) {
                int words = SearchUtils.searchInWords(name, filter);
                int distance = SearchUtils.searchLevenshteinDefault(name, filter, false);
                bestWords = Math.max(bestWords, words);
                bestDistance = Math.min(bestDistance, distance);
                relevancy = Math.max(relevancy, 1f - (float) distance / name.length());
            }

            if (bestWords > 0 || relevancy >= 0.5f) {
                entries.add(new Entry<>(value, bestDistance));
            }
        }

        entries.sort(Comparator.comparingInt(Entry::distance));
        list.clear();
        for (Entry<T> e : entries) {
            list.add(e.value);
        }
    }

    private record Entry<T>(T value, int distance) {
    }

    private void toggle(T value, boolean remove) {
        if (remove) {
            selected.remove(value);
            T additional = getAdditionalValue(value);
            if (additional != null) selected.remove(additional);
        } else {
            if (!selected.contains(value)) selected.add(value);
            T additional = getAdditionalValue(value);
            if (additional != null && !selected.contains(additional)) selected.add(additional);
        }
        setting.setValue(new ArrayList<>(selected));
        listDirty = true;
        if (sounds()) SoundUtil.playClick(1.0f, remove ? 0.9f : 1.1f);
    }

    // ------------------------------------------------------------- Ввод

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);

        double mx = click.x();
        double my = click.y();
        float wx = bx();
        float wy = by();

        // Закрыть
        if (inBounds(mx, my, wx + winW - HEADER_H, wy, HEADER_H, HEADER_H)) {
            if (sounds()) SoundUtil.playClick(1.0f, 1.2f);
            close();
            return true;
        }

        // Клики по колонкам: строка целиком добавляет/убирает (кнопка «+»/«-» тоже)
        if (my >= listTop() && my < listBottom()) {
            if (mx >= leftX() && mx < leftX() + colWidth()) {
                int index = rowIndexAt(my, scrollLeftSmooth);
                if (index >= 0 && index < leftItems.size()) {
                    toggle(leftItems.get(index), false);
                    return true;
                }
            } else if (mx >= rightX() && mx < rightX() + colWidth()) {
                int index = rowIndexAt(my, scrollRightSmooth);
                if (index >= 0 && index < rightItems.size()) {
                    toggle(rightItems.get(index), true);
                    return true;
                }
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hScroll, double vScroll) {
        if (vScroll == 0) return super.mouseScrolled(mx, my, hScroll, vScroll);

        float wx = bx();
        float wy = by();
        if (!inBounds(mx, my, wx, wy, winW, winH)) {
            return super.mouseScrolled(mx, my, hScroll, vScroll);
        }

        float delta = (float) vScroll * SCROLL_STEP;
        if (mx >= leftX() && mx < leftX() + colWidth()) {
            scrollLeft = Math.max(0, Math.min(scrollLeft - delta, maxScroll(leftItems.size())));
        } else if (mx >= rightX() && mx < rightX() + colWidth()) {
            scrollRight = Math.max(0, Math.min(scrollRight - delta, maxScroll(rightItems.size())));
        } else {
            scrollLeft = Math.max(0, Math.min(scrollLeft - delta, maxScroll(leftItems.size())));
            scrollRight = Math.max(0, Math.min(scrollRight - delta, maxScroll(rightItems.size())));
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        long win = MinecraftClient.getInstance().getWindow().getHandle();
        boolean ctrl = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;

        if (ctrl && key == GLFW.GLFW_KEY_V) {
            String clipboard = GLFW.glfwGetClipboardString(win);
            if (clipboard != null) {
                for (char c : clipboard.toCharArray()) {
                    if (query.length() >= 48) break;
                    if (c >= ' ' && c != 127) query += c;
                }
                listDirty = true;
            }
            return true;
        }

        if (key == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
            query = query.substring(0, query.length() - 1);
            listDirty = true;
            return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (input.isValidChar() && query.length() < 48) {
            query += input.asString();
            listDirty = true;
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------- Хелперы

    static boolean inBounds(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}

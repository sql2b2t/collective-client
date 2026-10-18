package me.collective.gui.alt;

import me.collective.font.FontManager;
import me.collective.gui.GuiGfx;
import me.collective.module.client.UI;
import me.collective.module.render.Blur;
import me.collective.render.BlurEngine;
import me.collective.render.RenderUtil;
import me.collective.render.SoundUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.session.Session;
import net.minecraft.text.Text;
import me.collective.mixin.MixinMinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Alt-менеджер в визуальном языке ClickGUI:
 * панель с акцентным заголовком, плоские строки-кнопки из палитры UI,
 * заливка активного аккаунта цветом bgEnabled, скроллбар как у панелей,
 * опции Line / WindowShadow / Snow / Blur берутся из настроек UI и Blur.
 * Вся геометрия привязана к целым пикселям — без размытых полупиксельных кромок.
 */
@Environment(EnvType.CLIENT)
public class AltManagerScreen extends Screen {

    private static final Path ALTS_FILE = net.fabricmc.loader.api.FabricLoader.getInstance()
            .getGameDir().resolve("collective").resolve("alts.txt");

    private static final float W = 300;
    private static final float H = 340;
    private static final float HEADER_H = 14;
    private static final float PAD = 4;
    private static final float FIELD_H = 16;
    private static final float LOGIN_W = 46;
    private static final float FOOTER_H = 14;
    private static final float ROW_GAP = 1;

    // Палитра-фолбэк на случай, если модуль UI ещё не инициализирован
    private static final Color FALLBACK_ACCENT = new Color(189, 153, 255, 115);
    private static final Color FALLBACK_BG = new Color(2, 0, 8, 119);
    private static final Color FALLBACK_TEXT = new Color(249, 244, 255);

    private static final Color DELETE_IDLE = new Color(150, 150, 175, 120);
    private static final Color DELETE_HOVER = new Color(255, 105, 110, 255);

    private final Screen parent;

    private final List<String> alts = new ArrayList<>();

    private float winX = Float.NaN;
    private float winY = Float.NaN;
    private boolean dragging;

    private final Random random = new Random();

    private String input = "";
    private boolean inputActive;

    private float scroll;
    private float scrollSmooth;

    private String status = "";
    private long statusUntil;
    private boolean clearArmed;
    private long clearArmedUntil;

    // Плавное появление/затухание крестика удаления (по имени аккаунта)
    private final Map<String, Float> deleteHover = new HashMap<>();

    // Снежинки, как в ClickGUI
    private static class Snowflake {
        float x, y, speed, size;
    }

    private final List<Snowflake> snowflakes = new ArrayList<>();

    public AltManagerScreen(Screen parent) {
        super(Text.literal("Alt Manager"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        loadAlts();

        if (Float.isNaN(winX) || Float.isNaN(winY)) {
            winX = (width - W) / 2f;
            winY = (height - H) / 2f;
        }
        clampWindow();

        snowflakes.clear();
        for (int i = 0; i < 80; i++) {
            Snowflake s = new Snowflake();
            s.x = random.nextFloat() * width;
            s.y = random.nextFloat() * height;
            s.speed = 0.4f + random.nextFloat() * 1.2f;
            s.size = 1.0f + random.nextFloat() * 2.0f;
            snowflakes.add(s);
        }
    }

    // ------------------------------------------------------------- Геометрия

    /** Окно всегда рисуется и обрабатывается по целым пикселям — кромки чёткие. */
    private float bx() { return Math.round(winX); }

    private float by() { return Math.round(winY); }

    private float inputY() { return by() + HEADER_H + 5; }

    private float listTop() { return inputY() + FIELD_H + 6; }

    private float footerY() { return by() + H - PAD - FOOTER_H; }

    private float listBottom() { return footerY() - 6; }

    private float rowH() { return UI.INSTANCE != null ? UI.INSTANCE.buttonHeight.getIntValue() : 12; }

    private int visibleRows() {
        float area = listBottom() - listTop();
        return Math.max(1, (int) (area / (rowH() + ROW_GAP)));
    }

    private void clampWindow() {
        winX = Math.max(2, Math.min(winX, width - W - 2));
        winY = Math.max(2, Math.min(winY, height - H - 2));
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

    private Color rowEnabled() {
        return UI.INSTANCE != null ? UI.INSTANCE.bgEnabled.getValue() : FALLBACK_ACCENT;
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

    private float animStep() {
        return (UI.INSTANCE != null ? UI.INSTANCE.animSpeed.getFloatValue() : 2.0f) * 0.15f;
    }

    // ------------------------------------------------------------- Рендер

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        if (Blur.INSTANCE != null && Blur.INSTANCE.isToggled() && Blur.INSTANCE.clickGui.getValue()) {
            List<float[]> regions = List.of(new float[]{ bx(), by(), bx() + W, by() + H });
            BlurEngine.INSTANCE.renderBlur(ctx, regions, Blur.INSTANCE.radius.getFloatValue());
        }
        ctx.fill(0, 0, width, height, new Color(0, 0, 0, 100).getRGB());
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        FontManager fm = FontManager.INSTANCE;
        MinecraftClient mc = MinecraftClient.getInstance();
        float wx = bx();
        float wy = by();

        long now = System.currentTimeMillis();
        if (clearArmed && now > clearArmedUntil) clearArmed = false;

        float scrollMax = Math.max(0, alts.size() - visibleRows());
        scroll = Math.max(0, Math.min(scroll, scrollMax));
        scrollSmooth += (scroll - scrollSmooth) * 0.35f;
        if (Math.abs(scrollSmooth - scroll) < 0.01f) scrollSmooth = scroll;

        // Снежинки (как в ClickGUI)
        if (UI.INSTANCE != null && UI.INSTANCE.snow.getValue()) {
            for (Snowflake s : snowflakes) {
                s.y += s.speed;
                if (s.y > height) {
                    s.y = 0;
                    s.x = random.nextFloat() * width;
                }
                RenderUtil.drawRect(ctx, s.x, s.y, s.x + s.size, s.y + s.size, new Color(255, 255, 255, 160));
            }
        }

        // Тень окна
        if (UI.INSTANCE != null && UI.INSTANCE.windowShadow.getValue()) {
            float s = UI.INSTANCE.shadowSize.getFloatValue();
            Color sc = UI.INSTANCE.shadowColor.getValue();
            RenderUtil.drawRect(ctx, wx + s, wy + HEADER_H + s, wx + W + s, wy + H + s, sc);
        }

        // Фон панели
        RenderUtil.drawRect(ctx, wx, wy, wx + W, wy + H, bg());

        // Хедер
        RenderUtil.drawRect(ctx, wx, wy, wx + W, wy + HEADER_H, accent());
        fm.drawStringWithShadow(ctx, "Alt Manager", wx + 2,
                wy + (HEADER_H - fm.getFontHeight()) / 2f + 0.5f, Color.WHITE);
        String count = alts.size() + " alts";
        fm.drawStringWithShadow(ctx, count, wx + W - 4 - fm.getStringWidth(count),
                wy + (HEADER_H - fm.getFontHeight()) / 2f + 0.5f, Color.WHITE);

        // Поле ввода ника
        float fieldX = wx + PAD;
        float fieldW = W - PAD * 2 - LOGIN_W - 4;
        float fy = inputY();
        RenderUtil.drawRect(ctx, fieldX, fy, fieldX + fieldW, fy + FIELD_H, rowBg());
        if (inputActive) {
            RenderUtil.drawOutline(ctx, fieldX, fy, fieldW, FIELD_H, 1, accent().getRGB());
        }
        float fieldTextY = fy + (FIELD_H - fm.getFontHeight()) / 2f + 0.5f;
        if (input.isEmpty()) {
            fm.drawStringWithShadow(ctx, "Никнейм...", fieldX + 5, fieldTextY, new Color(150, 150, 175, 140));
        } else {
            fm.drawStringWithShadow(ctx, input, fieldX + 5, fieldTextY, text());
        }
        if (inputActive && now % 1000 < 560) {
            float caretX = fieldX + 4 + fm.getStringWidth(input);
            RenderUtil.drawRect(ctx, caretX, fy + 4, caretX + 1, fy + FIELD_H - 4, text());
        }

        // Кнопка входа: акцентная, когда есть что вводить
        float loginX = wx + W - PAD - LOGIN_W;
        boolean hovLogin = inBounds(mx, my, loginX, fy, LOGIN_W, FIELD_H);
        boolean loginReady = !input.trim().isEmpty();
        RenderUtil.drawRect(ctx, loginX, fy, loginX + LOGIN_W, fy + FIELD_H,
                loginReady ? rowEnabled() : rowBg());
        if (hovLogin) {
            RenderUtil.drawRect(ctx, loginX, fy, loginX + LOGIN_W, fy + FIELD_H, new Color(255, 255, 255, 25));
        }
        String loginTxt = "Войти";
        fm.drawStringWithShadow(ctx, loginTxt,
                Math.round(loginX + (LOGIN_W - fm.getStringWidth(loginTxt)) / 2f), fieldTextY,
                loginReady ? enabledText() : text());

        // Список аккаунтов
        float lt = listTop();
        float lb = listBottom();
        ctx.enableScissor((int) (wx + 1), (int) lt, (int) (wx + W), (int) lb);
        {
            int idx0 = (int) Math.floor(scrollSmooth);
            float frac = scrollSmooth - idx0;
            float rowStep = rowH() + ROW_GAP;
            String curName = mc.getSession().getUsername();

            for (int i = idx0; i < alts.size(); i++) {
                float rowY = Math.round(lt + (i - idx0 - frac) * rowStep);
                if (rowY > lb) break;

                String alt = alts.get(i);
                boolean isCur = alt.equalsIgnoreCase(curName);
                boolean hovRow = inBounds(mx, my, wx + PAD, rowY, W - PAD * 2 - 14, rowH());
                boolean hovDel = inBounds(mx, my, wx + W - PAD - 14, rowY, 14, rowH());

                // Строка как кнопка модуля
                RenderUtil.drawRect(ctx, wx + PAD, rowY, wx + W - PAD, rowY + rowH(),
                        isCur ? rowEnabled() : rowBg());
                if (hovRow && !hovDel) {
                    RenderUtil.drawRect(ctx, wx + PAD, rowY, wx + W - PAD, rowY + rowH(),
                            new Color(255, 255, 255, 25));
                }

                // Плавный крестик удаления
                float dh = deleteHover.getOrDefault(alt, 0f);
                dh += ((hovDel ? 1f : 0f) - dh) * animStep();
                if (dh < 0.01f) dh = 0f;
                else if (dh > 0.99f) dh = 1f;
                deleteHover.put(alt, dh);
                if (dh > 0.01f) {
                    RenderUtil.drawRect(ctx, wx + W - PAD - 14, rowY, wx + W - PAD, rowY + rowH(),
                            new Color(255, 105, 110, (int) (45 * dh)));
                }

                // Точка-аватар (гладкий сканлайн-диск, не пиксельный блоб)
                GuiGfx.dot(ctx, wx + PAD + 7, rowY + rowH() / 2f, 2.5f, nicknameColor(alt).getRGB());

                // Ник (+ метка текущего)
                float nameX = wx + PAD + 13;
                String suffix = isCur ? "  current" : "";
                float maxNameW = (W - PAD * 2 - 14) - 13 - fm.getStringWidth(suffix) - 4;
                String label = alt;
                if (fm.getStringWidth(label) > maxNameW) {
                    while (fm.getStringWidth(label + "..") > maxNameW && label.length() > 2) {
                        label = label.substring(0, label.length() - 1);
                    }
                    label += "..";
                }
                float nameY = rowY + (rowH() - fm.getFontHeight()) / 2f + 0.5f;
                fm.drawStringWithShadow(ctx, label, nameX, nameY, isCur ? enabledText() : text());
                if (isCur) {
                    fm.drawStringWithShadow(ctx, suffix, nameX + fm.getStringWidth(label), nameY,
                            withAlpha(enabledText(), 140));
                }

                // Крестик
                String xTxt = "✕";
                float xw = fm.getStringWidth(xTxt);
                fm.drawStringWithShadow(ctx, xTxt,
                        wx + W - PAD - 14 + (14 - xw) / 2f, nameY,
                        blend(DELETE_IDLE, DELETE_HOVER, dh));
            }
        }
        ctx.disableScissor();

        // Пустой список
        if (alts.isEmpty()) {
            String l1 = "Нет сохранённых аккаунтов";
            String l2 = "Введи ник сверху и нажми «Войти»";
            float cy = lt + (lb - lt) / 2f;
            fm.drawStringWithShadow(ctx, l1, Math.round(wx + (W - fm.getStringWidth(l1)) / 2f), cy - 12, text());
            fm.drawStringWithShadow(ctx, l2, Math.round(wx + (W - fm.getStringWidth(l2)) / 2f), cy,
                    new Color(150, 150, 175, 160));
        }

        // Скроллбар как у панелей (ровно 2px, по целым пикселям)
        if (alts.size() > visibleRows()) {
            float trackH = lb - lt;
            float barH = Math.max(12, Math.round(trackH * (visibleRows() / (float) alts.size())));
            float travel = trackH - barH;
            float ratio = scrollMax <= 0 ? 0 : scrollSmooth / scrollMax;
            float barY = Math.round(lt + travel * ratio);
            float barX = wx + W - PAD - 3;
            RenderUtil.drawRect(ctx, barX, barY, barX + 2, barY + barH, new Color(255, 255, 255, 130));
        }

        // Статус
        if (!status.isEmpty() && now < statusUntil) {
            fm.drawStringWithShadow(ctx, status, Math.round(wx + (W - fm.getStringWidth(status)) / 2f),
                    footerY() - 11, enabledText());
        }

        // Футер: очистить / назад
        float ftY = footerY();
        float halfW = (W - PAD * 2 - 4) / 2f;
        float clearX = wx + PAD;
        float backX = clearX + halfW + 4;

        boolean hovClear = inBounds(mx, my, clearX, ftY, halfW, FOOTER_H);
        boolean hovBack = inBounds(mx, my, backX, ftY, halfW, FOOTER_H);

        RenderUtil.drawRect(ctx, clearX, ftY, clearX + halfW, ftY + FOOTER_H,
                clearArmed ? new Color(140, 30, 40, 220) : rowBg());
        if (hovClear && !clearArmed) {
            RenderUtil.drawRect(ctx, clearX, ftY, clearX + halfW, ftY + FOOTER_H, new Color(255, 255, 255, 25));
        }
        String clearTxt = clearArmed ? "Точно стереть?" : "Очистить список";
        fm.drawStringWithShadow(ctx, clearTxt,
                Math.round(clearX + (halfW - fm.getStringWidth(clearTxt)) / 2f),
                ftY + (FOOTER_H - fm.getFontHeight()) / 2f + 0.5f,
                clearArmed ? Color.WHITE : text());

        RenderUtil.drawRect(ctx, backX, ftY, backX + halfW, ftY + FOOTER_H, rowBg());
        if (hovBack) {
            RenderUtil.drawRect(ctx, backX, ftY, backX + halfW, ftY + FOOTER_H, new Color(255, 255, 255, 25));
        }
        String backTxt = "Назад";
        fm.drawStringWithShadow(ctx, backTxt,
                Math.round(backX + (halfW - fm.getStringWidth(backTxt)) / 2f),
                ftY + (FOOTER_H - fm.getFontHeight()) / 2f + 0.5f, text());

        // Рамка окна
        if (UI.INSTANCE != null && UI.INSTANCE.line.getValue()) {
            RenderUtil.drawOutline(ctx, wx - 1, wy - 1, W + 2, H + 2, 1, accent().getRGB());
        }

        super.render(ctx, mx, my, delta);
    }

    // ------------------------------------------------------------- Ввод

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x();
        double my = click.y();
        float wx = bx();
        float wy = by();

        // Заголовок — перетаскивание окна
        if (click.button() == 0 && inBounds(mx, my, wx, wy, W, HEADER_H)) {
            dragging = true;
            inputActive = false;
            return true;
        }

        // Поле ввода
        float fieldX = wx + PAD;
        float fieldW = W - PAD * 2 - LOGIN_W - 4;
        if (inBounds(mx, my, fieldX, inputY(), fieldW, FIELD_H)) {
            inputActive = true;
            if (sounds()) SoundUtil.playClick(1.0f, 1.4f);
            return true;
        }
        inputActive = false;

        // Кнопка входа
        if (inBounds(mx, my, wx + W - PAD - LOGIN_W, inputY(), LOGIN_W, FIELD_H)) {
            loginCurrentInput();
            return true;
        }

        // Строки аккаунтов
        float lt = listTop();
        float rowStep = rowH() + ROW_GAP;
        int idx0 = (int) Math.floor(scrollSmooth);
        float frac = scrollSmooth - idx0;
        for (int i = idx0; i < alts.size(); i++) {
            float rowY = Math.round(lt + (i - idx0 - frac) * rowStep);
            if (rowY > listBottom()) break;
            if (rowY + rowH() < lt) continue;

            if (inBounds(mx, my, wx + W - PAD - 14, rowY, 14, rowH())) {
                String removed = alts.remove(i);
                deleteHover.remove(removed);
                clampScroll();
                saveAlts();
                setStatus("Аккаунт " + removed + " удалён");
                if (sounds()) SoundUtil.playClick(1.0f, 0.9f);
                return true;
            }
            if (inBounds(mx, my, wx + PAD, rowY, W - PAD * 2 - 14, rowH())) {
                applySession(alts.get(i));
                setStatus("Вошли как " + alts.get(i));
                if (sounds()) SoundUtil.playClick(1.0f, 1.0f);
                return true;
            }
        }

        // Футер
        float ftY = footerY();
        float halfW = (W - PAD * 2 - 4) / 2f;
        float clearX = wx + PAD;
        float backX = clearX + halfW + 4;

        if (inBounds(mx, my, backX, ftY, halfW, FOOTER_H)) {
            if (sounds()) SoundUtil.playClick(1.0f, 1.2f);
            MinecraftClient.getInstance().setScreen(parent);
            return true;
        }
        if (inBounds(mx, my, clearX, ftY, halfW, FOOTER_H)) {
            if (clearArmed) {
                alts.clear();
                deleteHover.clear();
                clampScroll();
                saveAlts();
                clearArmed = false;
                setStatus("Список очищен");
                if (sounds()) SoundUtil.playClick(1.0f, 0.8f);
            } else {
                clearArmed = true;
                clearArmedUntil = System.currentTimeMillis() + 2500;
                if (sounds()) SoundUtil.playClick(1.0f, 1.0f);
            }
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0) dragging = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hScroll, double vScroll) {
        if (vScroll == 0) return super.mouseScrolled(mx, my, hScroll, vScroll);
        scroll = Math.max(0, Math.min(scroll - (float) vScroll,
                Math.max(0, alts.size() - visibleRows())));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (!inputActive) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                if (clearArmed) {
                    clearArmed = false;
                    return true;
                }
                MinecraftClient.getInstance().setScreen(parent);
                return true;
            }
            return super.keyPressed(input);
        }

        int key = input.key();
        long win = MinecraftClient.getInstance().getWindow().getHandle();
        boolean ctrl = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;

        if (ctrl && key == GLFW.GLFW_KEY_V) {
            String cb = GLFW.glfwGetClipboardString(win);
            if (cb != null) {
                for (char c : cb.toCharArray()) {
                    if (c >= ' ' && c != 127 && this.input.length() < 16) this.input += c;
                }
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            loginCurrentInput();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            this.inputActive = false;
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE && !this.input.isEmpty()) {
            this.input = this.input.substring(0, this.input.length() - 1);
        }
        return true;
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (this.inputActive && input.isValidChar() && this.input.length() < 16) {
            this.input += input.asString();
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (dragging) {
            winX += (float) deltaX;
            winY += (float) deltaY;
            clampWindow();
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------- Логика

    private void loginCurrentInput() {
        String nick = input.trim();
        if (nick.isEmpty()) return;
        applySession(nick);
        if (alts.stream().noneMatch(a -> a.equalsIgnoreCase(nick))) {
            alts.add(nick);
            clampScroll();
            saveAlts();
        }
        setStatus("Вошли как " + nick);
        input = "";
        inputActive = true;
        if (sounds()) SoundUtil.playClick(1.0f, 1.1f);
    }

    private void applySession(String username) {
        try {
            UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes());
            Session s = new Session(username, uuid, "0", Optional.empty(), Optional.empty());
            ((MixinMinecraftClient) MinecraftClient.getInstance()).setSession(s);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadAlts() {
        alts.clear();
        deleteHover.clear();
        try {
            if (Files.exists(ALTS_FILE)) {
                for (String line : Files.readAllLines(ALTS_FILE)) {
                    if (!line.isBlank()) alts.add(line.trim());
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void saveAlts() {
        try {
            Files.createDirectories(ALTS_FILE.getParent());
            Files.write(ALTS_FILE, alts);
        } catch (Exception ignored) {
        }
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, alts.size() - visibleRows())));
    }

    private void setStatus(String s) {
        status = s;
        statusUntil = System.currentTimeMillis() + 2400;
    }

    // ------------------------------------------------------------- Хелперы

    private static boolean inBounds(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static Color blend(Color a, Color b, float t) {
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    private static Color nicknameColor(String name) {
        Color[] palette = {
            new Color(70, 110, 245),
            new Color(20, 190, 140),
            new Color(240, 95, 35),
            new Color(160, 60, 190),
            new Color(250, 135, 20),
            new Color(55, 165, 80),
            new Color(35, 125, 220),
            new Color(120, 150, 255)
        };
        int hash = 0;
        for (char c : name.toCharArray()) hash = hash * 31 + c;
        return palette[Math.abs(hash) % palette.length];
    }
}

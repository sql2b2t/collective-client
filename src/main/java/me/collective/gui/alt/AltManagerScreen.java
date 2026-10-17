package me.collective.gui.alt;

import me.collective.font.FontManager;
import me.collective.gui.GuiGfx;
import me.collective.render.BlurEngine;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
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
import java.util.UUID;

@Environment(EnvType.CLIENT)
public class AltManagerScreen extends Screen {

    private final Screen parent;

    private List<String> alts = new ArrayList<>();
    private static final Path ALTS_FILE = net.fabricmc.loader.api.FabricLoader.getInstance()
            .getGameDir().resolve("collective").resolve("alts.txt");

    private String inputValue = "";
    private boolean inputActive = false;
    private int scrollOffset = 0;
    private float scrollSmooth = 0f;

    // Анимации
    private long lastFrameNanos;
    private long openedAt;
    private final Map<Integer, Float> rowHover = new HashMap<>();
    private float loginAnim, backAnim, clearAnim, fieldAnim;
    private String status = "";
    private long statusUntil;
    private boolean clearArmed = false;
    private long clearArmedUntil;

    // Палитра
    private static final Color BG_OVERLAY  = new Color(3, 3, 6, 150);
    private static final Color PANEL_BG    = new Color(13, 13, 19, 245);
    private static final Color HEADER_BG   = new Color(9, 9, 14, 255);
    private static final Color DIVIDER     = new Color(255, 255, 255, 14);
    private static final Color CARD_BG     = new Color(25, 25, 34, 255);
    private static final Color CARD_HOV    = new Color(34, 34, 47, 255);
    private static final Color CARD_CUR    = new Color(18, 34, 62, 255);
    private static final Color DEL_BG      = new Color(0, 0, 0, 0);
    private static final Color DEL_HOV     = new Color(205, 48, 62, 45);
    private static final Color INPUT_BG    = new Color(17, 17, 24, 255);
    private static final Color INPUT_ACT   = new Color(20, 22, 34, 255);
    private static final Color BTN_BG      = new Color(48, 86, 232, 255);
    private static final Color BTN_HOV     = new Color(72, 112, 255, 255);
    private static final Color BACK_BG     = new Color(28, 28, 38, 255);
    private static final Color BACK_HOV    = new Color(42, 42, 56, 255);
    private static final Color CLR_BG      = new Color(46, 20, 26, 255);
    private static final Color CLR_HOV     = new Color(84, 28, 38, 255);

    private static final Color TXT_PRI    = new Color(235, 235, 248);
    private static final Color TXT_SEC    = new Color(150, 150, 175);
    private static final Color TXT_HINT   = new Color(95, 95, 120);
    private static final Color TXT_CUR    = new Color(120, 180, 255);
    private static final Color TXT_RED    = new Color(255, 105, 110);
    private static final Color TXT_WHITE  = new Color(255, 255, 255);
    private static final Color ACCENT     = new Color(60, 100, 245);
    private static final Color ACCENT_LIT = new Color(120, 175, 255);
    private static final Color BORDER     = new Color(255, 255, 255, 16);
    private static final Color BORDER_ACT = new Color(60, 100, 245, 190);
    private static final Color SCROLL_BG  = new Color(255, 255, 255, 26);

    // Размеры
    private static final int PW = 360;
    private static final int PH = 430;
    private static final int R = 11;
    private static final int R2 = 7;
    private static final int MARGIN = 14;
    private static final int HEADER_H = 52;
    private static final int FIELD_H = 24;
    private static final int CARD_H = 28;
    private static final int CARD_GAP = 6;

    public AltManagerScreen(Screen parent) {
        super(Text.literal("Alt Manager"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        loadAlts();
        this.openedAt = System.currentTimeMillis();
        this.lastFrameNanos = System.nanoTime();
        this.scrollSmooth = 0;
        this.scrollOffset = 0;
    }

    //
    // Геометрия
    //
    private int px() { return (width - PW) / 2; }
    private int py() { return (height - PH) / 2; }
    private int inputY()   { return py() + HEADER_H + 14; }
    private int loginY()   { return inputY() + FIELD_H + 8; }
    private int listTop()  { return loginY() + FIELD_H + 14; }
    private int footerY()  { return py() + PH - 40; }
    private int listBottom() { return footerY() - 12; }
    private int visibleRows() {
        return Math.max(1, (listBottom() - listTop() + CARD_GAP) / (CARD_H + CARD_GAP));
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        List<float[]> regions = List.of(new float[]{ px(), py(), px() + PW, py() + PH });
        BlurEngine.INSTANCE.renderBlur(ctx, regions, 12f);
        ctx.fill(0, 0, width, height, BG_OVERLAY.getRGB());
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        long now = System.nanoTime();
        float dt = (now - this.lastFrameNanos) / 1.0e9f;
        this.lastFrameNanos = now;
        if (dt <= 0 || dt > 0.25f) dt = 1f / 60f;

        MinecraftClient mc = MinecraftClient.getInstance();
        FontManager fm = FontManager.INSTANCE;

        int px = px();
        int py = py();

        // Анимация появления панели: лёгкий сдвиг сверху вниз
        long sinceOpen = System.currentTimeMillis() - this.openedAt;
        float slide = Math.min(1f, sinceOpen / 220f);
        float eased = 1f - (1f - slide) * (1f - slide);
        float offsetY = (1f - eased) * -14f;
        py += offsetY;
        px = px();
        float pyF = py;

        // 1. Панель
        GuiGfx.fillRounded(ctx, px, (int) pyF, PW, PH, R, PANEL_BG.getRGB());
        GuiGfx.outlineRounded(ctx, px, (int) pyF, PW, PH, R, BORDER.getRGB());

        // 2. Хедер
        GuiGfx.fillRounded(ctx, px, (int) pyF, PW, HEADER_H, R, HEADER_BG.getRGB());

        // Акцентная полоска
        GuiGfx.fillRounded(ctx, px + MARGIN - 2, (int) pyF + 16, 3, 20, 1, ACCENT.getRGB());
        GuiGfx.fillRounded(ctx, px + MARGIN - 2, (int) pyF + 16, 1, 20, 0, ACCENT_LIT.getRGB());

        // Заголовок + подзаголовок
        fm.drawCustomString(ctx, "Alt Manager", px + MARGIN + 10, pyF + 15, TXT_PRI);
        String sub = alts.size() == 1 ? "1 аккаунт" : alts.size() + " аккаунтов";
        fm.drawCustomString(ctx, sub, px + MARGIN + 10, pyF + 30, TXT_SEC);

        // Текущий ник справа
        String curNick = mc.getSession().getUsername();
        float nickW = fm.getCustomStringWidth(curNick);
        float dotX = px + PW - nickW - 22;
        GuiGfx.dot(ctx, dotX, pyF + HEADER_H / 2f - 3, 3.2f, TXT_CUR.getRGB());
        fm.drawCustomString(ctx, curNick, dotX + 7, pyF + HEADER_H / 2f - 8, TXT_SEC);

        // Разделитель
        ctx.fill(px + 4, (int) (pyF + HEADER_H - 1), px + PW - 4, (int) (pyF + HEADER_H), DIVIDER.getRGB());

        // 3. Поле ввода
        int fieldX = px + MARGIN;
        int fieldW = PW - MARGIN * 2;
        int fy = (int) (inputY() + offsetY);
        boolean fieldHov = inBounds(mx, my, fieldX, fy, fieldW, FIELD_H) && !this.inputActive;

        float fieldTarget = this.inputActive ? 1f : (fieldHov ? 0.55f : 0f);
        this.fieldAnim += (fieldTarget - this.fieldAnim) * easing(dt, 14f);

        Color fieldBg = this.inputActive ? INPUT_ACT : INPUT_BG;
        Color fieldBd = mix(BORDER, BORDER_ACT, this.fieldAnim);

        GuiGfx.fillRounded(ctx, fieldX, fy, fieldW, FIELD_H, R2, fieldBg.getRGB());
        GuiGfx.outlineRounded(ctx, fieldX, fy, fieldW, FIELD_H, R2, fieldBd.getRGB());

        // Плейсхолдер / текст + каретка через мягкое мигание
        boolean caretOn = this.inputActive && System.currentTimeMillis() % 1000 < 560;
        float pad = 10;
        float maxW = fieldW - pad * 2 - 6;
        String displayed = this.inputValue;
        while (fm.getCustomStringWidth(displayed) > maxW && !displayed.isEmpty()) {
            displayed = displayed.substring(1);
        }
        if (this.inputActive && caretOn) displayed += "│";

        if (this.inputValue.isEmpty() && !this.inputActive) {
            fm.drawCustomString(ctx, "Введите никнейм...", fieldX + pad, fy + 8, TXT_HINT);
        } else {
            int headCut = this.inputValue.length() - displayed.length();
            String shown = displayed;
            if (caretOn && shown.endsWith("│")) shown = shown.substring(0, shown.length() - 1);
            float textX = fieldX + pad;
            if (headCut > 0) {
                String lead = "…";
                shown = headCut < this.inputValue.length() ? this.inputValue.substring(headCut) : shown;
                float leadW = fm.getCustomStringWidth(lead);
                fm.drawCustomString(ctx, lead, textX, fy + 8, TXT_HINT);
                textX += leadW + 1;
            }
            fm.drawCustomString(ctx, shown, textX, fy + 8, TXT_PRI);
        }

        // 4. Кнопка «Войти»
        int by = (int) (loginY() + offsetY);
        boolean btnHov = inBounds(mx, my, fieldX, by, fieldW, FIELD_H);
        this.loginAnim += ((btnHov ? 1f : 0f) - this.loginAnim) * easing(dt, 14f);

        GuiGfx.fillRounded(ctx, fieldX, by, fieldW, FIELD_H, R2, mix(BTN_BG, BTN_HOV, this.loginAnim).getRGB());
        // внутренняя верхняя подсветка
        ctx.fill(fieldX + 1, by + 1, fieldX + fieldW - 1, by + 4,
                new Color(255, 255, 255, (int) (26 + this.loginAnim * 22)).getRGB());

        String loginTxt = "Войти";
        float loginW = fm.getCustomStringWidth(loginTxt);
        fm.drawCustomString(ctx, loginTxt, fieldX + (fieldW - loginW) / 2f, by + 8, TXT_WHITE);

        // 5. Список
        int lt = (int) (listTop() + offsetY);
        float rowsMax = Math.max(0, alts.size() - visibleRows());
        this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, (int) rowsMax));
        this.scrollSmooth += (this.scrollOffset - this.scrollSmooth) * easing(dt, 18f);

        String curName = mc.getSession().getUsername();

        if (alts.isEmpty()) {
            String txt1 = "Нет сохранённых аккаунтов";
            String txt2 = "Введи ник сверху и нажми «Войти»";
            float w1 = fm.getCustomStringWidth(txt1);
            float w2 = fm.getCustomStringWidth(txt2);
            float cx = px + PW / 2f;
            float cy = lt + (listBottom() - listTop()) / 2f;
            GuiGfx.dotRing(ctx, cx, cy - 18, 11, new Color(70, 70, 100, 140).getRGB());
            GuiGfx.dot(ctx, cx, cy - 18, 4.5f, new Color(70, 70, 100, 180).getRGB());
            fm.drawCustomString(ctx, txt1, cx - w1 / 2f, cy + 2, TXT_SEC);
            fm.drawCustomString(ctx, txt2, cx - w2 / 2f, cy + 17, TXT_HINT);
        }

        // Клипуем зону списка)
        // Порядок отрисовки учитывает прозрачность панелей
        float scrollF = this.scrollSmooth;
        boolean scrolledClamped = scrollF > rowsMax;
        if (scrolledClamped) scrollF = rowsMax;

        ctx.enableScissor(px + 2, lt - 2, px + PW - 2, listBottom() + 4);
        {
            int idx0 = (int) Math.floor(scrollF);
            int visible = visibleRows() + 1;
            for (int i = idx0; i < alts.size() && i < idx0 + visible; i++) {
                float rowOff = (i - scrollF) * (CARD_H + CARD_GAP);
                float ayF = lt + 2 + rowOff;
                if (ayF > listBottom()) break;

                String alt = alts.get(i);
                boolean isCur = alt.equals(curName);
                float rowHoverT = this.rowHover.getOrDefault(i, 0f);
                boolean hovCard = inBounds(mx, my, fieldX, (int) ayF, fieldW - 30, CARD_H);
                float target = hovCard ? 1f : 0f;
                rowHoverT += (target - rowHoverT) * easing(dt, 16f);
                if (rowHoverT < 0.01f) this.rowHover.remove(i); else this.rowHover.put(i, rowHoverT);

                int cardX = fieldX;
                float delW = 24;
                float altW = fieldW - delW - 6;
                float delX = cardX + altW + 6;
                int ay = Math.round(ayF);

                // Карточка
                GuiGfx.fillRounded(ctx, cardX, (int) ayF, (int) altW, CARD_H, R2,
                        (isCur ? CARD_CUR : mix(CARD_BG, CARD_HOV, rowHoverT)).getRGB());
                if (isCur) {
                    GuiGfx.outlineRounded(ctx, cardX, (int) ayF, (int) altW, CARD_H, R2, ACCENT.getRGB());
                    // статусная полоска слева
                    GuiGfx.fillRounded(ctx, cardX + 4, (int) ayF + 8, 2, CARD_H - 17, 1, ACCENT_LIT.getRGB());
                } else if (rowHoverT > 0.02f) {
                    GuiGfx.outlineRounded(ctx, cardX, (int) ayF, (int) altW, CARD_H, R2,
                            new Color(255, 255, 255, (int) (rowHoverT * 40)).getRGB());
                }

                // Аватар: буква аккаунта в кружке
                Color avatarCol = mix(nicknameColor(alt), Color.WHITE, rowHoverT * 0.3f);
                float aCX = cardX + 15;
                float aCY = ayF + CARD_H / 2f;
                GuiGfx.dot(ctx, aCX, aCY, 7.5f, avatarCol.getRGB());
                GuiGfx.dotRing(ctx, aCX, aCY, 9.5f,
                        new Color(255, 255, 255, isCur ? 40 : (int) (rowHoverT * 30)).getRGB());

                String letter = String.valueOf(Character.toUpperCase(alt.charAt(0)));
                float letterW = fm.getCustomStringWidth(letter);
                fm.drawCustomString(ctx, letter, aCX - letterW / 2f, ay + 9, TXT_WHITE);

                // Ник + (ТЕКУЩИЙ)
                String label = alt;
                String suffix = isCur ? "  —  текущий" : "";
                float maxLabelW = altW - 44 - fm.getCustomStringWidth(suffix);
                if (fm.getCustomStringWidth(label) > maxLabelW) {
                    while (fm.getCustomStringWidth(label + "…") > maxLabelW && !label.isEmpty()) {
                        label = label.substring(0, label.length() - 1);
                    }
                    label += "…";
                }
                fm.drawCustomString(ctx, label, cardX + 30, ay + 9,
                        isCur ? TXT_CUR : (rowHoverT > 0.4f ? TXT_PRI : TXT_SEC));
                if (isCur) {
                    fm.drawCustomString(ctx, suffix, cardX + 30 + fm.getCustomStringWidth(label), ay + 9, TXT_SEC);
                }

                // Удалить
                boolean hovDel = inBounds(mx, my, delX, ay, (int) delW, CARD_H);
                String xTxt = "✕";
                float xW = fm.getCustomStringWidth(xTxt);
                if (hovDel) {
                    GuiGfx.fillRounded(ctx, (int) delX, ay, (int) delW, CARD_H, R2, DEL_HOV.getRGB());
                    GuiGfx.outlineRounded(ctx, (int) delX, ay, (int) delW, CARD_H, R2, TXT_RED.getRGB());
                    fm.drawCustomString(ctx, xTxt, delX + (delW - xW) / 2f, ay + 9, TXT_RED);
                } else {
                    Color dimX = new Color(150, 150, 175, 120);
                    fm.drawCustomString(ctx, xTxt, delX + (delW - xW) / 2f, ay + 9, dimX);
                }
            }
        }
        ctx.disableScissor();

        // Скроллбар
        if (alts.size() > visibleRows()) {
            int sbX = px + PW - 7;
            int trackY0 = lt + 2;
            int trackH = listBottom() - lt - 4;
            float barH = Math.max(16, (float) visibleRows() / alts.size() * trackH);
            float travel = trackH - barH;
            float ratio = rowsMax <= 0 ? 0 : scrollF / rowsMax;
            GuiGfx.fillRounded(ctx, sbX, trackY0, 3, trackH, 1, new Color(255, 255, 255, 10).getRGB());
            GuiGfx.fillRounded(ctx, sbX, (int) (trackY0 + travel * ratio), 3, (int) barH, 1, SCROLL_BG.getRGB());
        }

        // 6. Футер
        int ftrY = (int) (footerY() + offsetY);
        ctx.fill(px + 4, ftrY - 12, px + PW - 4, ftrY - 11, DIVIDER.getRGB());

        // Статус
        if (!this.status.isEmpty() && System.currentTimeMillis() < this.statusUntil) {
            float stW = fm.getCustomStringWidth(this.status);
            fm.drawCustomString(ctx, this.status, px + (PW - stW) / 2f, ftrY - 26, TXT_CUR);
        }

        int btnH = 24;
        int halfW = (PW - MARGIN * 2 - 8) / 2;
        int clearX = fieldX;
        int backX = clearX + halfW + 8;

        // Подтверждение очистки
        if (this.clearArmed && System.currentTimeMillis() > this.clearArmedUntil) this.clearArmed = false;

        boolean clrHov = inBounds(mx, my, clearX, ftrY, halfW, btnH);
        this.clearAnim += ((clrHov ? 1f : 0f) - this.clearAnim) * easing(dt, 14f);
        Color clrBase = this.clearArmed ? new Color(140, 30, 40, 255) : CLR_BG;
        GuiGfx.fillRounded(ctx, clearX, ftrY, halfW, btnH, R2, mix(clrBase, CLR_HOV, this.clearAnim).getRGB());
        GuiGfx.outlineRounded(ctx, clearX, ftrY, halfW, btnH, R2,
                (this.clearArmed ? TXT_RED : mix(new Color(255, 80, 80, 40), TXT_RED, this.clearAnim)).getRGB());
        String clearTxt = this.clearArmed ? "Точно стереть?" : "Очистить список";
        float clearW = fm.getCustomStringWidth(clearTxt);
        fm.drawCustomString(ctx, clearTxt, clearX + (halfW - clearW) / 2f, ftrY + 8,
                this.clearArmed ? TXT_WHITE : mix(new Color(220, 150, 155), TXT_RED, this.clearAnim));

        boolean backHov = inBounds(mx, my, backX, ftrY, halfW, btnH);
        this.backAnim += ((backHov ? 1f : 0f) - this.backAnim) * easing(dt, 14f);
        GuiGfx.fillRounded(ctx, backX, ftrY, halfW, btnH, R2, mix(BACK_BG, BACK_HOV, this.backAnim).getRGB());
        GuiGfx.outlineRounded(ctx, backX, ftrY, halfW, btnH, R2, BORDER.getRGB());
        String backTxt = "Назад";
        float backW = fm.getCustomStringWidth(backTxt);
        fm.drawCustomString(ctx, backTxt, backX + (halfW - backW) / 2f, ftrY + 8,
                mix(TXT_SEC, TXT_PRI, this.backAnim));

        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int px = px(), py = py();

        int fieldX = px + MARGIN;
        int fieldW = PW - MARGIN * 2;
        int fy = inputY();

        if (inBounds(mx, my, fieldX, fy, fieldW, FIELD_H)) {
            this.inputActive = true;
            return true;
        }
        this.inputActive = false;

        int by = loginY();
        if (inBounds(mx, my, fieldX, by, fieldW, FIELD_H)) {
            loginCurrentInput();
            return true;
        }

        int lt = listTop();
        float delW = 24;
        float altW = fieldW - delW - 6;

        for (int i = this.scrollOffset; i < alts.size(); i++) {
            int idx = i - this.scrollOffset;
            if (idx >= visibleRows()) break;
            int ay = lt + idx * (CARD_H + CARD_GAP);
            float delX = fieldX + altW + 6;

            if (inBounds(mx, my, delX, ay, (int) delW, CARD_H)) {
                alts.remove(i);
                clampScroll();
                saveAlts();
                setStatus("Аккаунт удалён");
                return true;
            }
            if (inBounds(mx, my, fieldX, ay, (int) altW, CARD_H)) {
                applySession(alts.get(i));
                setStatus("Вошли как " + alts.get(i));
                return true;
            }
        }

        int ftrY = footerY();
        int halfW = (fieldW - 8) / 2;
        int clearX = fieldX;
        int backX = clearX + halfW + 8;

        if (inBounds(mx, my, backX, ftrY, halfW, 24)) {
            MinecraftClient.getInstance().setScreen(parent);
            return true;
        }
        if (inBounds(mx, my, clearX, ftrY, halfW, 24)) {
            if (this.clearArmed) {
                alts.clear();
                clampScroll();
                saveAlts();
                this.clearArmed = false;
                setStatus("Список очищен");
            } else {
                this.clearArmed = true;
                this.clearArmedUntil = System.currentTimeMillis() + 2500;
            }
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hScroll, double vScroll) {
        int delta = vScroll > 0 ? -1 : 1;
        this.scrollOffset = Math.max(0, Math.min(this.scrollOffset + delta,
                Math.max(0, alts.size() - visibleRows())));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (!this.inputActive) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                if (this.clearArmed) {
                    this.clearArmed = false;
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
            if (cb != null) for (char c : cb.toCharArray())
                if (c >= ' ' && c != 127 && this.inputValue.length() < 16) this.inputValue += c;
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
        if (key == GLFW.GLFW_KEY_BACKSPACE && !this.inputValue.isEmpty()) {
            this.inputValue = this.inputValue.substring(0, this.inputValue.length() - 1);
        }
        return true;
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (this.inputActive && input.isValidChar() && this.inputValue.length() < 16) {
            this.inputValue += input.asString();
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    //
    // Логика
    //

    private void loginCurrentInput() {
        String nick = this.inputValue.trim();
        if (nick.isEmpty()) return;
        applySession(nick);
        if (!alts.contains(nick)) {
            alts.add(nick);
            clampScroll();
            saveAlts();
        }
        setStatus("Вошли как " + nick);
        this.inputValue = "";
        this.inputActive = false;
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
        try {
            if (Files.exists(ALTS_FILE)) {
                alts = new ArrayList<>(Files.readAllLines(ALTS_FILE).stream()
                        .filter(s -> !s.isBlank()).toList());
            }
        } catch (Exception ignored) {}
    }

    private void saveAlts() {
        try {
            Files.createDirectories(ALTS_FILE.getParent());
            Files.write(ALTS_FILE, alts);
        } catch (Exception ignored) {}
    }

    private void clampScroll() {
        this.scrollOffset = Math.max(0, Math.min(this.scrollOffset,
                Math.max(0, alts.size() - visibleRows())));
    }

    private void setStatus(String s) {
        this.status = s;
        this.statusUntil = System.currentTimeMillis() + 2400;
    }

    //
    // Хелперы
    //

    private static float easing(float dt, float speed) {
        return (float) (1 - Math.exp(-speed * dt));
    }

    private static Color mix(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = Math.round(a.getRed() * (1 - t) + b.getRed() * t);
        int g = Math.round(a.getGreen() * (1 - t) + b.getGreen() * t);
        int bl = Math.round(a.getBlue() * (1 - t) + b.getBlue() * t);
        int al = Math.round(a.getAlpha() * (1 - t) + b.getAlpha() * t);
        return new Color(r, g, bl, al);
    }

    private static boolean inBounds(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static boolean inBounds(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
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

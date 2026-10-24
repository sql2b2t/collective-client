package me.collective.gui.alt;

import com.mojang.authlib.GameProfile;
import me.collective.font.ModernFonts;
import me.collective.mixin.MixinMinecraftClient;
import me.collective.module.client.UI;
import me.collective.render.SoundUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.User;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;

/**
 * Высокопроизводительный Glassmorphic Alt Manager с поддержкой Microsoft OAuth 2.0 (Device Code Flow),
 * офлайн-профилей, мгновенного генератора ников и отзывчивого адаптивного дизайна.
 */
@Environment(EnvType.CLIENT)
public class AltManagerScreen extends Screen {

    private static final Path ALTS_FILE = FabricLoader.getInstance()
            .getGameDir().resolve("collective").resolve("alts.txt");

    private static final Identifier BG_TEXTURE = Identifier.of("collective", "textures/gui/alt_background.png");

    private static final int BG_MODAL = 0xDE141822;
    private static final int BORDER_MODAL = 0x2AFFFFFF;
    private static final int ACCENT_ICE = 0xFFA8E1FF;
    private static final int CARD_BG = 0x14FFFFFF;
    private static final int CARD_HOVER = 0x22FFFFFF;
    private static final int CARD_BORDER = 0x18FFFFFF;
    private static final int STATUS_ACTIVE = 0xFF2ECC71;
    private static final int STATUS_IDLE = 0xFF9AA6B2;

    private static final float CARD_H = 36f;
    private static final float CARD_GAP = 6f;

    private static final String[] RANDOM_PREFIXES = {
        "Shadow", "Frost", "Ghost", "Cyber", "Echo", "Nova", "Vortex", "Apex",
        "Phantom", "Rogue", "Zenith", "Hyper", "Solar", "Lunar", "Matrix", "Kitsune",
        "Astral", "Vesper", "Inferno", "Titan", "Specter", "Zero", "Pulse", "Quantum",
        "Nexus", "Storm", "Blaze", "Omega", "Glitch", "Sniper", "Raven", "Cobra"
    };

    private static final String[] RANDOM_SUFFIXES = {
        "_PvP", "_2b", "X", "99", "_", "77", "_GG", "01", "_god", "Pro", "7", "_dev"
    };

    public enum ModalType {
        NONE, ADD_ACCOUNT, DIRECT_LOGIN, MS_DEVICE_POLLING
    }

    public static class AltEntry {
        public String username;
        public String type; // "Microsoft" или "Offline"
        public UUID uuid;
        public String refreshToken = "";
        public String mcToken = "";
        private Supplier<SkinTextures> skinSupplier;

        public AltEntry(String username, String type) {
            this.username = username;
            this.type = "Microsoft".equalsIgnoreCase(type) ? "Microsoft" : "Offline";
            this.uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
        }

        public AltEntry(String username, String type, UUID uuid, String refreshToken, String mcToken) {
            this.username = username;
            this.type = "Microsoft".equalsIgnoreCase(type) ? "Microsoft" : "Offline";
            this.uuid = uuid != null ? uuid : UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
            this.refreshToken = refreshToken != null ? refreshToken : "";
            this.mcToken = mcToken != null ? mcToken : "";
        }

        public SkinTextures getSkin() {
            if (skinSupplier == null) {
                try {
                    skinSupplier = Minecraft.getInstance().getSkinProvider()
                            .supplySkinTextures(new GameProfile(uuid, username), true);
                } catch (Exception ignored) {
                }
            }
            if (skinSupplier != null) {
                try {
                    SkinTextures textures = skinSupplier.get();
                    if (textures != null) return textures;
                } catch (Exception ignored) {
                }
            }
            return DefaultSkinHelper.getSkinTextures(uuid);
        }
    }

    private final Screen parent;
    private final List<AltEntry> alts = new ArrayList<>();

    private int selectedIndex = 0;
    private String searchQuery = "";
    private boolean searchActive = false;

    private float scroll = 0f;
    private float scrollSmooth = 0f;

    private ModalType modal = ModalType.NONE;
    private String modalInput = "";
    private String modalTypeChoice = "Offline";
    private boolean modalInputActive = true;

    private MicrosoftAuth.DeviceCode msDeviceCode;
    private String msAuthStatus = "";

    private String statusMsg = "";
    private long statusUntil = 0;

    public AltManagerScreen(Screen parent) {
        super(Component.literal("Менеджер аккаунтов"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ModernFonts.ensureInitialized();
        SmoothGfx.ensureInitialized();
        loadAlts();
    }

    private float getPanelWidth() {
        return Math.min(width - 32, 580f);
    }

    private float getPanelHeight() {
        return Math.min(height - 32, 380f);
    }

    private float getPanelX() {
        return (width - getPanelWidth()) / 2f;
    }

    private float getPanelY() {
        return (height - getPanelHeight()) / 2f;
    }

    private List<AltEntry> getFilteredAlts() {
        if (searchQuery.trim().isEmpty()) {
            return alts;
        }
        String q = searchQuery.trim().toLowerCase();
        List<AltEntry> filtered = new ArrayList<>();
        for (AltEntry a : alts) {
            if (a.username.toLowerCase().contains(q) || a.type.toLowerCase().contains(q)) {
                filtered.add(a);
            }
        }
        return filtered;
    }

    // ============================================================= Render

    @Override
    public void renderBackground(GuiGraphics ctx, int mx, int my, float delta) {
        renderAtmosphericBackground(ctx);
    }

    private void renderAtmosphericBackground(GuiGraphics ctx) {
        if (width <= 0 || height <= 0) return;

        float rScreen = (float) width / (float) Math.max(1, height);
        float rTex = 1376f / 768f;
        float u = 0f;
        float v = 0f;
        int regW = 1376;
        int regH = 768;

        if (rScreen > rTex) {
            float cropH = 1376f / rScreen;
            v = Math.max(0f, (768f - cropH) / 2f);
            regH = Math.min(768, Math.max(1, (int) cropH));
        } else {
            float cropW = 768f * rScreen;
            u = Math.max(0f, (1376f - cropW) / 2f);
            regW = Math.min(1376, Math.max(1, (int) cropW));
        }

        try {
            ctx.drawTexture(
                    RenderPipelines.GUI_TEXTURED,
                    BG_TEXTURE,
                    0, 0,
                    u, v,
                    width, height,
                    regW, regH,
                    1376, 768,
                    0xFFFFFFFF
            );
        } catch (Throwable ignored) {
            ctx.fill(0, 0, width, height, 0xFF141923);
        }

        ctx.fill(0, 0, width, height, 0x33000000);
    }

    @Override
    public void render(GuiGraphics ctx, int mx, int my, float delta) {
        renderBackground(ctx, mx, my, delta);

        float px = getPanelX();
        float py = getPanelY();
        float pw = getPanelWidth();
        float ph = getPanelHeight();

        SmoothGfx.softGlow(ctx, px, py, pw, ph, 14f, 0x55000000, 16f, 6f);
        SmoothGfx.fillAndStroke(ctx, px, py, pw, ph, 14f, BG_MODAL, BORDER_MODAL, 1.0f);

        renderHeader(ctx, px, py, pw, mx, my);
        renderCardsList(ctx, px, py, pw, ph, mx, my);
        renderBottomBar(ctx, px, py, pw, ph, mx, my);

        if (!statusMsg.isEmpty() && System.currentTimeMillis() < statusUntil) {
            renderStatusToast(ctx, px, py, pw, ph);
        }

        if (modal != ModalType.NONE) {
            renderModalPopup(ctx, px, py, pw, ph, mx, my);
        }

        super.render(ctx, mx, my, delta);
    }

    // ------------------------------------------------------------- Header

    private void renderHeader(GuiGraphics ctx, float px, float py, float pw, int mx, int my) {
        ModernFonts.fontTitle.drawStringWithShadow(ctx, "Менеджер аккаунтов", px + 16f, py + 12f, 0xFFFFFFFF);

        String counterText = "Всего: " + alts.size();
        ModernFonts.fontBadge.drawString(ctx, counterText, px + 17f, py + 32f, 0x88FFFFFF);

        renderCurrentProfileBadge(ctx, px, py, pw);
        renderSearchBar(ctx, px, py, pw, mx, my);
    }

    private void renderCurrentProfileBadge(GuiGraphics ctx, float px, float py, float pw) {
        String curName = client.getUser().getUsername();
        UUID curUuid = client.getUser().getUuidOrNull();
        for (AltEntry a : alts) {
            if (a.username.equalsIgnoreCase(curName)) {
                if (a.uuid != null) curUuid = a.uuid;
                break;
            }
        }

        float nameW = ModernFonts.fontName.getStringWidth(curName);
        float statusW = ModernFonts.fontBadge.getStringWidth("В сети");
        float badgeH = 26f;
        float badgeW = 6f + 16f + 6f + nameW + 8f + 4f + 6f + statusW + 8f;

        float badgeX = px + (pw - badgeW) / 2f;
        float badgeY = py + 11f;

        float titleRight = px + 16f + ModernFonts.fontTitle.getStringWidth("Менеджер аккаунтов") + 12f;
        float searchLeft = px + pw - 16f - 125f - 12f;
        if (badgeX < titleRight) badgeX = titleRight;
        if (badgeX + badgeW > searchLeft) badgeX = searchLeft - badgeW;

        // Rounded badge container
        SmoothGfx.fillAndStroke(ctx, badgeX, badgeY, badgeW, badgeH, 6f, 0x1AFFFFFF, 0x22FFFFFF, 1.0f);

        // 3D Avatar (size 16, centered vertically in 26px badge)
        float avX = badgeX + 5f;
        float avY = badgeY + 5f;
        SmoothGfx.fillRounded(ctx, avX - 1f, avY - 1f, 18f, 18f, 3f, 0x22000000);
        AltSkinHelper.drawSkinHead(ctx, curName, curUuid, (int) avX, (int) avY, 16);
        SmoothGfx.strokeRounded(ctx, avX - 1f, avY - 1f, 18f, 18f, 3f, 1.0f, 0x33FFFFFF);

        // Username (vertically centered)
        float nameX = badgeX + 26f;
        float nameY = badgeY + (badgeH - ModernFonts.fontName.getFontHeight()) / 2f;
        ModernFonts.fontName.drawString(ctx, curName, nameX, nameY, 0xFFFFFFFF);

        // Active status indicator: pulsing emerald dot + text "В сети"
        float dotX = nameX + nameW + 8f;
        float dotY = badgeY + badgeH / 2f;
        float pulse = (float) (Math.sin(System.currentTimeMillis() / 300.0) * 0.5 + 0.5);
        int glowAlpha = Math.round(0x22 + pulse * 0x33);
        SmoothGfx.circle(ctx, dotX, dotY, 2.8f + pulse * 1.0f, (glowAlpha << 24) | 0x002ECC71);
        SmoothGfx.circle(ctx, dotX, dotY, 1.8f, STATUS_ACTIVE);
        ModernFonts.fontBadge.drawString(ctx, "В сети", dotX + 6f, dotY - ModernFonts.fontBadge.getFontHeight() / 2f, STATUS_ACTIVE);
    }

    private void renderSearchBar(GuiGraphics ctx, float px, float py, float pw, int mx, int my) {
        float sw = 125f;
        float sh = 20f;
        float sx = px + pw - 16f - sw;
        float sy = py + 14f;

        boolean hov = inBounds(mx, my, sx, sy, sw, sh);
        int bg = searchActive ? 0x2AFFFFFF : (hov ? 0x20FFFFFF : 0x15FFFFFF);
        int border = searchActive ? ACCENT_ICE : 0x18FFFFFF;
        SmoothGfx.fillAndStroke(ctx, sx, sy, sw, sh, sh * 0.5f, bg, border, 1.0f);

        SmoothGfx.drawSearchIcon(ctx, sx + 6.5f, sy + 5.5f, 9f, searchActive ? 0xFFFFFFFF : 0x88FFFFFF);

        float textX = sx + 20f;
        float textY = sy + (sh - ModernFonts.fontSearch.getFontHeight()) / 2f;

        if (searchQuery.isEmpty() && !searchActive) {
            ModernFonts.fontSearch.drawString(ctx, "Поиск...", textX, textY, 0x66FFFFFF);
        } else {
            String visible = searchQuery;
            float maxW = sw - (searchQuery.isEmpty() ? 26f : 36f);
            while (ModernFonts.fontSearch.getStringWidth(visible) > maxW && visible.length() > 1) {
                visible = visible.substring(0, visible.length() - 1);
            }
            ModernFonts.fontSearch.drawString(ctx, visible, textX, textY, 0xFFFFFFFF);

            if (searchActive && (System.currentTimeMillis() % 1000 < 500)) {
                float caretX = textX + ModernFonts.fontSearch.getStringWidth(visible) + 1f;
                SmoothGfx.fillRounded(ctx, caretX, sy + 4f, 1f, sh - 8f, 0.5f, 0xFFFFFFFF);
            }

            if (!searchQuery.isEmpty()) {
                float clrX = sx + sw - 14f;
                boolean hovClr = inBounds(mx, my, clrX - 2f, sy + 2f, 12f, sh - 4f);
                ModernFonts.fontBadge.drawString(ctx, "×", clrX, sy + (sh - ModernFonts.fontBadge.getFontHeight()) / 2f, hovClr ? 0xFFFFFFFF : 0x77FFFFFF);
            }
        }
    }

    // ------------------------------------------------------------- Cards List

    private void renderCardsList(GuiGraphics ctx, float px, float py, float pw, float ph, int mx, int my) {
        List<AltEntry> filtered = getFilteredAlts();

        float listTop = py + 48f;
        float listBottom = py + ph - 44f;
        float cardW = pw - 32f;

        int maxRows = Math.max(0, filtered.size() - 6);
        scroll = Math.max(0, Math.min(scroll, maxRows * (CARD_H + CARD_GAP)));
        scrollSmooth += (scroll - scrollSmooth) * 0.35f;
        if (Math.abs(scrollSmooth - scroll) < 0.1f) scrollSmooth = scroll;

        if (filtered.isEmpty()) {
            renderEmptyListPlaceholder(ctx, px, pw, listTop);
            return;
        }

        ctx.enableScissor((int) (px + 10), (int) listTop, (int) (px + pw - 10), (int) listBottom);

        String currentSession = client.getUser().getUsername();

        for (int i = 0; i < filtered.size(); i++) {
            AltEntry alt = filtered.get(i);
            float cardY = listTop + 2f + i * (CARD_H + CARD_GAP) - scrollSmooth;
            if (cardY + CARD_H < listTop || cardY > listBottom) continue;

            float cardX = px + 16f;
            boolean isSelected = (i == selectedIndex);
            boolean isCurrent = alt.username.equalsIgnoreCase(currentSession);
            boolean hovCard = inBounds(mx, my, cardX, cardY, cardW, CARD_H) && my >= listTop && my <= listBottom;

            renderCard(ctx, alt, cardX, cardY, cardW, isSelected, isCurrent, hovCard, mx, my);
        }

        ctx.disableScissor();
    }

    private void renderEmptyListPlaceholder(GuiGraphics ctx, float px, float pw, float listTop) {
        if (searchQuery.trim().isEmpty()) {
            String empty1 = "Список аккаунтов пуст";
            String empty2 = "Нажмите «+ Добавить» или «Случайный» для быстрого старта";
            float e1X = px + (pw - ModernFonts.fontName.getStringWidth(empty1)) / 2f;
            float e2X = px + (pw - ModernFonts.fontBadge.getStringWidth(empty2)) / 2f;
            ModernFonts.fontName.drawString(ctx, empty1, e1X, listTop + 60f, 0x99FFFFFF);
            ModernFonts.fontBadge.drawString(ctx, empty2, e2X, listTop + 78f, 0x55FFFFFF);
        } else {
            String empty1 = "Ничего не найдено по запросу «" + searchQuery + "»";
            String empty2 = "Нажмите Esc или очистите строку поиска";
            float e1X = px + (pw - ModernFonts.fontName.getStringWidth(empty1)) / 2f;
            float e2X = px + (pw - ModernFonts.fontBadge.getStringWidth(empty2)) / 2f;
            ModernFonts.fontName.drawString(ctx, empty1, e1X, listTop + 60f, 0x99FFFFFF);
            ModernFonts.fontBadge.drawString(ctx, empty2, e2X, listTop + 78f, 0x55FFFFFF);
        }
    }

    private void renderCard(GuiGraphics ctx, AltEntry alt, float cx, float cy, float cw,
                            boolean selected, boolean isCurrent, boolean hovered, int mx, int my) {
        if (selected) {
            SmoothGfx.strokeRounded(ctx, cx - 1.2f, cy - 1.2f, cw + 2.4f, CARD_H + 2.4f, 9f, 1.0f, 0x33A8E1FF);
        }

        int bgCol = selected ? 0x24426582 : (hovered ? CARD_HOVER : CARD_BG);
        int borderCol = selected ? ACCENT_ICE : CARD_BORDER;
        SmoothGfx.fillAndStroke(ctx, cx, cy, cw, CARD_H, 8f, bgCol, borderCol, selected ? 1.2f : 1.0f);

        // 3D Avatar + Head border ring
        AltSkinHelper.drawSkinHead(ctx, alt.username, alt.uuid, (int) (cx + 6f), (int) (cy + 6f), 24);
        SmoothGfx.strokeRounded(ctx, cx + 5.5f, cy + 5.5f, 25f, 25f, 3f, 1.0f, 0x22FFFFFF);

        // Username
        ModernFonts.fontName.drawStringWithShadow(ctx, alt.username, cx + 38f, cy + 6f, 0xFFFFFFFF);

        // Badge: Microsoft / Offline
        renderTypeBadge(ctx, alt.type, cx + 38f, cy + 21f);

        // Status: Активен / В запасе
        renderStatusIndicator(ctx, isCurrent, cx + cw - 120f, cy + 18f);

        // Quick action buttons on hover
        renderCardHoverActions(ctx, cx, cy, cw, isCurrent, mx, my);
    }

    private void renderTypeBadge(GuiGraphics ctx, String type, float bx, float by) {
        boolean isMs = "Microsoft".equalsIgnoreCase(type);
        String label = isMs ? "Microsoft" : "Офлайн";
        float bw = ModernFonts.fontBadge.getStringWidth(label) + 8f;
        float bh = 11f;

        int bg = isMs ? 0x350078D4 : 0x1EFFFFFF;
        int border = isMs ? 0x4485C2F2 : 0x15FFFFFF;
        int textCol = isMs ? 0xFF85C2F2 : 0xFFCAD5E0;
        SmoothGfx.fillAndStroke(ctx, bx, by, bw, bh, 3f, bg, border, 1.0f);
        ModernFonts.fontBadge.drawString(ctx, label, bx + 4f, by + (bh - ModernFonts.fontBadge.getFontHeight()) / 2f, textCol);
    }

    private void renderStatusIndicator(GuiGraphics ctx, boolean isCurrent, float sx, float sy) {
        if (isCurrent) {
            float pulse = (float) (Math.sin(System.currentTimeMillis() / 300.0) * 0.5 + 0.5);
            int glowAlpha = Math.round(0x22 + pulse * 0x33);
            SmoothGfx.circle(ctx, sx, sy, 3.2f + pulse * 1.2f, (glowAlpha << 24) | 0x002ECC71);
            SmoothGfx.circle(ctx, sx, sy, 2.0f, STATUS_ACTIVE);
            ModernFonts.fontStatus.drawStringWithShadow(ctx, "Активен", sx + 7f, sy - ModernFonts.fontStatus.getFontHeight() / 2f, STATUS_ACTIVE);
        } else {
            SmoothGfx.circle(ctx, sx, sy, 2.0f, STATUS_IDLE);
            ModernFonts.fontStatus.drawString(ctx, "В запасе", sx + 6f, sy - ModernFonts.fontStatus.getFontHeight() / 2f, STATUS_IDLE);
        }
    }

    private void renderCardHoverActions(GuiGraphics ctx, float cx, float cy, float cw,
                                        boolean isCurrent, int mx, int my) {
        // Delete button
        float delX = cx + cw - 20f;
        float delY = cy + (CARD_H - ModernFonts.fontTabs.getFontHeight()) / 2f;
        boolean hovDel = inBounds(mx, my, delX - 2, delY - 2, 14, 14);
        ModernFonts.fontTabs.drawString(ctx, "×", delX, delY, hovDel ? 0xFFFF5555 : 0x77FFFFFF);

        // Fast Login button
        if (!isCurrent) {
            float logW = 38f;
            float logH = 16f;
            float logX = cx + cw - 64f;
            float logY = cy + (CARD_H - logH) / 2f;
            boolean hovLog = inBounds(mx, my, logX, logY, logW, logH);

            int btnBg = hovLog ? 0x55A8E1FF : 0x22FFFFFF;
            SmoothGfx.fillAndStroke(ctx, logX, logY, logW, logH, 4f, btnBg, 0x33A8E1FF, 1.0f);
            ModernFonts.fontBadge.drawString(ctx, "Войти", logX + (logW - ModernFonts.fontBadge.getStringWidth("Войти")) / 2f,
                    logY + (logH - ModernFonts.fontBadge.getFontHeight()) / 2f, 0xFFFFFFFF);
        }
    }

    // ------------------------------------------------------------- Bottom Bar

    private void renderBottomBar(GuiGraphics ctx, float px, float py, float pw, float ph, int mx, int my) {
        float barY = py + ph - 38f;
        float barH = 20f;
        float gap = 5f;
        float x = px + 16f;

        // 1. + Добавить
        float wAdd = 74f;
        renderButton(ctx, x, barY, wAdd, barH, "+ Добавить", true, inBounds(mx, my, x, barY, wAdd, barH));
        x += wAdd + gap;

        // 2. Случайный
        float wRand = 74f;
        renderButton(ctx, x, barY, wRand, barH, "Случайный", false, inBounds(mx, my, x, barY, wRand, barH));
        x += wRand + gap;

        // 3. Прямой вход
        float wDirect = 82f;
        renderButton(ctx, x, barY, wDirect, barH, "Прямой вход", false, inBounds(mx, my, x, barY, wDirect, barH));

        // Right side buttons
        float rx = px + pw - 16f;

        // Назад
        float wBack = 52f;
        rx -= wBack;
        renderButton(ctx, rx, barY, wBack, barH, "Назад", false, inBounds(mx, my, rx, barY, wBack, barH));

        // Удалить
        float wDel = 60f;
        rx -= (wDel + gap);
        renderButton(ctx, rx, barY, wDel, barH, "Удалить", false, inBounds(mx, my, rx, barY, wDel, barH));

        // Войти
        float wLog = 56f;
        rx -= (wLog + gap);
        boolean hasSelection = selectedIndex >= 0 && selectedIndex < getFilteredAlts().size();
        renderButton(ctx, rx, barY, wLog, barH, "Войти", hasSelection, inBounds(mx, my, rx, barY, wLog, barH));

        // Footer Hints
        renderFooterHints(ctx, px, py, pw, ph);
    }

    private void renderFooterHints(GuiGraphics ctx, float px, float py, float pw, float ph) {
        String hints = "[↑/↓] Выбор   •   [Enter] Войти   •   [Del] Удалить   •   [Esc] Назад";
        float hw = ModernFonts.fontBadge.getStringWidth(hints);
        float hx = px + (pw - hw) / 2f;
        float hy = py + ph - 13f;
        ModernFonts.fontBadge.drawString(ctx, hints, hx, hy, 0x66FFFFFF);
    }

    private void renderButton(GuiGraphics ctx, float bx, float by, float bw, float bh,
                              String text, boolean primary, boolean hovered) {
        int bg = primary
                ? (hovered ? 0xFFFFFFFF : 0xFFE8EEF5)
                : (hovered ? 0x38FFFFFF : 0x1EFFFFFF);
        int border = primary ? 0x00000000 : 0x24FFFFFF;
        int textCol = primary ? 0xFF141822 : 0xFFFFFFFF;

        SmoothGfx.fillAndStroke(ctx, bx, by, bw, bh, bh * 0.5f, bg, border, 1.0f);
        float tw = ModernFonts.fontBtn.getStringWidth(text);
        ModernFonts.fontBtn.drawString(ctx, text, bx + (bw - tw) / 2f, by + (bh - ModernFonts.fontBtn.getFontHeight()) / 2f, textCol);
    }

    // ------------------------------------------------------------- Modals & Toasts

    private void renderStatusToast(GuiGraphics ctx, float px, float py, float pw, float ph) {
        float tw = ModernFonts.fontStatus.getStringWidth(statusMsg) + 20f;
        float th = 18f;
        float tx = px + (pw - tw) / 2f;
        float ty = py + ph - 58f;

        SmoothGfx.fillAndStroke(ctx, tx, ty, tw, th, 9f, 0xF0181F2B, ACCENT_ICE, 1.0f);
        ModernFonts.fontStatus.drawStringWithShadow(ctx, statusMsg, tx + 10f, ty + (th - ModernFonts.fontStatus.getFontHeight()) / 2f, 0xFFEBF3FB);
    }

    private void renderModalPopup(GuiGraphics ctx, float px, float py, float pw, float ph, int mx, int my) {
        SmoothGfx.fillRounded(ctx, px, py, pw, ph, 14f, 0xCC090C12);

        float mw = 250f;
        float mh = modal == ModalType.MS_DEVICE_POLLING ? 146f : (modal == ModalType.ADD_ACCOUNT ? 140f : 112f);
        float mxPos = px + (pw - mw) / 2f;
        float myPos = py + (ph - mh) / 2f;

        SmoothGfx.softGlow(ctx, mxPos, myPos, mw, mh, 12f, 0x55000000, 16f, 4f);
        SmoothGfx.fillAndStroke(ctx, mxPos, myPos, mw, mh, 12f, 0xF6181D26, 0x33FFFFFF, 1.0f);

        if (modal == ModalType.MS_DEVICE_POLLING) {
            renderMsPollingModal(ctx, mxPos, myPos, mw, mh, mx, my);
            return;
        }

        String title = modal == ModalType.ADD_ACCOUNT ? "Добавить аккаунт" : "Прямой вход";
        ModernFonts.fontName.drawStringWithShadow(ctx, title, mxPos + 16f, myPos + 12f, 0xFFFFFFFF);

        float curY = myPos + 30f;
        if (modal == ModalType.ADD_ACCOUNT) {
            curY = renderModalTypeSelector(ctx, mxPos, curY, mw, mx, my);
        }

        if (modal == ModalType.ADD_ACCOUNT && "Microsoft".equalsIgnoreCase(modalTypeChoice)) {
            renderMicrosoftAddPrompt(ctx, mxPos, curY, mw, mx, my);
        } else {
            renderOfflineInputPrompt(ctx, mxPos, curY, mw, mx, my);
        }
    }

    private float renderModalTypeSelector(GuiGraphics ctx, float mxPos, float curY, float mw, int mx, int my) {
        String[] types = {"Microsoft", "Офлайн"};
        float pillW = (mw - 32f - 6f) / 2f;

        for (int i = 0; i < types.length; i++) {
            String t = types[i];
            float pX = mxPos + 16f + i * (pillW + 6f);
            boolean sel = ("Microsoft".equalsIgnoreCase(t) && "Microsoft".equalsIgnoreCase(modalTypeChoice))
                    || ("Офлайн".equalsIgnoreCase(t) && "Offline".equalsIgnoreCase(modalTypeChoice));

            int bg = sel ? 0x55A8E1FF : 0x1AFFFFFF;
            int border = sel ? ACCENT_ICE : 0x18FFFFFF;
            SmoothGfx.fillAndStroke(ctx, pX, curY, pillW, 16f, 4f, bg, border, 1.0f);
            ModernFonts.fontBadge.drawString(ctx, t, pX + (pillW - ModernFonts.fontBadge.getStringWidth(t)) / 2f,
                    curY + (16f - ModernFonts.fontBadge.getFontHeight()) / 2f, 0xFFFFFFFF);
        }
        return curY + 22f;
    }

    private void renderMicrosoftAddPrompt(GuiGraphics ctx, float mxPos, float curY, float mw, int mx, int my) {
        String desc = "Официальный вход через браузер";
        ModernFonts.fontBadge.drawString(ctx, desc, mxPos + 16f, curY + 6f, 0x88FFFFFF);

        float btnW = mw - 32f;
        float btnH = 20f;
        float btnY = curY + 26f;
        boolean hov = inBounds(mx, my, mxPos + 16f, btnY, btnW, btnH);

        SmoothGfx.fillRounded(ctx, mxPos + 16f, btnY, btnW, btnH, 5f, hov ? 0xFFFFFFFF : 0xFFE2EAF2);
        String txt = "Авторизоваться через браузер";
        ModernFonts.fontBtn.drawString(ctx, txt, mxPos + 16f + (btnW - ModernFonts.fontBtn.getStringWidth(txt)) / 2f,
                btnY + (btnH - ModernFonts.fontBtn.getFontHeight()) / 2f, 0xFF141822);

        renderModalCloseButtons(ctx, mxPos, btnY + btnH + 10f, mw, mx, my, false);
    }

    private void renderOfflineInputPrompt(GuiGraphics ctx, float mxPos, float curY, float mw, int mx, int my) {
        float inW = mw - 32f - 24f;
        float inH = 20f;
        float inX = mxPos + 16f;

        SmoothGfx.fillAndStroke(ctx, inX, curY, inW, inH, 4f, 0x22FFFFFF, modalInputActive ? ACCENT_ICE : 0x18FFFFFF, 1.0f);

        if (modalInput.isEmpty() && !modalInputActive) {
            ModernFonts.fontSearch.drawString(ctx, "Никнейм...", inX + 6f, curY + (inH - ModernFonts.fontSearch.getFontHeight()) / 2f, 0x66FFFFFF);
        } else {
            ModernFonts.fontSearch.drawString(ctx, modalInput, inX + 6f, curY + (inH - ModernFonts.fontSearch.getFontHeight()) / 2f, 0xFFFFFFFF);
            if (modalInputActive && (System.currentTimeMillis() % 1000 < 500)) {
                float cx = inX + 6f + ModernFonts.fontSearch.getStringWidth(modalInput) + 1f;
                SmoothGfx.fillRounded(ctx, cx, curY + 4f, 1f, inH - 8f, 0.5f, 0xFFFFFFFF);
            }
        }

        // Random roll button
        float rndX = inX + inW + 4f;
        float rndW = 20f;
        boolean hovRnd = inBounds(mx, my, rndX, curY, rndW, inH);
        SmoothGfx.fillAndStroke(ctx, rndX, curY, rndW, inH, 4f, hovRnd ? 0x33FFFFFF : 0x18FFFFFF, 0x22FFFFFF, 1.0f);
        String dice = "🎲";
        ModernFonts.fontBadge.drawString(ctx, dice, rndX + (rndW - ModernFonts.fontBadge.getStringWidth(dice)) / 2f,
                curY + (inH - ModernFonts.fontBadge.getFontHeight()) / 2f, 0xFFFFFFFF);

        renderModalCloseButtons(ctx, mxPos, curY + inH + 12f, mw, mx, my, true);
    }

    private void renderModalCloseButtons(GuiGraphics ctx, float mxPos, float btnY, float mw, int mx, int my, boolean showConfirm) {
        float totalW = mw - 32f;
        float btnW = showConfirm ? (totalW - 6f) / 2f : totalW;
        float cancelX = mxPos + 16f;
        float btnH = 20f;

        boolean hovCancel = inBounds(mx, my, cancelX, btnY, btnW, btnH);
        SmoothGfx.fillAndStroke(ctx, cancelX, btnY, btnW, btnH, 4f, hovCancel ? 0x26FFFFFF : 0x16FFFFFF, 0x20FFFFFF, 1.0f);
        ModernFonts.fontBtn.drawString(ctx, "Отмена", cancelX + (btnW - ModernFonts.fontBtn.getStringWidth("Отмена")) / 2f,
                btnY + (btnH - ModernFonts.fontBtn.getFontHeight()) / 2f, 0xAAFFFFFF);

        if (showConfirm) {
            float confirmX = cancelX + btnW + 6f;
            boolean ready = !modalInput.trim().isEmpty();
            boolean hovConfirm = inBounds(mx, my, confirmX, btnY, btnW, btnH);

            int confBg = ready ? (hovConfirm ? 0xFFFFFFFF : 0xFFE2EAF2) : 0x20FFFFFF;
            int confText = ready ? 0xFF141822 : 0x66FFFFFF;
            SmoothGfx.fillRounded(ctx, confirmX, btnY, btnW, btnH, 4f, confBg);
            String confTxt = modal == ModalType.ADD_ACCOUNT ? "Добавить" : "Войти";
            ModernFonts.fontBtn.drawString(ctx, confTxt, confirmX + (btnW - ModernFonts.fontBtn.getStringWidth(confTxt)) / 2f,
                    btnY + (btnH - ModernFonts.fontBtn.getFontHeight()) / 2f, confText);
        }
    }

    private void renderMsPollingModal(GuiGraphics ctx, float mxPos, float myPos, float mw, float mh, int mx, int my) {
        ModernFonts.fontName.drawStringWithShadow(ctx, "Авторизация Microsoft", mxPos + 16f, myPos + 12f, 0xFFFFFFFF);

        String code = msDeviceCode != null ? msDeviceCode.userCode : "Загрузка...";
        ModernFonts.fontTitle.drawStringWithShadow(ctx, code, mxPos + (mw - ModernFonts.fontTitle.getStringWidth(code)) / 2f, myPos + 36f, ACCENT_ICE);

        String tip1 = "Код скопирован в буфер обмена!";
        ModernFonts.fontBadge.drawString(ctx, tip1, mxPos + (mw - ModernFonts.fontBadge.getStringWidth(tip1)) / 2f, myPos + 58f, 0xFF2ECC71);

        String tip2 = "Страница открыта в браузере";
        ModernFonts.fontSearch.drawString(ctx, tip2, mxPos + (mw - ModernFonts.fontSearch.getStringWidth(tip2)) / 2f, myPos + 72f, 0xAAFFFFFF);

        String status = msAuthStatus.isEmpty() ? "Ожидание входа в браузере..." : msAuthStatus;
        ModernFonts.fontSearch.drawString(ctx, status, mxPos + (mw - ModernFonts.fontSearch.getStringWidth(status)) / 2f, myPos + 88f, 0xFFADC2D1);

        float btnW = 80f;
        float btnH = 20f;
        float cancelX = mxPos + (mw - btnW) / 2f;
        float cancelY = myPos + mh - 28f;
        boolean hov = inBounds(mx, my, cancelX, cancelY, btnW, btnH);

        SmoothGfx.fillAndStroke(ctx, cancelX, cancelY, btnW, btnH, 4f, hov ? 0x30FFFFFF : 0x1EFFFFFF, 0x22FFFFFF, 1.0f);
        ModernFonts.fontBtn.drawString(ctx, "Отмена", cancelX + (btnW - ModernFonts.fontBtn.getStringWidth("Отмена")) / 2f,
                cancelY + (btnH - ModernFonts.fontBtn.getFontHeight()) / 2f, 0xFFFFFFFF);
    }

    // ============================================================= Input Handling

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x();
        double my = click.y();
        float px = getPanelX();
        float py = getPanelY();
        float pw = getPanelWidth();
        float ph = getPanelHeight();

        if (modal != ModalType.NONE) {
            handleModalClick(mx, my, px, py, pw, ph);
            return true;
        }

        // Search Bar click
        float sw = 125f;
        float sh = 20f;
        float sx = px + pw - 16f - sw;
        float sy = py + 14f;
        if (inBounds(mx, my, sx, sy, sw, sh)) {
            if (!searchQuery.isEmpty() && inBounds(mx, my, sx + sw - 16f, sy, 16f, sh)) {
                searchQuery = "";
                playClickSound(1.0f);
                return true;
            }
            searchActive = true;
            playClickSound(1.4f);
            return true;
        }
        searchActive = false;

        // Bottom Action Bar
        if (handleBottomBarClick(mx, my, px, py, pw, ph)) {
            return true;
        }

        // Cards list click
        return handleCardsClick(mx, my, px, py, pw, ph, doubled);
    }

    private boolean handleBottomBarClick(double mx, double my, float px, float py, float pw, float ph) {
        float barY = py + ph - 38f;
        float barH = 20f;
        float gap = 5f;
        float x = px + 16f;

        // + Добавить
        float wAdd = 74f;
        if (inBounds(mx, my, x, barY, wAdd, barH)) {
            modal = ModalType.ADD_ACCOUNT;
            modalInput = "";
            modalTypeChoice = "Offline";
            modalInputActive = true;
            playClickSound(1.2f);
            return true;
        }
        x += wAdd + gap;

        // Случайный
        float wRand = 74f;
        if (inBounds(mx, my, x, barY, wRand, barH)) {
            String rnd = generateRandomNickname();
            applySession(rnd);
            setStatus("Вход: " + rnd);
            playClickSound(1.3f);
            return true;
        }
        x += wRand + gap;

        // Прямой вход
        float wDirect = 82f;
        if (inBounds(mx, my, x, barY, wDirect, barH)) {
            modal = ModalType.DIRECT_LOGIN;
            modalInput = "";
            modalInputActive = true;
            playClickSound(1.2f);
            return true;
        }

        // Right side
        float rx = px + pw - 16f;

        // Назад
        float wBack = 52f;
        rx -= wBack;
        if (inBounds(mx, my, rx, barY, wBack, barH)) {
            playClickSound(1.0f);
            client.setScreen(parent);
            return true;
        }

        // Удалить
        float wDel = 60f;
        rx -= (wDel + gap);
        List<AltEntry> filtered = getFilteredAlts();
        if (inBounds(mx, my, rx, barY, wDel, barH)) {
            if (selectedIndex >= 0 && selectedIndex < filtered.size()) {
                AltEntry rem = filtered.get(selectedIndex);
                alts.remove(rem);
                saveAlts();
                if (selectedIndex >= filtered.size()) {
                    selectedIndex = Math.max(0, filtered.size() - 1);
                }
                setStatus("Удален: " + rem.username);
                playClickSound(0.9f);
            }
            return true;
        }

        // Войти
        float wLog = 56f;
        rx -= (wLog + gap);
        if (inBounds(mx, my, rx, barY, wLog, barH)) {
            if (selectedIndex >= 0 && selectedIndex < filtered.size()) {
                loginAlt(filtered.get(selectedIndex));
            }
            return true;
        }

        return false;
    }

    private boolean handleCardsClick(double mx, double my, float px, float py, float pw, float ph, boolean doubled) {
        float listTop = py + 48f;
        float listBottom = py + ph - 44f;
        float cardW = pw - 32f;
        List<AltEntry> filtered = getFilteredAlts();

        if (my < listTop || my > listBottom) return false;

        for (int i = 0; i < filtered.size(); i++) {
            float cardY = listTop + i * (CARD_H + CARD_GAP) - scrollSmooth;
            if (cardY + CARD_H < listTop || cardY > listBottom) continue;

            float cardX = px + 16f;

            // Delete click
            if (inBounds(mx, my, cardX + cardW - 22f, cardY + 6f, 16f, 20f)) {
                AltEntry rem = filtered.get(i);
                alts.remove(rem);
                saveAlts();
                if (selectedIndex >= filtered.size()) {
                    selectedIndex = Math.max(0, filtered.size() - 1);
                }
                setStatus("Удален: " + rem.username);
                playClickSound(0.9f);
                return true;
            }

            // Quick login button click
            if (inBounds(mx, my, cardX + cardW - 64f, cardY + 8f, 38f, 16f)) {
                loginAlt(filtered.get(i));
                return true;
            }

            // Card click
            if (inBounds(mx, my, cardX, cardY, cardW, CARD_H)) {
                selectedIndex = i;
                if (doubled) {
                    loginAlt(filtered.get(i));
                } else {
                    playClickSound(1.0f);
                }
                return true;
            }
        }
        return false;
    }

    private void handleModalClick(double mx, double my, float px, float py, float pw, float ph) {
        float mw = 250f;
        float mh = modal == ModalType.MS_DEVICE_POLLING ? 146f : (modal == ModalType.ADD_ACCOUNT ? 140f : 112f);
        float mxPos = px + (pw - mw) / 2f;
        float myPos = py + (ph - mh) / 2f;

        if (!inBounds(mx, my, mxPos, myPos, mw, mh)) {
            if (modal == ModalType.MS_DEVICE_POLLING) MicrosoftAuth.cancel();
            modal = ModalType.NONE;
            playClickSound(1.0f);
            return;
        }

        if (modal == ModalType.MS_DEVICE_POLLING) {
            float btnW = 80f;
            float btnH = 20f;
            float cancelX = mxPos + (mw - btnW) / 2f;
            float cancelY = myPos + mh - 28f;
            if (inBounds(mx, my, cancelX, cancelY, btnW, btnH)) {
                MicrosoftAuth.cancel();
                modal = ModalType.NONE;
                playClickSound(1.0f);
            }
            return;
        }

        float curY = myPos + 30f;
        if (modal == ModalType.ADD_ACCOUNT) {
            float pillW = (mw - 32f - 6f) / 2f;
            if (inBounds(mx, my, mxPos + 16f, curY, pillW, 16f)) {
                modalTypeChoice = "Microsoft";
                playClickSound(1.2f);
                return;
            }
            if (inBounds(mx, my, mxPos + 16f + pillW + 6f, curY, pillW, 16f)) {
                modalTypeChoice = "Offline";
                playClickSound(1.2f);
                return;
            }
            curY += 22f;
        }

        if (modal == ModalType.ADD_ACCOUNT && "Microsoft".equalsIgnoreCase(modalTypeChoice)) {
            float btnW = mw - 32f;
            float btnH = 20f;
            float btnY = curY + 26f;
            if (inBounds(mx, my, mxPos + 16f, btnY, btnW, btnH)) {
                startMicrosoftAuth();
                return;
            }
            if (inBounds(mx, my, mxPos + 16f, btnY + btnH + 10f, btnW, btnH)) {
                modal = ModalType.NONE;
                playClickSound(1.0f);
            }
            return;
        }

        float inW = mw - 32f - 24f;
        float inH = 20f;
        float inX = mxPos + 16f;

        if (inBounds(mx, my, inX, curY, inW, inH)) {
            modalInputActive = true;
            return;
        }

        // Dice roll click
        float rndX = inX + inW + 4f;
        float rndW = 20f;
        if (inBounds(mx, my, rndX, curY, rndW, inH)) {
            modalInput = generateRandomNickname();
            modalInputActive = true;
            playClickSound(1.3f);
            return;
        }

        float totalW = mw - 32f;
        float btnW = (totalW - 6f) / 2f;
        float btnH = 20f;
        float cancelX = inX;
        float confirmX = inX + btnW + 6f;
        float btnY = curY + inH + 12f;

        if (inBounds(mx, my, cancelX, btnY, btnW, btnH)) {
            modal = ModalType.NONE;
            playClickSound(1.0f);
            return;
        }

        if (inBounds(mx, my, confirmX, btnY, btnW, btnH)) {
            submitModal();
        }
    }

    private void submitModal() {
        String nick = modalInput.trim();
        if (nick.isEmpty()) return;

        if (modal == ModalType.ADD_ACCOUNT) {
            AltEntry existing = null;
            for (AltEntry a : alts) {
                if (a.username.equalsIgnoreCase(nick)) {
                    existing = a;
                    break;
                }
            }
            if (existing == null) {
                AltEntry newAlt = new AltEntry(nick, "Offline");
                alts.add(newAlt);
                saveAlts();
                selectedIndex = alts.size() - 1;
                setStatus("Добавлен: " + nick);
            } else {
                existing.type = "Offline";
                saveAlts();
                setStatus("Обновлен: " + nick);
            }
            playClickSound(1.3f);
            modal = ModalType.NONE;
        } else if (modal == ModalType.DIRECT_LOGIN) {
            applySession(nick);
            setStatus("Вход: " + nick);
            playClickSound(1.3f);
            modal = ModalType.NONE;
        }
    }

    private void startMicrosoftAuth() {
        modal = ModalType.MS_DEVICE_POLLING;
        msAuthStatus = "Запрос кода устройства...";
        msDeviceCode = null;

        MicrosoftAuth.requestDeviceCode().thenAccept(dc -> {
            this.msDeviceCode = dc;
            this.msAuthStatus = "Код скопирован! Жду авторизацию...";

            MicrosoftAuth.pollAndAuthenticate(dc, status -> this.msAuthStatus = status)
                    .thenAccept(authSession -> client.execute(() -> {
                        applyAuthSession(authSession);
                        modal = ModalType.NONE;
                        setStatus("Вход выполнен: " + authSession.username + " (Microsoft)");
                    })).exceptionally(ex -> {
                        client.execute(() -> {
                            modal = ModalType.NONE;
                            setStatus("Ошибка входа: " + ex.getMessage());
                        });
                        return null;
                    });
        }).exceptionally(ex -> {
            client.execute(() -> {
                modal = ModalType.NONE;
                setStatus("Не удалось связаться с сервером Microsoft");
            });
            return null;
        });
    }

    private void applyAuthSession(MicrosoftAuth.AuthSession s) {
        try {
            User session = new User(s.username, s.uuid, s.mcToken, Optional.empty(), Optional.empty());
            ((MixinMinecraftClient) client).setSession(session);

            AltEntry existing = null;
            for (AltEntry a : alts) {
                if (a.username.equalsIgnoreCase(s.username)) {
                    existing = a;
                    break;
                }
            }
            if (existing != null) {
                existing.type = "Microsoft";
                existing.uuid = s.uuid;
                existing.refreshToken = s.refreshToken;
                existing.mcToken = s.mcToken;
            } else {
                alts.add(new AltEntry(s.username, "Microsoft", s.uuid, s.refreshToken, s.mcToken));
                selectedIndex = alts.size() - 1;
            }
            saveAlts();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hScroll, double vScroll) {
        if (vScroll != 0) {
            int maxRows = Math.max(0, getFilteredAlts().size() - 6);
            scroll = Math.max(0, Math.min(scroll - (float) vScroll * 20f, maxRows * (CARD_H + CARD_GAP)));
            return true;
        }
        return super.mouseScrolled(mx, my, hScroll, vScroll);
    }

    private void ensureCardVisible(int index, float py, float ph) {
        float listTop = py + 48f;
        float listBottom = py + ph - 44f;
        float viewH = listBottom - listTop;
        float cardTop = index * (CARD_H + CARD_GAP);
        float cardBottom = cardTop + CARD_H;

        if (cardTop < scroll) {
            scroll = cardTop;
        } else if (cardBottom > scroll + viewH) {
            scroll = cardBottom - viewH;
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        float py = getPanelY();
        float ph = getPanelHeight();

        if (modal != ModalType.NONE) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                if (modal == ModalType.MS_DEVICE_POLLING) MicrosoftAuth.cancel();
                modal = ModalType.NONE;
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                submitModal();
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE && !modalInput.isEmpty()) {
                modalInput = modalInput.substring(0, modalInput.length() - 1);
                return true;
            }
            handleClipboard(key, true);
            return true;
        }

        if (searchActive) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                searchActive = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                return true;
            }
            handleClipboard(key, false);
            return true;
        }

        List<AltEntry> filtered = getFilteredAlts();
        if (key == GLFW.GLFW_KEY_UP) {
            if (selectedIndex > 0) {
                selectedIndex--;
                ensureCardVisible(selectedIndex, py, ph);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN) {
            if (selectedIndex < filtered.size() - 1) {
                selectedIndex++;
                ensureCardVisible(selectedIndex, py, ph);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (selectedIndex >= 0 && selectedIndex < filtered.size()) {
                loginAlt(filtered.get(selectedIndex));
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE) {
            if (selectedIndex >= 0 && selectedIndex < filtered.size()) {
                AltEntry rem = filtered.get(selectedIndex);
                alts.remove(rem);
                saveAlts();
                if (selectedIndex >= filtered.size()) {
                    selectedIndex = Math.max(0, filtered.size() - 1);
                }
                setStatus("Удален: " + rem.username);
            }
            return true;
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            client.setScreen(parent);
            return true;
        }

        return super.keyPressed(input);
    }

    private void handleClipboard(int key, boolean isModal) {
        long win = client.getWindow().getHandle();
        boolean ctrl = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;

        if (ctrl && key == GLFW.GLFW_KEY_V) {
            String cb = GLFW.glfwGetClipboardString(win);
            if (cb != null) {
                for (char c : cb.toCharArray()) {
                    if (isModal) {
                        if ((Character.isLetterOrDigit(c) || c == '_') && modalInput.length() < 16) {
                            modalInput += c;
                        }
                    } else {
                        if (c >= ' ' && c != 127 && searchQuery.length() < 24) {
                            searchQuery += c;
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (modal != ModalType.NONE && modalInputActive && input.isValidChar() && modalInput.length() < 16) {
            char c = input.asString().charAt(0);
            if (Character.isLetterOrDigit(c) || c == '_') {
                modalInput += c;
                return true;
            }
        }
        if (searchActive && input.isValidChar() && searchQuery.length() < 24) {
            searchQuery += input.asString();
            return true;
        }
        return super.charTyped(input);
    }

    // ============================================================= Account & User Logic

    private void loginAlt(AltEntry alt) {
        if ("Microsoft".equalsIgnoreCase(alt.type)) {
            if (!alt.refreshToken.isEmpty()) {
                setStatus("Обновление сессии Microsoft...");
                MicrosoftAuth.refreshSession(alt.refreshToken, s -> setStatus(s))
                        .thenAccept(authSession -> client.execute(() -> {
                            applyAuthSession(authSession);
                            setStatus("Вход: " + authSession.username + " (Microsoft)");
                        })).exceptionally(ex -> {
                            client.execute(() -> {
                                setStatus("Сессия истекла, войдите заново");
                                modal = ModalType.ADD_ACCOUNT;
                                modalTypeChoice = "Microsoft";
                            });
                            return null;
                        });
                return;
            } else {
                startMicrosoftAuth();
                return;
            }
        }

        applySession(alt.username);
        setStatus("Вход: " + alt.username);
        playClickSound(1.2f);
    }

    private void applySession(String username) {
        try {
            UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
            User s = new User(username, uuid, "0", Optional.empty(), Optional.empty());
            ((MixinMinecraftClient) client).setSession(s);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String generateRandomNickname() {
        Random rnd = new Random();
        String p = RANDOM_PREFIXES[rnd.nextInt(RANDOM_PREFIXES.length)];
        String s = RANDOM_SUFFIXES[rnd.nextInt(RANDOM_SUFFIXES.length)];
        return (p + s).substring(0, Math.min(16, (p + s).length()));
    }

    private void loadAlts() {
        alts.clear();
        try {
            if (Files.exists(ALTS_FILE)) {
                for (String line : Files.readAllLines(ALTS_FILE)) {
                    if (line.isBlank()) continue;
                    String[] parts = line.split(":");
                    String nick = parts[0].trim();
                    String rawType = parts.length > 1 ? parts[1].trim() : "Offline";
                    String type = "Microsoft".equalsIgnoreCase(rawType) ? "Microsoft" : "Offline";
                    UUID uuid = null;
                    if (parts.length > 2 && !parts[2].isBlank()) {
                        try { uuid = UUID.fromString(parts[2].trim()); } catch (Exception ignored) {}
                    }
                    String refresh = parts.length > 3 ? parts[3].trim() : "";
                    String mcToken = parts.length > 4 ? parts[4].trim() : "";

                    if (!nick.isEmpty()) {
                        alts.add(new AltEntry(nick, type, uuid, refresh, mcToken));
                    }
                }
            }
        } catch (Exception ignored) {
        }

        if (alts.isEmpty()) {
            String cur = client.getUser().getUsername();
            alts.add(new AltEntry(cur, "Offline"));
            saveAlts();
        }
    }

    private void saveAlts() {
        try {
            Files.createDirectories(ALTS_FILE.getParent());
            List<String> lines = new ArrayList<>();
            for (AltEntry a : alts) {
                String line = a.username + ":" + a.type;
                if (!a.refreshToken.isEmpty() || a.uuid != null) {
                    line += ":" + (a.uuid != null ? a.uuid.toString() : "")
                            + ":" + a.refreshToken
                            + ":" + a.mcToken;
                }
                lines.add(line);
            }
            Files.write(ALTS_FILE, lines);
        } catch (Exception ignored) {
        }
    }

    private void setStatus(String s) {
        statusMsg = s;
        statusUntil = System.currentTimeMillis() + 2600;
    }

    private void playClickSound(float pitch) {
        if (UI.INSTANCE != null && UI.INSTANCE.sounds.getValue()) {
            SoundUtil.playClick(1.0f, pitch);
        }
    }

    private static boolean inBounds(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
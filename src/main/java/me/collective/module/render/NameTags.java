package me.collective.module.render;

import me.collective.category.Category;
import me.collective.font.FontManager;
import me.collective.gui.GuiGfx;
import me.collective.module.Module;
import me.collective.module.client.UI;
import me.collective.render.RenderUtil;
import me.collective.render.RenderUtil3D;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Информативные неймтеги над игроками в стиле ClickGUI: плоская плашка
 * с акцентной шапкой (имя, пинг, дистанция) и стеклянным телом (полоса
 * здоровья с числом), а над плашкой — слоты брони.
 *
 * <p>Позиция каждой плашки — интерполированная позиция игрока, спроецированная
 * кадровыми матрицами мира ({@link me.collective.render.RenderFrame}), поэтому
 * теги не дрожат при движении и не отстают от игрока.
 */
public class NameTags extends Module {
    public static NameTags INSTANCE;

    public final BooleanSetting self = add(new BooleanSetting("На себя", false));
    public final BooleanSetting health = add(new BooleanSetting("Здоровье", true));
    public final BooleanSetting ping = add(new BooleanSetting("Пинг", true));
    public final BooleanSetting distance = add(new BooleanSetting("Дистанция", false));
    public final BooleanSetting armor = add(new BooleanSetting("Броня", true));
    public final NumberSetting scale = add(new NumberSetting("Размер", 1.0, 0.5, 2.5, 0.1));

    // Палитра берётся из модуля UI (стиль ClickGUI). Альфы чуть плотнее
    // интерфейсных: за плашкой яркий мир, а не тёмный blur.
    private static final int HEADER_ALPHA = 170;
    private static final int BODY_ALPHA = 205;
    private static final int OUTLINE_ALPHA = 150;
    private static final int SLOT_ALPHA = 215;
    private static final int SLOT_OUTLINE_ALPHA = 130;

    private static final Color DIST_COLOR = new Color(168, 172, 182);
    private static final Color ABSORPTION_COLOR = new Color(255, 215, 90);

    public NameTags() {
        super("NameTags", "Отображает кастомные информативные неймтеги над игроками с броней и здоровьем", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    public void onRender2D(DrawContext context, float delta) {
        if (!isToggled() || mc.world == null || mc.player == null) return;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == null || !player.isAlive()) continue;
            if (player == mc.player && !self.getValue()) continue;

            Vec3d pos = player.getLerpedPos(delta);
            Vector2f screen = RenderUtil3D.worldToScreen(pos.x, pos.y + player.getHeight() + 0.25, pos.z, sw, sh);
            if (screen == null) continue;

            renderTag(context, player, screen.x, screen.y);
        }
    }

    private void renderTag(DrawContext context, PlayerEntity player, float x, float y) {
        FontManager fm = FontManager.INSTANCE;
        float s = scale.getFloatValue();

        Color accent = UI.INSTANCE != null ? UI.INSTANCE.color.getValue() : new Color(189, 153, 255, 115);
        Color themeBg = UI.INSTANCE != null ? UI.INSTANCE.bgColor.getValue() : new Color(2, 0, 8, 119);
        Color themeText = UI.INSTANCE != null ? UI.INSTANCE.textColor.getValue() : new Color(249, 244, 255);

        int headerFill = GuiGfx.withAlpha(accent.getRGB(), HEADER_ALPHA);
        int bodyFill = GuiGfx.withAlpha(themeBg.getRGB(), BODY_ALPHA);
        int outlineColor = GuiGfx.withAlpha(accent.getRGB(), OUTLINE_ALPHA);
        int slotFill = GuiGfx.withAlpha(themeBg.getRGB(), SLOT_ALPHA);
        int slotOutlineColor = GuiGfx.withAlpha(accent.getRGB(), SLOT_OUTLINE_ALPHA);

        // --- Данные игрока
        int playerPing = -1;
        if (ping.getValue() && mc.getNetworkHandler() != null) {
            var entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
            if (entry != null) playerPing = entry.getLatency();
        }

        boolean showHp = health.getValue();
        float hp = player.getHealth() + player.getAbsorptionAmount();
        float absorption = player.getAbsorptionAmount();
        float maxHp = Math.max(1.0f, player.getMaxHealth());

        String name = player.getName().getString();
        String pingStr = playerPing >= 0 ? playerPing + "ms" : "";
        String distStr = distance.getValue()
                ? Math.round(Math.sqrt(mc.player.squaredDistanceTo(player))) + "м"
                : "";

        // --- Метрики
        float fh = fm.getFontHeightScaled(s);
        float padX = Math.max(3f, 3f * s);
        float padY = Math.max(2f, 2f * s);
        float gap = Math.max(3f, 3.5f * s);
        float headerH = Math.round(fh + padY * 2f);
        float dotSize = Math.max(3f, 4f * s);

        float nameW = fm.getStringWidthScaled(name, s);
        float pingTextW = pingStr.isEmpty() ? 0f : fm.getStringWidthScaled(pingStr, s);
        float pingW = pingStr.isEmpty() ? 0f : dotSize + 3f + pingTextW;
        float distW = distStr.isEmpty() ? 0f : fm.getStringWidthScaled(distStr, s);
        float rightW = pingW + (pingW > 0f && distW > 0f ? gap : 0f) + distW;
        float headerW = padX * 2f + nameW + (rightW > 0f ? gap : 0f) + rightW;

        String hpStr = String.valueOf((int) Math.ceil(hp));
        float hpNumW = showHp ? fm.getStringWidthScaled(hpStr, s) : 0f;
        float barH = Math.max(3f, 4f * s);
        float barW = showHp ? Math.round(Math.max(40f * s, Math.min(90f * s, headerW * 0.55f))) : 0f;
        float bodyRowH = showHp ? Math.max(barH, fh) : 0f;
        float bodyW = showHp ? padX * 2f + barW + gap + hpNumW : 0f;

        // --- Габариты плашки
        float boxW = Math.round(Math.max(headerW, bodyW));
        float boxH = Math.round(headerH + (showHp ? padY * 2f + bodyRowH : 0f));
        float bx = Math.round(x - boxW / 2f);
        float by = Math.round(y - boxH);

        // --- Слоты брони над плашкой
        if (armor.getValue()) {
            List<ItemStack> equipment = new ArrayList<>();
            equipment.add(player.getEquippedStack(EquipmentSlot.MAINHAND));
            equipment.add(player.getEquippedStack(EquipmentSlot.HEAD));
            equipment.add(player.getEquippedStack(EquipmentSlot.CHEST));
            equipment.add(player.getEquippedStack(EquipmentSlot.LEGS));
            equipment.add(player.getEquippedStack(EquipmentSlot.FEET));
            equipment.add(player.getEquippedStack(EquipmentSlot.OFFHAND));
            equipment.removeIf(ItemStack::isEmpty);

            if (!equipment.isEmpty()) {
                float slot = 18f;
                float rowW = equipment.size() * slot;
                float ix = Math.round(x - rowW / 2f);
                float iy = by - slot - 2f;
                for (ItemStack stack : equipment) {
                    RenderUtil.drawRect(context, ix, iy, ix + slot, iy + slot, slotFill);
                    RenderUtil.drawOutline(context, ix, iy, slot, slot, 1f, slotOutlineColor);
                    context.drawItem(stack, (int) ix + 1, (int) iy + 1);
                    ix += slot;
                }
            }
        }

        // --- Шапка: акцентная полоса с именем, пингом и дистанцией
        RenderUtil.drawRect(context, bx, by, bx + boxW, by + headerH, headerFill);

        float textY = by + (headerH - fh) / 2f + 0.5f;
        fm.drawStringWithShadowScaled(context, name, bx + padX, textY, themeText, s);

        float cursor = bx + boxW - padX;
        if (!distStr.isEmpty()) {
            cursor -= distW;
            fm.drawStringWithShadowScaled(context, distStr, cursor, textY, DIST_COLOR, s);
        }
        if (!pingStr.isEmpty()) {
            if (!distStr.isEmpty()) cursor -= gap;
            cursor -= pingW;
            Color pc = pingColor(playerPing);
            float dotY = by + (headerH - dotSize) / 2f;
            RenderUtil.drawRect(context, cursor, dotY, cursor + dotSize, dotY + dotSize, pc.getRGB());
            fm.drawStringWithShadowScaled(context, pingStr, cursor + dotSize + 3f, textY, pc, s);
        }

        // --- Тело: полоса здоровья с числом (стиль слайдера ClickGUI)
        if (showHp) {
            float row2Y = by + headerH + padY;
            float barY = Math.round(row2Y + (bodyRowH - barH) / 2f);
            float barX = bx + padX;
            RenderUtil.drawRect(context, barX, barY, barX + barW, barY + barH,
                    GuiGfx.withAlpha(themeBg.getRGB(), 165));

            float fill = Math.min(1f, hp / maxHp);
            float absorptionFill = Math.min(Math.max(0f, 1f - fill), Math.max(0f, absorption / maxHp));
            float inner = barW - 4f;
            Color hc = healthColor(fill);
            if (inner > 0f) {
                float fillW = inner * fill;
                if (fillW > 0.4f) {
                    RenderUtil.drawRect(context, barX + 2f, barY, barX + 2f + fillW, barY + barH, hc.getRGB());
                }
                float absW = inner * absorptionFill;
                if (absW > 0.4f) {
                    RenderUtil.drawRect(context, barX + 2f + fillW, barY, barX + 2f + fillW + absW, barY + barH,
                            ABSORPTION_COLOR.getRGB());
                }
            }
            fm.drawStringWithShadowScaled(context, hpStr, barX + barW + gap, row2Y + (bodyRowH - fh) / 2f, hc, s);
        }

        // --- Контур плашки (1px снаружи, как у окон ClickGUI)
        RenderUtil.drawOutline(context, bx - 1f, by - 1f, boxW + 2f, boxH + 2f, 1f, outlineColor);
    }

    private static Color pingColor(int ping) {
        if (ping < 60) return new Color(84, 255, 115);
        if (ping < 150) return new Color(255, 235, 90);
        if (ping < 300) return new Color(255, 170, 60);
        return new Color(255, 95, 95);
    }

    private static Color healthColor(float ratio) {
        Color red = new Color(255, 85, 95);
        Color yellow = new Color(255, 212, 80);
        Color green = new Color(90, 230, 110);
        if (ratio >= 0.5f) return lerp(yellow, green, (ratio - 0.5f) * 2f);
        return lerp(red, yellow, ratio * 2f);
    }

    private static Color lerp(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }
}

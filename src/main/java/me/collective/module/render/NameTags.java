package me.collective.module.render;

import me.collective.category.Category;
import me.collective.font.FontManager;
import me.collective.gui.GuiGfx;
import me.collective.module.Module;
import me.collective.module.client.UI;
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
 * Информативные неймтеги над игроками: скруглённая плашка с акцентной полоской,
 * имя с числом здоровья, пинг с цветной точкой, дистанция и слоты брони над плашкой.
 *
 * <p>Позиция каждой плашки — интерполированная позиция игрока, спроецированная
 * кадровыми матрицами мира ({@link me.collective.render.RenderFrame}), без
 * экранного сглаживания, поэтому теги не дрожат и не отстают при движении.
 */
public class NameTags extends Module {
    public static NameTags INSTANCE;

    public final BooleanSetting self = add(new BooleanSetting("На себя", false));
    public final BooleanSetting health = add(new BooleanSetting("Здоровье", true));
    public final BooleanSetting ping = add(new BooleanSetting("Пинг", true));
    public final BooleanSetting distance = add(new BooleanSetting("Дистанция", false));
    public final BooleanSetting armor = add(new BooleanSetting("Броня", true));
    public final NumberSetting scale = add(new NumberSetting("Размер", 1.0, 0.5, 2.5, 0.1));

    private static final Color PLATE = new Color(11, 11, 15, 205);
    private static final Color BORDER = new Color(255, 255, 255, 30);
    private static final Color NAME_COLOR = new Color(242, 245, 252);
    private static final Color DIST_COLOR = new Color(168, 172, 182);
    private static final Color SLOT_BG = new Color(10, 10, 14, 175);

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

        // --- Данные игрока
        int playerPing = -1;
        if (ping.getValue() && mc.getNetworkHandler() != null) {
            var entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
            if (entry != null) playerPing = entry.getLatency();
        }

        boolean showHp = health.getValue();
        float hp = player.getHealth() + player.getAbsorptionAmount();
        float maxHp = Math.max(1.0f, player.getMaxHealth());
        float hpFill = Math.min(1f, hp / maxHp);
        Color hpColor = healthColor(hpFill);

        String name = player.getName().getString();
        String hpStr = String.valueOf((int) Math.ceil(hp));
        String pingStr = playerPing >= 0 ? playerPing + "ms" : "";
        String distStr = distance.getValue()
                ? Math.round(Math.sqrt(mc.player.squaredDistanceTo(player))) + "м"
                : "";

        // --- Метрики
        float fh = fm.getFontHeightScaled(s);
        float padX = Math.max(4f, 4f * s);
        float padY = Math.max(2.5f, 3f * s);
        float gap = Math.max(3f, 4f * s);
        float accentW = Math.max(1.5f, 2f * s);
        int radius = Math.max(2, Math.round(3f * s));

        float nameW = fm.getStringWidthScaled(name, s);
        float hpW = showHp ? fm.getStringWidthScaled(hpStr, s) : 0f;
        float dotR = Math.max(1.5f, 2.2f * s);
        float pingBlockW = pingStr.isEmpty() ? 0 : dotR * 2f + 3f + fm.getStringWidthScaled(pingStr, s);
        float distBlockW = distStr.isEmpty() ? 0 : gap + fm.getStringWidthScaled(distStr, s);
        float rowW = nameW + (showHp ? gap + hpW : 0f) + pingBlockW + distBlockW;

        // --- Габариты плашки
        float boxW = rowW + padX * 2 + accentW + 2f;
        float boxH = fh + padY * 2;
        float bx = Math.round(x - boxW / 2f);
        float by = Math.round(y - boxH);
        float textLeft = bx + 1.5f + accentW + padX;

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
                float rowW2 = equipment.size() * slot;
                float ix = Math.round(x - rowW2 / 2f);
                float iy = by - slot - 2f;
                for (ItemStack stack : equipment) {
                    GuiGfx.fillRounded(context, ix, iy, slot, slot, 3, SLOT_BG.getRGB());
                    GuiGfx.outlineRounded(context, ix, iy, slot, slot, 3, BORDER.getRGB());
                    context.drawItem(stack, (int) ix + 1, (int) iy + 1);
                    ix += slot;
                }
            }
        }

        // --- Плашка: тень, фон, акцентная полоска, рамка
        GuiGfx.shadow(context, (int) bx, (int) by, (int) boxW, (int) boxH, radius);
        GuiGfx.fillRounded(context, bx, by, boxW, boxH, radius, PLATE.getRGB());
        Color accent = UI.INSTANCE != null ? UI.INSTANCE.color.getValue() : new Color(189, 153, 255);
        GuiGfx.fillRounded(context, bx + 1.5f, by + padY - 1f, accentW, boxH - (padY - 1f) * 2f,
                Math.max(1, Math.round(accentW / 2f)), accent.getRGB());
        GuiGfx.outlineRounded(context, bx, by, boxW, boxH, radius, BORDER.getRGB());

        // --- Строка: имя, число здоровья, пинг, дистанция
        float rowY = by + padY;
        float tx = textLeft;

        fm.drawStringWithShadowScaled(context, name, tx, rowY, NAME_COLOR, s);
        tx += nameW;

        if (showHp) {
            tx += gap;
            fm.drawStringWithShadowScaled(context, hpStr, tx, rowY, hpColor, s);
            tx += hpW;
        }

        if (!pingStr.isEmpty()) {
            Color pc = pingColor(playerPing);
            GuiGfx.dot(context, tx + dotR + 1f, rowY + fh / 2f - 0.5f, dotR, pc.getRGB());
            tx += dotR * 2f + 3f;
            fm.drawStringWithShadowScaled(context, pingStr, tx, rowY, pc, s);
            tx += fm.getStringWidthScaled(pingStr, s);
        }

        if (!distStr.isEmpty()) {
            tx += gap;
            fm.drawStringWithShadowScaled(context, distStr, tx, rowY, DIST_COLOR, s);
        }
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

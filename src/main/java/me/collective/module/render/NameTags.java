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
import java.util.HashMap;
import java.util.Map;

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
    /** Дальше этой дистанции табличка не рисуется — на толпе это главный срез растра. */
    public final NumberSetting cullDist = add(new NumberSetting("Макс. дистанция", 128.0, 16.0, 512.0, 8.0));

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
        double cullSq = this.cullDist.getValue() * this.cullDist.getValue();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == null || !player.isAlive()) continue;
            if (player == mc.player && !self.getValue()) continue;
            if (player != mc.player && mc.player.squaredDistanceTo(player) > cullSq) continue;

            TagData data = this.tagCache.get(player.getId());
            if (data == null) continue; // выйдет из кэша на следующем тике

            Vec3d pos = player.getLerpedPos(delta);
            Vector2f screen = RenderUtil3D.worldToScreen(pos.x, pos.y + player.getHeight() + 0.25, pos.z, sw, sh);
            if (screen == null) continue;

            renderTag(context, player, screen.x, screen.y, data);
        }
    }

    /**
     * Кэш подписи на игрока: строки, цвета и ширины считаются раз в тик,
     * отрисовка каждый кадр уже не собирает ничего заново.
     */
    private static final class TagData {
        String name = "";
        String hpStr = "";
        String pingStr = "";
        String distStr = "";
        Color hpColor = new Color(255, 255, 255);
        Color pingColor = new Color(255, 255, 255);
        int ping = -1;
        float nameW;
        float hpW;
        float pingBlockW;
        float distBlockW;
    }

    private final Map<Integer, TagData> tagCache = new HashMap<>();

    @Override
    public void onUpdate() {
        this.tagCache.clear();
        if (!isToggled() || mc.world == null || mc.player == null) return;

        FontManager fm = FontManager.INSTANCE;
        float s = scale.getFloatValue();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == null || !player.isAlive()) continue;

            TagData d = new TagData();
            d.name = player.getName().getString();

            if (ping.getValue() && mc.getNetworkHandler() != null) {
                var entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
                if (entry != null) d.ping = entry.getLatency();
            }
            d.pingStr = d.ping >= 0 ? d.ping + "ms" : "";
            d.pingColor = pingColor(d.ping);

            float hp = player.getHealth() + player.getAbsorptionAmount();
            float maxHp = Math.max(1.0f, player.getMaxHealth());
            d.hpColor = healthColor(Math.min(1f, hp / maxHp));
            d.hpStr = health.getValue() ? String.valueOf((int) Math.ceil(hp)) : "";

            d.distStr = distance.getValue()
                    ? Math.round(Math.sqrt(mc.player.squaredDistanceTo(player))) + "м" : "";

            d.nameW = fm.getStringWidthScaled(d.name, s);
            d.hpW = d.hpStr.isEmpty() ? 0 : fm.getStringWidthScaled(d.hpStr, s);
            float dotR = Math.max(1.5f, 2.2f * s);
            d.pingBlockW = d.pingStr.isEmpty() ? 0 : dotR * 2f + 3f + fm.getStringWidthScaled(d.pingStr, s);
            d.distBlockW = d.distStr.isEmpty() ? 0 : gapWidth(s) + fm.getStringWidthScaled(d.distStr, s);

            this.tagCache.put(player.getId(), d);
        }
    }

    private float gapWidth(float s) {
        return Math.max(3f, 4f * s);
    }

    private void renderTag(DrawContext context, PlayerEntity player, float x, float y, TagData d) {
        FontManager fm = FontManager.INSTANCE;
        float s = scale.getFloatValue();

        // --- Данные игрока: готовые из кэша тика
        int playerPing = d.ping;
        boolean showHp = !d.hpStr.isEmpty();
        Color hpColor = d.hpColor;

        String name = d.name;
        String hpStr = d.hpStr;
        String pingStr = d.pingStr;
        String distStr = d.distStr;

        // --- Метрики
        float fh = fm.getFontHeightScaled(s);
        float padX = Math.max(4f, 4f * s);
        float padY = Math.max(2.5f, 3f * s);
        float gap = Math.max(3f, 4f * s);
        float accentW = Math.max(1.5f, 2f * s);
        int radius = Math.max(2, Math.round(3f * s));

        float nameW = d.nameW;
        float hpW = d.hpW;
        float dotR = Math.max(1.5f, 2.2f * s);
        float pingBlockW = d.pingBlockW;
        float distBlockW = d.distBlockW;
        float rowW = nameW + (showHp ? gap + hpW : 0f) + pingBlockW + distBlockW;

        // --- Габариты плашки
        float boxW = rowW + padX * 2 + accentW + 2f;
        float boxH = fh + padY * 2;
        float bx = Math.round(x - boxW / 2f);
        float by = Math.round(y - boxH);
        float textLeft = bx + 1.5f + accentW + padX;

        // --- Слоты брони над плашкой: без списков и removeIf на каждый кадр
        if (armor.getValue()) {
            ItemStack[] equipment = new ItemStack[6];
            int count = 0;
            equipment[count++] = player.getEquippedStack(EquipmentSlot.MAINHAND);
            equipment[count++] = player.getEquippedStack(EquipmentSlot.HEAD);
            equipment[count++] = player.getEquippedStack(EquipmentSlot.CHEST);
            equipment[count++] = player.getEquippedStack(EquipmentSlot.LEGS);
            equipment[count++] = player.getEquippedStack(EquipmentSlot.FEET);
            equipment[count++] = player.getEquippedStack(EquipmentSlot.OFFHAND);

            int visible = 0;
            for (int i = 0; i < count; i++) {
                if (!equipment[i].isEmpty()) visible++;
            }

            if (visible > 0) {
                float slot = 18f;
                float rowW2 = visible * slot;
                float ix = Math.round(x - rowW2 / 2f);
                float iy = by - slot - 2f;
                for (int i = 0; i < count; i++) {
                    if (equipment[i].isEmpty()) continue;
                    GuiGfx.fillRounded(context, ix, iy, slot, slot, 3, SLOT_BG.getRGB());
                    GuiGfx.outlineRounded(context, ix, iy, slot, slot, 3, BORDER.getRGB());
                    context.drawItem(equipment[i], (int) ix + 1, (int) iy + 1);
                    context.drawStackOverlay(mc.textRenderer, equipment[i], (int) ix + 1, (int) iy + 1);
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
            GuiGfx.dot(context, tx + dotR + 1f, rowY + fh / 2f - 0.5f, dotR, d.pingColor.getRGB());
            tx += dotR * 2f + 3f;
            fm.drawStringWithShadowScaled(context, pingStr, tx, rowY, d.pingColor, s);
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

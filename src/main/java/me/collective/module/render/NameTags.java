package me.collective.module.render;

import me.collective.category.Category;
import me.collective.font.FontManager;
import me.collective.module.Module;
import me.collective.render.RenderUtil;
import me.collective.render.RenderUtil3D;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Formatting;
import org.joml.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class NameTags extends Module {
    public static NameTags INSTANCE;

    public final BooleanSetting self = add(new BooleanSetting("На себя", false));
    public final BooleanSetting health = add(new BooleanSetting("Здоровье", true));
    public final BooleanSetting ping = add(new BooleanSetting("Пинг", true));
    public final BooleanSetting armor = add(new BooleanSetting("Броня", true));
    public final NumberSetting scale = add(new NumberSetting("Размер", 1.0, 0.5, 2.5, 0.1));

    /** Сглаженные экранные позиции (гасят прыжки от view-bobbing и корекций сети). */
    private final Map<UUID, Vector2f> smooth = new HashMap<>();
    private final Set<UUID> seen = new HashSet<>();
    private static final float SMOOTH = 0.6f;
    private static final float SNAP_DIST_SQ = 40f * 40f;

    public NameTags() {
        super("NameTags", "Отображает кастомные информативные неймтеги над игроками с броней и здоровьем", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    public void onDisable() {
        smooth.clear();
        seen.clear();
    }

    @Override
    public void onRender2D(DrawContext context, float delta) {
        if (!isToggled() || mc.world == null || mc.player == null) return;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        seen.clear();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == null || !player.isAlive()) continue;
            if (player == mc.player && !self.getValue()) continue;

            net.minecraft.util.math.Vec3d pos = player.getLerpedPos(delta);
            double x = pos.x;
            double y = pos.y + player.getHeight() + 0.45;
            double z = pos.z;

            Vector2f target = RenderUtil3D.worldToScreen(x, y, z, sw, sh);
            UUID uuid = player.getUuid();
            if (target == null) {
                smooth.remove(uuid);
                continue;
            }
            seen.add(uuid);

            Vector2f s = smooth.get(uuid);
            if (s == null) {
                s = new Vector2f(target);
                smooth.put(uuid, s);
            } else {
                float dx = target.x - s.x;
                float dy = target.y - s.y;
                if (dx * dx + dy * dy > SNAP_DIST_SQ) {
                    s.set(target);
                } else {
                    s.x += dx * SMOOTH;
                    s.y += dy * SMOOTH;
                }
            }

            renderTag(context, player, s.x, s.y);
        }
        smooth.keySet().retainAll(seen);
    }

    private void renderTag(DrawContext context, PlayerEntity player, float x, float y) {
        String name = player.getName().getString();

        int hp = (int) Math.ceil(player.getHealth() + player.getAbsorptionAmount());
        String hpColor = hp > 15 ? Formatting.GREEN.toString() : (hp > 8 ? Formatting.YELLOW.toString() : Formatting.RED.toString());
        String hpStr = health.getValue() ? (" " + hpColor + hp) : "";

        int playerPing = -1;
        if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getPlayerListEntry(player.getUuid()) != null) {
            playerPing = mc.getNetworkHandler().getPlayerListEntry(player.getUuid()).getLatency();
        }
        String pingStr = (ping.getValue() && playerPing >= 0) ? (Formatting.GRAY + " [" + playerPing + "ms]") : "";

        String fullTag = name + hpStr + pingStr;

        float width = FontManager.INSTANCE.getStringWidth(fullTag);
        float height = FontManager.INSTANCE.getFontHeight();

        float padX = 4.0f;
        float padY = 2.0f;

        float boxX1 = x - width / 2.0f - padX;
        float boxY1 = y - height / 2.0f - padY;
        float boxX2 = x + width / 2.0f + padX;
        float boxY2 = y + height / 2.0f + padY;

        // Background & border
        RenderUtil.drawRect(context, boxX1, boxY1, boxX2, boxY2, new Color(12, 12, 15, 190));
        RenderUtil.drawOutline(context, boxX1, boxY1, boxX2 - boxX1, boxY2 - boxY1, 1.0f, new Color(40, 40, 50, 200).getRGB());

        // Text
        FontManager.INSTANCE.drawStringWithShadow(context, fullTag, x - width / 2.0f, y - height / 2.0f + 1.0f, Color.WHITE);

        // Armor items
        if (armor.getValue()) {
            List<ItemStack> items = new ArrayList<>();
            items.add(player.getEquippedStack(EquipmentSlot.MAINHAND));
            items.add(player.getEquippedStack(EquipmentSlot.HEAD));
            items.add(player.getEquippedStack(EquipmentSlot.CHEST));
            items.add(player.getEquippedStack(EquipmentSlot.LEGS));
            items.add(player.getEquippedStack(EquipmentSlot.FEET));
            items.add(player.getEquippedStack(EquipmentSlot.OFFHAND));

            float itemStartX = x - (items.size() * 16.0f) / 2.0f;
            float itemY = boxY1 - 18.0f;

            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = items.get(i);
                if (stack != null && !stack.isEmpty()) {
                    float ix = itemStartX + i * 16.0f;
                    context.drawItem(stack, (int) ix, (int) itemY);
                }
            }
        }
    }
}

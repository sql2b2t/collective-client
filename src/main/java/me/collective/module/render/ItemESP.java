package me.collective.module.render;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.Render3DEvent;
import me.collective.font.FontManager;
import me.collective.gui.GuiGfx;
import me.collective.module.Module;
import me.collective.render.RenderUtil3D;
import me.collective.render.Renderer3D;
import me.collective.render.ShapeMode;
import me.collective.setting.ActionSetting;
import me.collective.setting.BooleanSetting;
import me.collective.setting.ColorSetting;
import me.collective.setting.ItemListSetting;
import me.collective.setting.ModeSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Подсветка выпавших предметов: боксы сквозь стены с мягким затуханием к краю
 * дистанции и таблички над предметами — иконка, название, суммарное количество
 * и дистанция. Стопки одного типа рядом друг с другом объединяются в одну
 * табличку, а список интересных предметов задаётся пикером (ItemListSetting).
 * Позиции берутся из интерполированных координат кадра, поэтому при движении
 * боксы и таблички не дёргаются на 20 tps.
 */
public class ItemESP extends Module {
    /** Радиус объединения стопок одного типа (в блоках). */
    private static final double MERGE_RADIUS = 2.0;

    public final ModeSetting filter = add(new ModeSetting("Фильтр", "Все", "Все", "Список"));
    public final ItemListSetting items = add(new ItemListSetting("Предметы", List.of(
            Items.DIAMOND, Items.NETHERITE_INGOT, Items.NETHERITE_SCRAP, Items.ANCIENT_DEBRIS,
            Items.SHULKER_BOX, Items.ELYTRA, Items.TOTEM_OF_UNDYING, Items.ENCHANTED_GOLDEN_APPLE),
            v -> filter.process("Список")));
    public final ActionSetting selectAll = add(new ActionSetting("Все предметы", 0)
            .handle(() -> {
                List<Item> all = new ArrayList<>();
                for (Item item : BuiltInRegistries.ITEM) {
                    if (item != Items.AIR) all.add(item);
                }
                items.setValue(all);
                info("Выбраны все предметы (" + all.size() + ")");
            })
            .process("Выбрать")
            .handle((Supplier<Boolean>) () -> filter.process("Список")));
    public final NumberSetting range = add(new NumberSetting("Дистанция", 48.0, 8.0, 128.0, 4.0));
    public final ColorSetting sideColor = add(new ColorSetting("Заполнение", new Color(110, 200, 255, 55)));
    public final ColorSetting lineColor = add(new ColorSetting("Контур", new Color(110, 200, 255, 200)));
    public final ModeSetting shape = add(new ModeSetting("Форма", "Both", "Both", "Sides", "Lines"));
    public final BooleanSetting fade = add(new BooleanSetting("Затухание по дистанции", true));
    public final BooleanSetting tracers = add(new BooleanSetting("Трассеры", false));

    public final BooleanSetting names = add(new BooleanSetting("Названия", true));
    public final BooleanSetting icons = add(new BooleanSetting("Иконки", true, v -> names.getValue()));
    public final BooleanSetting showDistance = add(new BooleanSetting("Дистанция на табличке", true, v -> names.getValue()));
    public final BooleanSetting merge = add(new BooleanSetting("Объединять стопки", true, v -> names.getValue()));
    public final NumberSetting nameScale = add(new NumberSetting("Размер табличек", 1.0, 0.6, 1.6, 0.1, v -> names.getValue()));

    public ItemESP() {
        super("ItemESP", "Подсвечивает выпавшие предметы сквозь стены: боксы, иконки, количество и дистанция.", Category.RENDER);
    }

    // ------------------------------------------------------------- 3D-боксы

    /**
     * Полный скан мира — только раз в тик. Оба путя отрисовки (3D и 2D)
     * читают готовый список, дистанция между тиками уточняется на кадре.
     */
    private final List<ItemEntity> cachedItems = new ArrayList<>();

    @Override
    public void onUpdate() {
        this.cachedItems.clear();
        if (!isToggled() || mc.level == null || mc.player == null) return;

        double rangeSq = this.range.getValue() * this.range.getValue();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ItemEntity itemEntity)) continue;
            ItemStack stack = itemEntity.getItem();
            if (stack.isEmpty() || !passesFilter(stack.getItem())) continue;
            if (mc.player.distanceToSqr(itemEntity) > rangeSq) continue;
            this.cachedItems.add(itemEntity);
        }
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (!isToggled() || mc.level == null || mc.player == null || this.cachedItems.isEmpty()) return;

        double rangeBlocks = range.getValue();
        double rangeSq = rangeBlocks * rangeBlocks;
        ShapeMode mode = currentShape();
        Vec3 cam = event.camera != null
                ? event.camera.position()
                : mc.gameRenderer.mainCamera().position();
        float delta = event.tickDelta;

        for (ItemEntity itemEntity : this.cachedItems) {
            double distSq = mc.player.distanceToSqr(itemEntity);
            if (distSq > rangeSq) continue;
            double dist = Math.sqrt(distSq);

            double factor = fadeFactor(dist, rangeBlocks);
            Color side = fade(sideColor.getValue(), factor);
            Color line = fade(lineColor.getValue(), factor);
            if (side.getAlpha() <= 2 && line.getAlpha() <= 2) continue;

            AABB box = lerpedBox(itemEntity, delta).inflate(0.06);
            Renderer3D.box(box, side, line, mode, true);

            if (tracers.getValue()) {
                Vec3 center = box.getCenter();
                Renderer3D.line(cam.x, cam.y, cam.z, center.x, center.y, center.z, line);
            }
        }
    }

    /** Бокс по интерполированной позиции кадра — без рывков тикового обновления. */
    private static AABB lerpedBox(ItemEntity entity, float delta) {
        Vec3 lerped = entity.getPosition(delta);
        return entity.getBoundingBox().move(
                lerped.x - entity.getX(),
                lerped.y - entity.getY(),
                lerped.z - entity.getZ());
    }

    // ------------------------------------------------------------- 2D-таблички

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float delta) {
        if (!isToggled() || !names.getValue() || mc.level == null || mc.player == null
                || this.cachedItems.isEmpty()) return;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        double rangeBlocks = range.getValue();

        // Список собран раз в тик в onUpdate — здесь только отрисовка.
        List<ItemEntity> found = this.cachedItems;

        for (ItemLabel label : buildLabels(found, delta)) {
            Vector2f screen = RenderUtil3D.worldToScreen(label.x, label.y + 0.3, label.z, sw, sh);
            if (screen == null) continue;

            double dist = Math.sqrt(mc.player.distanceToSqr(label.x, label.y, label.z));
            renderLabel(context, label, screen.x, screen.y, dist, fadeFactor(dist, rangeBlocks));
        }
    }

    private void renderLabel(GuiGraphicsExtractor context, ItemLabel label, float cx, float cy, double dist, double fadeFactor) {
        FontManager fm = FontManager.INSTANCE;
        float fs = nameScale.getFloatValue();
        double textFactor = 0.25 + 0.75 * fadeFactor;

        String nameText = label.display.getHoverName().getString();
        String countText = label.count > 1 ? " ×" + label.count : "";
        String distSegment = showDistance.getValue() ? "  " + (int) Math.round(dist) + "м" : "";

        float nameW = fm.getStringWidthScaled(nameText, fs);
        float countW = fm.getStringWidthScaled(countText, fs);
        float distW = distSegment.isEmpty() ? 0 : fm.getStringWidthScaled(distSegment, fs);

        float fontH = fm.getFontHeightScaled(fs);
        float iconW = icons.getValue() ? 18f : 0f;
        float padX = 4f;
        float padY = 2f;

        float boxW = padX + iconW + nameW + countW + distW + padX;
        float boxH = Math.max(fontH + padY * 2, icons.getValue() ? 20f : 0f);
        float bx = Math.round(cx - boxW / 2f);
        float by = Math.round(cy - boxH - 3f);
        int radius = 3;

        GuiGfx.fillRounded(context, bx, by, boxW, boxH, radius, fadeInt(new Color(10, 10, 14, 200), fadeFactor));
        GuiGfx.outlineRounded(context, bx, by, boxW, boxH, radius, fadeInt(new Color(255, 255, 255, 30), fadeFactor));

        float tx = bx + padX;
        if (icons.getValue()) {
            context.item(label.display, (int) (bx + padX), Math.round(by + (boxH - 16f) / 2f));
            tx += iconW;
        }

        float textY = Math.round(by + (boxH - fontH) / 2f + 0.5f);
        fm.drawStringWithShadowScaled(context, nameText, tx, textY, fade(new Color(238, 242, 250), textFactor), fs);
        tx += nameW;
        if (!countText.isEmpty()) {
            fm.drawStringWithShadowScaled(context, countText, tx, textY, fade(new Color(255, 205, 95), textFactor), fs);
            tx += countW;
        }
        if (!distSegment.isEmpty()) {
            fm.drawStringWithShadowScaled(context, distSegment, tx, textY, fade(new Color(165, 170, 180), textFactor), fs);
        }
    }

    // ------------------------------------------------------------- Логика

    private List<ItemLabel> buildLabels(List<ItemEntity> entities, float delta) {
        List<ItemLabel> labels = new ArrayList<>();
        boolean mergeOn = merge.getValue();
        double mergeDistSq = MERGE_RADIUS * MERGE_RADIUS;

        for (ItemEntity entity : entities) {
            ItemStack stack = entity.getItem();
            ItemLabel target = null;
            if (mergeOn) {
                for (ItemLabel label : labels) {
                    if (label.item == stack.getItem() && label.distanceSqTo(entity, delta) <= mergeDistSq) {
                        target = label;
                        break;
                    }
                }
            }
            if (target == null) {
                target = new ItemLabel(stack.getItem());
                labels.add(target);
            }
            target.add(entity, delta);
        }
        return labels;
    }

    private boolean passesFilter(Item item) {
        return !filter.process("Список") || items.getValue().contains(item);
    }

    private double fadeFactor(double dist, double rangeBlocks) {
        if (!fade.getValue() || rangeBlocks <= 1) return 1.0;
        double start = rangeBlocks * 0.7;
        if (dist <= start) return 1.0;
        return Math.max(0.12, 1.0 - (dist - start) / (rangeBlocks - start));
    }

    private ShapeMode currentShape() {
        return switch (shape.getValue().trim()) {
            case "Sides" -> ShapeMode.Sides;
            case "Lines" -> ShapeMode.Lines;
            default -> ShapeMode.Both;
        };
    }

    private static Color fade(Color color, double factor) {
        double f = Math.max(0.0, Math.min(1.0, factor));
        int alpha = (int) Math.round(color.getAlpha() * f);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    private static int fadeInt(Color color, double factor) {
        return fade(color, factor).getRGB();
    }

    /** Стопка одного типа: несколько сущностей рядом рисуются одной табличкой. */
    private static final class ItemLabel {
        private final Item item;
        private final List<ItemEntity> parts = new ArrayList<>();
        private ItemStack display;
        private double x, y, z;
        private int count;

        private ItemLabel(Item item) {
            this.item = item;
        }

        private void add(ItemEntity entity, float delta) {
            ItemStack stack = entity.getItem();
            Vec3 lerped = entity.getPosition(delta);
            double top = lerped.y + entity.getBoundingBox().getYsize();
            if (parts.isEmpty()) {
                x = lerped.x;
                y = top;
                z = lerped.z;
            } else {
                double n = parts.size();
                x = (x * n + lerped.x) / (n + 1);
                y = Math.max(y, top);
                z = (z * n + lerped.z) / (n + 1);
            }
            count += stack.getCount();
            parts.add(entity);
            display = stack;
        }

        private double distanceSqTo(ItemEntity entity, float delta) {
            Vec3 lerped = entity.getPosition(delta);
            double top = lerped.y + entity.getBoundingBox().getYsize();
            double dx = x - lerped.x;
            double dy = y - top;
            double dz = z - lerped.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }
}
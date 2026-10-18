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
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
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
                for (Item item : Registries.ITEM) {
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

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (!isToggled() || mc.world == null || mc.player == null) return;

        double rangeBlocks = range.getValue();
        ShapeMode mode = currentShape();
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ItemEntity itemEntity)) continue;
            ItemStack stack = itemEntity.getStack();
            if (stack.isEmpty() || !passesFilter(stack.getItem())) continue;

            double dist = Math.sqrt(mc.player.squaredDistanceTo(itemEntity));
            if (dist > rangeBlocks) continue;

            double factor = fadeFactor(dist, rangeBlocks);
            Color side = fade(sideColor.getValue(), factor);
            Color line = fade(lineColor.getValue(), factor);
            if (side.getAlpha() <= 2 && line.getAlpha() <= 2) continue;

            Box box = itemEntity.getBoundingBox().expand(0.06);
            Renderer3D.box(box, side, line, mode, true);

            if (tracers.getValue()) {
                Vec3d center = box.getCenter();
                Renderer3D.line(cam.x, cam.y, cam.z, center.x, center.y, center.z, line);
            }
        }
    }

    // ------------------------------------------------------------- 2D-таблички

    @Override
    public void onRender2D(DrawContext context, float delta) {
        if (!isToggled() || !names.getValue() || mc.world == null || mc.player == null) return;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        double rangeBlocks = range.getValue();

        List<ItemEntity> found = new ArrayList<>();
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ItemEntity itemEntity)) continue;
            ItemStack stack = itemEntity.getStack();
            if (stack.isEmpty() || !passesFilter(stack.getItem())) continue;
            if (Math.sqrt(mc.player.squaredDistanceTo(itemEntity)) > rangeBlocks) continue;
            found.add(itemEntity);
        }
        if (found.isEmpty()) return;

        for (ItemLabel label : buildLabels(found)) {
            Vector2f screen = RenderUtil3D.worldToScreen(label.x, label.y + 0.3, label.z, sw, sh);
            if (screen == null) continue;

            double dist = Math.sqrt(mc.player.squaredDistanceTo(label.x, label.y, label.z));
            renderLabel(context, label, screen.x, screen.y, dist, fadeFactor(dist, rangeBlocks));
        }
    }

    private void renderLabel(DrawContext context, ItemLabel label, float cx, float cy, double dist, double fadeFactor) {
        FontManager fm = FontManager.INSTANCE;
        float fs = nameScale.getFloatValue();
        double textFactor = 0.25 + 0.75 * fadeFactor;

        String nameText = label.display.getName().getString();
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
            context.drawItem(label.display, (int) (bx + padX), Math.round(by + (boxH - 16f) / 2f));
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

    private List<ItemLabel> buildLabels(List<ItemEntity> entities) {
        List<ItemLabel> labels = new ArrayList<>();
        boolean mergeOn = merge.getValue();
        double mergeDistSq = MERGE_RADIUS * MERGE_RADIUS;

        for (ItemEntity entity : entities) {
            ItemStack stack = entity.getStack();
            ItemLabel target = null;
            if (mergeOn) {
                for (ItemLabel label : labels) {
                    if (label.item == stack.getItem() && label.distanceSqTo(entity) <= mergeDistSq) {
                        target = label;
                        break;
                    }
                }
            }
            if (target == null) {
                target = new ItemLabel(stack.getItem());
                labels.add(target);
            }
            target.add(entity);
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

        private void add(ItemEntity entity) {
            ItemStack stack = entity.getStack();
            double top = entity.getBoundingBox().maxY;
            if (parts.isEmpty()) {
                x = entity.getX();
                y = top;
                z = entity.getZ();
            } else {
                double n = parts.size();
                x = (x * n + entity.getX()) / (n + 1);
                y = Math.max(y, top);
                z = (z * n + entity.getZ()) / (n + 1);
            }
            count += stack.getCount();
            parts.add(entity);
            display = stack;
        }

        private double distanceSqTo(ItemEntity entity) {
            double dx = x - entity.getX();
            double dy = y - entity.getBoundingBox().maxY;
            double dz = z - entity.getZ();
            return dx * dx + dy * dy + dz * dz;
        }
    }
}

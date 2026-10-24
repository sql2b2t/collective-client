package me.collective.module.render;

import me.collective.setting.Setting;
import me.collective.setting.SettingGroup;
import me.collective.setting.BoolSetting;
import me.collective.setting.DoubleSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.IntSetting;
import me.collective.setting.KeybindSetting;

import me.collective.util.sort.ShulkerDataParser;
import me.collective.util.tracker.ChestTrackerDataManager;
import me.collective.util.tracker.TrackedContainer;
import me.collective.util.tracker.ContainerIndexRecorder;
import me.collective.module.ModuleManager;
import me.collective.event.events.TickEvent;
import me.collective.event.events.PacketEvent;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.world.level.block.EnderChestBlock;
import me.collective.module.Module;
import me.collective.category.Category;
import me.collective.setting.Keybind;
import me.collective.event.EventHandler;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Тултипы содержимого контейнеров (порт из BepHax, там — ContainerTooltips).
 *
 * <p>При наведении на сундук показывает, что в нём лежит, по данным индекса
 * ({@link ContainerIndex}). Для шалкеров в рамке — их реальное содержимое.
 *
 * <p>Сам индекс этот модуль тоже пополняет: если {@link ContainerIndex}
 * выключен, запись всё равно идёт, пока включён тултип.
 */
public class ContainerTooltips extends Module {
    private static final Identifier SLOT_TEXTURE = Identifier.ofVanilla("container/slot");
    private static final int ITEM_SIZE = 18;
    private static final long UPDATE_INTERVAL_MS = 100L;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDisplay = settings.createGroup("Отображение");
    private final SettingGroup sgFilters = settings.createGroup("Фильтры");

    private final Setting<Boolean> showAutomatically = sgGeneral.add(new BoolSetting.Builder()
        .name("показывать-само")
        .description("Показывать подсказку сразу при наведении на контейнер.")
        .defaultValue(true)
        .build());

    private final Setting<Keybind> showKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("клавиша-подсказки")
        .description("Клавиша, которую надо держать, чтобы показать подсказку (когда автопоказ выключен).")
        .defaultValue(Keybind.fromKey(342))
        .visible(() -> !showAutomatically.get())
        .build());

    private final Setting<Double> maxDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("макс-дистанция")
        .description("Дальше этого расстояния подсказка не показывается.")
        .defaultValue(5.0)
        .min(1.0)
        .max(10.0)
        .sliderRange(1.0, 10.0)
        .build());

    private final Setting<Boolean> hideInScreens = sgGeneral.add(new BoolSetting.Builder()
        .name("скрывать-в-меню")
        .description("Не рисовать подсказку, пока открыт экран.")
        .defaultValue(true)
        .build());

    private final Setting<TooltipPosition> position = sgDisplay.add(new EnumSetting.Builder<TooltipPosition>()
        .name("позиция")
        .description("Где на экране рисуется подсказка.")
        .defaultValue(TooltipPosition.TOP_CENTER)
        .build());

    private final Setting<Integer> offsetX = sgDisplay.add(new IntSetting.Builder()
        .name("смещение-x")
        .description("Сдвиг подсказки по горизонтали от выбранной позиции.")
        .defaultValue(0)
        .min(-500)
        .max(500)
        .sliderRange(-200, 200)
        .build());

    private final Setting<Integer> offsetY = sgDisplay.add(new IntSetting.Builder()
        .name("смещение-y")
        .description("Сдвиг подсказки по вертикали от выбранной позиции.")
        .defaultValue(0)
        .min(-500)
        .max(500)
        .sliderRange(-200, 200)
        .build());

    private final Setting<Boolean> showContainerName = sgDisplay.add(new BoolSetting.Builder()
        .name("показывать-имя")
        .description("Показывать название типа контейнера или его табличку.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> showPosition = sgDisplay.add(new BoolSetting.Builder()
        .name("показывать-координаты")
        .description("Показывать координаты контейнера.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> showLastUpdated = sgDisplay.add(new BoolSetting.Builder()
        .name("показывать-когда-прочитано")
        .description("Показывать, как давно контейнер был открыт.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> showTrackedContainers = sgFilters.add(new BoolSetting.Builder()
        .name("контейнеры-из-индекса")
        .description("Показывать содержимое сундуков и бочек по данным индекса.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> showItemFrameShulkers = sgFilters.add(new BoolSetting.Builder()
        .name("шалкеры-в-рамках")
        .description("Показывать содержимое шалкеров, висящих в рамках.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> showEmptyContainers = sgFilters.add(new BoolSetting.Builder()
        .name("пустые")
        .description("Показывать подсказку и для пустого контейнера.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> shulkerBadge = sgFilters.add(new BoolSetting.Builder()
        .name("метка-в-шалкере")
        .description("Рисовать в углу шалкера иконку того, что внутри.")
        .defaultValue(true)
        .build());

    private TooltipData currentTooltip = null;
    private long lastUpdateTime = 0L;
    private boolean wasInWorld = false;

    public ContainerTooltips() {
        super("ContainerTooltips", "Показывает содержимое контейнера или шалкера при наведении.", Category.RENDER);
    }

    @Override
    public void onActivate() {
        ChestTrackerDataManager.onModuleActivate();
        if (ownsRecorder()) ContainerIndexRecorder.setOptions(true, true, true);
        currentTooltip = null;
        lastUpdateTime = 0L;
    }

    @Override
    public void onDeactivate() {
        currentTooltip = null;
        ChestTrackerDataManager.saveData();
        ChestTrackerDataManager.onModuleDeactivate();
    }

    @Override
    public void onUpdate() {
        // Замена GameJoined/GameLeft из b2xy: выход из мира — сохранить индекс,
        // вход обрабатывается лениво через getData(), плюс периодический автосейв.
        if (mc.world == null) {
            if (wasInWorld) {
                wasInWorld = false;
                currentTooltip = null;
                ChestTrackerDataManager.saveData();
            }
            return;
        }
        wasInWorld = true;
        ChestTrackerDataManager.tickAutosave();
    }

    // ContainerIndex owns its options and recording when enabled. Otherwise the
    // tooltip is a standalone recorder, including ender chests it can preview.
    private boolean ownsRecorder() {
        ContainerIndex index = ModuleManager.INSTANCE.getModule(ContainerIndex.class);
        return index == null || !index.isToggled();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!ownsRecorder()) return;
        ContainerIndexRecorder.setOptions(true, true, true);
        ContainerIndexRecorder.onTick();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (ownsRecorder() && event.getPacket() instanceof InventoryS2CPacket packet) {
            ContainerIndexRecorder.onInventoryPacket(packet);
        }
    }

    public void onRender2D(GuiGraphics drawContext, float tickDelta) {
        if (mc.player == null || mc.world == null) return;

        if (hideInScreens.get() && mc.currentScreen != null) {
            currentTooltip = null;
            return;
        }
        if (!showAutomatically.get() && !showKey.get().isPressed()) {
            currentTooltip = null;
            return;
        }

        updateTooltipData();
        if (currentTooltip != null) renderTooltip(drawContext, currentTooltip);
    }

    private void updateTooltipData() {
        // Пересчёт каждый кадр лишний: наведение меняется медленно
        long now = System.currentTimeMillis();
        if (now - lastUpdateTime < UPDATE_INTERVAL_MS) return;
        lastUpdateTime = now;

        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.getPos().distanceTo(mc.player.getEyePos()) > maxDistance.get()) {
            currentTooltip = null;
            return;
        }

        if (showTrackedContainers.get() && hit instanceof BlockHitResult blockHit) {
            TooltipData data = trackedTooltip(blockHit.getBlockPos());
            if (data != null) {
                currentTooltip = data;
                return;
            }
        }

        if (showItemFrameShulkers.get()
            && hit instanceof EntityHitResult entityHit
            && entityHit.getEntity() instanceof ItemFrameEntity frame) {
            TooltipData data = itemFrameTooltip(frame);
            if (data != null) {
                currentTooltip = data;
                return;
            }
        }

        currentTooltip = null;
    }

    private TooltipData trackedTooltip(BlockPos pos) {
        String dimension = mc.world.getRegistryKey().getValue().toString();
        BlockState state = mc.world.getBlockState(pos);

        TrackedContainer container = ChestTrackerDataManager.getData().getContainer(pos, dimension);
        // Сундук могли записать по другой половине двойного
        if (container == null && state.getBlock() instanceof ChestBlock) {
            BlockPos other = me.collective.util.sort.ContainerGeometry.otherHalf(mc.world, pos);
            if (other != null) container = ChestTrackerDataManager.getData().getContainer(other, dimension);
        }
        // Содержимое эндер-сундука общее — подойдёт любая его запись
        if (container == null && state.getBlock() instanceof EnderChestBlock) {
            container = anyEnderChest(dimension);
        }

        if (container == null) return null;

        List<ItemStack> items = container.getItemStacks();
        if (items.isEmpty() && !showEmptyContainers.get()) return null;

        return new TooltipData(containerName(container), items,
            showPosition.get() ? formatPosition(pos) : null,
            showLastUpdated.get() ? formatLastUpdated(container.getLastUpdated()) : null);
    }

    private TooltipData itemFrameTooltip(ItemFrameEntity frame) {
        ItemStack held = frame.getHeldItemStack();
        if (held.isEmpty() || !(held.getItem() instanceof BlockItem blockItem)
            || !(blockItem.getBlock() instanceof ShulkerBoxBlock)) return null;

        List<ItemStack> items = ShulkerDataParser.parseShulkerContentsAsList(held);
        if (items.isEmpty() && !showEmptyContainers.get()) return null;

        return new TooltipData(held.getName().getString(), items,
            showPosition.get() ? formatPosition(frame.getBlockPos()) : null, null);
    }

    private TrackedContainer anyEnderChest(String dimension) {
        for (TrackedContainer container : ChestTrackerDataManager.getData().getAllContainers(dimension)) {
            if ("ender_chest".equals(container.getContainerType())) return container;
        }
        return null;
    }

    private void renderTooltip(GuiGraphics context, TooltipData data) {
        TextRenderer text = mc.textRenderer;
        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();

        int perRow = data.items.size() < 9 ? Math.max(1, data.items.size()) : 9;
        int rows = data.items.isEmpty() ? 1 : (int) Math.ceil(data.items.size() / 9.0);

        int contentWidth = perRow * ITEM_SIZE + 2;
        int contentHeight = rows * ITEM_SIZE + 2;
        if (data.items.isEmpty()) contentWidth = Math.max(contentWidth, text.getWidth("Пусто") + 4);

        int headerHeight = 0;
        if (showContainerName.get() && data.name != null) {
            headerHeight += 9 + 2;
            contentWidth = Math.max(contentWidth, text.getWidth(data.name) + 4);
        }
        if (data.position != null) {
            headerHeight += 9 + 1;
            contentWidth = Math.max(contentWidth, text.getWidth(data.position) + 4);
        }
        if (data.lastUpdated != null) {
            headerHeight += 9 + 1;
            contentWidth = Math.max(contentWidth, text.getWidth(data.lastUpdated) + 4);
        }

        int totalHeight = contentHeight + headerHeight;
        int x;
        int y;
        switch (position.get()) {
            case TOP_LEFT -> {
                x = 10;
                y = 10;
            }
            case TOP_RIGHT -> {
                x = screenWidth - contentWidth - 10;
                y = 10;
            }
            case CENTER -> {
                x = (screenWidth - contentWidth) / 2;
                y = (screenHeight - totalHeight) / 2;
            }
            case BOTTOM_LEFT -> {
                x = 10;
                y = screenHeight - totalHeight - 10;
            }
            case BOTTOM_RIGHT -> {
                x = screenWidth - contentWidth - 10;
                y = screenHeight - totalHeight - 10;
            }
            default -> {
                x = (screenWidth - contentWidth) / 2;
                y = 10;
            }
        }

        x = Math.max(12, Math.min(x, screenWidth - contentWidth - 12)) + offsetX.get();
        y = Math.max(12, Math.min(y, screenHeight - totalHeight - 12)) + offsetY.get();

        TooltipBackgroundRenderer.render(context, x, y, contentWidth, totalHeight, null);

        int currentY = y;
        if (showContainerName.get() && data.name != null) {
            context.drawText(text, data.name, x, currentY, -1, true);
            currentY += 9 + 2;
        }
        if (data.position != null) {
            context.drawText(text, data.position, x, currentY, -8355712, true);
            currentY += 9 + 1;
        }
        if (data.lastUpdated != null) {
            context.drawText(text, data.lastUpdated, x, currentY, -8355712, true);
            currentY += 9 + 1;
        }

        if (data.items.isEmpty()) {
            context.drawText(text, "Пусто", x, currentY + 2, -8355712, true);
            return;
        }

        context.fill(x, currentY, x + perRow * ITEM_SIZE + 2, currentY + rows * ITEM_SIZE + 2, -7631989);

        int index = 0;
        for (int row = 0; row < rows && index < data.items.size(); row++) {
            for (int col = 0; col < perRow && index < data.items.size(); col++) {
                ItemStack stack = data.items.get(index++);
                int itemX = x + col * ITEM_SIZE + 2;
                int itemY = currentY + row * ITEM_SIZE + 2;
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, SLOT_TEXTURE, itemX, itemY, ITEM_SIZE, ITEM_SIZE);
                context.drawItem(stack, itemX + 1, itemY + 1);
                context.drawStackOverlay(text, stack, itemX + 1, itemY + 1);
                if (shulkerBadge.get()) drawShulkerBadge(context, stack, itemX + ITEM_SIZE - 8, itemY + ITEM_SIZE - 8);
            }
        }
    }

    /** Иконка самого частого предмета внутри шалкера — видно, что за кит. */
    private void drawShulkerBadge(GuiGraphics context, ItemStack stack, int x, int y) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)
            || !(blockItem.getBlock() instanceof ShulkerBoxBlock)) return;

        List<ItemStack> nested = ShulkerDataParser.parseShulkerContentsAsList(stack);
        if (nested.isEmpty()) return;

        context.fill(x - 1, y - 1, x + 9, y + 9, -16777216);
        context.drawItem(nested.get(0), x, y);
    }

    private String containerName(TrackedContainer container) {
        String custom = container.getCustomName();
        return custom != null && !custom.isEmpty() ? custom : containerTypeName(container.getContainerType());
    }

    private static String containerTypeName(String type) {
        if (type == null) return "Контейнер";
        return switch (type) {
            case "chest" -> "Сундук";
            case "trapped_chest" -> "Зачарованный сундук";
            case "barrel" -> "Бочка";
            case "shulker_box" -> "Шалкер";
            case "ender_chest" -> "Эндер-сундук";
            case "hopper" -> "Воронка";
            case "dispenser" -> "Раздатчик";
            case "dropper" -> "Сбросчик";
            case "copper_chest" -> "Медный сундук";
            default -> "Контейнер";
        };
    }

    private static String formatPosition(BlockPos pos) {
        return "§7[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]";
    }

    private static String formatLastUpdated(long timestamp) {
        long minutes = (System.currentTimeMillis() - timestamp) / 60000L;
        long hours = minutes / 60L;
        long days = hours / 24L;
        if (days > 0) return "§7прочитан " + days + " дн. назад";
        if (hours > 0) return "§7прочитан " + hours + " ч. назад";
        return minutes > 0 ? "§7прочитан " + minutes + " мин. назад" : "§7прочитан только что";
    }

    public enum TooltipPosition {
        TOP_LEFT,
        TOP_CENTER,
        TOP_RIGHT,
        CENTER,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    private static class TooltipData {
        final String name;
        final List<ItemStack> items;
        final String position;
        final String lastUpdated;

        TooltipData(String name, List<ItemStack> items, String position, String lastUpdated) {
            this.name = name;
            this.items = items != null ? items : new ArrayList<>();
            this.position = position;
            this.lastUpdated = lastUpdated;
        }
    }
}
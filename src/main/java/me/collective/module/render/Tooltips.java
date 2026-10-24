package me.collective.module.render;
import me.collective.gui.ContainerPreviewScreen;

import me.collective.setting.Setting;
import me.collective.setting.SettingGroup;
import me.collective.setting.BoolSetting;
import me.collective.setting.EnumSetting;

import me.collective.module.tooltips.ContainerContents;
import me.collective.module.tooltips.ContainerTooltipComponent;
import me.collective.module.tooltips.CountingDataOutput;
import me.collective.module.tooltips.DominantStacks;
import me.collective.module.tooltips.MapTooltipComponent;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.category.Category;
import me.collective.event.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.Bees;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Расширенные тултипы предметов.
 *
 * <p>Порт модуля {@code Tooltips} из OpenMio (Minecraft 1.21.1). Набор настроек,
 * значения по умолчанию, порядок проверок и вид результата повторяют
 * оригинал; отличия перечислены в {@link ContainerTooltipComponent} и
 * {@link MapTooltipComponent}.
 *
 * <p>Архитектура оригинала — подмена вызова
 * {@code GuiGraphics.drawTooltip} через {@code @Redirect} с передачей готового
 * списка компонентов в {@code drawTooltipsHook} — в Yarn 1.21.11 воспроизведена
 * один в один: тот же перехват и тот же
 * {@code GuiGraphics.drawTooltipImmediately}.
 *
 * <p>Тултип собирается как список компонентов: строки текста плюс наши
 * компоненты-сетка и карта. Отрисовку рамки в оригинале подкрашивали через
 * {@code renderBorder}, которого в 1.21.11 нет: цвета там — {@code static
 * final}, а рамка рисуется текстурой. Поэтому при включённом
 * {@link #colored} рамка рисуется вручную, а иначе тултип рисуется ванильным
 * кодом без единой правки.
 */
public class Tooltips extends Module {

    /** Текст-подсказка оригинала про Alt. */
    private static final String ALT_HINT = "Hold Left-Alt to hide item amount.";

    private final SettingGroup sgMain = settings.createGroup("Основное");
    private final SettingGroup sgExtra = settings.createGroup("Дополнительно");

    private final Setting<Boolean> colored = sgMain.add(new BoolSetting.Builder()
            .name("colored")
            .description("Цветная рамка тултипа по цвету шилкера.")
            .defaultValue(true)
            .build());

    public enum MajorityMode {
        MINI, FULL
    }

    public enum MajorityPosition {
        TOP_RIGHT, CENTER
    }

    private final Setting<Boolean> majorityItem = sgMain.add(new BoolSetting.Builder()
            .name("majorityItem")
            .description("Подменять иконку шилкера в слоте доминирующим предметом.")
            .defaultValue(true)
            .build());

    private final Setting<MajorityMode> majorityMode = sgMain.add(new EnumSetting.Builder<MajorityMode>()
            .name("majorityMode")
            .description("Режим отображения: миниатюра (Mini) или полная замена (Full).")
            .defaultValue(MajorityMode.MINI)
            .visible(() -> majorityItem.get())
            .build());

    private final Setting<MajorityPosition> majorityPos = sgMain.add(new EnumSetting.Builder<MajorityPosition>()
            .name("majorityPosition")
            .description("Позиция миниатюры: правый верхний угол (TopRight) или центр (Center).")
            .defaultValue(MajorityPosition.TOP_RIGHT)
            .visible(() -> majorityItem.get() && majorityMode.get() == MajorityMode.MINI)
            .build());

    private final Setting<Boolean> keepText = sgMain.add(new BoolSetting.Builder()
            .name("keepText")
            .description("Не заменять строки тултипа, а дописать компонент под ними.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> shulkerContent = sgMain.add(new BoolSetting.Builder()
            .name("shulkerContent")
            .description("Показывать содержимое контейнера сеткой 3×9.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> beeNest = sgExtra.add(new BoolSetting.Builder()
            .name("beeNest")
            .description("Показывать позицию цветка для пчёл в предмете улья.")
            .defaultValue(false)
            .build());

    private final Setting<Boolean> mapOverlay = sgExtra.add(new BoolSetting.Builder()
            .name("mapOverlay")
            .description("Показывать изображение заполненной карты в тултипе.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> size = sgExtra.add(new BoolSetting.Builder()
            .name("size")
            .description("Показывать вес предмета в NBT: B, KB или MB.")
            .defaultValue(false)
            .build());

    /** Immutable render snapshot, built only after client-side inventory updates. */
    private List<DominantStacks.Entry> list = List.of();
    private List<ItemStack> previousStacks = List.of();

    /** Цвет рамки для текущего тултипа; {@code null} — рамка не красится. */
    private Integer color;

    public Tooltips() {
        super("Tooltips", "Расширенные подсказки предметов (содержимое шалкеров, карты).", Category.RENDER);
        INSTANCE = this;
    }

    /** Хот-путь миксинов: ноль линейных поисков модулей на каждый отрисованный слот. */
    public static Tooltips INSTANCE;

    // ── Накопление списка по пакетам ──

    @EventHandler
    public void onTick(TickEvent.Post event) {
        if (!majorityItem.get() || mc.player == null) {
            clearSnapshot();
            return;
        }
        var slots = mc.player.currentScreenHandler.slots;
        boolean changed = slots.size() != previousStacks.size();
        if (!changed) {
            for (int i = 0; i < slots.size(); i++) {
                if (!ItemStack.areEqual(slots.get(i).getStack(), previousStacks.get(i))) {
                    changed = true;
                    break;
                }
            }
        }
        if (!changed) return;
        List<ItemStack> stacks = new ArrayList<>(slots.size());
        List<DominantStacks.Entry> next = new ArrayList<>();
        for (Slot slot : slots) {
            ItemStack copy = slot.getStack().copy();
            stacks.add(copy);
            DominantStacks.Entry entry = DominantStacks.of(copy);
            if (entry != null) next.add(entry);
        }
        previousStacks = List.copyOf(stacks);
        list = List.copyOf(next);
    }

    @Override
    public void onDisable() {
        clearSnapshot();
    }

    @Override
    public void onWorldChange() {
        clearSnapshot();
    }

    private void clearSnapshot() {
        list = List.of();
        previousStacks = List.of();
    }

    /** Список для подмены иконок; используется миксином отрисовки слотов. */
    public List<DominantStacks.Entry> dominantEntries() {
        return list;
    }

    /** Активен ли режим подмены иконок. */
    public boolean majorityActive() {
        return majorityItem.get() && isActive();
    }

    public MajorityMode getMajorityMode() {
        return majorityMode != null ? majorityMode.get() : MajorityMode.MINI;
    }

    public MajorityPosition getMajorityPosition() {
        return majorityPos != null ? majorityPos.get() : MajorityPosition.TOP_RIGHT;
    }

    // ── Сборка тултипа ──

    /**
     * Преобразует строки тултипа и данные в компоненты и дописывает наши.
     *
     * <p>Аналог {@code getList1128} и {@code RenderWithTooltipEvent}: на вход
     * — строки и {@code TooltipData} из ванильного вызова, на выход — список
     * компонентов, который уходит в {@code drawTooltipImmediately}.
     *
     * @return готовый список компонентов
     */
    public List<TooltipComponent> buildComponents(List<Component> lines,
                                                   net.minecraft.item.tooltip.TooltipData data,
                                                   ItemStack stack, int mouseX, int mouseY) {
        List<TooltipComponent> components = new ArrayList<>();

        for (Component line : lines) {
            // «3/27» и «и ещё N…» не показываем: содержимое уже видно сеткой
            if (isContainerLabel(line)) continue;

            components.add(TooltipComponent.of(line.asOrderedText()));
        }

        if (data != null) {
            components.add(TooltipComponent.of(data));
        }

        // цвет рамки — как в оригинале, с проверкой на шилкер
        color = colored.get() ? borderColorOf(stack) : null;

        // содержимое контейнера
        List<ItemStack> slots = null;
        boolean hasContents = false;

        if (shulkerContent.get()) {
            slots = ContainerContents.read(stack);
            hasContents = ContainerContents.hasContents(slots);
        }

        // у бандла ваниль уже рисует сетку содержимого, второй раз не нужно
        boolean vanillaGrid = data != null;

        if (hasContents && !vanillaGrid) {
            if (keepText.get()) {
                components.add(new ContainerTooltipComponent(slots, mapOverlay.get(), mouseX, mouseY));
            } else {
                components.clear();
                components.add(TooltipComponent.of(stack.getName().asOrderedText()));
                components.add(new ContainerTooltipComponent(slots, mapOverlay.get(), mouseX, mouseY));
            }

            openPreviewScreenOnMiddleClick(slots, stack);
        }

        // карта
        if (stack.isOf(Items.FILLED_MAP)) {
            MapIdComponent mapId = stack.get(DataComponentTypes.MAP_ID);
            MapState mapState = mapStateOf(stack, mapId);

            if (mapState != null) {
                if (keepText.get()) {
                    components.add(new MapTooltipComponent(mapId.id(), mapState));
                } else {
                    components.clear();
                    components.add(TooltipComponent.of(stack.getName().asOrderedText()));
                    components.add(new MapTooltipComponent(mapId.id(), mapState));
                }

                if (mapOverlay.get()) {
                    components.add(TooltipComponent.of(
                            Component.literal(ALT_HINT).formatted(ChatFormatting.YELLOW).asOrderedText()));
                }
            }
        }

        // улей: позиции цветков
        if (beeNest.get()) {
            BeesComponent bees = stack.get(DataComponentTypes.BEES);
            if (bees != null) {
                for (var bee : bees.bees()) {
                    NbtCompound nbt = bee.entityData().getNbtWithoutId();
                    if (nbt == null || !nbt.contains("FlowerPos")) continue;

                    BlockPos pos = flowerPos(nbt);
                    if (pos != null) {
                        components.add(TooltipComponent.of(
                                Component.literal("Position " + pos.toShortString()).asOrderedText()));
                    }
                }
            }
        }

        // размер предмета
        if (size.get()) {
            String sizeText = sizeOf(stack);
            if (sizeText != null) {
                components.add(TooltipComponent.of(Component.literal(sizeText).asOrderedText()));
            }
        }

        return components;
    }

    /**
     * Строки ванильного тултипа, описывающие содержимое контейнера.
     *
     * <p>В 1.21.11 {@code ContainerComponent} добавляет две строки:
     * {@code item.container.item_count} («3/27») и
     * {@code item.container.more_items} («и ещё N…»). Они и есть надписи о том,
     * что лежит внутри, поэтому отсекаются; содержимое показывает сетка.
     */
    private static boolean isContainerLabel(Component line) {
        if (!(line.getContent() instanceof net.minecraft.text.TranslatableTextContent content)) {
            return false;
        }

        String key = content.getKey();
        return "item.container.item_count".equals(key)
            || "item.container.more_items".equals(key);
    }

    /** Текущий цвет рамки; забирает миксин отрисовки. */
    public Integer borderColor() {
        Integer value = color;
        color = null;
        return value;
    }

    // ── Вспомогательное ──

    /**
     * Состояние карты.
     *
     * <p>Перенос {@code getMapState2653}: серверное состояние, затем кэш по
     * имени для карт с собственным названием.
     */
    private static MapState mapStateOf(ItemStack stack, MapIdComponent id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.world == null || id == null) return null;

        MapState state = FilledMapItem.getMapState(id, mc.world);
        if (id.id() != -1 && state != null) return state;

        if (stack.contains(DataComponentTypes.CUSTOM_NAME)) {
            return ContainerTooltipComponent.NamedMaps.get(stack.getName().getString());
        }

        return null;
    }

    /** Координаты цветка: массив из трёх int либо compound с x/y/z. */
    private static BlockPos flowerPos(NbtCompound nbt) {
        var value = nbt.get("FlowerPos");
        if (value == null) return null;

        if (value instanceof net.minecraft.nbt.NbtIntArray array) {
            int[] values = array.getIntArray();
            if (values.length >= 3) return new BlockPos(values[0], values[1], values[2]);
        }

        if (value instanceof net.minecraft.nbt.NbtCompound compound
                && compound.contains("x") && compound.contains("y") && compound.contains("z")) {
            return new BlockPos(compound.getInt("x", 0), compound.getInt("y", 0), compound.getInt("z", 0));
        }

        return null;
    }

    /** Вес предмета: считается объём сериализованного NBT. */
    private static String sizeOf(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;

        try {
            var ops = RegistryOps.of(NbtOps.INSTANCE, mc.player.getRegistryManager());
            var encoded = ItemStack.CODEC.encodeStart(ops, stack).result().orElse(null);
            if (encoded == null) return null;

            CountingDataOutput counter = new CountingDataOutput();
            encoded.write(counter);

            long count = counter.count();
            long kb = count / 1024;
            long mb = kb / 1024;

            if (mb > 0) return String.format("%,d MB", mb);
            if (kb > 0) return String.format("%,d KB", kb);
            return String.format("%,d B", count);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Цвет рамки по цвету шилкера.
     *
     * <p>Оригинал берёт {@code MapColor.color}, переводит в float-компоненты
     * и затем в {@code Color}; рамка красится этим цветом и его затемнённой
     * версией ({@code darker()} в AWT — это умножение на 0.25).
     */
    private static Integer borderColorOf(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return null;
        if (!(blockItem.getBlock() instanceof net.minecraft.block.ShulkerBoxBlock block)) return null;

        if (block.getColor() == null) return null;

        MapColor mapColor = block.getColor().getMapColor();
        return 0xFF000000 | mapColor.color;
    }

    /** Затемнённая версия цвета для второй половины рамки. */
    public static int darker(int rgb) {
        int r = (int) (((rgb >> 16) & 0xFF) * 0.25f);
        int g = (int) (((rgb >> 8) & 0xFF) * 0.25f);
        int b = (int) ((rgb & 0xFF) * 0.25f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Средняя кнопка мыши открывает содержимое в отдельном окне. */
    private void openPreviewScreenOnMiddleClick(List<ItemStack> slots, ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.currentScreen instanceof HandledScreen<?>)) return;
        // уже открыто окно просмотра — повторно не открываем
        if (mc.currentScreen instanceof ContainerPreviewScreen) return;
        if (GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) != GLFW.GLFW_PRESS) {
            return;
        }

        mc.setScreen(new ContainerPreviewScreen(slots, stack));
    }
}
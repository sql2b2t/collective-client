package me.collective.module.render;
import me.collective.gui.ContainerPreviewScreen;

import me.collective.setting.Setting;
import me.collective.setting.SettingGroup;
import me.collective.setting.BoolSetting;

import me.collective.module.tooltips.ContainerContents;
import me.collective.module.tooltips.ContainerTooltipComponent;
import me.collective.module.tooltips.CountingDataOutput;
import me.collective.module.tooltips.DominantStacks;
import me.collective.module.tooltips.MapTooltipComponent;
import me.collective.event.events.PacketEvent;
import me.collective.module.Module;
import me.collective.category.Category;
import me.collective.event.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BeesComponent;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.block.MapColor;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryOps;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
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
 * {@code DrawContext.drawTooltip} через {@code @Redirect} с передачей готового
 * списка компонентов в {@code drawTooltipsHook} — в Yarn 1.21.11 воспроизведена
 * один в один: тот же перехват и тот же
 * {@code DrawContext.drawTooltipImmediately}.
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

    private final Setting<Boolean> majorityItem = sgMain.add(new BoolSetting.Builder()
            .name("majorityItem")
            .description("Подменять иконку шилкера в слоте доминирующим предметом.")
            .defaultValue(false)
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

    /**
     * Список контейнеров с доминирующими предметами.
     *
     * <p>Копия {@code Tooltips.list}: синхронизированный список, который
     * наполняется из сетевых пакетов.
     */
    private final List<DominantStacks.Entry> list = Collections.synchronizedList(new ArrayList<>());

    /** Цвет рамки для текущего тултипа; {@code null} — рамка не красится. */
    private Integer color;

    public Tooltips() {
        super("Tooltips", "Расширенные подсказки предметов (содержимое шалкеров, карты).", Category.RENDER);
        INSTANCE = this;
    }

    /** Хот-путь миксинов: ноль линейных поисков модулей на каждый отрисованный слот. */
    public static Tooltips INSTANCE;

    // ── Накопление списка по пакетам ──

    /**
     * Обработка входящих пакетов.
     *
     * <p>Перенос обработчиков {@code ChannelRead0Event}: полный инвентарь
     * пересчитывает список, обновление одного слота — тоже полностью, как в
     * оригинале.
     */
    @EventHandler
    public void onPacket(PacketEvent.Receive event) {
        if (!majorityItem.get()) return;

        MinecraftClient mc = MinecraftClient.getInstance();

        if (event.packet instanceof InventoryS2CPacket packet) {
            list.clear();
            for (ItemStack stack : packet.contents()) {
                DominantStacks.Entry entry = DominantStacks.of(stack);
                if (entry != null) list.add(entry);
            }
            return;
        }

        if (event.packet instanceof ScreenHandlerSlotUpdateS2CPacket) {
            if (mc.player == null || mc.player.currentScreenHandler == null) return;

            list.clear();
            for (Slot slot : mc.player.currentScreenHandler.slots) {
                DominantStacks.Entry entry = DominantStacks.of(slot.getStack());
                if (entry != null) list.add(entry);
            }
        }
    }

    /** Список для подмены иконок; используется миксином отрисовки слотов. */
    public List<DominantStacks.Entry> dominantEntries() {
        return list;
    }

    /** Активен ли режим подмены иконок. */
    public boolean majorityActive() {
        return majorityItem.get() && isActive();
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
    public List<TooltipComponent> buildComponents(List<Text> lines,
                                                   net.minecraft.item.tooltip.TooltipData data,
                                                   ItemStack stack, int mouseX, int mouseY) {
        List<TooltipComponent> components = new ArrayList<>();

        for (Text line : lines) {
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

        if (shulkerContent.get() && stack.get(DataComponentTypes.CONTAINER) != null) {
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
                            Text.literal(ALT_HINT).formatted(Formatting.YELLOW).asOrderedText()));
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
                                Text.literal("Position " + pos.toShortString()).asOrderedText()));
                    }
                }
            }
        }

        // размер предмета
        if (size.get()) {
            String sizeText = sizeOf(stack);
            if (sizeText != null) {
                components.add(TooltipComponent.of(Text.literal(sizeText).asOrderedText()));
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
    private static boolean isContainerLabel(Text line) {
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
        MinecraftClient mc = MinecraftClient.getInstance();
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
        MinecraftClient mc = MinecraftClient.getInstance();
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
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.currentScreen instanceof HandledScreen<?>)) return;
        // уже открыто окно просмотра — повторно не открываем
        if (mc.currentScreen instanceof ContainerPreviewScreen) return;
        if (GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) != GLFW.GLFW_PRESS) {
            return;
        }

        mc.setScreen(new ContainerPreviewScreen(slots, stack));
    }
}
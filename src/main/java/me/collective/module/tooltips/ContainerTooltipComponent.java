package me.collective.module.tooltips;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.List;

/**
 * Сетка содержимого контейнера в тултипе.
 *
 * <p>Порт {@code HeightSearchHelper4} один в один: размеры 155×53, ячейка 17
 * *пикселей*, 9 ячеек в ряду, подсветка ячейки под курсором и описание
 * предмета из сетки.
 *
 * <p>Отличия от оригинала, вынужденные версией:
 *
 * <ul>
 *   <li>счётчик {@code 69} рисуется строкой {@code "0"}, а не красным
 *       {@code FormattedCharSequence}: в Yarn у {@code drawStackOverlay} есть только
 *       перегрузка со {@code String}, форматирование недоступно;
 *   <li>карта внутри ячейки блитится готовой текстурой карты вместо вызова
 *       ванильного картографического рендерера — в 1.21.11 он принимает
 *       {@code MapRenderState} и очередь render-команд, недоступную из
 *       компонента тултипа.
 * </ul>
 */
public class ContainerTooltipComponent implements ClientTooltipComponent {

    /** Служебная метка оригинала для стака с нулевым количеством. */
    public static final int ZERO_MARKER = 69;

    public static final int WIDTH = 155;
    public static final int HEIGHT = 53;

    private static final int CELL = 17;
    private static final int COLUMNS = 9;

    private final List<ItemStack> list;
    private final boolean mapOverlay;
    private final int mouseX;
    private final int mouseY;

    /**
     * @param list       27 ячеек контейнера
     * @param mapOverlay рисовать ли карту внутри ячеек
     * @param mouseX     координата курсора, оригинал берёт её из статических полей
     *                   события рендера
     * @param mouseY     координата курсора
     */
    public ContainerTooltipComponent(List<ItemStack> list, boolean mapOverlay, int mouseX, int mouseY) {
        this.list = list;
        this.mapOverlay = mapOverlay;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    @Override
    public int getHeight(Font textRenderer) {
        return HEIGHT;
    }

    @Override
    public int getWidth(Font textRenderer) {
        return WIDTH;
    }

    @Override
    public void extractImage(Font textRenderer, int x, int y, int width, int height, GuiGraphicsExtractor context) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack hovered = null;

        for (int i = 0; i < list.size(); i++) {
            int cellX = x + CELL * (i % COLUMNS);
            int cellY = y + CELL * (i / COLUMNS);

            boolean inside = mouseX >= cellX && mouseX <= cellX + CELL
                    && mouseY >= cellY && mouseY <= cellY + CELL;

            ItemStack stack = list.get(i);

            if (inside && !stack.isEmpty() && hovered == null) hovered = stack;

            MapId mapId = mapIdOf(stack);
            MapItemSavedData mapState = mapOverlay ? mapStateOf(stack, mapId, mc) : null;

            if (!mapOverlay || mapId == null || mapState == null) {
                context.item(stack, cellX + 1, cellY + 1);
                // метка нулевого количества в оригинале рисуется красной «0»
                context.itemDecorations(textRenderer, stack, cellX + 1, cellY + 1,
                        stack.getCount() == ZERO_MARKER ? "0" : null);
            } else {
                drawMap(context, mapId, mapState, cellX + 1, cellY + 1);

                // при зажатом Alt счётчик на карте скрывается
                if (!isAltPressed(mc)) {
                    context.itemDecorations(textRenderer, stack, cellX + 1, cellY + 1);
                }
            }
        }

        if (hovered == null || hovered.is(Items.AIR)) return;

        context.setTooltipForNextFrame(textRenderer, hovered, mouseX, mouseY);
    }

    private static MapId mapIdOf(ItemStack stack) {
        MapId id = stack.get(DataComponents.MAP_ID);
        return id != null ? id : new MapId(-1);
    }

    /**
     * Состояние карты из предмета.
     *
     * <p>Перенос {@code getMapState2654}: сперва пробуем серверное состояние,
     * затем — кэш по имени, если у предмета есть своё имя.
     */
    private static MapItemSavedData mapStateOf(ItemStack stack, MapId id, Minecraft mc) {
        if (mc.level == null) return null;

        MapItemSavedData state = MapItem.getSavedData(id, mc.level);
        if (id.id() != -1 && state != null) return state;

        if (stack.has(DataComponents.CUSTOM_NAME)) {
            return NamedMaps.get(stack.getHoverName().getString());
        }

        return null;
    }

    /** Блит текстуры карты в ячейку сетки. */
    private static void drawMap(GuiGraphicsExtractor context, MapId id, MapItemSavedData state, int x, int y) {
        Minecraft mc = Minecraft.getInstance();

        var texture = mc.getMapTextureManager().prepareMapTexture(id, state);
        if (texture == null) return;

        var matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(0.25f);

        context.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, texture,
                0, 0, 0f, 0f, 128, 128, 128, 128);

        matrices.popMatrix();
    }

    private static boolean isAltPressed(Minecraft mc) {
        return org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().handle(), org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    /**
     * Кэш состояний карт по названию.
     *
     * <p>В оригинале это поле {@code map} у вспомогательного класса; здесь он
     * пустой по умолчанию, но оставлен, чтобы повторить поведение с
     * одноимёнными картами.
     */
    public static final class NamedMaps {
        private NamedMaps() {
        }

        private static final java.util.Map<String, MapItemSavedData> MAPS = new java.util.concurrent.ConcurrentHashMap<>();

        public static void put(String name, MapItemSavedData state) {
            if (name != null && state != null) MAPS.put(name, state);
        }

        public static MapItemSavedData get(String name) {
            return MAPS.get(name);
        }
    }
}
package me.collective.module.tooltips;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.resources.Identifier;

/**
 * Превью карты в тултипе.
 *
 * <p>Порт {@code HeightTooltipComponent}: размеры 108×110. Оригинал рисует
 * подложку {@code textures/map/map_background.png} с масштабом 1.5, затем саму
 * карту с масштабом 0.75 и сдвигом на 8 пикселей.
 *
 * <p>Геометрия и подложка перенесены как есть. Сама карта блитится готовой
 * текстурой, а не через {@code MapRenderer}: в 1.21.11 он принимает
 * {@code MapRenderState} и очередь render-команд, до которых нельзя дотянуться
 * из компонента тултипа.
 */
public class MapTooltipComponent implements ClientTooltipComponent {

    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/map/map_background.png");

    public static final int WIDTH = 108;
    public static final int HEIGHT = 110;

    private final int id;
    private final MapItemSavedData mapState;

    public MapTooltipComponent(int id, MapItemSavedData mapState) {
        this.id = id;
        this.mapState = mapState;
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
        var matrices = context.pose();

        // подложка: масштаб 1.5, как в оригинале
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(1.5f);
        context.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0, 0f, 0f, 64, 64, 64, 64);
        matrices.popMatrix();

        Minecraft mc = Minecraft.getInstance();
        var texture = mc.getMapTextureManager().prepareMapTexture(new MapId(id), mapState);
        if (texture == null) return;

        // карта: масштаб 0.75 и сдвиг 8 пикселей
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(0.75f);
        matrices.translate(8f, 8f);
        context.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0f, 0f, 128, 128, 128, 128);
        matrices.popMatrix();
    }

    public int id() {
        return id;
    }

    public MapItemSavedData mapState() {
        return mapState;
    }
}
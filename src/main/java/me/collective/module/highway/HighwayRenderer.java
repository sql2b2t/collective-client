package me.collective.module.highway;

import me.collective.event.events.Render3DEvent;
import me.collective.render.Renderer3D;
import me.collective.render.ShapeMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Отрисовка мира для {@link HighwayBuilderHIG}: контуры зон копания/укладки
 * и FX на только что поставленные блоки. Логика выбора позиций остаётся
 * в билдере — рендерер только спрашивает предикаты и рисует результат.
 */
final class HighwayRenderer {
    /** Время жизни подсветки поставленного блока, мс. */
    private static final long PLACEMENT_FX_MS = 4000L;

    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final MBlockPos POS_RENDER = new MBlockPos();
    private static final Map<BlockPos, Long> PLACED_FX = new HashMap<>();

    private HighwayRenderer() {
    }

    /** Логика установки отмечает блок, чтобы он мигнул при постройке. */
    static void notePlacement(BlockPos pos) {
        PLACED_FX.put(pos, System.currentTimeMillis());
    }

    /** Подписи над двойной копкой (normal/packet) — 2D-слой. */
    static void render2D(HighwayBuilderHIG b, DrawContext context) {
        if (!b.isToggled() || b.suspended || !b.renderMine.getValue()) return;
        if (b.normalMining != null) b.normalMining.renderLetter(context);
        if (b.packetMining != null) b.packetMining.renderLetter(context);
    }

    static void render3D(HighwayBuilderHIG b, Render3DEvent event) {
        if (!b.isToggled() || mc.player == null || mc.world == null) return;
        if (b.suspended || b.blockPosProvider == null) return;

        if (b.renderMine.getValue()) {
            render(b, b.blockPosProvider.getFront(), pos -> b.canMine(pos, true), true);
            if (b.floor.getValue() == HighwayBuilderHIG.Floor.Replace)
                render(b, b.blockPosProvider.getFloor(), pos -> b.canMine(pos, false), true);
            if (b.railings.getValue())
                render(b, b.blockPosProvider.getRailings(0), pos -> b.canMine(pos, false), true);
            if (b.mineAboveRailings.getValue())
                render(b, b.blockPosProvider.getRailings(1), pos -> b.canMine(pos, true), true);
            if (b.state == HighwayBuilderHIG.State.MineEChestBlockade)
                render(b, b.blockPosProvider.getBlockade(true, b.blockadeType.getValue()), pos -> b.canMine(pos, true), true);
        }

        if (b.renderPlace.getValue()) {
            render(b, b.blockPosProvider.getLiquids(), pos -> b.canPlace(pos, true), false);

            if (b.railings.getValue()) {
                render(b, b.blockPosProvider.getRailings(0), pos -> b.canPlace(pos, false), false);

                if (b.cornerBlock.getValue()) {
                    render(b, b.blockPosProvider.getRailings(-1), mBlockPos -> {
                        boolean valid = false;
                        for (MBlockPos pos : b.blockPosProvider.getRailings(0)) {
                            if (!b.blocksToPlace.getValue().contains(pos.getState().getBlock())
                                && pos.add(0, -1, 0).equals(mBlockPos)) {
                                valid = true;
                                break;
                            }
                        }
                        return valid && b.canPlace(mBlockPos, false);
                    }, false);
                }
            }

            render(b, b.blockPosProvider.getFloor(), pos -> b.canPlace(pos, false), false);
            if (b.state == HighwayBuilderHIG.State.PlaceEChestBlockade)
                render(b, b.blockPosProvider.getBlockade(false, b.blockadeType.getValue()), pos -> b.canPlace(pos, false), false);
        }

        renderPlacementFx(b);
    }

    private static void renderPlacementFx(HighwayBuilderHIG b) {
        long now = System.currentTimeMillis();
        PLACED_FX.entrySet().removeIf(e -> now - e.getValue() > PLACEMENT_FX_MS);
        Color sideBase = b.renderPlaceSideColor.getValue();
        Color lineBase = b.renderPlaceLineColor.getValue();
        for (Map.Entry<BlockPos, Long> e : new ArrayList<>(PLACED_FX.entrySet())) {
            float age = Math.max(0f, Math.min(1f, (now - e.getValue()) / (float) PLACEMENT_FX_MS));
            int alpha = (int) (lineBase.getAlpha() * (1f - age));
            if (alpha <= 4) continue;
            BlockPos pos = e.getKey();
            Renderer3D.box(new Box(pos),
                new Color(sideBase.getRed(), sideBase.getGreen(), sideBase.getBlue(), (int) (sideBase.getAlpha() * (1f - age))),
                new Color(lineBase.getRed(), lineBase.getGreen(), lineBase.getBlue(), alpha),
                b.renderPlaceShape.getValue(), true);
        }
    }

    private static void render(HighwayBuilderHIG b, HighwayBuilderHIG.MBPIterator it, Predicate<MBlockPos> predicate, boolean mine) {
        Color sideColor = mine ? b.renderMineSideColor.getValue() : b.renderPlaceSideColor.getValue();
        Color lineColor = mine ? b.renderMineLineColor.getValue() : b.renderPlaceLineColor.getValue();
        ShapeMode shapeMode = mine ? b.renderMineShape.getValue() : b.renderPlaceShape.getValue();

        for (MBlockPos pos : it) {
            POS_RENDER.set(pos);
            if (predicate.test(POS_RENDER)) {
                Renderer3D.box(POS_RENDER.getBlockPos(), sideColor, lineColor, shapeMode, 0);
            }
        }
    }
}

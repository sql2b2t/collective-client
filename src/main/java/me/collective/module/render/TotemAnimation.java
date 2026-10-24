package me.collective.module.render;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.EnumSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;
import com.mojang.math.Axis;

public class TotemAnimation extends Module {
    public static TotemAnimation INSTANCE;

    public enum Mode {
        FadeOut, Size, Otkisuli, Insert, Fall, Rocket, Roll, Off
    }

    public final EnumSetting<Mode> mode = add(new EnumSetting<>("Режим", Mode.FadeOut));
    public final NumberSetting speed = add(new NumberSetting("Скорость", 40.0, 1.0, 100.0, 1.0));

    private final ItemStackRenderState itemRenderState = new ItemStackRenderState();

    public TotemAnimation() {
        super("TotemAnimation", "Кастомная плавная анимация тотема бессмертия при срабатывании", Category.RENDER);
        INSTANCE = this;
    }

    public void showFloatingItem(ItemStack stack) {
        // Track item activation
    }

    public boolean renderCustomAnimation(PoseStack matrices, float tickDelta, SubmitNodeCollector renderCommands, ItemStack floatingItem, int floatingItemTimer) {
        if (!isToggled() || mode.getValue() == Mode.Off) return false;
        if (floatingItem == null || floatingItemTimer <= 0) return false;

        int totalTicks = getTime();
        int elapsed = totalTicks - floatingItemTimer;
        float progress = Mth.clamp(((float) elapsed + tickDelta) / (float) totalTicks, 0.0f, 1.0f);

        float p2 = progress * progress;
        float p3 = progress * p2;
        float oscillation = 10.25F * p3 * p2 - 24.95F * p2 * p2 + 25.5F * p3 - 13.8F * p2 + 4.0F * progress;
        float oscRad = oscillation * (float) Math.PI;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        matrices.pushPose();
        try {
            float adj = (float) elapsed + tickDelta;
            float baseScale = 0.5f + 1.75f * Mth.sin(oscRad);

            switch (mode.getValue()) {
                case FadeOut -> {
                    float x2 = (float) (Math.sin((adj * 112.0f) / 180.0f) * 1.5f);
                    float y2 = (float) (Math.cos((adj * 112.0f) / 180.0f) * 1.5f);
                    matrices.translate((sw / 2.0f) + x2, (sh / 2.0f) + y2, -50.0f);
                    matrices.scale(baseScale * 50.0f, -baseScale * 50.0f, baseScale * 50.0f);
                }
                case Size -> {
                    matrices.translate(sw / 2.0f, sh / 2.0f, -50.0f);
                    float s = Math.max(0.01f, 1.0f - progress);
                    matrices.scale(s * 50.0f, -s * 50.0f, s * 50.0f);
                }
                case Otkisuli -> {
                    matrices.translate(sw / 2.0f, sh / 2.0f, -50.0f);
                    matrices.scale(baseScale * 50.0f, -baseScale * 50.0f, baseScale * 50.0f);
                    matrices.rotate(Axis.ZP.rotationDegrees(adj * 8.0f));
                }
                case Insert -> {
                    matrices.translate(sw / 2.0f, (sh / 2.0f) - (progress * 80.0f), -50.0f);
                    matrices.scale(baseScale * 50.0f, -baseScale * 50.0f, baseScale * 50.0f);
                }
                case Fall -> {
                    matrices.translate(sw / 2.0f, (sh / 2.0f) + (progress * 120.0f), -50.0f);
                    matrices.scale(baseScale * 50.0f, -baseScale * 50.0f, baseScale * 50.0f);
                }
                case Rocket -> {
                    matrices.translate(sw / 2.0f, (sh / 2.0f) - (progress * 200.0f), -50.0f);
                    matrices.scale(baseScale * 50.0f, -baseScale * 50.0f, baseScale * 50.0f);
                }
                case Roll -> {
                    matrices.translate(sw / 2.0f, sh / 2.0f, -50.0f);
                    matrices.rotate(Axis.YP.rotationDegrees(adj * 18.0f));
                    float sc = Math.max(0.1f, 2.0f - adj * 0.04f);
                    matrices.scale(sc, sc, sc);
                }
                case Off -> {}
            }

            mc.getItemModelResolver().updateForNonLiving(
                    itemRenderState,
                    floatingItem,
                    ItemDisplayContext.FIXED,
                    null
            );

            itemRenderState.submit(
                    matrices,
                    renderCommands,
                    15728880,
                    OverlayTexture.NO_OVERLAY,
                    0
            );

        } finally {
            matrices.popPose();
        }
        return true;
    }

    private int getTime() {
        int inverted = 101 - speed.getIntValue();
        if (mode.getValue() == Mode.FadeOut) return Math.max(10, inverted / 4);
        if (mode.getValue() == Mode.Insert) return Math.max(10, inverted / 2);
        return Math.max(10, inverted);
    }
}
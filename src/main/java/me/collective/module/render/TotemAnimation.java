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

    private final ItemRenderState itemRenderState = new ItemRenderState();

    public TotemAnimation() {
        super("TotemAnimation", "Кастомная плавная анимация тотема бессмертия при срабатывании", Category.RENDER);
        INSTANCE = this;
    }

    public void showFloatingItem(ItemStack stack) {
        // Track item activation
    }

    public boolean renderCustomAnimation(PoseStack matrices, float tickDelta, OrderedRenderCommandQueue renderCommands, ItemStack floatingItem, int floatingItemTimer) {
        if (!isToggled() || mode.getValue() == Mode.Off) return false;
        if (floatingItem == null || floatingItemTimer <= 0) return false;

        int totalTicks = getTime();
        int elapsed = totalTicks - floatingItemTimer;
        float progress = MathHelper.clamp(((float) elapsed + tickDelta) / (float) totalTicks, 0.0f, 1.0f);

        float p2 = progress * progress;
        float p3 = progress * p2;
        float oscillation = 10.25F * p3 * p2 - 24.95F * p2 * p2 + 25.5F * p3 - 13.8F * p2 + 4.0F * progress;
        float oscRad = oscillation * (float) Math.PI;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        matrices.push();
        try {
            float adj = (float) elapsed + tickDelta;
        float baseScale = 0.5f + 1.75f * MathHelper.sin(oscRad);

        switch (mode.getValue()) {
            case FadeOut -> {
                float x2 = (float) (Math.sin((adj * 112.0f) / 180.0f) * 1.5f);
                float y2 = (float) (Math.cos((adj * 112.0f) / 180.0f) * 0.8f);
                matrices.translate(x2, y2, -1.0f);
                matrices.scale(baseScale, baseScale, baseScale);
            }
            case Size -> {
                matrices.translate(0.0f, 0.0f, -1.0f);
                matrices.scale(baseScale, baseScale, baseScale);
            }
            case Otkisuli -> {
                matrices.translate(0.0f, 0.0f, -1.0f);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(adj * 8.0f));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(adj * 8.0f));
                float sc = Math.max(0.1f, 2.0f - adj * 0.04f);
                matrices.scale(sc, sc, sc);
            }
            case Insert -> {
                matrices.translate(0.0f, 0.0f, -1.0f);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(adj * 12.0f));
                float sc = Math.max(0.1f, 2.0f - adj * 0.04f);
                matrices.scale(sc, sc, sc);
            }
            case Fall -> {
                float down = (float) (Math.pow(adj * 0.05f, 3) * 0.5f);
                matrices.translate(0.0f, -down, -1.0f);
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(adj * 15.0f));
                float sc = Math.max(0.1f, 2.0f - adj * 0.04f);
                matrices.scale(sc, sc, sc);
            }
            case Rocket -> {
                float up = (float) (Math.pow(adj * 0.05f, 3) * 0.5f);
                matrices.translate(0.0f, up, -1.0f);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(adj * floatingItemTimer * 2.0f));
                float sc = Math.max(0.1f, 2.0f - adj * 0.04f);
                matrices.scale(sc, sc, sc);
            }
            case Roll -> {
                float right = (float) (Math.pow(adj * 0.05f, 2) * 1.5f);
                matrices.translate(right, 0.0f, -1.0f);
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(adj * 40.0f));
                float sc = Math.max(0.1f, 2.0f - adj * 0.04f);
                matrices.scale(sc, sc, sc);
            }
            case Off -> {}
        }

        mc.getItemModelManager().clearAndUpdate(
                itemRenderState,
                floatingItem,
                ItemDisplayContext.FIXED,
                mc.world,
                null,
                0
        );

        itemRenderState.render(
                matrices,
                renderCommands,
                15728880,
                OverlayTexture.DEFAULT_UV,
                0
        );

        } finally {
            matrices.pop();
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
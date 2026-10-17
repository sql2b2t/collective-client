package me.collective.module.hud;

import me.collective.render.RenderUtil;
import me.collective.setting.BooleanSetting;
import me.collective.setting.ColorSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import java.awt.Color;

public class ImageHUD extends HudModule {
    public enum Image {
        Collective,
        Komod1,
        Komod2,
        Komod3
    }

    private static final Identifier TEXTURE_COLLECTIVE = Identifier.of("collective", "textures/collective_rpc.png");
    private static final Identifier TEXTURE_KOMOD1 = Identifier.of("collective", "textures/komod1.png");
    private static final Identifier TEXTURE_KOMOD2 = Identifier.of("collective", "textures/komod2.png");
    private static final Identifier TEXTURE_KOMOD3 = Identifier.of("collective", "textures/komod3.png");

    public final EnumSetting<Image> image = add(new EnumSetting<>("Image", Image.Collective));
    public final NumberSetting widthSetting = add(new NumberSetting("Width", 64.0, 16.0, 256.0, 1.0));
    public final NumberSetting heightSetting = add(new NumberSetting("Height", 64.0, 16.0, 256.0, 1.0));
    public final BooleanSetting border = add(new BooleanSetting("Border", true));
    public final ColorSetting borderColor = add(new ColorSetting("BorderColor", new Color(130, 80, 240, 220)));
    public final NumberSetting rotation = add(new NumberSetting("Rotation", 0.0, 0.0, 360.0, 5.0));

    public ImageHUD() {
        super("ImageHUD", "Выводит выбранную картинку или логотип на экран.", 10, 60);
    }

    /** Реальный размер текстуры для UV-региона. */
    private int textureSize() {
        return switch (image.getValue()) {
            case Komod1, Komod2, Komod3 -> 1024;
            default -> 128;
        };
    }

    public Identifier getTexture() {        return switch (image.getValue()) {
            case Komod1 -> TEXTURE_KOMOD1;
            case Komod2 -> TEXTURE_KOMOD2;
            case Komod3 -> TEXTURE_KOMOD3;
            default -> TEXTURE_COLLECTIVE;
        };
    }

    @Override
    public void render(DrawContext context, float delta) {
        if (!isToggled()) return;

        float w = widthSetting.getFloatValue();
        float h = heightSetting.getFloatValue();
        this.width = w;
        this.height = h;

        if (background.getValue()) {
            RenderUtil.drawRect(context, x, y, x + w, y + h, new Color(15, 15, 20, 160));
        }

        float angle = rotation.getFloatValue();
        if (angle != 0.0f) {
            RenderUtil.drawRotatedImage(context, getTexture(), x, y, w, h, angle, 0xFFFFFFFF, textureSize(), textureSize());
        } else {
            int ts = textureSize();
            context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                getTexture(),
                (int) x,
                (int) y,
                0f,
                0f,
                (int) w,
                (int) h,
                ts,
                ts,
                ts,
                ts,
                0xFFFFFFFF
            );
        }

        if (border.getValue()) {
            RenderUtil.drawOutline(context, x, y, w, h, 1.0f, borderColor.getValue().getRGB());
        }
    }
}

package me.collective.module.hud;

import me.collective.font.FontManager;
import me.collective.module.client.HUD;
import me.collective.render.RenderUtil;
import me.collective.setting.BooleanSetting;
import me.collective.setting.StringSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.Color;

public class Watermark extends HudModule {
    public final StringSetting text = add(new StringSetting("Component", "Collective"));
    public final BooleanSetting version = add(new BooleanSetting("Version", true));

    public Watermark() {
        super("Watermark", "Отображает ватермарку с названием и версией клиента.", 4, 4);
    }

    @Override
    public void render(GuiGraphicsExtractor context, float delta) {
        if (!isToggled()) return;

        // Версия — из метаданных мода (fabric.mod.json), чтобы не врать в релизах
        String modVersion = "1.3";
        try {
            modVersion = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer("collective")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse(modVersion);
        } catch (Throwable ignored) {
        }
        String title = text.getValue() + (version.getValue() ? " §fv" + modVersion : "");
        this.width = FontManager.INSTANCE.getStringWidth(title) + 6;
        this.height = FontManager.INSTANCE.getFontHeight() + 4;

        if (background.getValue()) {
            RenderUtil.drawRect(context, x, y, x + width, y + height, new Color(20, 20, 20, 180));
            RenderUtil.drawOutline(context, x, y, width, height, 1, HUD.INSTANCE.getHudColor(0).getRGB());
        }

        FontManager.INSTANCE.drawStringWithShadow(context, title, x + 3, y + 2, HUD.INSTANCE.getHudColor(0));
    }
}
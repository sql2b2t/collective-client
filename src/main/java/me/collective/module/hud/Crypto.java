package me.collective.module.hud;

import me.collective.font.FontManager;
import me.collective.render.RenderUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.Color;

public class Crypto extends HudModule {
    public Crypto() {
        super("Crypto", "Отображает актуальные биржевые курсы криптовалют на экране.", 4, 380);
    }

    @Override
    public void render(GuiGraphicsExtractor context, float delta) {
        if (!isToggled()) return;

        String text = "BTC: §a$98,420 §f| ETH: §a$3,850";
        this.width = FontManager.INSTANCE.getStringWidth(text) + 6;
        this.height = FontManager.INSTANCE.getFontHeight() + 4;

        if (background.getValue()) {
            RenderUtil.drawRect(context, x, y, x + width, y + height, new Color(20, 20, 20, 160));
        }

        FontManager.INSTANCE.drawStringWithShadow(context, text, x + 3, y + 2, new Color(247, 147, 26));
    }
}
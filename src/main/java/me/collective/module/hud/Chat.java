package me.collective.module.hud;

import me.collective.setting.BooleanSetting;

public class Chat extends HudModule {
    public final BooleanSetting customFont = add(new BooleanSetting("CustomFont", true));
    public final BooleanSetting clearBackground = add(new BooleanSetting("ClearBackground", false));

    public Chat() {
        super("Chat", "Кастомизация внешнего вида чата: анимация, прозрачность и шрифт.", 4, 250);
        this.width = 0;
        this.height = 0;
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphicsExtractor context, float delta) {
        // Chat module settings applied to ChatHud
    }
}
package me.collective.module.hud;

import me.collective.render.RenderUtil;
import me.collective.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import java.awt.Color;

public class PlayerModel extends HudModule {
    public final NumberSetting size = add(new NumberSetting("Size", 30, 15, 60, 1));

    public PlayerModel() {
        super("PlayerModel", "Отображает 3D-модель вашего персонажа на экране.", 20, 200);
        this.width = 40;
        this.height = 70;
    }

    @Override
    public void render(GuiGraphicsExtractor context, float delta) {
        if (!isToggled() || mc.player == null) return;

        this.width = size.getIntValue() * 1.4f;
        this.height = size.getIntValue() * 2.2f;

        if (background.getValue()) {
            RenderUtil.drawRect(context, x, y, x + width, y + height, new Color(20, 20, 20, 150));
        }

        int x1 = (int) x;
        int y1 = (int) y;
        int x2 = (int) (x + width);
        int y2 = (int) (y + height);

        InventoryScreen.extractEntityInInventoryFollowsMouse(context, x1, y1, x2, y2, size.getIntValue(), 0.0625f, 0, 0, mc.player);
    }
}
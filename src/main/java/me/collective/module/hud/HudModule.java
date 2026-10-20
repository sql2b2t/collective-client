package me.collective.module.hud;

import me.collective.category.Category;
import me.collective.font.FontManager;
import me.collective.module.Module;
import me.collective.module.client.HUD;
import me.collective.render.RenderUtil;
import me.collective.setting.BooleanSetting;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

public abstract class HudModule extends Module {
    public enum Anchor {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        NONE
    }

    protected float x;
    protected float y;
    protected float width;
    protected float height;
    protected Anchor anchor = Anchor.NONE;

    private boolean dragging;
    private float dragX;
    private float dragY;

    public final BooleanSetting background = add(new BooleanSetting("Background", false));

    public HudModule(String name, String description, float defaultX, float defaultY) {
        super(name, description, Category.HUD);
        this.x = defaultX;
        this.y = defaultY;
        setToggled(true);
    }

    public abstract void render(DrawContext context, float delta);

    public void renderEditor(DrawContext context, float delta, double mouseX, double mouseY) {
        if (!isToggled()) return;

        render(context, delta);

        boolean hovered = isHovered(mouseX, mouseY);
        int outlineColor = hovered ? new Color(110, 80, 230, 255).getRGB() : new Color(255, 255, 255, 80).getRGB();
        int boxColor = hovered ? new Color(110, 80, 230, 60).getRGB() : new Color(0, 0, 0, 40).getRGB();

        RenderUtil.drawRect(context, x - 2, y - 2, x + width + 2, y + height + 2, boxColor);
        RenderUtil.drawOutline(context, x - 2, y - 2, width + 4, height + 4, 1, outlineColor);
        FontManager.INSTANCE.drawString(context, getName(), x, y - 10, new Color(200, 200, 200, 200));
    }

    public boolean isHovered(double mouseX, double mouseY) {
        return mouseX >= x - 2 && mouseX <= x + width + 2 && mouseY >= y - 2 && mouseY <= y + height + 2;
    }

    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isHovered(mouseX, mouseY)) {
            this.dragging = true;
            this.dragX = (float) (mouseX - x);
            this.dragY = (float) (mouseY - y);
        }
    }

    public void mouseReleased(int button) {
        if (button == 0) {
            this.dragging = false;
        }
    }

    public void mouseDragged(double mouseX, double mouseY) {
        if (this.dragging) {
            this.x = (float) (mouseX - dragX);
            this.y = (float) (mouseY - dragY);

            int screenW = mc.getWindow().getScaledWidth();
            int screenH = mc.getWindow().getScaledHeight();

            this.x = Math.max(2, Math.min(screenW - width - 2, this.x));
            this.y = Math.max(2, Math.min(screenH - height - 2, this.y));
        }
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public Anchor getAnchor() {
        return anchor;
    }

    public void setAnchor(Anchor anchor) {
        this.anchor = anchor;
    }
}

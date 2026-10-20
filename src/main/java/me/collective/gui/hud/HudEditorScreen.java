package me.collective.gui.hud;

import me.collective.category.Category;
import me.collective.font.FontManager;
import me.collective.gui.component.Panel;
import me.collective.module.ModuleManager;
import me.collective.module.client.UI;
import me.collective.module.hud.HudModule;
import me.collective.module.render.Blur;
import me.collective.render.BlurEngine;
import me.collective.render.RenderUtil;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class HudEditorScreen extends Screen {
    private static HudEditorScreen instance;

    public static HudEditorScreen getInstance() {
        if (instance == null) {
            instance = new HudEditorScreen();
        }
        return instance;
    }

    private final Panel hudPanel;
    private HudModule draggingModule = null;
    private float dragX = 0;
    private float dragY = 0;
    private HudModule hoveredModule = null;
    private HudModule lastHoveredModule = null;
    private long hoverStartTime = -1L;
    private HudModule selectedModule = null;

    private boolean snappedCenterX = false;
    private boolean snappedCenterY = false;
    private float snapLineX = 0;
    private float snapLineY = 0;

    // Snow particles (matching Collective ClickGui / HudEditor)
    private static class Snowflake {
        float x, y, speed, size;
    }
    private final List<Snowflake> snowflakes = new ArrayList<>();
    private final Random random = new Random();

    public HudEditorScreen() {
        super(Text.literal("HUD Editor"));
        // Collective HUD Editor contains the Category.HUD panel at (85, 10)
        this.hudPanel = new Panel(Category.HUD, 85, 10, 92);

        // Initialize snow particles
        for (int i = 0; i < 80; i++) {
            Snowflake s = new Snowflake();
            s.x = random.nextFloat() * 1200;
            s.y = random.nextFloat() * 800;
            s.speed = 0.4f + random.nextFloat() * 1.2f;
            s.size = 1.0f + random.nextFloat() * 2.0f;
            snowflakes.add(s);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float screenW = width;
        float screenH = height;
        float cx = screenW / 2.0f;
        float cy = screenH / 2.0f;

        // 1. Dark semi-transparent background (Collective style)
        RenderUtil.drawRect(context, 0, 0, screenW, screenH, new Color(0, 0, 0, 120));

        // 2. Center Guidelines (faint white crosshair lines across screen)
        Color centerLineColor = new Color(255, 255, 255, 40);
        RenderUtil.drawRect(context, cx - 0.5f, 0, cx + 0.5f, screenH, centerLineColor);
        RenderUtil.drawRect(context, 0, cy - 0.5f, screenW, cy + 0.5f, centerLineColor);

        // 3. Falling Snow effect (if enabled in UI module)
        if (UI.INSTANCE != null && UI.INSTANCE.snow.getValue()) {
            for (Snowflake s : snowflakes) {
                s.y += s.speed;
                if (s.y > screenH) {
                    s.y = 0;
                    s.x = random.nextFloat() * screenW;
                }
                RenderUtil.drawRect(context, s.x, s.y, s.x + s.size, s.y + s.size, new Color(255, 255, 255, 180));
            }
        }

        // 4. Update dragging & snapping
        snappedCenterX = false;
        snappedCenterY = false;

        if (draggingModule != null) {
            float newX = mouseX - dragX;
            float newY = mouseY - dragY;
            float mW = draggingModule.getWidth();
            float mH = draggingModule.getHeight();

            // Center X snapping (within 6px)
            if (Math.abs((newX + mW / 2f) - cx) <= 6.0f) {
                newX = cx - mW / 2f;
                snappedCenterX = true;
                snapLineX = cx;
            }

            // Center Y snapping (within 6px)
            if (Math.abs((newY + mH / 2f) - cy) <= 6.0f) {
                newY = cy - mH / 2f;
                snappedCenterY = true;
                snapLineY = cy;
            }

            // Edge snapping
            if (newX <= 8.0f) {
                newX = 2.0f;
            } else if (newX + mW >= screenW - 8.0f) {
                newX = screenW - mW - 2.0f;
            }

            if (newY <= 8.0f) {
                newY = 2.0f;
            } else if (newY + mH >= screenH - 8.0f) {
                newY = screenH - mH - 2.0f;
            }

            newX = Math.max(2, Math.min(screenW - mW - 2, newX));
            newY = Math.max(2, Math.min(screenH - mH - 2, newY));

            draggingModule.setX(newX);
            draggingModule.setY(newY);
        }

        // 5. Draw Snap Feedback (Yellow lines across the screen)
        if (snappedCenterX) {
            RenderUtil.drawRect(context, snapLineX - 0.5f, 0, snapLineX + 0.5f, screenH, Color.YELLOW);
        }
        if (snappedCenterY) {
            RenderUtil.drawRect(context, 0, snapLineY - 0.5f, screenW, snapLineY + 0.5f, Color.YELLOW);
        }

        // 6. Detect Hovered HUD Element (excluding area under HUD Category panel)
        hoveredModule = null;
        boolean panelHovered = hudPanel.isHovered(mouseX, mouseY);

        if (!panelHovered && draggingModule == null) {
            for (HudModule module : ModuleManager.INSTANCE.getHudModules()) {
                if (!module.isToggled()) continue;
                float mx = module.getX();
                float my = module.getY();
                float mw = Math.max(20, module.getWidth());
                float mh = Math.max(10, module.getHeight());

                if (mouseX >= mx - 2 && mouseX <= mx + mw + 2 && mouseY >= my - 2 && mouseY <= my + mh + 2) {
                    hoveredModule = module;
                    break;
                }
            }
        }

        if (hoveredModule != lastHoveredModule) {
            lastHoveredModule = hoveredModule;
            hoverStartTime = (hoveredModule != null) ? System.currentTimeMillis() : -1L;
        }

        // 7. Render all active HUD elements
        for (HudModule module : ModuleManager.INSTANCE.getHudModules()) {
            if (!module.isToggled()) continue;

            float mx = module.getX();
            float my = module.getY();
            float mw = Math.max(20, module.getWidth());
            float mh = Math.max(10, module.getHeight());

            // Translucent black backing box (Collective style)
            RenderUtil.drawRect(context, mx - 1, my - 1, mx + mw + 1, my + mh + 1, new Color(0, 0, 0, 120));

            // Draw HUD element content
            module.render(context, delta);

            // Highlight outline and floating name tooltip when hovered or selected
            boolean isHighlighted = (module == hoveredModule) || (module == draggingModule) || (module == selectedModule);
            if (isHighlighted) {
                int themeColor = (UI.INSTANCE != null) ? UI.INSTANCE.color.getValue().getRGB() : new Color(189, 153, 255).getRGB();
                RenderUtil.drawOutline(context, mx - 2, my - 2, mw + 4, mh + 4, 1, themeColor);

                // Hover tooltip after 300ms or when dragging/selected
                boolean showTooltip = (module == draggingModule) || (module == selectedModule) ||
                        (hoverStartTime != -1L && System.currentTimeMillis() - hoverStartTime >= 300L);

                if (showTooltip) {
                    String name = module.getName();
                    float nw = FontManager.INSTANCE.getStringWidth(name);
                    float labelX = mx + mw / 2f - nw / 2f;
                    float labelY = my - 12;
                    if (labelY < 4) labelY = my + mh + 4;

                    RenderUtil.drawRect(context, labelX - 3, labelY - 1, labelX + nw + 3, labelY + FontManager.INSTANCE.getFontHeight() + 1, new Color(14, 14, 18, 220));
                    RenderUtil.drawOutline(context, labelX - 3, labelY - 1, nw + 6, FontManager.INSTANCE.getFontHeight() + 2, 1, themeColor);
                    FontManager.INSTANCE.drawStringWithShadow(context, name, labelX, labelY + 0.5f, Color.WHITE);
                }
            }
        }

        // 8. Frosted Blur behind HUD Category Panel (if blur enabled)
        if (Blur.INSTANCE != null && Blur.INSTANCE.isToggled() && Blur.INSTANCE.clickGui.getValue()) {
            List<float[]> blurRegions = new ArrayList<>();
            hudPanel.collectBlurRegions(blurRegions, Blur.INSTANCE.categories.getValue(), Blur.INSTANCE.settings.getValue(), "");
            BlurEngine.INSTANCE.renderBlur(context, blurRegions, Blur.INSTANCE.radius.getFloatValue());
        }

        // 9. Render HUD Category Panel
        hudPanel.render(context, mouseX, mouseY, delta, "");

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        int button = click.button();
        double mouseX = click.x();
        double mouseY = click.y();

        // Priority 1: Interaction with HUD Category Panel
        if (hudPanel.mouseClicked(mouseX, mouseY, button)) {
            selectedModule = null;
            return true;
        }

        // Priority 2: Clicking on HUD elements
        for (HudModule module : ModuleManager.INSTANCE.getHudModules()) {
            if (!module.isToggled()) continue;

            float mx = module.getX();
            float my = module.getY();
            float mw = Math.max(20, module.getWidth());
            float mh = Math.max(10, module.getHeight());

            if (mouseX >= mx - 2 && mouseX <= mx + mw + 2 && mouseY >= my - 2 && mouseY <= my + mh + 2) {
                if (button == 0) {
                    draggingModule = module;
                    selectedModule = module;
                    dragX = (float) (mouseX - mx);
                    dragY = (float) (mouseY - my);
                    return true;
                } else if (button == 1) {
                    // Right-click toggles module off in editor
                    module.setToggled(false);
                    return true;
                }
            }
        }

        selectedModule = null;
        return super.mouseClicked(click, bl);
    }

    @Override
    public boolean mouseReleased(Click click) {
        hudPanel.mouseReleased(click.x(), click.y(), click.button());
        if (draggingModule != null) {
            draggingModule = null;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        // Let panel handle keybinds/text typing
        if (hudPanel.keyPressed(key)) {
            return true;
        }

        // Keyboard arrow nudging (Collective style: 1 pixel adjustment)
        HudModule target = draggingModule != null ? draggingModule : (selectedModule != null ? selectedModule : hoveredModule);
        if (target != null) {
            if (key == GLFW.GLFW_KEY_LEFT) {
                target.setX(Math.max(2, target.getX() - 1));
                return true;
            } else if (key == GLFW.GLFW_KEY_RIGHT) {
                target.setX(Math.min(width - target.getWidth() - 2, target.getX() + 1));
                return true;
            } else if (key == GLFW.GLFW_KEY_UP) {
                target.setY(Math.max(2, target.getY() - 1));
                return true;
            } else if (key == GLFW.GLFW_KEY_DOWN) {
                target.setY(Math.min(height - target.getHeight() - 2, target.getY() + 1));
                return true;
            } else if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
                target.setToggled(false);
                selectedModule = null;
                return true;
            }
        }

        return super.keyPressed(input);
    }

    @Override
    public void close() {
        if (draggingModule != null) {
            draggingModule = null;
        }
        if (UI.INSTANCE != null && UI.INSTANCE.isToggled()) {
            UI.INSTANCE.setToggled(false);
        }
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

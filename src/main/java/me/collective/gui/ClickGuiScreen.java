package me.collective.gui;

import me.collective.category.Category;
import me.collective.font.FontManager;
import me.collective.gui.component.ModuleButton;
import me.collective.gui.component.Panel;
import me.collective.module.Module;
import me.collective.module.client.UI;
import me.collective.module.render.Blur;
import me.collective.render.BlurEngine;
import me.collective.render.RenderUtil;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ClickGuiScreen extends Screen {
    private static ClickGuiScreen instance;

    public static ClickGuiScreen getInstance() {
        if (instance == null) {
            instance = new ClickGuiScreen();
        }
        return instance;
    }

    private final List<Panel> panels = new ArrayList<>();
    private boolean searchActive;
    private String searchQuery = "";
    private Module hoveredModule;

    // Snow particles
    private static class Snowflake {
        float x, y, speed, size;
    }
    private final List<Snowflake> snowflakes = new ArrayList<>();
    private final Random random = new Random();

    public ClickGuiScreen() {
        super(Text.literal("Collective"));

        float startX = 10;
        float startY = 10;
        // Порядок вкладок фиксирован: Combat, Movement, Render, Player, Misc, Exploit, Client, Highway Tools
        Category[] panelOrder = new Category[] {
            Category.COMBAT, Category.MOVEMENT, Category.RENDER, Category.PLAYER,
            Category.MISC, Category.EXPLOIT, Category.CLIENT, Category.HIGHWAY
        };
        for (Category category : panelOrder) {
            float width = UI.INSTANCE != null ? UI.INSTANCE.getFrameWidth(category) : 92;
            panels.add(new Panel(category, startX, startY, width));
            startX += width + 3;
        }

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
        // Detect hovered module first
        hoveredModule = null;
        for (Panel panel : panels) {
            for (ModuleButton btn : panel.getButtons()) {
                if (btn.isHovered(mouseX, mouseY)) {
                    hoveredModule = btn.getModule();
                }
            }
        }

        // 1. Shaded / Frosted Blur behind categories and module settings
        if (Blur.INSTANCE != null && Blur.INSTANCE.isToggled() && Blur.INSTANCE.clickGui.getValue()) {
            List<float[]> blurRegions = new ArrayList<>();
            boolean incCat = Blur.INSTANCE.categories.getValue();
            boolean incSet = Blur.INSTANCE.settings.getValue();

            for (Panel panel : panels) {
                panel.collectBlurRegions(blurRegions, incCat, incSet, searchActive ? searchQuery : "");
            }

            if (searchActive) {
                float boxW = 180;
                float boxH = 18;
                float boxX = width / 2f - boxW / 2f;
                float boxY = 8;
                blurRegions.add(new float[]{ boxX, boxY, boxX + boxW, boxY + boxH });
            }

            if (Blur.INSTANCE.tooltips.getValue() && hoveredModule != null && hoveredModule.getDescription() != null && !hoveredModule.getDescription().isEmpty()) {
                String desc = hoveredModule.getDescription();
                float dw = FontManager.INSTANCE.getStringWidth(desc);
                float dx = mouseX + 8;
                float dy = mouseY + 8;
                if (dx + dw + 10 > width) {
                    dx = width - dw - 12;
                }
                blurRegions.add(new float[]{ dx - 3, dy - 2, dx + dw + 5, dy + 12 });
            }

            BlurEngine.INSTANCE.renderBlur(context, blurRegions, Blur.INSTANCE.radius.getFloatValue());
        }

        // 2. Snow effect
        if (UI.INSTANCE.snow.getValue()) {
            for (Snowflake s : snowflakes) {
                s.y += s.speed;
                if (s.y > height) {
                    s.y = 0;
                    s.x = random.nextFloat() * width;
                }
                RenderUtil.drawRect(context, s.x, s.y, s.x + s.size, s.y + s.size, new Color(255, 255, 255, 160));
            }
        }

        // 3. Render panels
        for (Panel panel : panels) {
            panel.render(context, mouseX, mouseY, delta, searchActive ? searchQuery : "");
        }

        // 4. Search bar
        if (searchActive) {
            float boxW = 180;
            float boxH = 18;
            float boxX = width / 2f - boxW / 2f;
            float boxY = 8;

            RenderUtil.drawRect(context, boxX, boxY, boxX + boxW, boxY + boxH, new Color(15, 15, 18, 230));
            RenderUtil.drawOutline(context, boxX, boxY, boxW, boxH, 1, UI.INSTANCE.color.getValue().getRGB());

            String displayText = searchQuery.isEmpty() ? "Search..." : searchQuery + "_";
            FontManager.INSTANCE.drawStringWithShadow(context, displayText, boxX + 6, boxY + 5, Color.WHITE);
        } else {
            String hint = "Ctrl + F to activate search.";
            float hw = FontManager.INSTANCE.getStringWidth(hint);
            FontManager.INSTANCE.drawStringWithShadow(context, hint, width - hw - 6, height - 12, new Color(140, 140, 140));
        }

        // 5. Module Description Tooltip
        if (hoveredModule != null && hoveredModule.getDescription() != null && !hoveredModule.getDescription().isEmpty()) {
            String desc = hoveredModule.getDescription();
            float dw = FontManager.INSTANCE.getStringWidth(desc);
            float dx = mouseX + 8;
            float dy = mouseY + 8;

            if (dx + dw + 10 > width) {
                dx = width - dw - 12;
            }

            RenderUtil.drawRect(context, dx - 3, dy - 2, dx + dw + 5, dy + 12, new Color(10, 10, 10, 200));
            RenderUtil.drawOutline(context, dx - 3, dy - 2, dw + 8, 14, 1, new Color(189, 153, 255, 200).getRGB());
            FontManager.INSTANCE.drawStringWithShadow(context, desc, dx, dy + 1, Color.WHITE);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        // Любой клик вне текстовой строки снимает режим ввода: иначе editing
        // остаётся в «протухшей» строке и печать уходит мимо целевого поля.
        for (Panel panel : panels) {
            panel.clearFocusFromTextInputs();
        }
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel panel = panels.get(i);
            if (panel.mouseClicked(click.x(), click.y(), click.button())) {
                return true;
            }
        }
        return super.mouseClicked(click, bl);
    }

    @Override
    public boolean mouseReleased(Click click) {
        for (Panel panel : panels) {
            panel.mouseReleased(click.x(), click.y(), click.button());
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0) {
            for (Panel panel : panels) {
                if (panel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
                    return true;
                }
            }
            // If mouse is not hovering any panel, scroll all panels together with clamping
            float scrollDelta = (float) (verticalAmount * 16.0);
            for (Panel panel : panels) {
                panel.scroll(scrollDelta);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();

        if (input.hasCtrl() && key == GLFW.GLFW_KEY_F) {
            searchActive = !searchActive;
            if (!searchActive) searchQuery = "";
            return true;
        }

        if (searchActive) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                searchActive = false;
                searchQuery = "";
                return true;
            } else if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                }
                return true;
            } else if (key == GLFW.GLFW_KEY_ENTER) {
                searchActive = false;
                return true;
            }
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        for (Panel panel : panels) {
            if (panel.keyPressed(key)) return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (searchActive && input.isValidChar()) {
            searchQuery += input.asString();
            return true;
        }
        if (input.isValidChar()) {
            char chr = input.asString().charAt(0);
            for (Panel panel : panels) {
                if (panel.charTyped(chr)) return true;
            }
        }
        return super.charTyped(input);
    }

    @Override
    public void close() {
        if (UI.INSTANCE != null) {
            UI.INSTANCE.setToggled(false);
        }
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

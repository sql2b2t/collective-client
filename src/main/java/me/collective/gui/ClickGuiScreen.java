package me.collective.gui;

import me.collective.category.Category;
import me.collective.font.FontManager;
import me.collective.gui.alt.SmoothGfx;
import me.collective.gui.component.ModuleButton;
import me.collective.gui.component.Panel;
import me.collective.module.Module;
import me.collective.module.client.UI;
import me.collective.module.render.Blur;
import me.collective.render.BlurEngine;
import me.collective.render.RenderUtil;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
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
    private int searchCursor;
    private Module hoveredModule;

    // Snow particles
    private static class Snowflake {
        float x, y, speed, size;
    }
    private final List<Snowflake> snowflakes = new ArrayList<>();
    private final Random random = new Random();

    public ClickGuiScreen() {
        super(Component.literal("Collective"));

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
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Detect hovered module first
        hoveredModule = null;
        String query = searchActive ? searchQuery : "";
        for (Panel panel : panels) {
            for (ModuleButton btn : panel.getButtons()) {
                if (!query.isEmpty() && !btn.getModule().getName().toLowerCase().contains(query.toLowerCase())) {
                    continue;
                }
                if (btn.isHovered(mouseX, mouseY)) {
                    hoveredModule = btn.getModule();
                }
            }
        }

        // Multi-level granular blur for different parts of ClickGUI
        if (Blur.INSTANCE != null && Blur.INSTANCE.isToggled()) {
            // 1. Background Blur (весь экран / мир игры на заднем плане)
            if (Blur.INSTANCE.clickGui.getValue()) {
                float rad = Blur.INSTANCE.radius.getFloatValue();
                float op = Blur.INSTANCE.opacity != null ? Blur.INSTANCE.opacity.getFloatValue() : 0.65f;
                BlurEngine.INSTANCE.renderBlur(context, rad, op);
            }

            // 2. Category Panels Blur (шапки и контейнеры панелей)
            if (Blur.INSTANCE.categories.getValue()) {
                List<float[]> catRegions = new ArrayList<>();
                for (Panel panel : panels) {
                    panel.collectCategoryRegions(catRegions, searchActive ? searchQuery : "");
                }
                float catRad = Blur.INSTANCE.catRadius.getFloatValue();
                float catOp = Blur.INSTANCE.catOpacity.getFloatValue();
                BlurEngine.INSTANCE.renderBlur(context, catRegions, catRad, catOp);
            }

            // 3. Expanded Module Settings Blur (раскрытые списки настроек)
            if (Blur.INSTANCE.settings.getValue()) {
                List<float[]> setRegions = new ArrayList<>();
                for (Panel panel : panels) {
                    panel.collectSettingsRegions(setRegions, searchActive ? searchQuery : "");
                }
                if (!setRegions.isEmpty()) {
                    float setRad = Blur.INSTANCE.setRadius.getFloatValue();
                    float setOp = Blur.INSTANCE.setOpacity.getFloatValue();
                    BlurEngine.INSTANCE.renderBlur(context, setRegions, setRad, setOp);
                }
            }
        }

        // 2. Snow effect
        if (UI.INSTANCE != null && UI.INSTANCE.snow.getValue()) {
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

            int outlineCol = UI.INSTANCE != null ? UI.INSTANCE.color.getValue().getRGB() : new Color(189, 153, 255).getRGB();
            RenderUtil.drawRect(context, boxX, boxY, boxX + boxW, boxY + boxH, new Color(15, 15, 18, 230));
            RenderUtil.drawOutline(context, boxX, boxY, boxW, boxH, 1, outlineCol);

            if (searchQuery.isEmpty()) {
                FontManager.INSTANCE.drawStringWithShadow(context, "Поиск...", boxX + 6, boxY + 5, new Color(150, 150, 150));
            } else {
                FontManager.INSTANCE.drawStringWithShadow(context, searchQuery, boxX + 6, boxY + 5, Color.WHITE);
            }
            boolean caret = (System.currentTimeMillis() / 450) % 2 == 0;
            if (caret) {
                searchCursor = Math.max(0, Math.min(searchCursor, searchQuery.length()));
                float caretX = boxX + 6 + FontManager.INSTANCE.getStringWidth(searchQuery.substring(0, searchCursor));
                RenderUtil.drawRect(context, caretX, boxY + 4f, caretX + 1f, boxY + 14f, outlineCol);
            }
        } else {
            String hint = "Ctrl + F — открыть поиск";
            float hw = FontManager.INSTANCE.getStringWidth(hint);
            FontManager.INSTANCE.drawStringWithShadow(context, hint, width - hw - 6, height - 12, new Color(140, 140, 140));
        }

        // 5. Module Description Tooltip
        if (hoveredModule != null && hoveredModule.getDescription() != null && !hoveredModule.getDescription().isEmpty()) {
            renderModuleTooltip(context, hoveredModule, mouseX, mouseY);
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean bl) {
        // Любой клик вне текстовой строки снимает режим ввода: иначе editing
        // остаётся в «протухшей» строке и печать уходит мимо целевого поля.
        for (Panel panel : panels) {
            panel.clearFocusFromTextInputs();
        }

        if (searchActive) {
            float boxW = 180;
            float boxH = 18;
            float boxX = width / 2f - boxW / 2f;
            float boxY = 8;
            if (click.x() >= boxX && click.x() <= boxX + boxW && click.y() >= boxY && click.y() <= boxY + boxH) {
                return true;
            }
        }

        String query = searchActive ? searchQuery : "";
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel panel = panels.get(i);
            if (panel.mouseClicked(click.x(), click.y(), click.button(), query)) {
                return true;
            }
        }
        return super.mouseClicked(click, bl);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
        for (Panel panel : panels) {
            panel.mouseReleased(click.x(), click.y(), click.button());
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0) {
            String query = searchActive ? searchQuery : "";
            for (Panel panel : panels) {
                if (panel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount, query)) {
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
    public boolean keyPressed(net.minecraft.client.input.KeyEvent input) {
        int key = input.key();

        if (input.hasControlDown() && (key == com.mojang.blaze3d.platform.InputConstants.KEY_F || key == 70)) {
            searchActive = !searchActive;
            if (!searchActive) {
                searchQuery = "";
                searchCursor = 0;
            } else {
                searchCursor = searchQuery.length();
            }
            return true;
        }

        if (searchActive) {
            searchCursor = Math.max(0, Math.min(searchCursor, searchQuery.length()));
            if (input.isEscape() || key == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE || key == 256) {
                searchActive = false;
                searchQuery = "";
                searchCursor = 0;
                return true;
            } else if (key == com.mojang.blaze3d.platform.InputConstants.KEY_LEFT || key == 263) {
                if (searchCursor > 0) searchCursor--;
                return true;
            } else if (key == com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT || key == 262) {
                if (searchCursor < searchQuery.length()) searchCursor++;
                return true;
            } else if (key == com.mojang.blaze3d.platform.InputConstants.KEY_HOME || key == 268) {
                searchCursor = 0;
                return true;
            } else if (key == com.mojang.blaze3d.platform.InputConstants.KEY_END || key == 269) {
                searchCursor = searchQuery.length();
                return true;
            } else if (key == com.mojang.blaze3d.platform.InputConstants.KEY_BACKSPACE || key == 259) {
                if (searchCursor > 0 && !searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchCursor - 1) + searchQuery.substring(searchCursor);
                    searchCursor--;
                }
                return true;
            } else if (key == com.mojang.blaze3d.platform.InputConstants.KEY_DELETE || key == 261) {
                if (searchCursor < searchQuery.length()) {
                    searchQuery = searchQuery.substring(0, searchCursor) + searchQuery.substring(searchCursor + 1);
                }
                return true;
            } else if (key == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || key == 257) {
                searchActive = false;
                return true;
            }
        }

        if (input.isEscape() || key == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE || key == 256
                || me.collective.util.KeyHelper.isRightShift(key)
                || (UI.INSTANCE != null && me.collective.util.KeyHelper.isMatch(UI.INSTANCE.getKeybind().getValue(), key))) {
            onClose();
            return true;
        }

        for (Panel panel : panels) {
            if (panel.keyPressed(key)) return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent input) {
        if (searchActive && input.isAllowedChatCharacter()) {
            searchCursor = Math.max(0, Math.min(searchCursor, searchQuery.length()));
            String str = input.codepointAsString();
            searchQuery = searchQuery.substring(0, searchCursor) + str + searchQuery.substring(searchCursor);
            searchCursor += str.length();
            return true;
        }
        if (input.isAllowedChatCharacter()) {
            char chr = input.codepointAsString().charAt(0);
            for (Panel panel : panels) {
                if (panel.charTyped(chr)) return true;
            }
        }
        return super.charTyped(input);
    }

    private List<String> wrapTooltipText(String text, float maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return lines;

        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (FontManager.INSTANCE.getStringWidth(candidate) <= maxWidth) {
                current = new StringBuilder(candidate);
            } else {
                if (current.length() > 0) {
                    lines.add(current.toString());
                }
                current = new StringBuilder(word);
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    private float[] getTooltipBounds(Module module, float mouseX, float mouseY) {
        String desc = module.getDescription();
        if (desc == null || desc.trim().isEmpty()) {
            return new float[]{ mouseX, mouseY, 0, 0 };
        }

        float maxTextW = 160f;
        List<String> descLines = wrapTooltipText(desc, maxTextW);
        if (descLines.isEmpty()) {
            return new float[]{ mouseX, mouseY, 0, 0 };
        }

        float textW = 0;
        for (String line : descLines) {
            textW = Math.max(textW, FontManager.INSTANCE.getStringWidth(line));
        }

        float padX = 6f;
        float padY = 3.5f;
        float lineH = 10.0f;

        float cardW = textW + padX * 2f;
        float cardH = descLines.size() * lineH + padY * 2f - 1.5f;

        float cardX = mouseX + 8f;
        float cardY = mouseY + 8f;

        if (cardX + cardW > width - 6f) {
            cardX = mouseX - cardW - 6f;
        }
        if (cardX < 6f) cardX = 6f;

        if (cardY + cardH > height - 6f) {
            cardY = mouseY - cardH - 6f;
        }
        if (cardY < 6f) cardY = 6f;

        return new float[]{ cardX, cardY, cardW, cardH };
    }

    private void renderModuleTooltip(GuiGraphicsExtractor context, Module module, float mouseX, float mouseY) {
        float[] b = getTooltipBounds(module, mouseX, mouseY);
        if (b[2] <= 0 || b[3] <= 0) return;

        float x = b[0], y = b[1], w = b[2], h = b[3];
        int accent = ClickGuiTheme.getAccentColor();

        // 1. Tooltips Blur: dynamically blur the background/panels under the tooltip
        boolean hasBlur = Blur.INSTANCE != null && Blur.INSTANCE.isToggled() && Blur.INSTANCE.tooltips.getValue();
        if (hasBlur) {
            List<float[]> tipRegions = new ArrayList<>();
            tipRegions.add(new float[]{ x, y, x + w, y + h });
            float tipRad = Blur.INSTANCE.tipRadius.getFloatValue();
            float tipOp = Blur.INSTANCE.tipOpacity.getFloatValue();
            BlurEngine.INSTANCE.renderBlur(context, tipRegions, tipRad, tipOp);
        }

        SmoothGfx.ensureInitialized();

        // 2. Minimal soft drop shadow
        SmoothGfx.softGlow(context, x, y, w, h, 3.0f, 0x33000000, 2.5f, 1.0f);

        // 3. Frosted translucent obsidian surface with delicate accent outline
        int fillColor = hasBlur ? 0x750B0F19 : 0xDC0B0F19;
        int borderColor = (accent & 0x00FFFFFF) | 0x2E000000;
        SmoothGfx.fillAndStroke(context, x, y, w, h, 3.0f, fillColor, borderColor, 0.8f);

        // 4. Crisp description text
        List<String> descLines = wrapTooltipText(module.getDescription(), 160f);
        float curY = y + 3.5f;
        for (String line : descLines) {
            FontManager.INSTANCE.drawStringWithShadow(context, line, x + 6f, curY, new Color(241, 245, 249));
            curY += 10.0f;
        }
    }


    @Override
    public void onClose() {
        if (UI.INSTANCE != null) {
            UI.INSTANCE.setToggled(false);
        }
        super.onClose();
    }

    @Override
    public void removed() {
        if (UI.INSTANCE != null && UI.INSTANCE.isToggled()) {
            UI.INSTANCE.setToggled(false);
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
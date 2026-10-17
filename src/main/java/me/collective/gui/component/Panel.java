package me.collective.gui.component;

import me.collective.category.Category;
import me.collective.font.FontManager;
import me.collective.module.Module;
import me.collective.module.ModuleManager;
import me.collective.module.client.UI;
import me.collective.render.RenderUtil;
import me.collective.render.SoundUtil;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class Panel {
    private final Category category;
    private float x;
    private float y;
    private float width;
    private static final float HEADER_HEIGHT = 14;
    private boolean open = true;
    private float openProgress = 1.0f;

    private boolean dragging;
    private float dragX;
    private float dragY;

    private final List<ModuleButton> buttons = new ArrayList<>();

    public Panel(Category category, float x, float y, float width) {
        this.category = category;
        this.x = x;
        this.y = y;
        this.width = width;

        float btnH = UI.INSTANCE.buttonHeight.getIntValue();
        float curY = y + HEADER_HEIGHT;
        List<Module> mods = ModuleManager.INSTANCE.getModulesByCategory(category);
        mods.sort((m1, m2) -> m1.getName().compareToIgnoreCase(m2.getName()));
        for (Module m : mods) {
            buttons.add(new ModuleButton(m, x, curY, width, btnH));
            curY += btnH;
        }
    }

    public float getContentHeight(String searchQuery) {
        float h = 0;
        for (ModuleButton btn : buttons) {
            if (searchQuery != null && !searchQuery.isEmpty()) {
                if (!btn.getModule().getName().toLowerCase().contains(searchQuery.toLowerCase())) {
                    continue;
                }
            }
            h += btn.getTotalHeight();
        }
        return h;
    }

    public float getTotalHeight(String searchQuery) {
        return HEADER_HEIGHT + getContentHeight(searchQuery) * openProgress;
    }

    public void collectBlurRegions(List<float[]> regions, boolean includeCategories, boolean includeSettings, String searchQuery) {
        float animHeight = getContentHeight(searchQuery) * openProgress;
        float totalH = HEADER_HEIGHT + animHeight;

        if (includeCategories) {
            regions.add(new float[]{ x, y, x + width, y + totalH });
        }

        if (includeSettings && openProgress > 0.4f) {
            float btnH = UI.INSTANCE.buttonHeight.getIntValue();
            float curY = y + HEADER_HEIGHT;
            for (ModuleButton btn : buttons) {
                if (searchQuery != null && !searchQuery.isEmpty()) {
                    if (!btn.getModule().getName().toLowerCase().contains(searchQuery.toLowerCase())) {
                        continue;
                    }
                }
                if (btn.isOpen()) {
                    float sH = btn.getSettingsHeight();
                    if (sH > 0) {
                        float sY = curY + btnH;
                        regions.add(new float[]{ x + 1, sY, x + width - 1, sY + sH });
                    }
                }
                curY += btn.getTotalHeight();
            }
        }
    }

    private float scrollOffset = 0;

    public void render(DrawContext context, int mouseX, int mouseY, float delta, String searchQuery) {
        if (dragging) {
            this.x = mouseX - dragX;
            this.y = mouseY - dragY;
        }

        this.width = UI.INSTANCE != null ? UI.INSTANCE.getFrameWidth(category) : width;

        float speed = UI.INSTANCE.animSpeed.getFloatValue() * 0.1f;
        if (open) {
            openProgress = Math.min(1.0f, openProgress + speed);
        } else {
            openProgress = Math.max(0.0f, openProgress - speed);
        }

        // Header bar (exact Collective styling)
        Color headerColor = UI.INSTANCE.color.getValue();
        RenderUtil.drawRect(context, x, y, x + width, y + HEADER_HEIGHT, headerColor);

        // Header title
        FontManager.INSTANCE.drawStringWithShadow(context, category.getName(), x + 2, y + (HEADER_HEIGHT - FontManager.INSTANCE.getFontHeight()) / 2f + 0.5f, Color.WHITE);

        // Fold symbol
        String foldIcon = open ? "-" : "+";
        float iw = FontManager.INSTANCE.getStringWidth(foldIcon);
        FontManager.INSTANCE.drawStringWithShadow(context, foldIcon, x + width - 4 - iw, y + (HEADER_HEIGHT - FontManager.INSTANCE.getFontHeight()) / 2f + 0.5f, Color.WHITE);

        if (openProgress > 0.001f) {
            float contentHeight = getContentHeight(searchQuery);
            int screenH = net.minecraft.client.MinecraftClient.getInstance().getWindow().getScaledHeight();
            float availableH = Math.max(80, screenH - y - HEADER_HEIGHT - 12);
            float visibleH = Math.min(contentHeight, availableH) * openProgress;
            float maxScroll = Math.max(0, contentHeight - availableH);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));

            if (contentHeight > 0) {
                // Background behind modules
                RenderUtil.drawRect(context, x, y + HEADER_HEIGHT, x + width, y + HEADER_HEIGHT + visibleH, UI.INSTANCE.bgColor.getValue());

                // Enable scissor during unfolding animation and scrolling
                context.enableScissor((int) x, (int) (y + HEADER_HEIGHT), (int) (x + width), (int) Math.ceil(y + HEADER_HEIGHT + visibleH));

                float btnH = UI.INSTANCE.buttonHeight.getIntValue();
                float curY = y + HEADER_HEIGHT - scrollOffset;

                for (ModuleButton btn : buttons) {
                    if (searchQuery != null && !searchQuery.isEmpty()) {
                        if (!btn.getModule().getName().toLowerCase().contains(searchQuery.toLowerCase())) {
                            continue;
                        }
                    }
                    btn.setX(x);
                    btn.setY(curY);
                    btn.setWidth(width);
                    btn.setHeight(btnH);
                    btn.render(context, mouseX, mouseY, delta);
                    curY += btn.getTotalHeight();
                }

                // Scrollbar indicator
                if (maxScroll > 0) {
                    float scrollbarHeight = Math.max(12, visibleH * (visibleH / contentHeight));
                    float scrollProgress = scrollOffset / maxScroll;
                    float scrollbarY = y + HEADER_HEIGHT + scrollProgress * (visibleH - scrollbarHeight);
                    RenderUtil.drawRect(context, x + width - 2.5f, scrollbarY, x + width - 0.5f, scrollbarY + scrollbarHeight, new Color(255, 255, 255, 130));
                }

                context.disableScissor();

                // Window line outline
                if (UI.INSTANCE.line.getValue()) {
                    RenderUtil.drawOutline(context, x - 1, y - 1, width + 2, HEADER_HEIGHT + visibleH + 2, 1, UI.INSTANCE.color.getValue().getRGB());
                }

                // Window shadow
                if (UI.INSTANCE.windowShadow.getValue()) {
                    float shadow = UI.INSTANCE.shadowSize.getFloatValue();
                    Color sCol = UI.INSTANCE.shadowColor.getValue();
                    RenderUtil.drawRect(context, x + shadow, y + HEADER_HEIGHT + visibleH, x + width, y + HEADER_HEIGHT + visibleH + shadow, sCol);
                    RenderUtil.drawRect(context, x + width, y + shadow, x + width + shadow, y + HEADER_HEIGHT + visibleH + shadow, sCol);
                }
            }
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHeaderHovered(mouseX, mouseY)) {
            if (button == 0) {
                this.dragging = true;
                this.dragX = (float) (mouseX - x);
                this.dragY = (float) (mouseY - y);
                return true;
            } else if (button == 1) {
                this.open = !open;
                if (UI.INSTANCE.sounds.getValue()) {
                    SoundUtil.playClick(1.0f, 1.2f);
                }
                return true;
            }
        }

        if (open) {
            int screenH = net.minecraft.client.MinecraftClient.getInstance().getWindow().getScaledHeight();
            float availableH = Math.max(80, screenH - y - HEADER_HEIGHT - 12);
            float visibleH = Math.min(getContentHeight(""), availableH) * openProgress;
            if (mouseY >= y + HEADER_HEIGHT && mouseY <= y + HEADER_HEIGHT + visibleH) {
                for (ModuleButton btn : buttons) {
                    if (btn.mouseClicked(mouseX, mouseY, button)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.dragging = false;
        }
        if (open) {
            for (ModuleButton btn : buttons) {
                btn.mouseReleased(mouseX, mouseY, button);
            }
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount == 0) return false;
        if (!isHovered(mouseX, mouseY)) return false;

        if (open) {
            for (ModuleButton btn : buttons) {
                if (btn.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
                    return true;
                }
            }

            float contentHeight = getContentHeight("");
            int screenH = net.minecraft.client.MinecraftClient.getInstance().getWindow().getScaledHeight();
            float availableH = Math.max(80, screenH - y - HEADER_HEIGHT - 12);
            float maxScroll = Math.max(0, contentHeight - availableH);

            if (maxScroll > 0) {
                scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (float) (verticalAmount * 16.0)));
                return true;
            }
        }

        // Adjust whole panel position if content didn't scroll
        int screenH = net.minecraft.client.MinecraftClient.getInstance().getWindow().getScaledHeight();
        this.y += (float) (verticalAmount * 16.0);
        this.y = Math.max(5, Math.min(screenH - HEADER_HEIGHT - 10, this.y));
        return true;
    }

    public boolean keyPressed(int key) {
        if (open) {
            for (ModuleButton btn : buttons) {
                if (btn.keyPressed(key)) return true;
            }
        }
        return false;
    }

    public boolean charTyped(char chr) {
        if (open) {
            for (ModuleButton btn : buttons) {
                if (btn.charTyped(chr)) return true;
            }
        }
        return false;
    }

    public boolean isHeaderHovered(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + HEADER_HEIGHT;
    }

    public boolean isHovered(double mouseX, double mouseY) {
        float h = HEADER_HEIGHT;
        if (open) {
            int screenH = net.minecraft.client.MinecraftClient.getInstance().getWindow().getScaledHeight();
            float availableH = Math.max(80, screenH - y - HEADER_HEIGHT - 12);
            h += Math.min(getContentHeight(""), availableH) * openProgress;
        }
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + h;
    }

    public List<ModuleButton> getButtons() {
        return buttons;
    }

    public Category getCategory() {
        return category;
    }

    public float getX() { return x; }
    public float getY() { return y; }
    public float getWidth() { return width; }
    public boolean isOpen() { return open; }

    public void scroll(float delta) {
        int screenH = net.minecraft.client.MinecraftClient.getInstance().getWindow().getScaledHeight();
        this.y += delta;
        this.y = Math.max(5, Math.min(screenH - HEADER_HEIGHT - 10, this.y));
    }
}

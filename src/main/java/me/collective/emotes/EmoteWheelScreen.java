package me.collective.emotes;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Колесо/список эмоций: постраничный список (8 на странице),
 * выбор мышью/клавиатурой, finish(play), авто-закрытие через renderClosing.
 */
public class EmoteWheelScreen extends Screen {
    private static final int PAGE_SIZE = 8;
    private static final int PANEL_W = 180;
    private static final int ROW_H = 20;
    private static final int ICON = 16;

    private int page;
    private int selected;
    private boolean finished;

    private int listX;
    private int listY;
    private int listW = PANEL_W;

    public EmoteWheelScreen() {
        super(Component.literal("Эмоции"));
    }

    private int pageCount() {
        int size = Emote.ALL.size();
        return Math.max(1, (size + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private int globalIndex() {
        return page * PAGE_SIZE + selected;
    }

    public Emote getSelectedEmote() {
        int i = globalIndex();
        if (i < 0 || i >= Emote.ALL.size()) return null;
        return Emote.ALL.get(i);
    }

    private void clampSelection() {
        int size = Emote.ALL.size();
        if (size == 0) {
            page = 0;
            selected = 0;
            return;
        }
        page = Math.floorMod(page, pageCount());
        int count = Math.min(PAGE_SIZE, size - page * PAGE_SIZE);
        if (count <= 0) {
            page = 0;
            count = Math.min(PAGE_SIZE, size);
        }
        if (selected < 0) selected = 0;
        if (selected >= count) selected = count - 1;
    }

    private void finish(boolean play) {
        if (finished) return;
        finished = true;
        if (play) {
            EmoteEngine.playSelected();
        }
        super.close();
    }

    public boolean renderClosing(float mouseX) {
        return false;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        clampSelection();
        int size = Emote.ALL.size();
        int count = size == 0 ? 0 : Math.min(PAGE_SIZE, size - page * PAGE_SIZE);

        listX = (this.width - listW) / 2;
        listY = (this.height - PAGE_SIZE * ROW_H) / 2;

        context.fill(0, 0, this.width, this.height, 0x78060910);
        context.fill(listX - 6, listY - 24, listX + listW + 6, listY + PAGE_SIZE * ROW_H + 22, 0xE0070B13);

        if (this.textRenderer != null) {
            context.drawCenteredTextWithShadow(this.textRenderer, Component.literal("Эмоции"), this.width / 2, listY - 16, 0xFFF8FBFF);
        }

        if (size == 0) {
            if (this.textRenderer != null) {
                context.drawCenteredTextWithShadow(this.textRenderer, Component.literal("Нет эмоций"), this.width / 2, this.height / 2, 0xFF969EAE);
            }
        } else {
            int hovered = rowAt(mouseX, mouseY, count);
            if (hovered >= 0) selected = hovered;

            for (int i = 0; i < count; i++) {
                Emote emote = Emote.ALL.get(page * PAGE_SIZE + i);
                int rx = listX;
                int ry = listY + i * ROW_H;
                boolean sel = i == selected;
                if (sel) {
                    context.fill(rx, ry, rx + listW, ry + ROW_H, 0x59DEE5F2);
                    context.fill(rx, ry, rx + 2, ry + ROW_H, 0xFFB999FF);
                }
                Identifier icon = emote.icon();
                if (icon != null) {
                    context.drawTexture(RenderPipelines.GUI_TEXTURED, icon, rx + 4, ry + 2, 0.0f, 0.0f, ICON, ICON, ICON, ICON, 0xFFFFFFFF);
                }
                if (this.textRenderer != null) {
                    String label = emote.title() != null ? emote.title() : emote.id();
                    context.drawTextWithShadow(this.textRenderer, label, rx + 24, ry + 6, sel ? 0xFFF8FBFF : 0xFFDEE5F2);
                }
            }

            if (this.textRenderer != null) {
                String pages = (page + 1) + " / " + pageCount();
                context.drawCenteredTextWithShadow(this.textRenderer, Component.literal(pages), this.width / 2, listY + PAGE_SIZE * ROW_H + 6, 0xFF969EAE);
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private int rowAt(double mouseX, double mouseY, int count) {
        if (mouseX < listX || mouseX >= listX + listW) return -1;
        int rel = (int) (mouseY - listY);
        if (rel < 0) return -1;
        int row = rel / ROW_H;
        return row < count ? row : -1;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        clampSelection();
        int count = Emote.ALL.isEmpty() ? 0 : Math.min(PAGE_SIZE, Emote.ALL.size() - page * PAGE_SIZE);
        if (click.button() == 0) {
            int row = rowAt(click.x(), click.y(), count);
            if (row >= 0) {
                selected = row;
                finish(true);
                return true;
            }
            return true;
        }
        if (click.button() == 1) {
            finish(false);
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0 && pageCount() > 1) {
            page = Math.floorMod(page + (verticalAmount < 0 ? 1 : -1), pageCount());
            selected = 0;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            finish(false);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (!Emote.ALL.isEmpty()) finish(true);
            else finish(false);
            return true;
        }
        clampSelection();
        int count = Emote.ALL.isEmpty() ? 0 : Math.min(PAGE_SIZE, Emote.ALL.size() - page * PAGE_SIZE);
        if (key == GLFW.GLFW_KEY_UP) {
            if (count > 0) selected = (selected - 1 + count) % count;
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN) {
            if (count > 0) selected = (selected + 1) % count;
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_PAGE_UP) {
            if (pageCount() > 1) {
                page = Math.floorMod(page - 1, pageCount());
                selected = 0;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_PAGE_DOWN) {
            if (pageCount() > 1) {
                page = Math.floorMod(page + 1, pageCount());
                selected = 0;
            }
            return true;
        }
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_8) {
            int row = key - GLFW.GLFW_KEY_1;
            if (row < count) {
                selected = row;
                finish(true);
                return true;
            }
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        finished = true;
        super.close();
    }

    @Override
    public void removed() {
        finished = true;
        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
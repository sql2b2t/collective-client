package me.collective.gui;

import me.collective.font.FontManager;
import me.collective.module.client.UI;
import me.collective.render.RenderUtil;
import me.collective.setting.BlockListSetting;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Окно выбора блоков в стиле Meteor: строка поиска сверху и две колонки —
 * слева все доступные блоки (клик добавляет в список), справа уже выбранные
 * (клик убирает). Иконки рисуются предметной моделью, каждая колонка
 * скроллится независимо.
 */
public class BlockPickerScreen extends Screen {
    private static final float W = 360;
    private static final float H = 280;
    private static final float TITLE_H = 15;
    private static final float SEARCH_H = 15;
    private static final float COL_HEAD_H = 11;
    private static final float ROW_H = 18;
    private static final float ICON_SIZE = 16;
    private static final float SYM_SIZE = 12;

    private final Screen parent;
    private final BlockListSetting setting;

    private final List<Block> allBlocks = new ArrayList<>();

    private String query = "";
    private float scrollAll;
    private float scrollSel;

    public BlockPickerScreen(BlockListSetting setting, Screen parent) {
        super(Text.literal("Select Blocks"));
        this.setting = setting;
        this.parent = parent;

        for (Block block : Registries.BLOCK) {
            if (skip(block)) continue;
            allBlocks.add(block);
        }
        allBlocks.sort((a, b) -> name(a).compareToIgnoreCase(name(b)));
    }

    private static boolean skip(Block block) {
        if (block == Blocks.AIR) return true;
        if (block.asItem() == Items.AIR) return true; // технические блоки без предмета (air и т.п.)
        return Registries.BLOCK.getId(block).getPath().endsWith("_wall_banner");
    }

    private static String name(Block block) {
        return block.getName().getString();
    }

    private static String path(Block block) {
        return Registries.BLOCK.getId(block).getPath();
    }

    // ------------------------------------------------------------- Габариты

    private float panelX() { return (width - W) / 2f; }

    private float panelY() { return (height - H) / 2f; }

    private float searchY() { return panelY() + TITLE_H + 2; }

    private float colY() { return searchY() + SEARCH_H + 4; }

    private float colBottom() { return panelY() + H - 5; }

    private float rowsTop() { return colY() + COL_HEAD_H; }

    private float colW() { return (W - 22) / 2f; }

    private float leftX() { return panelX() + 6; }

    private float rightX() { return leftX() + colW() + 10; }

    private int visibleRows() {
        return Math.max(1, (int) ((colBottom() - rowsTop()) / ROW_H));
    }

    // ------------------------------------------------------------- Фильтрация

    private boolean matches(Block block) {
        String q = query.toLowerCase().trim();
        if (q.isEmpty()) return true;
        return name(block).toLowerCase().contains(q) || path(block).contains(q);
    }

    /** Слева — все блоки, которых нет в списке. */
    private List<Block> leftBlocks() {
        List<Block> selected = setting.getValue();
        List<Block> out = new ArrayList<>();
        for (Block block : allBlocks) {
            if (selected.contains(block)) continue;
            if (matches(block)) out.add(block);
        }
        return out;
    }

    /** Справа — выбранные блоки. */
    private List<Block> rightBlocks() {
        List<Block> out = new ArrayList<>();
        List<Block> selected = setting.getValue();
        for (Block block : selected) {
            if (matches(block)) out.add(block);
        }
        return out;
    }

    private void add(Block block) {
        List<Block> value = new ArrayList<>(setting.getValue());
        if (value.contains(block)) return;
        value.add(block);
        setting.setValue(value);
    }

    private void remove(Block block) {
        List<Block> value = new ArrayList<>(setting.getValue());
        if (!value.remove(block)) return;
        setting.setValue(value);
    }

    // ------------------------------------------------------------- Рендер

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float px = panelX();
        float py = panelY();
        int fh = FontManager.INSTANCE.getFontHeight();
        Color bg = UI.INSTANCE.bgColor.getValue();
        Color text = UI.INSTANCE.textColor.getValue();
        Color accent = UI.INSTANCE.color.getValue();

        // Затемнение фона
        RenderUtil.drawRect(context, 0, 0, width, height, new Color(0, 0, 0, 150));

        // Панель
        RenderUtil.drawRect(context, px, py, px + W, py + H, bg);
        RenderUtil.drawOutline(context, px, py, W, H, 1, accent.getRGB());

        // Заголовок
        String title = "Select Blocks [" + setting.getValue().size() + "]";
        FontManager.INSTANCE.drawStringWithShadow(context, title, px + 6, py + (TITLE_H - fh) / 2f + 1, text);
        RenderUtil.drawRect(context, px + 1, py + TITLE_H - 1, px + W - 1, py + TITLE_H, new Color(0, 0, 0, 90));

        // Строка поиска
        float sx = px + 6;
        float sy = searchY();
        RenderUtil.drawRect(context, sx, sy, sx + W - 12, sy + SEARCH_H, new Color(10, 10, 14, 220));
        RenderUtil.drawOutline(context, sx, sy, W - 12, SEARCH_H, 1, accent.getRGB());
        String search = query.isEmpty() ? "Search..." : query + "_";
        FontManager.INSTANCE.drawStringWithShadow(context, search, sx + 4, sy + (SEARCH_H - fh) / 2f + 1,
                query.isEmpty() ? new Color(120, 120, 126) : text);

        // Колонки
        List<Block> left = leftBlocks();
        List<Block> right = rightBlocks();
        int visible = visibleRows();

        scrollAll = clampScroll(scrollAll, left.size(), visible);
        scrollSel = clampScroll(scrollSel, right.size(), visible);

        drawColumn(context, leftX(), "All", left, scrollAll, true, mouseX, mouseY, accent, text);
        drawColumn(context, rightX(), "Selected", right, scrollSel, false, mouseX, mouseY, accent, text);

        // Подсказка
        String hint = "Click a block to add/remove | ESC to return";
        float hw = FontManager.INSTANCE.getStringWidth(hint);
        FontManager.INSTANCE.drawStringWithShadow(context, hint, px + (W - hw) / 2f,
                py + H - fh - 2.5f, new Color(130, 130, 136));

        super.render(context, mouseX, mouseY, delta);
    }

    private float clampScroll(float scroll, int count, int visible) {
        int maxOffset = Math.max(0, count - visible);
        if (scroll > maxOffset) scroll = maxOffset;
        if (scroll < 0) scroll = 0;
        return scroll;
    }

    private void drawColumn(DrawContext context, float cx, String header, List<Block> blocks, float scroll,
                            boolean isLeft, int mouseX, int mouseY, Color accent, Color text) {
        float fw = colW();
        float top = colY();
        float rowsTop = top + COL_HEAD_H;
        float bottom = colBottom();

        // Шапка колонки
        int fh = FontManager.INSTANCE.getFontHeight();
        FontManager.INSTANCE.drawStringWithShadow(context, header, cx, top, new Color(150, 150, 156));
        RenderUtil.drawRect(context, cx, top + COL_HEAD_H - 1, cx + fw, top + COL_HEAD_H, new Color(0, 0, 0, 90));

        // Фон области и клип по ней: частично видимые ряды аккуратно обрезаются
        RenderUtil.drawRect(context, cx - 2, rowsTop - 1, cx + fw + 2, bottom, new Color(10, 10, 14, 190));
        context.enableScissor((int) (cx - 2), (int) rowsTop, (int) (cx + fw + 2), (int) bottom);

        float symX = cx + fw - SYM_SIZE;
        float nameW = fw - ICON_SIZE - SYM_SIZE - 10;

        // Смещение скролла внутри колонки
        int offset = (int) scroll;
        float frac = scroll - offset;

        int hovered = hoveredRow(cx, blocks.size(), offset, mouseX, mouseY);

        for (int i = Math.max(0, offset - 2); i < blocks.size(); i++) {
            float rowY = rowsTop + (i - offset - frac) * ROW_H;
            if (rowY + ROW_H < rowsTop - ROW_H) continue;
            if (rowY > bottom) break;

            Block block = blocks.get(i);
            boolean hover = i == hovered;

            if (hover) {
                RenderUtil.drawRect(context, cx, rowY, cx + fw, rowY + ROW_H - 1, new Color(255, 255, 255, 30));
            }

            // Иконка блока
            context.drawItem(new ItemStack(block), (int) (cx + 2), Math.round(rowY + (ROW_H - ICON_SIZE) / 2f));

            // Имя
            int sh = FontManager.INSTANCE.getFontHeight();
            String label = name(block);
            if (FontManager.INSTANCE.getStringWidth(label) > nameW) {
                while (label.length() > 3 && FontManager.INSTANCE.getStringWidth(label + "..") > nameW) {
                    label = label.substring(0, label.length() - 2);
                }
                label = label + "..";
            }
            FontManager.INSTANCE.drawStringWithShadow(context, label, cx + ICON_SIZE + 5,
                    rowY + (ROW_H - sh) / 2f + 1, hover ? text : new Color(168, 168, 175));

            // Плюс/минус
            String sym = isLeft ? "+" : "-";
            float symY = rowY + (ROW_H - SYM_SIZE) / 2f;
            RenderUtil.drawRect(context, symX, symY, symX + SYM_SIZE, symY + SYM_SIZE, new Color(255, 255, 255, 18));
            RenderUtil.drawOutline(context, symX, symY, SYM_SIZE, SYM_SIZE, 1, new Color(0, 0, 0, 160).getRGB());
            FontManager.INSTANCE.drawStringWithShadow(context, sym, symX + (SYM_SIZE
                    - FontManager.INSTANCE.getStringWidth(sym)) / 2f, symY + (SYM_SIZE - sh) / 2f + 1,
                    hover ? accent : new Color(110, 110, 118));
        }

        context.disableScissor();
    }

    private int hoveredRow(float cx, int count, int offset, int mouseX, int mouseY) {
        float fy = mouseY;
        if (mouseX < cx || mouseX > cx + colW()) return -1;
        if (fy < rowsTop() || fy > colBottom()) return -1;
        int row = (int) ((fy - rowsTop()) / ROW_H) + offset;
        if (row < 0 || row >= count) return -1;
        return row;
    }

    // ------------------------------------------------------------- Ввод

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        double mx = click.x();
        double my = click.y();
        if (mx < panelX() || mx > panelX() + W || my < panelY() || my > panelY() + H) {
            return super.mouseClicked(click, doubled);
        }

        // Клики по строке поиска никуда не проваливаются
        if (my >= searchY() && my < searchY() + SEARCH_H) return true;

        float rowsTop = rowsTop();
        if (my >= rowsTop && my < colBottom()) {
            boolean inLeft = mx >= leftX() && mx <= leftX() + colW() + 2;
            boolean inRight = !inLeft && mx >= rightX() - 2 && mx <= rightX() + colW() + 2;
            if (!inLeft && !inRight) return true;
            List<Block> blocks = inLeft ? leftBlocks() : rightBlocks();
            int row = (int) ((my - rowsTop) / ROW_H) + (int) (inLeft ? scrollAll : scrollSel);
            if (row >= 0 && row < blocks.size()) {
                if (inLeft) add(blocks.get(row));
                else remove(blocks.get(row));
                if (UI.INSTANCE.sounds.getValue()) {
                    me.collective.render.SoundUtil.playClick(1.0f, 1.0f);
                }
            }
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount == 0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        if (mouseX < panelX() || mouseX > panelX() + W || mouseY < rowsTop() || mouseY > colBottom()) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        if (mouseX < rightX()) {
            scrollAll = Math.max(0, scrollAll - (float) verticalAmount * 1.5f);
        } else {
            scrollSel = Math.max(0, scrollSel - (float) verticalAmount * 1.5f);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!query.isEmpty()) {
                query = query.substring(0, query.length() - 1);
                scrollAll = 0;
                scrollSel = 0;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            // Enter добавляет первый найденный слева блок
            List<Block> left = leftBlocks();
            if (!left.isEmpty()) {
                add(left.get(0));
                return true;
            }
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (input.isValidChar()) {
            String s = input.asString();
            if (!s.isEmpty() && s.charAt(0) >= 32 && s.charAt(0) != 127) {
                query = query + s;
                scrollAll = 0;
                scrollSel = 0;
                return true;
            }
        }
        return super.charTyped(input);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Фон рисуем сами в render(): затемнение + панель.
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

package me.collective.gui.component;

import me.collective.font.FontManager;
import me.collective.module.client.UI;
import me.collective.render.RenderUtil;
import me.collective.setting.Setting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Базовый список выбранных записей реестра (блоки, сущности): строка-заголовок,
 * при клике раскрывается поиск со скроллом и чекбоксами по всем записям реестра.
 *
 * @param <T> тип записи (Block, EntityType)
 */
public abstract class RegistryListComponent<T> extends Component {
    private static final float HEADER_H = 11f;
    private static final float SEARCH_H = 10f;
    private static final float LIST_H = 66f;
    private static final float ROW_H = 9f;

    protected final Setting<List<T>> setting;
    private final List<T> all = new ArrayList<>();

    private boolean open;
    private String query = "";
    private int cursor;
    private float scroll;
    private String lastName = "";

    protected RegistryListComponent(Setting<List<T>> setting, float x, float y, float width, float height) {
        super(x, y, width, height);
        this.setting = setting;
        for (T entry : registryEntries()) {
            all.add(entry);
        }
        all.sort((a, b) -> displayName(a).compareToIgnoreCase(displayName(b)));
    }

    protected abstract Iterable<T> registryEntries();

    protected abstract Identifier idOf(T entry);

    protected String displayName(T entry) {
        return idOf(entry).getPath();
    }

    private float contentHeight() {
        return open ? HEADER_H + SEARCH_H + LIST_H : HEADER_H;
    }

    private List<T> filtered() {
        String q = query.toLowerCase().trim();
        if (q.isEmpty()) return all;
        List<T> out = new ArrayList<>();
        for (T entry : all) {
            if (displayName(entry).contains(q)) out.add(entry);
        }
        return out;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (lastName.isEmpty()) lastName = setting.getName();
        float fs = FontManager.settingsScale();
        int fh = FontManager.INSTANCE.getFontHeightScaled(fs);
        float h = contentHeight();
        if (this.height != h) this.height = h;

        Color bg = UI.INSTANCE.bgColor.getValue();
        Color text = UI.INSTANCE.textColor.getValue();
        Color accent = UI.INSTANCE.color.getValue();

        // Заголовок
        RenderUtil.drawRect(context, x, y, x + width, y + HEADER_H, bg);
        String count = String.valueOf(setting.getValue().size());
        String label = lastName + " [" + count + "]";
        FontManager.INSTANCE.drawStringWithShadowScaled(context, label, x + 4,
            y + (HEADER_H - fh) / 2f + 0.5f, text, fs);
        String gear = open ? "-" : "+";
        float gw = FontManager.INSTANCE.getStringWidthScaled(gear, fs);
        FontManager.INSTANCE.drawStringWithShadowScaled(context, gear, x + width - 6 - gw,
            y + (HEADER_H - fh) / 2f + 0.5f,
            open ? accent : text, fs);

        if (!open) return;

        float lx = x + 2;
        float lw = width - 4;
        float ly = y + HEADER_H;
        float contentTop = ly + SEARCH_H;
        float LY2 = contentTop + LIST_H;

        // Панель со вставкой
        RenderUtil.drawRect(context, lx, ly, lx + lw, LY2, new Color(10, 10, 14, 220));
        RenderUtil.drawRect(context, lx, ly, lx + lw, ly + 1, bg);

        // Строка поиска
        String prefix = "Поиск: ";
        cursor = Math.max(0, Math.min(cursor, query.length()));
        FontManager.INSTANCE.drawStringWithShadowScaled(context, prefix + query, lx + 3, ly + 1, text, fs);
        boolean caret = (System.currentTimeMillis() / 450) % 2 == 0;
        if (caret) {
            float caretX = lx + 3 + FontManager.INSTANCE.getStringWidthScaled(prefix + query.substring(0, cursor), fs);
            RenderUtil.drawRect(context, caretX, ly + 1f, caretX + 1f, ly + 9f, accent);
        }

        // Скролл-клип
        List<T> filtered = filtered();
        int maxRows = (int) (LIST_H / ROW_H);
        int maxOffset = Math.max(0, filtered.size() - maxRows);
        scroll = Math.min(scroll, maxOffset);
        float rowY = contentTop - scroll * ROW_H;

        for (T entry : filtered) {
            if (rowY + ROW_H < contentTop) {
                rowY += ROW_H;
                continue;
            }
            if (rowY >= LY2) break;

            boolean selected = setting.getValue().contains(entry);
            boolean hover = mouseX >= lx && mouseX <= lx + lw
                && mouseY >= Math.max(rowY, contentTop) && mouseY < rowY + ROW_H && mouseY < LY2;

            if (hover) {
                RenderUtil.drawRect(context, lx + 1, rowY, lx + lw - 1, rowY + Math.min(ROW_H, LY2 - rowY),
                    new Color(255, 255, 255, 25));
            }

            // Индикатор выбора
            RenderUtil.drawRect(context, lx + 2, rowY + 2.5f, lx + 5, rowY + 5.5f,
                selected ? accent : new Color(60, 60, 66));
            if (selected) {
                RenderUtil.drawRect(context, lx + lw - 2, rowY + 1f, lx + lw, rowY + ROW_H - 1f, accent);
            }

            // Текст (обрезаем по ширине)
            String name = displayName(entry);
            int avail = (int) (lw - 12);
            if (FontManager.INSTANCE.getStringWidthScaled(name, fs) > avail) {
                while (name.length() > 3 && FontManager.INSTANCE.getStringWidthScaled(name + "..", fs) > avail) {
                    name = name.substring(0, name.length() - 2);
                }
                name = name + "..";
            }
            FontManager.INSTANCE.drawStringWithShadowScaled(context, name, lx + 7, rowY + 1,
                selected ? text : new Color(170, 170, 175), fs);

            rowY += ROW_H;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (!isHovered(mouseX, mouseY)) return false;

        if (mouseY < y + HEADER_H) {
            open = !open;
            if (!open) {
                query = "";
                cursor = 0;
            } else {
                cursor = query.length();
            }
            if (UI.INSTANCE.sounds.getValue()) {
                me.collective.render.SoundUtil.playClick(1.0f, open ? 1.2f : 1.0f);
            }
            return true;
        }

        // Клик по строке списка — переключить выбор
        float contentTop = y + HEADER_H + SEARCH_H;
        if (mouseY >= contentTop && mouseY < contentTop + LIST_H) {
            List<T> filtered = filtered();
            int idx = (int) ((mouseY - contentTop) / ROW_H + scroll);
            if (idx >= 0 && idx < filtered.size()) {
                T entry = filtered.get(idx);
                List<T> newValue = new ArrayList<>(setting.getValue());
                if (newValue.contains(entry)) {
                    newValue.remove(entry);
                } else {
                    newValue.add(entry);
                }
                setting.setValue(newValue);
                if (UI.INSTANCE.sounds.getValue()) {
                    me.collective.render.SoundUtil.playClick(1.0f, 1.0f);
                }
                return true;
            }
        }

        return true; // клики в области поиска не проваливаем в другие компоненты
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (open && isHovered(mouseX, mouseY)) {
            scroll = Math.max(0, scroll - (float) verticalAmount * 1.5f);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key) {
        if (!open) return false;
        cursor = Math.max(0, Math.min(cursor, query.length()));
        if (key == GLFW.GLFW_KEY_LEFT) {
            if (cursor > 0) cursor--;
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT) {
            if (cursor < query.length()) cursor++;
            return true;
        }
        if (key == GLFW.GLFW_KEY_HOME) {
            cursor = 0;
            return true;
        }
        if (key == GLFW.GLFW_KEY_END) {
            cursor = query.length();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (cursor > 0 && !query.isEmpty()) {
                query = query.substring(0, cursor - 1) + query.substring(cursor);
                cursor--;
                scroll = 0;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE) {
            if (cursor < query.length()) {
                query = query.substring(0, cursor) + query.substring(cursor + 1);
                scroll = 0;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            open = false;
            query = "";
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr) {
        if (!open) return false;
        if (chr >= 32 && chr != 127) {
            cursor = Math.max(0, Math.min(cursor, query.length()));
            query = query.substring(0, cursor) + chr + query.substring(cursor);
            cursor++;
            scroll = 0;
            return true;
        }
        return false;
    }
}

package me.collective.gui.clickgui2;

import me.collective.category.Category;
import me.collective.font.FontManager;
import me.collective.module.Module;
import me.collective.module.ModuleManager;
import me.collective.module.client.UI;
import me.collective.module.render.Blur;
import me.collective.render.BlurEngine;
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

import static me.collective.gui.GuiGfx.fillRounded;
import static me.collective.gui.GuiGfx.outlineRounded;

/**
 * Перенос второго стиля исходника: окна категорий собираются в каскад,
 * клик выносит окно на передний план, перетаскивание за HEADER, скролл
 * колесом (у наведённого окна; вне окон — все сразу, как в классике),
 * поиск Ctrl+F по всем окнам верхним боксом, колор-пикер по свотчу.
 */
public class ClickGui2Screen extends Screen {
    private static ClickGui2Screen instance;

    public static ClickGui2Screen getInstance() {
        if (instance == null) {
            instance = new ClickGui2Screen();
        }
        return instance;
    }

    private final List<ClickGui2Window> windows = new ArrayList<>();

    private boolean searchActive;
    private String searchQuery = "";

    private ClickGui2Picker picker;
    private ClickGui2Window draggingWindow;

    private long lastNano = System.nanoTime();

    public ClickGui2Screen() {
        super(Text.literal("Collective"));
        buildWindows();
    }

    private void buildWindows() {
        windows.clear();
        Category[] order = new Category[] {
            Category.COMBAT, Category.MOVEMENT, Category.RENDER, Category.PLAYER,
            Category.MISC, Category.EXPLOIT, Category.CLIENT, Category.HIGHWAY
        };

        float startX = 10f;
        float startY = 10f;
        for (Category category : order) {
            List<Module> modules = ModuleManager.INSTANCE.getModulesByCategory(category);
            if (modules != null && !modules.isEmpty()) {
                if (startX + ClickGui2Window.WIDTH > width - 10f) {
                    startX = 10f;
                    startY += 60f;
                }
                windows.add(new ClickGui2Window(category.getName(), modules, startX, startY));
                startX += ClickGui2Window.WIDTH + 8f;
            }
        }
    }

    @Override
    public void init() {
        super.init();
        lastNano = System.nanoTime();
        for (ClickGui2Window window : windows) {
            window.setSearch(currentSearchLower());
        }
    }

    private String currentSearchLower() {
        return searchActive ? searchQuery.toLowerCase() : "";
    }

    // ── Рендер ──

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float mx = mouseX;
        float my = mouseY;
        long now = System.nanoTime();
        float dt = Math.min(0.1f, Math.max(0.001f, (now - lastNano) / 1_000_000_000f));
        lastNano = now;

        String search = currentSearchLower();

        // 1. Блюр за окнами (и пикером), как в классическом экране
        if (Blur.INSTANCE != null && Blur.INSTANCE.isToggled() && Blur.INSTANCE.clickGui.getValue()) {
            List<float[]> blurRegions = new ArrayList<>();
            for (ClickGui2Window window : windows) {
                blurRegions.add(window.bounds());
            }
            if (searchActive) {
                blurRegions.add(new float[]{boxX(), boxY(), boxX() + BOX_W, boxY() + BOX_H});
            }
            if (picker != null) {
                blurRegions.add(picker.bounds());
            }
            BlurEngine.INSTANCE.renderBlur(context, blurRegions, Blur.INSTANCE.radius.getFloatValue());
        }

        // 2. Поиск активен → окна фильтруются по нему
        for (ClickGui2Window window : windows) {
            window.setSearch(search);
        }

        // 3. Окна сзади наперед; активное перетаскивание тянется за мышью
        for (ClickGui2Window window : windows) {
            window.mouseDragging(mx, my);
            window.render(context, mx, my, dt);
        }

        // 4. Пикер цвета
        if (picker != null) {
            picker.mouseDragging(mx, my);
            picker.render(context, mx, my);
        }

        // 5. Поисковый бокс / подсказка
        if (searchActive) {
            fillRounded(context, boxX(), boxY(), BOX_W, BOX_H, 5f,
                    ClickGui2Theme.rgb(ClickGui2Theme.Role.BACKGROUND));
            outlineRounded(context, boxX(), boxY(), BOX_W, BOX_H, 5f,
                    ClickGui2Theme.rgb(ClickGui2Theme.Role.MAIN));

            String display = searchQuery.isEmpty() ? "Search..." : searchQuery;
            if (ClickGui2Widgets.caretNow()) {
                display += "_";
            }
            FontManager.INSTANCE.drawStringWithShadow(context, display,
                    boxX() + 6f, boxY() + 5f, ClickGui2Theme.rgb(ClickGui2Theme.Role.TEXT));
        } else {
            String hint = "Ctrl + F to activate search.";
            float hw = FontManager.INSTANCE.getStringWidth(hint);
            FontManager.INSTANCE.drawStringWithShadow(context, hint, width - hw - 6f, height - 12f,
                    ClickGui2Theme.rgb(ClickGui2Theme.Role.DESELECT_CATEGORY_TEXT));
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private static final float BOX_W = 180f;
    private static final float BOX_H = 18f;

    private float boxX() {
        return width / 2f - BOX_W / 2f;
    }

    private float boxY() {
        return 8f;
    }

    // ── Мышь ──

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        float mx = (float) click.x();
        float my = (float) click.y();
        int button = click.button();

        if (picker != null) {
            if (picker.mouseClicked(mx, my)) {
                return true;
            }
            picker = null; // клик мимо пикера — закрыть
        }

        for (int i = windows.size() - 1; i >= 0; i--) {
            ClickGui2Window window = windows.get(i);
            if (window.inWindow(mx, my)) {
                bringToFront(window);
                if (window.headerHit(mx, my)) {
                    window.startDrag(mx, my);
                    draggingWindow = window;
                } else if (button == 0 || button == 1) {
                    window.mouseClicked(mx, my, button,
                            color -> picker = new ClickGui2Picker(color, mx, my, width, height));
                }
                return true;
            }
        }

        return super.mouseClicked(click, bl);
    }

    @Override
    public boolean mouseReleased(Click click) {
        for (ClickGui2Window window : windows) {
            window.endDrag();
        }
        if (picker != null) {
            picker.mouseReleased();
        }
        draggingWindow = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount == 0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        float mx = (float) mouseX;
        float my = (float) mouseY;
        for (int i = windows.size() - 1; i >= 0; i--) {
            ClickGui2Window window = windows.get(i);
            if (window.inWindow(mx, my)) {
                window.scrollBy((float) (-verticalAmount * 18.0));
                return true;
            }
        }

        // вне окон — прокрутка всех окон (как в классике)
        float delta = (float) (-verticalAmount * 18.0);
        for (ClickGui2Window window : windows) {
            window.scrollBy(delta);
        }
        return true;
    }

    private void bringToFront(ClickGui2Window window) {
        if (windows.remove(window)) {
            windows.add(window);
        }
    }

    // ── Клавиатура ──

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();

        if (picker != null && key == GLFW.GLFW_KEY_ESCAPE) {
            picker = null;
            return true;
        }

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
            } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                searchActive = false;
                return true;
            }
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (searchActive && input.isValidChar() && searchQuery.length() < 32) {
            searchQuery += input.asString();
            return true;
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

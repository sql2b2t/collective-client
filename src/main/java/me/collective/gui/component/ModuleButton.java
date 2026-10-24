package me.collective.gui.component;

import me.collective.font.FontManager;
import me.collective.gui.alt.SmoothGfx;
import me.collective.gui.ClickGuiTheme;
import me.collective.module.Module;
import me.collective.module.client.UI;
import me.collective.render.RenderUtil;
import me.collective.render.SoundUtil;
import me.collective.setting.*;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class ModuleButton extends Component {
    private final Module module;
    private final List<Component> components = new ArrayList<>();
    /** Настройка[i] соответствует компоненту[i] — для проверки видимости. */
    private final List<Setting<?>> settings = new ArrayList<>();
    private boolean open;
    private float toggleProgress;

    public ModuleButton(Module module, float x, float y, float width, float height) {
        super(x, y, width, height);
        this.module = module;
        this.toggleProgress = module.isToggled() ? 1.0f : 0.0f;

        float settingH = 16f;
        float curY = y + height;

        for (Setting<?> setting : module.getSettings()) {
            Component c = makeComponent(setting, x, curY, width, settingH);
            if (c == null) continue; // тип не поддерживается — строк без дыр
            components.add(c);
            settings.add(setting);
            curY += c.getHeight();
        }
    }

    private static Component makeComponent(Setting<?> setting, float x, float y, float width, float height) {
        if (setting instanceof BooleanSetting) {
            return new BooleanComponent((BooleanSetting) setting, x, y, width, height);
        }
        if (setting instanceof NumberSetting) {
            return new SliderComponent((NumberSetting) setting, x, y, width, height);
        }
        if (setting instanceof IntegerSetting) {
            return new IntegerComponent((IntegerSetting) setting, x, y, width, height);
        }
        if (setting instanceof StringSetting) {
            return new TextComponent((StringSetting) setting, x, y, width, height);
        }
        if (setting instanceof EnumSetting) {
            return new EnumComponent((EnumSetting<?>) setting, x, y, width, height);
        }
        if (setting instanceof ModeSetting) {
            return new ModeComponent((ModeSetting) setting, x, y, width, height);
        }
        if (setting instanceof ChoiceSetting) {
            return new ChoiceComponent((ChoiceSetting) setting, x, y, width, height);
        }
        if (setting instanceof ColorSetting) {
            return new ColorComponent((ColorSetting) setting, x, y, width, height);
        }
        if (setting instanceof SettingColorSetting) {
            return new SettingColorComponent((SettingColorSetting) setting, x, y, width, height);
        }
        if (setting instanceof KeybindSetting || (setting.getValue() instanceof Keybind)) {
            return new KeybindComponent(setting, x, y, width, height);
        }
        if (setting instanceof ActionSetting) {
            return new ActionComponent((ActionSetting) setting, x, y, width, height);
        }
        if (setting instanceof BlockListSetting) {
            return new BlockListComponent((BlockListSetting) setting, x, y, width, height);
        }
        if (setting instanceof EntityListSetting) {
            return new EntityListComponent((EntityListSetting) setting, x, y, width, height);
        }
        if (setting instanceof ItemListSetting) {
            return new ItemListComponent((ItemListSetting) setting, x, y, width, height);
        }
        return null;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        float speed = UI.INSTANCE.animSpeed.getFloatValue() * 0.1f;
        if (module.isToggled()) {
            toggleProgress = Math.min(1.0f, toggleProgress + speed);
        } else {
            toggleProgress = Math.max(0.0f, toggleProgress - speed);
        }

        // Background and animated fill
        float btnX1 = x;
        float btnX2 = x + width;
        float btnY1 = y;
        float btnY2 = y + height;

        RenderUtil.drawRect(context, btnX1, btnY1, btnX2, btnY2, UI.INSTANCE.bgButton.getValue());

        if (toggleProgress > 0.001f) {
            float fillX2 = btnX1 + width * toggleProgress;
            RenderUtil.drawRect(context, btnX1, btnY1, fillX2, btnY2, UI.INSTANCE.bgEnabled.getValue());
        }

        // Module Name
        int pad = UI.INSTANCE.modulePadding.getIntValue();
        Color textCol = module.isToggled() ? UI.INSTANCE.enabledColor.getValue() : UI.INSTANCE.textColor.getValue();
        FontManager.INSTANCE.drawStringWithShadow(context, module.getName(), x + pad, y + (height - FontManager.INSTANCE.getFontHeight()) / 2f + 0.5f, textCol);

        // Keybind indicator [KEY]
        if (UI.INSTANCE.binds.getValue() && module.getKeybind().getValue() != GLFW.GLFW_KEY_UNKNOWN) {
            String bindText = "[" + module.getKeybind().getKeyName().toUpperCase() + "]";
            float nameW = FontManager.INSTANCE.getStringWidth(module.getName());
            FontManager.INSTANCE.drawStringWithShadow(context, bindText, x + pad + nameW + 3, y + (height - FontManager.INSTANCE.getFontHeight()) / 2f + 1f, new Color(170, 170, 170));
        }

        // Gear indicator (+ / -)
        if (UI.INSTANCE.gear.getValue() && !components.isEmpty()) {
            String gearStr = open ? "-" : "+";
            float gw = FontManager.INSTANCE.getStringWidth(gearStr);
            FontManager.INSTANCE.drawStringWithShadow(context, gearStr, x + width - 4 - gw, y + (height - FontManager.INSTANCE.getFontHeight()) / 2f + 0.5f, UI.INSTANCE.textColor.getValue());
        }

        // Render sub-settings when open
        if (open && !components.isEmpty()) {
            float curY = y + height;
            float sH = getSettingsHeight();

            // Distinct settings container background (frosted glass backing with smooth corners)
            SmoothGfx.fillRounded(context, x, curY, width, sH, 3.0f, 0x500A0E18);
            // Left accent line synchronized with Colors
            SmoothGfx.fillRounded(context, x, curY, 2.0f, sH, 1.0f, ClickGuiTheme.getAccentColor());

            for (int i = 0; i < components.size(); i++) {
                Setting<?> setting = settings.get(i);
                if (!setting.isVisible()) continue; // невидимка не занимает строку
                Component c = components.get(i);
                float itemH = c.getHeight();
                c.setX(x);
                c.setY(curY);
                c.setWidth(width);
                c.setHeight(itemH);
                c.render(context, mouseX, mouseY, delta);
                curY += itemH;
            }
        }
    }

    public float getSettingsHeight() {
        if (!open) return 0;
        float h = 0;
        for (int i = 0; i < components.size(); i++) {
            if (!settings.get(i).isVisible()) continue;
            h += components.get(i).getHeight();
        }
        return h;
    }

    public float getTotalHeight() {
        return height + getSettingsHeight();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered(mouseX, mouseY)) {
            if (button == 0) {
                module.toggle();
                if (UI.INSTANCE.sounds.getValue()) {
                    SoundUtil.playClick(1.0f, 1.0f);
                }
                return true;
            } else if (button == 1) {
                if (!components.isEmpty()) {
                    open = !open;
                    if (UI.INSTANCE.sounds.getValue()) {
                        SoundUtil.playClick(1.0f, 1.2f);
                    }
                    return true;
                }
            }
        }

        if (open) {
            for (Component c : components) {
                if (c.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }

        return false;
    }

    /** Разворачивает режим ввода у всех текстовых строк модуля. */
    public void clearFocusFromTextInputs() {
        for (Component c : components) {
            c.clearFocus();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (open) {
            for (Component c : components) {
                if (c.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (open) {
            for (Component c : components) {
                c.mouseReleased(mouseX, mouseY, button);
            }
        }
    }

    @Override
    public boolean keyPressed(int key) {
        if (open) {
            for (Component c : components) {
                if (c.keyPressed(key)) return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr) {
        if (open) {
            for (Component c : components) {
                if (c.charTyped(chr)) return true;
            }
        }
        return false;
    }

    public Module getModule() {
        return module;
    }

    public boolean isOpen() {
        return open;
    }

    public List<Component> getComponents() {
        return components;
    }
}
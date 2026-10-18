package me.collective.gui.component;

import me.collective.font.FontManager;
import me.collective.module.Module;
import me.collective.module.client.UI;
import me.collective.render.RenderUtil;
import me.collective.render.SoundUtil;
import me.collective.setting.*;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class ModuleButton extends Component {
    private final Module module;
    private final List<Component> components = new ArrayList<>();
    private boolean open;
    private float toggleProgress;

    public ModuleButton(Module module, float x, float y, float width, float height) {
        super(x, y, width, height);
        this.module = module;
        this.toggleProgress = module.isToggled() ? 1.0f : 0.0f;

        float settingH = 11;
        float curY = y + height;

        for (Setting<?> setting : module.getSettings()) {
            if (setting instanceof BooleanSetting) {
                components.add(new BooleanComponent((BooleanSetting) setting, x, curY, width, settingH));
            } else if (setting instanceof NumberSetting) {
                components.add(new SliderComponent((NumberSetting) setting, x, curY, width, settingH));
            } else if (setting instanceof EnumSetting) {
                components.add(new EnumComponent((EnumSetting<?>) setting, x, curY, width, settingH));
            } else if (setting instanceof ModeSetting) {
                components.add(new ModeComponent((ModeSetting) setting, x, curY, width, settingH));
            } else if (setting instanceof ChoiceSetting) {
                components.add(new ChoiceComponent((ChoiceSetting) setting, x, curY, width, settingH));
            } else if (setting instanceof ColorSetting) {
                components.add(new ColorComponent((ColorSetting) setting, x, curY, width, settingH));
            } else if (setting instanceof KeybindSetting) {
                components.add(new KeybindComponent((KeybindSetting) setting, x, curY, width, settingH));
            } else if (setting instanceof ActionSetting) {
                components.add(new ActionComponent((ActionSetting) setting, x, curY, width, settingH));
            } else if (setting instanceof BlockListSetting) {
                components.add(new BlockListComponent((BlockListSetting) setting, x, curY, width, settingH));
            } else if (setting instanceof EntityListSetting) {
                components.add(new EntityListComponent((EntityListSetting) setting, x, curY, width, settingH));
            } else if (setting instanceof ItemListSetting) {
                components.add(new ItemListComponent((ItemListSetting) setting, x, curY, width, settingH));
            }
            curY += settingH;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
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

            // Distinct settings container background (frosted glass backing)
            RenderUtil.drawRect(context, x, curY, x + width, curY + sH, new Color(14, 14, 18, 180));
            // Left accent line
            RenderUtil.drawRect(context, x, curY, x + 2.0f, curY + sH, UI.INSTANCE.color.getValue());

            for (Component c : components) {
                c.setX(x);
                c.setY(curY);
                c.setWidth(width);
                c.render(context, mouseX, mouseY, delta);
                curY += c.getHeight();
            }
        }
    }

    public float getSettingsHeight() {
        if (!open) return 0;
        float h = 0;
        for (Component c : components) {
            h += c.getHeight();
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

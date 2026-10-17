package me.collective.module.client;

import me.collective.category.Category;
import me.collective.gui.ClickGuiScreen;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.ColorSetting;
import me.collective.setting.NumberSetting;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

public class UI extends Module {
    public static UI INSTANCE;

    public final NumberSetting buttonHeight = add(new NumberSetting("ButtonHeight", 12, 10, 24, 1));
    public final NumberSetting frameWidth = add(new NumberSetting("FrameWidth", 92, 70, 200, 1));
    public final BooleanSetting customWidths = add(new BooleanSetting("CustomWidths", false));
    public final NumberSetting combatWidth = add(new NumberSetting("CombatWidth", 92, 70, 200, 1, (v) -> customWidths.getValue()));
    public final NumberSetting movementWidth = add(new NumberSetting("MovementWidth", 92, 70, 200, 1, (v) -> customWidths.getValue()));
    public final NumberSetting highwayWidth = add(new NumberSetting("HighwayWidth", 92, 70, 200, 1, (v) -> customWidths.getValue()));
    public final NumberSetting playerWidth = add(new NumberSetting("PlayerWidth", 92, 70, 200, 1, (v) -> customWidths.getValue()));
    public final NumberSetting renderWidth = add(new NumberSetting("RenderWidth", 92, 70, 200, 1, (v) -> customWidths.getValue()));
    public final NumberSetting exploitWidth = add(new NumberSetting("ExploitWidth", 92, 70, 200, 1, (v) -> customWidths.getValue()));
    public final NumberSetting miscWidth = add(new NumberSetting("MiscWidth", 92, 70, 200, 1, (v) -> customWidths.getValue()));
    public final NumberSetting clientWidth = add(new NumberSetting("ClientWidth", 92, 70, 200, 1, (v) -> customWidths.getValue()));
    public final NumberSetting hudWidth = add(new NumberSetting("HudWidth", 92, 70, 200, 1, (v) -> customWidths.getValue()));

    public final NumberSetting modulePadding = add(new NumberSetting("ModulePadding", 2, 0, 10, 1));
    public final NumberSetting settingsFontSize = add(new NumberSetting("SettingsFontSize", 7.0, 5.0, 14.0, 0.5));
    public final NumberSetting animSpeed = add(new NumberSetting("AnimSpeed", 2.0, 0.5, 5.0, 0.1));
    public final NumberSetting guiScale = add(new NumberSetting("GuiScale", 1.0, 0.5, 2.0, 0.1));

    public float getFrameWidth(Category category) {
        if (!customWidths.getValue() || category == null) {
            return frameWidth.getIntValue();
        }
        return switch (category) {
            case COMBAT -> combatWidth.getIntValue();
            case MOVEMENT -> movementWidth.getIntValue();
            case HIGHWAY -> highwayWidth.getIntValue();
            case PLAYER -> playerWidth.getIntValue();
            case RENDER -> renderWidth.getIntValue();
            case EXPLOIT -> exploitWidth.getIntValue();
            case MISC -> miscWidth.getIntValue();
            case CLIENT -> clientWidth.getIntValue();
            case HUD -> hudWidth.getIntValue();
        };
    }

    public final BooleanSetting gear = add(new BooleanSetting("Gear", false));
    public final BooleanSetting binds = add(new BooleanSetting("Binds", true));
    public final BooleanSetting line = add(new BooleanSetting("Line", true));
    public final BooleanSetting snow = add(new BooleanSetting("Snow", true));
    public final BooleanSetting windowShadow = add(new BooleanSetting("WindowShadow", false));
    // Exact Collective default colors:
    public final ColorSetting shadowColor = add(new ColorSetting("ShadowColor", new Color(0, 0, 0, 70)));
    public final NumberSetting shadowSize = add(new NumberSetting("ShadowSize", 3.0, 1.0, 10.0, 0.5));

    public final BooleanSetting sounds = add(new BooleanSetting("Sounds", true));
    public final BooleanSetting hoverSound = add(new BooleanSetting("Hover", true));
    public final NumberSetting volume = add(new NumberSetting("Volume", 1.0, 0.1, 2.0, 0.1));

    // Collective signature pastel lavender & translucent obsidian glass
    public final ColorSetting color = add(new ColorSetting("Color", new Color(189, 153, 255, 115)));
    public final ColorSetting bgColor = add(new ColorSetting("BgColor", new Color(2, 0, 8, 119)));
    public final ColorSetting bgButton = add(new ColorSetting("BgButton", new Color(159, 128, 219, 42)));
    public final ColorSetting bgEnabled = add(new ColorSetting("BgEnabled", new Color(189, 153, 255, 115)));
    public final ColorSetting textColor = add(new ColorSetting("TextColor", new Color(249, 244, 255, 255)));
    public final ColorSetting enabledColor = add(new ColorSetting("EnabledColor", new Color(221, 201, 255, 255)));
    public final ColorSetting tint = add(new ColorSetting("Tint", new Color(96, 62, 77, 37)));
    public final ColorSetting gradientColor = add(new ColorSetting("GradientColor", new Color(194, 162, 252, 34)));

    public UI() {
        super("UI", "Displays the client's click GUI.", Category.CLIENT, GLFW.GLFW_KEY_RIGHT_SHIFT);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        if (mc.currentScreen == null) {
            mc.setScreen(ClickGuiScreen.getInstance());
        }
    }

    @Override
    public void onDisable() {
        if (mc.currentScreen instanceof ClickGuiScreen || mc.currentScreen instanceof me.collective.gui.hud.HudEditorScreen) {
            mc.setScreen(null);
        }
    }
}

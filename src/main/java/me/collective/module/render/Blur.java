package me.collective.module.render;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;

public class Blur extends Module {
    public static Blur INSTANCE;

    // 1. Background Blur (весь экран / мир игры на заднем плане)
    public final BooleanSetting clickGui = add(new BooleanSetting("ClickGUI", true));
    public final NumberSetting radius = add(new NumberSetting("Radius", 10, 1, 16, 1, (v) -> clickGui.getValue()));
    public final NumberSetting opacity = add(new NumberSetting("Opacity", 0.01, 0.01, 1.0, 0.01, (v) -> clickGui.getValue()));

    // 2. Categories / Panels (шапки и карточки категорий)
    public final BooleanSetting categories = add(new BooleanSetting("Categories", true));
    public final NumberSetting catRadius = add(new NumberSetting("CatRadius", 6, 1, 16, 1, (v) -> categories.getValue()));
    public final NumberSetting catOpacity = add(new NumberSetting("CatOpacity", 0.01, 0.01, 1.0, 0.01, (v) -> categories.getValue()));

    // 3. Settings (раскрытые списки настроек модулей)
    public final BooleanSetting settings = add(new BooleanSetting("Settings", true));
    public final NumberSetting setRadius = add(new NumberSetting("SetRadius", 8, 1, 16, 1, (v) -> settings.getValue()));
    public final NumberSetting setOpacity = add(new NumberSetting("SetOpacity", 0.01, 0.01, 1.0, 0.01, (v) -> settings.getValue()));

    // 4. Tooltips (всплывающие подсказки описаний модулей)
    public final BooleanSetting tooltips = add(new BooleanSetting("Tooltips", true));
    public final NumberSetting tipRadius = add(new NumberSetting("TipRadius", 10, 1, 16, 1, (v) -> tooltips.getValue()));
    public final NumberSetting tipOpacity = add(new NumberSetting("TipOpacity", 0.35, 0.01, 1.0, 0.01, (v) -> tooltips.getValue()));

    // 5. HUD
    public final BooleanSetting hud = add(new BooleanSetting("HUD", false));

    public Blur() {
        super("Blur", "Размывает задний план в интерфейсах, панелях, настройках и подсказках.", Category.RENDER);
        INSTANCE = this;
        setToggled(true);
    }
}
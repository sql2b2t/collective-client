package me.collective.module.render;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;

/**
 * Модификатор эндер-кристаллов для удобного PvP: уменьшение размера, фиксация вращения и покачивания.
 */
public class CrystalModifier extends Module {
    public static CrystalModifier INSTANCE;

    public final NumberSetting scale = add(new NumberSetting("Scale", 0.6, 0.1, 1.5, 0.05));
    public final BooleanSetting bounce = add(new BooleanSetting("Bounce", false));
    public final BooleanSetting rotate = add(new BooleanSetting("Rotate", false));
    public final BooleanSetting base = add(new BooleanSetting("Base", false));
    public final BooleanSetting beam = add(new BooleanSetting("Beam", false));

    public CrystalModifier() {
        super("CrystalModifier", "Изменение масштаба кристаллов, отключение анимаций покачивания и вращения.", Category.RENDER);
        INSTANCE = this;
    }
}

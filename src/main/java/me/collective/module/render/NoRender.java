package me.collective.module.render;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;

/**
 * Отключает раздражающие экранные эффекты и оверлеи.
 *
 * <p>Тряска при уроне живёт в отдельном модуле NoHurtCam — здесь её нет,
 * чтобы не дублировать.
 */
public class NoRender extends Module {
    public static NoRender INSTANCE;

    public final BooleanSetting fire = add(new BooleanSetting("Огонь", true));
    public final BooleanSetting pumpkin = add(new BooleanSetting("Тыква", true));
    public final BooleanSetting powderSnow = add(new BooleanSetting("Порошковый снег", true));
    public final BooleanSetting nausea = add(new BooleanSetting("Тошнота", true));
    public final BooleanSetting water = add(new BooleanSetting("Под водой", false));
    public final BooleanSetting inWall = add(new BooleanSetting("В блоке", false));
    public final BooleanSetting portal = add(new BooleanSetting("Портал", false));
    public final BooleanSetting vignette = add(new BooleanSetting("Виньетка", false));

    public NoRender() {
        super("NoRender", "Убирает экранные оверлеи: огонь, тыкву, портал и т.п.", Category.RENDER);
        INSTANCE = this;
    }
}

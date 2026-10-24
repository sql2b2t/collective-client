package me.collective.module.client;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.NumberSetting;

public class AntiCheat extends Module {
    public static AntiCheat INSTANCE;

    public final BooleanSetting f2b2t = add(new BooleanSetting("2b2t", false));
    public final NumberSetting yawStep = add(new NumberSetting("YawStep", 55.0, 5.0, 180.0, 1.0));
    public final BooleanSetting movementSync = add(new BooleanSetting("MovementSync", false));
    public final EnumSetting<Rotations> rotations = add(new EnumSetting<>("Rotations", Rotations.CLIENT));
    public final BooleanSetting strictDirection = add(new BooleanSetting("StrictDirection", false));

    public enum Rotations {
        NONE, PACKET, CLIENT
    }

    public AntiCheat() {
        super("AntiCheat", "Управление глобальными профилями и настройками под разные античиты.", Category.CLIENT);
        INSTANCE = this;
    }

    public boolean is2b2t() {
        return isToggled() && f2b2t.getValue();
    }
}
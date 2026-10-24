package me.collective.module.combat;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;

/**
 * Порт HitBoxes из ThunderHack.
 *
 * <p>Расширяет хитбоксы сущностей — проще попадать. Работает через миксин
 * Entity.getBoundingBox (см. MixinEntityHitbox).
 */
public class HitBox extends Module {
    public static HitBox INSTANCE;

    public final NumberSetting xzExpand = add(new NumberSetting("XZExpand", 1.0, 0.0, 5.0, 0.1));
    public final NumberSetting yExpand = add(new NumberSetting("YExpand", 0.0, 0.0, 5.0, 0.1));
    public final BooleanSetting affectToAura = add(new BooleanSetting("AffectToAura", false));

    public HitBox() {
        super("HitBox", "Увеличенные хитбоксы сущностей.", Category.COMBAT);
        INSTANCE = this;
    }

    @Override
    public String getInfoString() {
        return "H: " + Math.round(xzExpand.compute() * 100.0) / 100.0
            + " V: " + Math.round(yExpand.compute() * 100.0) / 100.0;
    }
}
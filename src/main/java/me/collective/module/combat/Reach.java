package me.collective.module.combat;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;

/**
 * Порт Reach из ThunderHack.
 *
 * <p>Увеличивает дистанцию ударов и ломания блоков (см. MixinPlayerReach).
 */
public class Reach extends Module {
    public static Reach INSTANCE;

    public final NumberSetting blocksRange = add(new NumberSetting("BlocksRange", 3.0, 0.1, 10.0, 0.1));
    public final NumberSetting entityRange = add(new NumberSetting("EntityRange", 3.0, 0.1, 10.0, 0.1));
    public final BooleanSetting creative = add(new BooleanSetting("Creative", false));
    public final NumberSetting creativeBlocksRange = add(new NumberSetting("CBlocksRange", 5.0, 0.1, 10.0, 0.1, (v) -> creative.getValue()));
    public final NumberSetting creativeEntityRange = add(new NumberSetting("CEntityRange", 5.0, 0.1, 10.0, 0.1, (v) -> creative.getValue()));

    public Reach() {
        super("Reach", "Увеличенная дальность ударов и блоков.", Category.COMBAT);
        INSTANCE = this;
    }

    @Override
    public String getInfoString() {
        return "B: " + blocksRange.compute() + " E: " + entityRange.compute();
    }
}
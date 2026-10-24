package me.collective.module.client;

import me.collective.category.Category;
import me.collective.config.ConfigManager;
import me.collective.module.Module;
import me.collective.setting.NumberSetting;

/**
 * Автосохранение конфига: интервал + ручной сейв через включение.
 */
public class AutoSaveCfg extends Module {
    public static AutoSaveCfg INSTANCE;

    public final NumberSetting intervalMin = add(new NumberSetting("Интервал (мин)", 3.0, 0.5, 30.0, 0.5));

    public AutoSaveCfg() {
        super("AutoSaveCfg", "Автосохранение конфига.", Category.CLIENT);
        INSTANCE = this;
        setToggled(true);

        intervalMin.setChangeListener(v -> ConfigManager.getInstance()
            .setAutosaveMs((long) (v.doubleValue() * 60_000L)));
        ConfigManager.getInstance().setAutosaveMs((long) (intervalMin.getValue().doubleValue() * 60_000L));
    }

    @Override
    public void onEnable() {
        ConfigManager.getInstance().markDirty();
    }
}
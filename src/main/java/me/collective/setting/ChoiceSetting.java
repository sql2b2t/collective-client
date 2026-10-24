package me.collective.setting;

import java.util.ArrayList;
import java.util.List;

public class ChoiceSetting extends Setting<String> {
    private final List<BooleanSetting> options = new ArrayList<>();

    public ChoiceSetting(String name, BooleanSetting... settings) {
        super(name, "");
        if (settings != null) {
            for (BooleanSetting s : settings) {
                options.add(s);
            }
        }
    }

    public List<BooleanSetting> getOptions() {
        return options;
    }

    public boolean process(String name) {
        if (name == null) return false;
        for (BooleanSetting s : options) {
            if (s.getName().trim().equalsIgnoreCase(name.trim())) {
                return s.getValue();
            }
        }
        return false;
    }

    public ChoiceSetting handle(java.util.function.Supplier<Boolean> hiddenSupplier) {
        setVisibilityCondition(hiddenSupplier);
        return this;
    }
}
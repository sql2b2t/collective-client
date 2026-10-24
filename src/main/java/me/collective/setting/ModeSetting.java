package me.collective.setting;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class ModeSetting extends Setting<String> {
    private final List<String> modes;

    public ModeSetting(String name, String defaultMode, String... modes) {
        super(name, defaultMode);
        if (modes != null && modes.length > 0) {
            this.modes = Arrays.asList(modes);
        } else {
            this.modes = List.of(defaultMode);
        }
    }

    /** Вариант с отображением только при истинном предикате (например, по выбранному режиму). */
    public ModeSetting(String name, String defaultMode, Predicate<Boolean> visible, String... modes) {
        super(name, defaultMode, visible);
        if (modes != null && modes.length > 0) {
            this.modes = Arrays.asList(modes);
        } else {
            this.modes = List.of(defaultMode);
        }
    }

    public List<String> getModes() {
        return modes;
    }

    public boolean process(String mode) {
        if (mode == null) return false;
        return getValue().trim().equalsIgnoreCase(mode.trim());
    }

    public String compute() {
        return getValue();
    }

    public ModeSetting handle(Supplier<Boolean> hiddenSupplier) {
        setVisibilityCondition(hiddenSupplier);
        return this;
    }

    public void cycle() {
        int index = -1;
        for (int i = 0; i < modes.size(); i++) {
            if (modes.get(i).trim().equalsIgnoreCase(getValue().trim())) {
                index = i;
                break;
            }
        }
        if (index == -1) index = 0;
        int next = (index + 1) % modes.size();
        setValue(modes.get(next));
    }

    public void cycleReverse() {
        int index = -1;
        for (int i = 0; i < modes.size(); i++) {
            if (modes.get(i).trim().equalsIgnoreCase(getValue().trim())) {
                index = i;
                break;
            }
        }
        if (index == -1) index = 0;
        int prev = (index - 1 + modes.size()) % modes.size();
        setValue(modes.get(prev));
    }
}

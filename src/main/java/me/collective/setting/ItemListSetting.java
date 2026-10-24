package me.collective.setting;

import net.minecraft.world.item.Item;
import java.util.ArrayList;
import java.util.List;

public class ItemListSetting extends Setting<List<Item>> {
    public ItemListSetting(String name, List<Item> defaultValue) {
        super(name, defaultValue);
    }

    public ItemListSetting(String name, List<Item> defaultValue, java.util.function.Predicate<Boolean> visible) {
        super(name, defaultValue, visible);
    }

    public static class Builder {
        private String name = "";
        private String description = "";
        private List<Item> defaultValue = new ArrayList<>();

        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder defaultValue(List<Item> defaultValue) { this.defaultValue = defaultValue; return this; }

        public Setting<List<Item>> build() {
            return new ItemListSetting(name, defaultValue != null ? defaultValue : new ArrayList<>());
        }
    }
}
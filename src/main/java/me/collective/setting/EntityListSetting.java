package me.collective.setting;

import net.minecraft.world.entity.EntityType;
import java.util.ArrayList;
import java.util.List;

public class EntityListSetting extends Setting<List<EntityType<?>>> {
    public EntityListSetting(String name, List<EntityType<?>> defaultValue) {
        super(name, defaultValue);
    }

    public static class Builder {
        private String name = "";
        private String description = "";
        private List<EntityType<?>> defaultValue = new ArrayList<>();

        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder defaultValue(List<EntityType<?>> defaultValue) { this.defaultValue = defaultValue; return this; }
        @SafeVarargs
        public final Builder defaultValue(EntityType<?>... entities) { this.defaultValue = List.of(entities); return this; }

        public EntityListSetting build() {
            return new EntityListSetting(name, defaultValue != null ? defaultValue : new ArrayList<>());
        }
    }
}
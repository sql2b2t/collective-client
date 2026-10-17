package me.collective.gui.component;

import me.collective.setting.EntityListSetting;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** Список сущностей (EntityListSetting) с поиском и чекбоксами. */
public class EntityListComponent extends RegistryListComponent<EntityType<?>> {
    public EntityListComponent(EntityListSetting setting, float x, float y, float width, float height) {
        super(setting, x, y, width, height);
    }

    @Override
    protected Iterable<EntityType<?>> registryEntries() {
        return Registries.ENTITY_TYPE;
    }

    @Override
    protected Identifier idOf(EntityType<?> entry) {
        return Registries.ENTITY_TYPE.getId(entry);
    }
}

package me.collective.gui.settings;

import me.collective.setting.ItemListSetting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Порт ItemListSettingScreen из Meteor Client (через CollectionListSettingScreen):
 * предметы ищутся по переведённому имени и id (нечёткий поиск), в колонках
 * рисуется иконка предмета. Из списка исключается только {@code air}.
 */
public class ItemListSettingScreen extends CollectionListSettingScreen<Item> {

    public ItemListSettingScreen(Screen parent, ItemListSetting setting) {
        super(parent, setting, "Select Items");
    }

    @Override
    protected Iterable<Item> allValues() {
        return Registries.ITEM;
    }

    @Override
    protected boolean includeValue(Item value) {
        return value != Items.AIR;
    }

    @Override
    protected String[] getValueNames(Item value) {
        return new String[]{
                value.getName().getString(),
                Registries.ITEM.getId(value).toString()
        };
    }

    @Override
    protected String labelFor(Item value) {
        return value.getName().getString();
    }

    @Override
    protected ItemStack iconFor(Item value) {
        return new ItemStack(value);
    }

    @Override
    protected String emptyLeftHint() {
        return "Все предметы выбраны";
    }
}
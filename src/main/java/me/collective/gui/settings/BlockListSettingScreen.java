package me.collective.gui.settings;

import me.collective.setting.BlockListSetting;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * Порт BlockListSettingScreen из Meteor Client (через CollectionListSettingScreen):
 * блоки ищутся по переведённому имени и id (нечёткий поиск), в колонках
 * рисуется предметная иконка блока. Поведение как в оригинале:
 * из списка исключаются только {@code air} и {@code *_wall_banner}, а стеновые
 * баннеры добавляются и убираются парно со своими баннерами.
 */
public class BlockListSettingScreen extends CollectionListSettingScreen<Block> {

    public BlockListSettingScreen(Screen parent, BlockListSetting setting) {
        super(parent, setting, "Select Blocks");
    }

    @Override
    protected Iterable<Block> allValues() {
        return BuiltInRegistries.BLOCK;
    }

    @Override
    protected boolean includeValue(Block value) {
        if (value == Blocks.AIR) return false;
        return !BuiltInRegistries.BLOCK.getKey(value).getPath().endsWith("_wall_banner");
    }

    @Override
    protected String[] getValueNames(Block value) {
        return new String[]{
                value.getName().getString(),
                BuiltInRegistries.BLOCK.getKey(value).toString()
        };
    }

    @Override
    protected String labelFor(Block value) {
        return value.getName().getString();
    }

    @Override
    protected ItemStack iconFor(Block value) {
        return new ItemStack(value);
    }

    @Override
    protected Block getAdditionalValue(Block value) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(value);
        String path = id.getPath();
        // Баннеры добавляются/удаляются вместе со стеновой версией (как в Meteor)
        if (path.endsWith("_wall_banner") || !path.endsWith("_banner")) return null;
        return BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(id.getNamespace(), path.substring(0, path.length() - 6) + "wall_banner"));
    }
}
package me.collective.gui.component;

import me.collective.setting.BlockListSetting;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** Список блоков (BlockListSetting) с поиском и чекбоксами. */
public class BlockListComponent extends RegistryListComponent<Block> {
    public BlockListComponent(BlockListSetting setting, float x, float y, float width, float height) {
        super(setting, x, y, width, height);
    }

    @Override
    protected Iterable<Block> registryEntries() {
        return Registries.BLOCK;
    }

    @Override
    protected Identifier idOf(Block entry) {
        return Registries.BLOCK.getId(entry);
    }
}

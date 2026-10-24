package me.collective.module.player;

import me.collective.category.Category;
import me.collective.module.Module;

/**
 * Порт MultiTask из Meteor Client.
 *
 * <p>Позволяет есть и копать/бить одновременно (см. MixinMultiTask).
 */
public class MultiTask extends Module {
    public MultiTask() {
        super("MultiTask", "Использование предметов во время копания.", Category.PLAYER);
    }
}
package me.collective.setting;

import net.minecraft.block.Block;
import java.util.ArrayList;
import java.util.List;

public class BlockListSetting extends Setting<List<Block>> {
    public BlockListSetting(String name, List<Block> defaultValue) {
        super(name, defaultValue);
    }

    public static class Builder {
        private String name = "";
        private String description = "";
        private List<Block> defaultValue = new ArrayList<>();

        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder defaultValue(List<Block> defaultValue) { this.defaultValue = defaultValue; return this; }
        public Builder defaultValue(Block... blocks) { this.defaultValue = List.of(blocks); return this; }

        public BlockListSetting build() {
            return new BlockListSetting(name, defaultValue != null ? defaultValue : new ArrayList<>());
        }
    }
}

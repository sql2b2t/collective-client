package me.collective.client.utils

import net.minecraft.client.KeyMapping

class SoftwareKeyMapping : KeyMapping("collective-wrapper-keybind", 0, KeyMapping.CATEGORY_MISC) {
    private var down: Boolean = false

    init {
        KeyMapping.ALL.remove("collective-wrapper-keybind")
    }

    override fun isDown(): Boolean {
        return down
    }

    override fun setDown(down: Boolean) {
        this.down = down
    }
}

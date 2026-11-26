package me.collective.client.render

import java.util.function.Supplier
import net.minecraft.network.chat.Style

interface DynamicColorStyle {
    fun `collective$withColor`(colorSupplier: Supplier<*>): Style
}

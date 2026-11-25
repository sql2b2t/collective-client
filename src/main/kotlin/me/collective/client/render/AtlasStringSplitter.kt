package me.collective.client.render

import net.minecraft.client.StringSplitter

open class AtlasStringSplitter(renderer: AtlasTextRenderer) : StringSplitter(
    WidthProvider { codePoint, _ ->
        renderer.getCharacterWidth(Character.toString(codePoint)[0])
    }
)

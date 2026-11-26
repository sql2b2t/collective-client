package me.collective.client.gui.components

enum class ComponentFlag {
   PADDING_SHIFT,
   SILENT,
   SILENT_HOVER;

   val mask: Int = 1 shl this.ordinal
}

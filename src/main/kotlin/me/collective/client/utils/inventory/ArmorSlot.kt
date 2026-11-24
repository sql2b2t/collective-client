package me.collective.client.utils.inventory

enum class ArmorSlot(
   @get:JvmName("getInventoryIndexField") val inventoryIndex: Int,
   @get:JvmName("getArmorIndexField") val armorIndex: Int
) {
   HELMET(39, 3),
   CHESTPLATE(38, 2),
   LEGGINGS(37, 1),
   BOOTS(36, 0);

   fun getInventoryIndex(): Int = this.inventoryIndex
   fun getArmorIndex(): Int = this.armorIndex
   fun getContainerSlot(): Int = 8 - this.armorIndex

   val containerSlot: Int
      @JvmName("getContainerSlotProp") get() = getContainerSlot()
}

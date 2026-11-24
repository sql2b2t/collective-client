package me.collective.client.modules.movement

import me.collective.client.settings.Setting
import java.util.HashMap
import java.util.Objects

class EnumSelectionMap<T>(
   choiceSetting: Setting<out Enum<*>>
) {
   private val valuesByChoice: HashMap<Enum<*>, T> = HashMap()
   private val choiceSetting: Setting<out Enum<*>> = Objects.requireNonNull(choiceSetting)

   fun register(choice: Enum<*>, value: T) {
      this.valuesByChoice[choice] = value
   }

   fun getSelected(): T {
      return this.valuesByChoice[this.choiceSetting.value]!!
   }

   @get:JvmName("currentSelected")
   val selected: T
      get() = getSelected()
}

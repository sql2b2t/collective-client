package me.collective.client.utils

interface Toggleable {
   fun enable()

   fun isToggled(): Boolean

   fun disable()

   fun toggle() {
      if (isToggled()) {
         disable()
      } else {
         enable()
      }
   }

   fun setToggled(toggled: Boolean) {
      if (isToggled() != toggled) {
         toggle()
      }
   }
}

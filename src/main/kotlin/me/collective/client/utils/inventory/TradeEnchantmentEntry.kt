package me.collective.client.utils.inventory

import com.google.gson.annotations.SerializedName
import java.util.Objects

class TradeEnchantmentEntry(
   @field:SerializedName("name") private val name: String,
   @field:SerializedName("lvl") private val level: Int
) {

   fun name(): String {
      return this.name
   }

   fun level(): Int {
      return this.level
   }

   override fun toString(): String {
      return "TradeEnchantmentEntry[name=" + this.name + ", level=" + this.level + "]"
   }

   override fun hashCode(): Int {
      return 31 * Objects.hashCode(this.name) + this.level
   }

   override fun equals(other: Any?): Boolean {
      if (this === other) {
         return true
      }
      if (other !is TradeEnchantmentEntry) {
         return false
      }

      return this.level == other.level() && Objects.equals(this.name, other.name)
   }

   @SerializedName("name")
   fun getName(): String {
      return this.name
   }

   @SerializedName("lvl")
   fun getLevel(): Int {
      return this.level
   }
}

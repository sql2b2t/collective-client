package me.collective.client.utils.combat

interface EndCrystalState {
   fun getSpawnTime(): Long

   fun setClientAttacked(attacked: Boolean)

   fun isClientAttacked(): Boolean
}

var EndCrystalState.isClientAttacked: Boolean
   get() = isClientAttacked()
   set(value) = setClientAttacked(value)


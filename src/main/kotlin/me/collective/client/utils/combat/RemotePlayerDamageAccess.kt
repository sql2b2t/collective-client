package me.collective.client.utils.combat

import net.minecraft.world.damagesource.DamageSource

interface RemotePlayerDamageAccess {
   fun damageSuper(source: DamageSource, amount: Float): Boolean = true
}

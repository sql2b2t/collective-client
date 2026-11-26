package me.collective.client.events

import net.minecraft.world.effect.MobEffectInstance

class LivingEntityAddEffectEvent(val effect: MobEffectInstance) : CancellableEvent()

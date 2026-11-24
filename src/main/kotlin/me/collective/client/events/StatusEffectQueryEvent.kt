package me.collective.client.events

import net.minecraft.core.Holder
import net.minecraft.world.effect.MobEffect

class StatusEffectQueryEvent(val effect: Holder<MobEffect>) : CancellableEvent()

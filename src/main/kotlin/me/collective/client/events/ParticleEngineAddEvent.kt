package me.collective.client.events

import net.minecraft.client.particle.Particle

class ParticleEngineAddEvent(var particle: Particle) : CancellableEvent()

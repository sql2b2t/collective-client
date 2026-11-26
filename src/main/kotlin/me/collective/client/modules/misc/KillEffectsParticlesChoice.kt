package me.collective.client.modules.misc

import me.collective.client.ClientAccess
import me.collective.client.settings.NamedEntry

import java.util.ArrayList
import java.util.concurrent.ThreadLocalRandom
import java.util.function.Predicate
import net.minecraft.client.particle.Particle
import net.minecraft.client.particle.ParticleEngine
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleType
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.phys.Vec3

enum class KillEffectsParticlesChoice(private val displayName: String) : ClientAccess, NamedEntry {
   OFF("Off"),
   ASHES("Ashes"),
   CUSTOM("Custom");

   override fun getName(): String {
      return this.displayName
   }

   fun chooseParticle(module: KillEffectsModule): ParticleOptions? {
      return when (this) {
         ASHES -> {
            val random = ThreadLocalRandom.current()
            if (random.nextBoolean()) ParticleTypes.ASH else ParticleTypes.WHITE_ASH
         }
         CUSTOM -> this.selectCustomParticle(module)
         else -> null
      }
   }

   fun emitParticles(type: ParticleOptions, position: Vec3) {
      val random = ThreadLocalRandom.current()

      var index = 0
      while (index < random.nextInt(35) + 25) {
         val particleEngine: ParticleEngine = ClientAccess.collectiveMinecraft.particleEngine
         var x: Double = position.x()
         x += random.nextGaussian() * 0.2
         var y: Double = position.y() + 2.0
         y -= random.nextGaussian() * 0.5
         var z: Double = position.z()
         z += random.nextGaussian() * 0.2
         val velocityY: Double = -0.1 - random.nextGaussian() * 0.5
         val particle: Particle? = particleEngine.createParticle(type, x, y, z, 0.0, velocityY, 0.0)
         if (particle != null) {
            particle.lifetime += 15
         }
         index++
      }
   }

   private fun selectCustomParticle(module: KillEffectsModule): ParticleOptions? {
      val particleTypes = ArrayList<Any>(module.particleTypesSetting.value)
      val invalidParticleType = Predicate<Any> { type -> type !is ParticleOptions }
      particleTypes.removeIf(invalidParticleType)
      if (particleTypes.isEmpty()) {
         return null
      }

      val randomSnapshot = ThreadLocalRandom.current()
      val typeCount = particleTypes.size
      val index = randomSnapshot.nextInt(typeCount)
      return particleTypes[index] as ParticleOptions
   }

}

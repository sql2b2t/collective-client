package me.collective.client.utils.combat

import me.collective.client.ClientAccess
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.world.WorldUtils

import java.util.Comparator
import java.util.function.Predicate
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.entity.NeutralMob
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.monster.piglin.Piglin
import net.minecraft.world.entity.monster.piglin.PiglinBrute
import net.minecraft.world.entity.npc.Villager
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.Fireball
import net.minecraft.world.entity.projectile.ShulkerBullet
import net.minecraft.world.entity.projectile.windcharge.WindCharge
import net.minecraft.world.entity.vehicle.VehicleEntity

class TargetSelector : ClientAccess {
   private var playersEnabled = false
   private var neutralsEnabled = false
   private var animalsEnabled = false
   private var villagersEnabled = false
   private var hostilesEnabled = false
   private var crystalsEnabled = false
   private var projectilesEnabled = false
   private var vehiclesEnabled = false
   private var wallRange = 0.0f
   private var visibleRange = 0.0f
   private var extraRangeEnabled = false
   private var ignoreNamedEntities = false
   private var requireArmor = false
   private var ignoreInvisiblePlayers = false
   private var comparator: Comparator<Entity>
   private var predicate: Predicate<Entity>? = null

   init {
      val comparatorPlayerSnapshot = ClientAccess.collectiveMinecraft.player
      this.comparator = Comparator.comparing { candidate: Entity ->
         comparatorPlayerSnapshot?.distanceToSqr(candidate) ?: 0.0
      }
   }

   fun select(): Entity? {
      var selected: Entity? = null
      val level = ClientAccess.collectiveMinecraft.level ?: return null

      for (candidate in level.entitiesForRendering()) {
         if (candidate is ExperienceOrb) {
            continue
         }

         if (!WorldUtils.isTargetable(candidate) || !matchesTargetType(candidate)) {
            continue
         }
         if (candidate !is Player && candidate.hasCustomName() && ignoreNamedEntities) {
            continue
         }
         if (candidate is Player) {

            if (candidate.isInvisible && ignoreInvisiblePlayers) {
               continue
            }
         }
         val activePredicate = predicate
         if (activePredicate != null && !activePredicate.test(candidate)) {
            continue
         }

         val useExtraRange = extraRangeEnabled
         val distance = WorldUtils.getEntityDistance(candidate, useExtraRange)
         val wallLimit = wallRange
         val visibleLimit = visibleRange
         if (distance > Math.max(visibleLimit, wallLimit)) {
            continue
         }

         val rangeDifference = wallRange - visibleRange
         var useVisibleRange = Math.abs(rangeDifference) <= 0.01f
         if (!useVisibleRange) {
            useVisibleRange = RaycastUtils.hasVisibleEntitySample(candidate)
         }
         val applicableRange = if (useVisibleRange) visibleRange else wallRange
         if (distance > applicableRange) {
            continue
         }
         if (selected == null || comparator.compare(candidate, selected) < 0) {
            selected = candidate
         }
      }

      return selected
   }

   private fun matchesTargetType(candidate: Entity): Boolean {
      if (candidate is Player && playersEnabled) {

         if (isEligiblePlayer(candidate)) {
            return true
         }
      }
      return EntityUtils.isPassive(candidate) && animalsEnabled && candidate !is Villager ||
         candidate is Villager && villagersEnabled ||
         EntityUtils.isHostile(candidate) && hostilesEnabled &&
         (!EntityUtils.isNeutral(candidate) || EntityUtils.isLookingAtPlayer(candidate)) ||
         candidate !is PiglinBrute && candidate is Piglin && neutralsEnabled ||
         candidate is NeutralMob && neutralsEnabled ||
         candidate is EndCrystal && crystalsEnabled ||
         (candidate is Fireball || candidate is WindCharge || candidate is ShulkerBullet) &&
         projectilesEnabled ||
         candidate is VehicleEntity && vehiclesEnabled
   }

   private fun isEligiblePlayer(player: Player): Boolean {
      if (requireArmor) {

         if (!PlayerStateUtils.hasArmor(player)) {
            return false
         }
      }

      return player.isAlive && player.deathTime <= 0
   }

   fun withPlayers(enabled: Boolean): TargetSelector {
      playersEnabled = enabled
      return this
   }

   fun withNeutrals(enabled: Boolean): TargetSelector {
      neutralsEnabled = enabled
      return this
   }

   fun withAnimals(enabled: Boolean): TargetSelector {
      animalsEnabled = enabled
      return this
   }

   fun withVillagers(enabled: Boolean): TargetSelector {
      villagersEnabled = enabled
      return this
   }

   fun withHostiles(enabled: Boolean): TargetSelector {
      hostilesEnabled = enabled
      return this
   }

   fun withCrystals(enabled: Boolean): TargetSelector {
      crystalsEnabled = enabled
      return this
   }

   fun withProjectiles(enabled: Boolean): TargetSelector {
      projectilesEnabled = enabled
      return this
   }

   fun withVehicles(enabled: Boolean): TargetSelector {
      vehiclesEnabled = enabled
      return this
   }

   fun wallRange(range: Float): TargetSelector {
      this.wallRange = range
      return this
   }

   fun range(range: Float): TargetSelector {
      visibleRange = range
      this.wallRange = range
      return this
   }

   fun visibleRange(range: Float): TargetSelector {
      visibleRange = range
      return this
   }

   fun useExtraRange(enabled: Boolean): TargetSelector {
      extraRangeEnabled = enabled
      return this
   }

   fun ignoreNamed(enabled: Boolean): TargetSelector {
      ignoreNamedEntities = enabled
      return this
   }

   fun ignoreNakeds(enabled: Boolean): TargetSelector {
      requireArmor = enabled
      return this
   }

   fun ignoreInvisibles(enabled: Boolean): TargetSelector {
      ignoreInvisiblePlayers = enabled
      return this
   }

   fun sortBy(comparator: Comparator<Entity>): TargetSelector {
      this.comparator = comparator
      return this
   }

   fun filter(predicate: Predicate<Entity>): TargetSelector {
      this.predicate = predicate
      return this
   }
}

package me.collective.client.utils.world

import me.collective.client.managers.CrystalInteractionManager
import me.collective.client.managers.SwingInteractionManager
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.entity.SwingAction
import me.collective.client.utils.network.PacketSender

import me.collective.client.utils.math.MathConstants
import me.collective.client.utils.ElapsedTimer
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.modules.client.AntiCheatModule
import me.collective.client.modules.misc.KillEffectsModule
import me.collective.client.settings.Setting
import me.collective.client.settings.options.AntiCheatStrictDirectionChoice
import me.collective.client.settings.options.BlockSampleMode

import me.collective.mixin.BlockBehaviourAccessor
import java.util.ArrayList
import java.util.Collections
import java.util.function.Predicate
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.FireworkRocketEntity
import net.minecraft.world.entity.projectile.arrow.Arrow
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.EggItem
import net.minecraft.world.item.EnderpearlItem
import net.minecraft.world.item.ExperienceBottleItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.SnowballItem
import net.minecraft.world.item.ThrowablePotionItem
import net.minecraft.world.item.TridentItem
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.AbstractChestBlock
import net.minecraft.world.level.block.AbstractFurnaceBlock
import net.minecraft.world.level.block.AnvilBlock
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.BasePressurePlateBlock
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ButtonBlock
import net.minecraft.world.level.block.CakeBlock
import net.minecraft.world.level.block.ComposterBlock
import net.minecraft.world.level.block.CraftingTableBlock
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.EnchantingTableBlock
import net.minecraft.world.level.block.FenceGateBlock
import net.minecraft.world.level.block.NoteBlock
import net.minecraft.world.level.block.StainedGlassBlock
import net.minecraft.world.level.block.StainedGlassPaneBlock
import net.minecraft.world.level.block.StandingSignBlock
import net.minecraft.world.level.block.TrapDoorBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.border.WorldBorder
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class BlockInteractionUtils private constructor() : ClientAccess {
   companion object {
      private val placementDirections = arrayOf(
         Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP
      )
      private val interactionTimer = ElapsedTimer()

      @JvmField
      var isPlacingBlock: Boolean = false

      private val antiCheat: AntiCheatModule? get() = ClientContext.getModule(AntiCheatModule::class.java)
      private val killEffects: KillEffectsModule? get() = ClientContext.getModule(KillEffectsModule::class.java)

      @JvmStatic
      fun isInteractionCooldownActive(): Boolean {
         val timerSnapshot = interactionTimer

         return !timerSnapshot.hasElapsed(50L)
      }

      @JvmStatic
      fun resetInteractionCooldown() {
         interactionTimer.reset()
      }

      @JvmStatic
      fun requiresSneakForInteraction(position: BlockPos): Boolean {
         val level = ClientAccess.collectiveMinecraft.level ?: return false
         val state = level.getBlockState(position)
         val block = state.block
         if (block === Blocks.RESPAWN_ANCHOR) {
            return true
         }

         return state.hasBlockEntity() ||
         block is BedBlock ||
         block is AbstractChestBlock<*>
            || block is DoorBlock ||
         block is TrapDoorBlock ||
         block is ButtonBlock ||
         block is ComposterBlock ||
         block is NoteBlock ||
         block is StandingSignBlock ||
         block is AnvilBlock ||
         block is AbstractFurnaceBlock ||
         block is CraftingTableBlock ||
         block is EnchantingTableBlock ||
         block is CakeBlock
      }

      @JvmStatic
      fun canPlaceAt(
         position: BlockPos, playersOnly: Boolean
      ): Boolean {
         return canPlaceAt(position, playersOnly, false)
      }

      @JvmStatic
      fun isThrowableItem(item: Item): Boolean {
         return item is EggItem ||
         item is SnowballItem ||
         item is EnderpearlItem ||
         item is ExperienceBottleItem ||
         item is ThrowablePotionItem ||
         item is TridentItem
      }

      @JvmStatic
      fun isGlass(state: BlockState): Boolean {
         val glassPane = Blocks.GLASS_PANE
         if (state.`is`(glassPane)) {
            return true
         }
         val glass = Blocks.GLASS
         return state.`is`(glass) ||
         state.block is StainedGlassBlock ||
         state.block is StainedGlassPaneBlock
      }

      @JvmStatic
      fun findSupportDirection(
         position: BlockPos, strictDirection: Boolean, checkRaytrace: Boolean
      ): Direction? {
         val directions = placementDirections
         for (direction in directions) {
            val neighbor = position.relative(direction)
            if (neighbor.y >= WorldUtils.getTopY()) {
               continue
            }
            val level = ClientAccess.collectiveMinecraft.level ?: continue
            val borderSnapshot: WorldBorder = level.worldBorder
            val x = neighbor.x.toDouble()
            val z = neighbor.z.toDouble()
            if (!borderSnapshot.isWithinBounds(x, z)) {
               continue
            }
            if (checkRaytrace) {
               val raytraceMode = BlockSampleMode.X8
               val clickedFace = direction.opposite
               if (!WorldUtils.canSeeAny(raytraceMode.sampleFace(neighbor, clickedFace))) {
                  continue
               }
            }
            if (ClientContext.worldStateManager.isPlacementOccupied(neighbor)) {
               if (!strictDirection) {
                  return direction
               }
               if (getStrictDirections(neighbor).contains(direction.opposite)) {
                  return direction
               }
            }
         }
         return null
      }

      @JvmStatic
      fun isInteractiveBlock(block: Block): Boolean {
         return block is CraftingTableBlock ||
         block is AnvilBlock ||
         block is ButtonBlock ||
         block is BasePressurePlateBlock ||
         block is BaseEntityBlock ||
         block is BedBlock ||
         block is FenceGateBlock ||
         block is DoorBlock ||
         block is NoteBlock ||
         block is TrapDoorBlock
      }

      @JvmStatic
      fun canPlaceCrystal(
         basePosition: BlockPos, requireTwoAirBlocks: Boolean, checkEntities: Boolean, checkCrystals: Boolean, allowFire: Boolean, shortHeight: Boolean, expandPlayers: Boolean
      ): Boolean {
         if (!WorldUtils.isBedrockOrObsidian(basePosition)) {
            return false
         }

         val crystalPosition = basePosition.offset(0, 1, 0)
         val level = ClientAccess.collectiveMinecraft.level ?: return false
         val crystalBlock = level.getBlockState(crystalPosition).block
         if (allowFire) {
            if (crystalBlock !== Blocks.AIR && crystalBlock !== Blocks.FIRE) {
               return false
            }
         } else if (crystalBlock !== Blocks.AIR) {
            return false
         }

         if (requireTwoAirBlocks) {
            val upperLevel = ClientAccess.collectiveMinecraft.level ?: return false
            val upperPosition = basePosition.offset(0, 2, 0)
            if (upperLevel.getBlockState(upperPosition).block !== Blocks.AIR) {
               return false
            }
         }

         val boundsSnapshot = AABB(
            crystalPosition.x.toDouble(), crystalPosition.y.toDouble(), crystalPosition.z.toDouble(),
            (crystalPosition.x + 1).toDouble(), crystalPosition.y + (if (shortHeight) 0.9 else 2.0), (crystalPosition.z + 1).toDouble()
         )
         var blocked = false
         val entityLevel = ClientAccess.collectiveMinecraft.level ?: return false
         for (entity in entityLevel.entitiesForRendering()) {
            if (entity.isRemoved) {
               continue
            }
            if (!entity.isAlive) {
               continue
            }
            var entityBounds = entity.boundingBox
            if (entity is Player &&
         entity !== ClientAccess.collectiveMinecraft.player!! &&
         expandPlayers
            ) {
               entityBounds = entityBounds.inflate(0.01)
            } else if (entity is EndCrystal && !checkCrystals) {
               continue
            }

            if (entityBounds.intersects(boundsSnapshot)) {
               blocked = true
               break
            }
         }

         return !checkEntities || !blocked
      }

      @JvmStatic
      fun placeBlockIgnoringCrystals(
         position: BlockPos, direction: Direction?, airPlace: Boolean, hand: InteractionHand
      ): Boolean {
         return placeBlockIgnoringCrystals(position, null, direction, airPlace, hand)
      }

      @JvmStatic
      fun placeBlock(
         position: BlockPos, hitPosition: Vec3?, direction: Direction?, airPlace: Boolean, hand: InteractionHand
      ): Boolean {
         val playerGuard = ClientAccess.collectiveMinecraft.player ?: return false
         val gameModeGuard = ClientAccess.collectiveMinecraft.gameMode ?: return false
         if (ClientAccess.collectiveMinecraft.level == null) {
            return false
         }
         val heldBlockSnapshot = getHeldBlock(hand)

         if (!canPlaceBlock(position, heldBlockSnapshot, false)) {
            return false
         }

         val placingInAir = airPlace && direction == null
         var resolvedDirection = direction
         if (resolvedDirection == null) {
            if (!airPlace) {
               return false
            }

            resolvedDirection = if (ClientAccess.collectiveMinecraft.hitResult is BlockHitResult) {
               (ClientAccess.collectiveMinecraft.hitResult as BlockHitResult).direction
            } else {
               Direction.DOWN
            }
         }

         val neighborSnapshot = if (placingInAir) {
            position
         } else {
            position.relative(resolvedDirection)
         }

         val requiresSneak = requiresSneakForInteraction(neighborSnapshot)
         var resolvedHitPosition = hitPosition
         if (resolvedHitPosition == null) {
            resolvedHitPosition = Vec3.atCenterOf(position).add(
               Vec3.atLowerCornerOf(resolvedDirection.getUnitVec3i()).scale(MathConstants.HALF)
            )
         }

         if (requiresSneak) {
            ClientContext.playerStateTracker.setSneaking(true)
         }

         isPlacingBlock = true
         val gameModeSnapshot: MultiPlayerGameMode = gameModeGuard
         val playerSnapshot: LocalPlayer = playerGuard
         val hitResultSnapshot = BlockHitResult(resolvedHitPosition, if (placingInAir) resolvedDirection else resolvedDirection.opposite, neighborSnapshot, false)
         val result = gameModeSnapshot.useItemOn(playerSnapshot, hand, hitResultSnapshot)
         isPlacingBlock = false
         if (result is InteractionResult.Success) {
            if (result.swingSource() == InteractionResult.SwingSource.CLIENT) {
               playerSnapshot.swing(hand)
            }
         }

         if (requiresSneak) {
            ClientContext.playerStateTracker.setSneaking(false)
         }

         return true
      }

      private fun getHeldBlock(hand: InteractionHand): Block? {
         val player = ClientAccess.collectiveMinecraft.player ?: return null
         val item = player.getItemInHand(hand).item
         if (item is BlockItem) {
            return item.block
         }
         return null
      }

      @JvmStatic
      fun attackCrystalAt(position: BlockPos, minimumAge: Int): Entity? {
         val level = ClientAccess.collectiveMinecraft.level ?: return null
         for (entity in level.entitiesForRendering()) {
            if (entity is EndCrystal) {
               val entityBounds = entity.boundingBox
               val blockBounds = AABB(position)
               if (entityBounds.intersects(blockBounds) && entity.tickCount >= minimumAge) {
                  attackEntity(entity)
                  return entity
               }
            }
         }

         return null
      }

      @JvmStatic
      fun canPlaceBlock(
         position: BlockPos, block: Block?, playersOnly: Boolean
      ): Boolean {
         return canPlaceBlock(position, block, playersOnly, false)
      }

      @JvmStatic
      fun canPlaceBlock(
         position: BlockPos, block: Block?, playersOnly: Boolean, ignoreCrystals: Boolean
      ): Boolean {
         if (isOutsidePlacementBounds(position)) {
            return false
         }

         if (WorldUtils.isPlacementOccupied(position)) {
            return false
         }

         val shapeSnapshot: VoxelShape
         if (block == null) {
            shapeSnapshot = Shapes.empty()
         } else {
            val collisionState = block.defaultBlockState()
            val collisionLevel = ClientAccess.collectiveMinecraft.level ?: return false
            val collisionPosition = BlockPos.ZERO
            shapeSnapshot = collisionState.getCollisionShape(collisionLevel, collisionPosition)
         }

         val boundsSnapshot: AABB
         if (shapeSnapshot.isEmpty) {
            boundsSnapshot = AABB(position)
         } else {
            val shapeBounds = shapeSnapshot.bounds()
            boundsSnapshot = BoundingBoxUtils.placeAtBlock(shapeBounds, position)
         }

         if (block != null && !(block as BlockBehaviourAccessor).isCollidable()) {
            return true
         }

         return !hasBlockingEntity(boundsSnapshot, playersOnly, ignoreCrystals)
      }

      @JvmStatic
      fun hasBlockingEntity(
         bounds: AABB, playersOnly: Boolean, ignoreCrystals: Boolean
      ): Boolean {
         val entityLevel = ClientAccess.collectiveMinecraft.level ?: return false
         val entityType = Entity::class.java
         val entityFilter = Predicate<Entity> { candidateEntity ->
            candidateEntity !is ThrownExperienceBottle &&
         candidateEntity !is ItemEntity &&
         candidateEntity !is ItemFrame &&
         candidateEntity !is FireworkRocketEntity &&
         candidateEntity !is ExperienceOrb
         }
         for (entity in entityLevel.getEntitiesOfClass(entityType, bounds, entityFilter)) {
            val entityBounds = entity.boundingBox
            if (!BoundingBoxUtils.hasVolumeOverlap(bounds, entityBounds) &&
         entity !== ClientAccess.collectiveMinecraft.player!!
            ) {
               continue
            }
            if (entity is EndCrystal && ignoreCrystals) {
               continue
            }
            if (entity is Arrow) {
               continue
            }
            if (entity is Player) {
               if (entity.boundingBox.intersects(bounds)) {
                  return true
               }
            } else if (!playersOnly) {
               return true
            }
         }

         return false
      }

      @JvmStatic
      fun attackEntity(entity: Entity) {
         val attackManagerSnapshot: CrystalInteractionManager = ClientContext.crystalInteractionManager
         val entityId = entity.getId()

         if (attackManagerSnapshot.attackEntity(entityId)) {
            val effectsSnapshot = killEffects!!
            if (effectsSnapshot.isToggled() && entity is Player) {
               effectsSnapshot.trackTarget(entity)
            }

            val player = ClientAccess.collectiveMinecraft.player ?: return
            player.resetOnlyAttackStrengthTicker()
         }
      }

      @JvmStatic
      fun interactWithEntity(entity: Entity, hand: InteractionHand) {
         val gameModeSnapshot = ClientAccess.collectiveMinecraft.gameMode ?: return
         val playerSnapshot = ClientAccess.collectiveMinecraft.player ?: return
         val hitResultSnapshot = EntityHitResult(entity)
         gameModeSnapshot.interact(playerSnapshot, entity, hitResultSnapshot, hand)
         playerSnapshot.swing(hand)
      }

      @JvmStatic
      fun findSupportPosition(
         position: BlockPos, maxDistance: Int, checkPlayers: Boolean, strictDirection: Boolean, predicate: Predicate<in BlockPos>
      ): BlockPos? {
         for (distance in 1..maxDistance) {
            for (direction in placementDirections) {
               val candidate = position.relative(direction, distance)
               if (!predicate.test(candidate)) {
                  continue
               }
               if (findSupportDirection(candidate, strictDirection) == null) {
                  continue
               }
               val playerLevel = ClientAccess.collectiveMinecraft.level ?: continue
               val playerType = Player::class.java
               val playerBounds = AABB(candidate)
               val playerFilter = Predicate<Player> { checkPlayers }
               if (playerLevel.getEntitiesOfClass(playerType, playerBounds, playerFilter).isEmpty()) {
                  return candidate
               }
            }
         }

         return null
      }

      @JvmStatic
      fun placeBlock(
         position: BlockPos, airPlace: Boolean, hand: InteractionHand
      ): Boolean {
         val directionSnapshot = findSupportDirection(position)
         return placeBlock(position, null, directionSnapshot, airPlace, hand)
      }

      @JvmStatic
      fun findSupportPosition(
         position: BlockPos, maxDistance: Int, checkPlayers: Boolean, strictDirection: Boolean
      ): BlockPos? {
         val acceptPosition = Predicate<BlockPos> { true }
         return findSupportPosition(position, maxDistance, checkPlayers, strictDirection, acceptPosition)
      }

      @JvmStatic
      fun isCollidable(position: BlockPos): Boolean {
         return (getBlock(position) as BlockBehaviourAccessor)
            .isCollidable()
      }

      @JvmStatic
      fun isOutsidePlacementBounds(position: BlockPos): Boolean {
         val level = ClientAccess.collectiveMinecraft.level ?: return true
         val borderSnapshot: WorldBorder = level.worldBorder
         if (!borderSnapshot.isWithinBounds(position)) {
            return true
         }
         if (!Level.isInSpawnableBounds(position)) {
            return true
         }

         if (position.y >= WorldUtils.getTopY()) {
            return true
         }
         return !(position.y >= level.minY)
      }

      @JvmStatic
      fun getBlock(position: BlockPos): Block {
         val level = ClientAccess.collectiveMinecraft.level!!
         return level.getBlockState(position).block
      }

      @JvmStatic
      fun placeBlockIgnoringCrystals(
         position: BlockPos, hitPosition: Vec3?, direction: Direction?, airPlace: Boolean, hand: InteractionHand
      ): Boolean {
         return placeBlockIgnoringCrystals(position, hitPosition, direction, airPlace, hand, ClientAccess.collectiveMinecraft.player!!.getItemInHand(hand))
      }

      @JvmStatic
      fun placeBlockIgnoringCrystals(
         position: BlockPos, hitPosition: Vec3?, direction: Direction?, airPlace: Boolean, hand: InteractionHand, heldStack: ItemStack
      ): Boolean {
         if (ClientAccess.collectiveMinecraft.level == null ||
         ClientAccess.collectiveMinecraft.player == null ||
         ClientAccess.collectiveMinecraft.gameMode == null
         ) {
            return false
         }
         val heldBlock: Block?
         if (heldStack.item is BlockItem) {
            heldBlock = (heldStack.item as BlockItem).block
         } else {
            heldBlock = null
         }

         if (!canPlaceBlock(position, heldBlock, false, true)) {
            return false
         }

         return sendPlacement(position, hitPosition, direction, airPlace, hand)
      }

      @JvmStatic
      fun findSupportPath(
         position: BlockPos, maxDistance: Int, checkPlayers: Boolean, strictDirection: Boolean, predicate: Predicate<in BlockPos>
      ): List<BlockPos> {
         val path = ArrayList<BlockPos>()

         for (direction in placementDirections) {
            path.clear()

            for (distance in 1..maxDistance) {
               val candidate = position.relative(direction, distance)
               path.add(0, candidate)
               if (!predicate.test(candidate)) {
                  break
               }
               val playerLevel = ClientAccess.collectiveMinecraft.level ?: continue
               val playerType = Player::class.java
               val playerBounds = AABB(candidate)
               val playerFilter = Predicate<Player> { checkPlayers }
               if (!playerLevel.getEntitiesOfClass(playerType, playerBounds, playerFilter).isEmpty()) {
                  break
               }

               if (findSupportDirection(candidate, strictDirection) != null) {
                  return java.util.List.copyOf(path)
               }
            }
         }

         return Collections.emptyList<BlockPos>()
      }

      @JvmStatic
      fun findSupportPath(
         position: BlockPos, maxDistance: Int, checkPlayers: Boolean, strictDirection: Boolean
      ): List<BlockPos> {
         val acceptPosition = Predicate<BlockPos> { true }
         return findSupportPath(position, maxDistance, checkPlayers, strictDirection, acceptPosition)
      }

      @JvmStatic
      fun findSupportDirection(position: BlockPos, strictDirection: Boolean): Direction? {
         return findSupportDirection(position, strictDirection, false)
      }

      @JvmStatic
      fun getStrictDirectionsOrFallback(position: BlockPos, fallback: Direction): List<Direction> {
         val directions = getStrictDirections(position)
         if (directions.isEmpty()) {
            return Collections.singletonList(fallback)
         } else {
            return directions
         }
      }

      @JvmStatic
      fun canPlaceAt(
         position: BlockPos, playersOnly: Boolean, ignoreCrystals: Boolean
      ): Boolean {
         if (isOutsidePlacementBounds(position)) {
            return false
         }

         if (WorldUtils.isPlacementOccupied(position)) {
            return false
         }

         val boundsSnapshot = AABB(position)
         return !hasBlockingEntity(boundsSnapshot, playersOnly, ignoreCrystals)
      }

      @JvmStatic
      fun canPlayerInteract(): Boolean {
         val player = ClientAccess.collectiveMinecraft.player ?: return false
         if (player.isSpectator()) {
            return false
         }
         if (!player.isAlive) {
            return false
         }
         if (player.isHandsBusy()) {
            return false
         }
         return !player.isSleeping
      }

      @JvmStatic
      fun isStrictDirectionEnabled(): Boolean {
         val strictDirectionSetting: Setting<AntiCheatStrictDirectionChoice> = antiCheat!!.strictDirectionSetting
         return strictDirectionSetting.getValue() != AntiCheatStrictDirectionChoice.OFF
      }

      @JvmStatic
      fun findSupportDirection(position: BlockPos): Direction? {
         return findSupportDirection(position, false)
      }

      @JvmStatic
      fun sendPlacement(
         position: BlockPos, hitPosition: Vec3?, direction: Direction?, airPlace: Boolean, hand: InteractionHand
      ): Boolean {
         val placingInAir = airPlace && direction == null
         var resolvedDirection = direction
         if (resolvedDirection == null) {
            val directions = getStrictDirections(position)
            if (!airPlace) {
               return false
            }

            resolvedDirection = if (directions.isEmpty()) Direction.DOWN else directions.first()
         }

         val neighborSnapshot = if (placingInAir) {
            position
         } else {
            position.relative(resolvedDirection)
         }

         val requiresSneak = requiresSneakForInteraction(neighborSnapshot) && !placingInAir
         if (requiresSneak) {
            ClientContext.playerStateTracker.setSneaking(true)
         }

         var resolvedHitPosition = hitPosition
         if (resolvedHitPosition == null) {
            resolvedHitPosition = Vec3.atCenterOf(position).add(
               Vec3.atLowerCornerOf(resolvedDirection.getUnitVec3i()).scale(MathConstants.HALF)
            )
         }

         val hitResultSnapshot = BlockHitResult(resolvedHitPosition, if (placingInAir) resolvedDirection else resolvedDirection.opposite, neighborSnapshot, false)
         PacketSender.sendUseItemOn(hand, hitResultSnapshot)
         val swingManager: SwingInteractionManager = ClientContext.swingManager
         val interactionKind = SwingAction.INTERACT
         swingManager.swing(hand, interactionKind)
         if (requiresSneak) {
            ClientContext.playerStateTracker.setSneaking(false)
         }

         return true
      }

      @JvmStatic
      fun getStrictDirections(position: BlockPos): List<Direction> {
         val player = ClientAccess.collectiveMinecraft.player ?: return emptyList()
         val directionOrder = placementDirections
         val directions = directionOrder.toMutableList()
         val strictDirectionMode = antiCheat!!.strictDirectionSetting.getValue()
         if (strictDirectionMode == AntiCheatStrictDirectionChoice.NCP) {
            for (direction in placementDirections) {
               val neighbor = position.relative(direction)
               val level = ClientAccess.collectiveMinecraft.level ?: continue
               val neighborState = level.getBlockState(neighbor)
               if (WorldUtils.isPlacementOccupied(neighbor)) {
                  if (neighborState.isSolidRender()) {
                     directions.remove(direction)
                  }
               }
            }
         }

         if (position.y + 1 > WorldUtils.getTopY()) {
            directions.remove(Direction.UP)
         }

         val eyePosition = player.getEyePosition()
         val blockCenter = Vec3.atCenterOf(position)
         val deltaX = eyePosition.x - blockCenter.x
         val deltaZ = eyePosition.z - blockCenter.z
         if (deltaX > MathConstants.HALF) {
            directions.remove(Direction.WEST)
         } else if (deltaX < -MathConstants.HALF) {
            directions.remove(Direction.EAST)
         } else {
            directions.remove(Direction.WEST)
            directions.remove(Direction.EAST)
         }

         if (deltaZ > MathConstants.HALF) {
            directions.remove(Direction.NORTH)
         } else if (deltaZ < -MathConstants.HALF) {
            directions.remove(Direction.SOUTH)
         } else {
            directions.remove(Direction.NORTH)
            directions.remove(Direction.SOUTH)
         }

         if (strictDirectionMode == AntiCheatStrictDirectionChoice.GRIM) {
            val bodySamples = PlayerStateUtils.streamBodySamplePositions().toList()
            val sampleAbove = bodySamples.none { bodySample ->
               bodySample.y > position.y + 1
            }

            val sampleBelow = bodySamples.none { bodySample ->
               bodySample.y < position.y
            }
            if (sampleAbove) {
               directions.remove(Direction.UP)
            }

            if (sampleBelow) {
               directions.remove(Direction.DOWN)
            }
         } else {
            if (eyePosition.y < position.y) {
               directions.remove(Direction.UP)
            }

            if (eyePosition.y > position.y) {
               directions.remove(Direction.DOWN)
            }
         }

         return directions
      }
   }
}

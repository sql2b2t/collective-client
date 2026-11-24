package me.collective.client.modules.misc

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Optional
import java.util.concurrent.TimeUnit
import java.util.function.Predicate
import java.util.function.UnaryOperator

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.audio.BuiltinSound
import me.collective.client.events.ClientChunkCacheReplaceWithPacketDataEvent
import me.collective.client.events.EventHandler
import me.collective.client.managers.CommandRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.IdentifierSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.DimensionType
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.utils.collections.NamedIdentifier
import me.collective.client.utils.world.WorldUtils

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.ClickEvent.SuggestCommand
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse
import net.minecraft.world.entity.vehicle.minecart.MinecartChest
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity

class StashFinderModule : Module("StashFinder", "Logs and/or notifies you about possible stashes.", ModuleCategory.MISC) {
   @JvmField
   val logSetting: Setting<Boolean>
   @JvmField
   val xaeroSetting: Setting<Boolean>
   private val notifySetting: Setting<Boolean>
   private val censorCoordsSetting: Setting<Boolean>
   private val soundSetting: Setting<Boolean>
   private val typeSetting: Setting<NamedIdentifier>
   private val volumeSetting: Setting<Float>
   private val ignoreNaturalSetting: Setting<Boolean>
   private val dungeonSetting: Setting<Boolean>
   private val fortressSetting: Setting<Boolean>
   private val bastionSetting: Setting<Boolean>
   private val ancientCitySetting: Setting<Boolean>
   private val trialChambersSetting: Setting<Boolean>
   private val targetsSetting: Setting<Boolean>
   private val shulkersSetting: Setting<Boolean>
   private val chestEntitiesSetting: Setting<Boolean>
   private val chestsSetting: Setting<Boolean>
   private val chestCountSetting: Setting<Int>
   private val stackedCartsSetting: Setting<Boolean>
   private val minimumSetting: Setting<Int>
   private val soundTimer: ElapsedTimer

   private fun isNaturalStorage(position: BlockPos): Boolean {
      val chunkSnapshot = ClientAccess.collectiveMinecraft.level?.getChunk(position) ?: return false
      val fortressOption = this.fortressSetting
      val fortressSnapshot = fortressOption.value
      val bastionOption = this.bastionSetting
      val bastionSnapshot = bastionOption.value
      val ancientCityOption = this.ancientCitySetting
      val ancientCitySnapshot = ancientCityOption.value
      val dungeonOption = this.dungeonSetting
      val dungeonSnapshot = dungeonOption.value
      val trialChambersOption = this.trialChambersSetting
      val trialChambers = trialChambersOption.value
      val storagePosition = position
      return WorldUtils.isStructureChest(
         chunkSnapshot, storagePosition, fortressSnapshot, bastionSnapshot, ancientCitySnapshot, dungeonSnapshot, trialChambers
      )
   }

   private fun hasChestEntity(chunk: ChunkPos): Boolean {
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level ?: return false
      val entities = level.entitiesForRendering().iterator()

      while (entities.hasNext()) {
         val entity = entities.next()
         if (entity is AbstractChestedHorse && entity.hasChest() && entity.chunkPosition() == chunk) {
            return true
         }
      }

      return false
   }

   private fun appendTimestamp(description: String): String {
      return description + " (" + SimpleDateFormat("MM/dd HH:mm a").format(Date()) + ")"
   }

   @EventHandler
   private fun onClientChunkCacheReplaceWithPacketDataEvent(
      event: ClientChunkCacheReplaceWithPacketDataEvent
   ) {
      val chunk = event.chunk
      val chunkPosition = chunk.pos
      var chestCount = 0
      val blockEntities = chunk.blockEntities.values.iterator()

      while (blockEntities.hasNext()) {
         val blockEntity = blockEntities.next()
         val ignoreNaturalOption = this.ignoreNaturalSetting
         val ignoreNatural = ignoreNaturalOption.value
         if (ignoreNatural) {
            val naturalPositionSnapshot = blockEntity.blockPos
            if (this.isNaturalStorage(naturalPositionSnapshot)) {
               continue
            }
         }

         if (blockEntity is ChestBlockEntity) {
            val chestTrackerSnapshot = ClientContext.worldStateManager
            val chestPositionSnapshot = blockEntity.blockPos

            if (!chestTrackerSnapshot.hasKnownPlacement(chestPositionSnapshot)) {
               chestCount++
            }
         }

         val chestsOption = this.chestsSetting
         val findChests = chestsOption.value
         if (findChests) {
            val chestCountOption = this.chestCountSetting
            val requiredChestCount = chestCountOption.value
            if (chestCount >= requiredChestCount) {

               val chestDescription = (chestCount).toString() + " chests"

               val chestTimestamp = this.appendTimestamp(chestDescription)
               val chestStash = this.createStash(chestTimestamp, chunkPosition)
               if (this.registerStash(chestStash, chestDescription)) {
                  break
               }
               continue
            }
         }

         if (blockEntity is ShulkerBoxBlockEntity) {
            val shulkersOption = this.shulkersSetting
            val findShulkers = shulkersOption.value
            if (findShulkers) {
               val shulkerTrackerSnapshot = ClientContext.worldStateManager
               val shulkerPositionSnapshot = blockEntity.blockPos

               if (!shulkerTrackerSnapshot.hasKnownPlacement(shulkerPositionSnapshot)) {

                  val shulkerDescription = "a shulkerbox"

                  val shulkerTimestamp = this.appendTimestamp(shulkerDescription)
                  val shulkerStash = this.createStash(shulkerTimestamp, chunkPosition)
                  if (this.registerStash(shulkerStash, shulkerDescription)) {
                     break
                  }
               }
            }
         }
      }

      val stackedCartsOption = this.stackedCartsSetting
      val findStackedCarts = stackedCartsOption.value
      if (findStackedCarts) {
         val deferredExecutor = ClientContext.taskScheduler
         val scanEntities = Runnable {
            val stackedMinecartCount = this.countStackedMinecarts(chunkPosition)
            val minimumOption = this.minimumSetting
            val requiredMinecarts = minimumOption.value
            if (stackedMinecartCount >= requiredMinecarts) {

               val minecartTimestamp = this.appendTimestamp(("Stacked minecarts"))
               val minecartStash = this.createStash(
                  minecartTimestamp, chunkPosition
               )

               this.registerStash(minecartStash, ("Stacked minecarts"))
            }

            val chestEntitiesOption = this.chestEntitiesSetting
            val findChestEntities = chestEntitiesOption.value
            if (findChestEntities) {
               if (this.hasChestEntity(chunkPosition)) {

                  val entityTimestamp = this.appendTimestamp(("Entity with chest"))
                  val entityStash = this.createStash(
                     entityTimestamp, chunkPosition
                  )

                  this.registerStash(entityStash, ("Entity with chest"))
               }
            }
         }

         deferredExecutor.scheduleRotationTask(scanEntities, 15)
      }
   }

   fun createStash(description: String, chunk: ChunkPos): StashEntry {

      val dimensionOption = WorldUtils.getDimension()
      val dimension = dimensionOption.name
      val serverCheckConnection = ClientAccess.collectiveMinecraft.player?.connection
      val serverAddress: String = serverCheckConnection?.serverData?.ip ?: "singleplayer"

      val server = serverAddress
      val middleBlockX = chunk.middleBlockX
      return StashEntry(description, middleBlockX, chunk.middleBlockZ, dimension, server)
   }

   protected fun registerStash(stash: StashEntry, description: String): Boolean {
      val existingStash: Optional<*> = ClientContext.stashRegistry.findFirst(
         Predicate<StashEntry> { candidate: StashEntry ->
            // Дедупликация по X/Z + сервер + измерение: сравнение только по координатам
            // не давало регистрировать стэши с теми же X/Z на другом сервере/измерении.
            candidate.getX().toInt() == stash.getX().toInt()
               && candidate.getZ().toInt() == stash.getZ().toInt()
               && candidate.getServer() == stash.getServer()
               && candidate.getDimension() == stash.getDimension()
         }
      )
      return if (existingStash.isEmpty() &&
         ClientContext.stashRegistry.registerEntry(stash)
      ) {
         val xaeroOption = this.xaeroSetting
         val addXaeroWaypoint = xaeroOption.value
         if (addXaeroWaypoint && ClientContext.waypointAdapter.isAvailable()) {
            val waypointName = "Stash " + stash.getName()

            val waypointX = stash.getX().toInt()

            val waypointPosition = BlockPos(
               waypointX, 64, stash.getZ().toInt()
            )
            ClientContext.waypointAdapter.addWaypoint(waypointName, waypointPosition)
         }

         val notifyOption = this.notifySetting
         val notify = notifyOption.value
         if (notify) {
            val moduleName = this.name
            val moduleNameComponent = Component.literal(moduleName)

            val foundDescription = " has found " + (description) + " at: "
            val notification: MutableComponent = moduleNameComponent.append(foundDescription)
            val censorCoordsOption = this.censorCoordsSetting
            val censorCoordinates = censorCoordsOption.value
            if (!censorCoordinates) {

               val highlightCommand = ("%shighlight %d ~ %d").format(
                  CommandRegistry.getPrefix(),
                  stash.getX().toInt(),
                  stash.getZ().toInt()
               )
               val highlightClick = SuggestCommand(highlightCommand)

               val coordinateString = stash.formatCoordinates()

               val coordinates: MutableComponent = (ChatUtils.watermarkText(coordinateString) as MutableComponent)
                  .withStyle(UnaryOperator<Style> { style: Style ->

                     style.withClickEvent(highlightClick)
                  })
               notification.append(coordinates)
            } else {

               notification.append(ChatUtils.watermarkText(("### ### ###")))
            }

            ChatUtils.printChat(notification, ChatUtils.createSignature(-13579), ChatUtils.MessagePriority.LOW)
         }

         val soundOption = this.soundSetting
         val playSound = soundOption.value
         if (playSound) {
            val timerSnapshot = this.soundTimer

            val timeoutUnit = TimeUnit.SECONDS
            val elapsedTimer = timerSnapshot
            if (elapsedTimer.hasElapsed(5.0, timeoutUnit)) {
               val soundManagerSnapshot = ClientContext.soundManager
               val soundTypeOption = this.typeSetting
               val soundTypeSnapshot = soundTypeOption.value
               val volumeOption = this.volumeSetting
               val soundVolume = volumeOption.value
               soundManagerSnapshot.playSound(soundTypeSnapshot, soundVolume)
               val timerSnapshotReset = this.soundTimer

               timerSnapshotReset.reset()
            }
         }

         true
      } else {
         false
      }
   }

   private fun countStackedMinecarts(chunk: ChunkPos): Int {
      var overlappingCount = 0
      val seenMinecarts = ArrayList<MinecartChest>()
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level ?: return overlappingCount
      val entities = level.entitiesForRendering().iterator()

      while (entities.hasNext()) {
         val entity = entities.next()
         if (entity is MinecartChest && entity.chunkPosition() == chunk) {
            val previousMinecarts = seenMinecarts.iterator()
            while (previousMinecarts.hasNext()) {
               val previousMinecart = previousMinecarts.next()
               val boundsSnapshot = entity.boundingBox
               val previousBounds = previousMinecart.boundingBox
               if (boundsSnapshot.intersects(previousBounds)) {
                  overlappingCount++
                  break
               }
            }
            seenMinecarts.add(entity)
         }
      }

      return overlappingCount
   }

   init {
      val logDefault = true
      this.logSetting = this.add(BooleanSetting(("Log"), logDefault))
      val xaeroDefault = false
      this.xaeroSetting = this.add(BooleanSetting(("Xaero"), xaeroDefault))
      val notifyDefault = true
      val notifyDefinition = BooleanSetting(("Notify"), notifyDefault)
      notifyDefinition.markVisibilityDependency()
      this.notifySetting = this.add(notifyDefinition)
      val censorDefault = false
      val censorDefinition = BooleanSetting(("CensorCoords"), censorDefault)
      this.censorCoordsSetting = this.add(censorDefinition.dependsOn(this.notifySetting))
      val soundDefault = true
      val soundDefinition = BooleanSetting(("Sound"), soundDefault)
      soundDefinition.markVisibilityDependency()
      this.soundSetting = this.add(soundDefinition)
      val warningSound = BuiltinSound.WARNING
      val defaultSound = warningSound.identifier
      val typeDefinition = IdentifierSetting(("Type"), defaultSound)
      this.typeSetting = this.add(typeDefinition.dependsOn(this.soundSetting))
      val volumeDefinition = NumberSetting<Float>("Volume", 1.0f, 0.0f, 1.0f)
      this.volumeSetting = this.add(volumeDefinition.dependsOn(this.soundSetting))
      val ignoreNaturalDefault = false
      val ignoreNaturalDefinition = BooleanSetting(("IgnoreNatural"), ignoreNaturalDefault)
      ignoreNaturalDefinition.markVisibilityDependency()
      this.ignoreNaturalSetting = this.add(ignoreNaturalDefinition)
      val dungeonDefault = true
      val dungeonDefinition = BooleanSetting(("Dungeon"), dungeonDefault)
      this.dungeonSetting = this.add(dungeonDefinition.dependsOn(this.ignoreNaturalSetting))
      val fortressDefault = true
      val fortressDefinition = BooleanSetting(("Fortress"), fortressDefault)
      this.fortressSetting = this.add(fortressDefinition.dependsOn(this.ignoreNaturalSetting))
      val bastionDefault = true
      val bastionDefinition = BooleanSetting(("Bastion"), bastionDefault)
      this.bastionSetting = this.add(bastionDefinition.dependsOn(this.ignoreNaturalSetting))
      val ancientCityDefault = false
      val ancientCityDefinition = BooleanSetting(("AncientCity"), ancientCityDefault)
      this.ancientCitySetting = this.add(ancientCityDefinition.dependsOn(this.ignoreNaturalSetting))
      val trialChambersDefault = true
      val trialChambersDefinition = BooleanSetting(("TrialChambers"), trialChambersDefault)
      this.trialChambersSetting = this.add(trialChambersDefinition.dependsOn(this.ignoreNaturalSetting))
      val targetsDefault = false
      val targetsDefinition = BooleanSetting(("Targets"), targetsDefault)
      targetsDefinition.markAsGroup()
      targetsDefinition.markVisibilityDependency()
      this.targetsSetting = this.add(targetsDefinition)
      val shulkersDefault = true
      val shulkersDefinition = BooleanSetting(("Shulkers"), shulkersDefault)
      this.shulkersSetting = this.add(shulkersDefinition.dependsOn(this.targetsSetting))
      val chestEntitiesDefault = false
      val chestEntitiesDefinition = BooleanSetting(("ChestEntities"), chestEntitiesDefault)
      this.chestEntitiesSetting = this.add(chestEntitiesDefinition.dependsOn(this.targetsSetting))
      val chestsDefault = true
      val chestsDefinition = BooleanSetting(("Chests"), chestsDefault)
      chestsDefinition.markVisibilityDependency()
      this.chestsSetting = this.add(chestsDefinition.dependsOn(this.targetsSetting))
      val chestCountDefinition = NumberSetting<Int>("ChestCount", 5, 2, 20)
      this.chestCountSetting = this.add(chestCountDefinition.dependsOn(this.targetsSetting, this.chestsSetting))
      val stackedCartsDefault = true
      val stackedCartsDefinition = BooleanSetting(("StackedCarts"), stackedCartsDefault)
      stackedCartsDefinition.markVisibilityDependency()
      this.stackedCartsSetting = this.add(stackedCartsDefinition.dependsOn(this.targetsSetting))
      val minimumDefinition = NumberSetting<Int>("Minimum", 2, 2, 8)
      this.minimumSetting = this.add(minimumDefinition.dependsOn(this.targetsSetting, this.stackedCartsSetting))
      this.soundTimer = ElapsedTimer()
      if (!ClientContext.waypointAdapter.isAvailable()) {
         this.unregister(this.xaeroSetting)
      }

      this.setDrawn(false)
   }
}

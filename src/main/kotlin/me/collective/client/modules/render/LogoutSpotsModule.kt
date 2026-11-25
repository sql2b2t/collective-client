package me.collective.client.modules.render


import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.commands.FakeplayerCommand
import me.collective.client.events.ClientLevelRemoveEntityEvent
import me.collective.client.events.PacketEvent
import me.collective.client.events.WorldRenderEvent
import me.collective.client.events.ConnectScreenConnectEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.managers.RenderStateManager
import me.collective.client.managers.ServerStateManager
import me.collective.client.render.GuiDrawUtils
import me.collective.client.render.LineMeshRenderer
import me.collective.client.render.WorldBoxRenderer
import me.collective.client.utils.ServerAddressUtils
import me.collective.client.utils.entity.SidePlayer
import me.collective.client.utils.collections.ObjectPair
import me.collective.client.audio.BuiltinSound
import me.collective.client.utils.ElapsedTimer
import me.collective.client.render.RenderUtils
import me.collective.client.render.FontRenderer
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.utils.inventory.EquipmentUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.world.WorldUtils
import me.collective.client.render.EntityChamsRenderer
import me.collective.client.utils.collections.NamedIdentifier
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.client.FontsModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.IdentifierSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.settings.options.DimensionType
import me.collective.client.utils.math.MathUtils
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import me.collective.mixin.LivingEntityAccessor
import java.awt.Color
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Predicate
import java.util.stream.Collectors
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.Direction.Axis
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import com.mojang.authlib.GameProfile
import com.mojang.blaze3d.vertex.PoseStack
import java.util.regex.Matcher

class LogoutSpotsModule : Module("LogoutSpots", "Highlights log out spots.", ModuleCategory.RENDER) {
   companion object {

      private var fontsModule: FontsModule? = null
      private var renderingTexturedModel: Boolean = false

      private fun isPlayerListed(player: Player): Boolean {
         val profile = player.gameProfile
         val capturedName = profile.name
         return isNameListed(capturedName)
      }

      private fun isNameListed(playerName: String): Boolean {
         val connection: ClientPacketListener = ClientAccess.collectiveMinecraft.player!!.connection
         return connection.onlinePlayers.stream().noneMatch { playerInfo ->
            val profile = playerInfo.profile
            profile.name.equals(playerName, ignoreCase = true)
         }
      }

      private fun removeSpot(owner: LogoutSpotsModule, playerName: String) {
         synchronized(owner.spotsByName) {
            val spots = owner.spotsByName
            spots.remove(playerName)
         }
      }

      private fun getCurrentServerAddress(): String {

         return ServerAddressUtils.getCurrentServerAddress()
      }

      private fun hasArmor(player: Player): Boolean {
         return PlayerStateUtils.hasArmor(player)
      }

      private fun removeFirstUnlistedSpot(owner: LogoutSpotsModule) {
         synchronized(owner.spotsByName) {
            var nameToRemove: String? = null
            val spots = owner.spotsByName
            val names = spots.keys.iterator()

            while (names.hasNext()) {
               val playerName = names.next()

               if (!isNameListed(playerName)) {
                  nameToRemove = playerName
                  break
               }
            }

            if (nameToRemove != null) {

               val removedName = nameToRemove
               owner.removeSpot(removedName)
            }
         }
      }

      private fun clearForServerConnection(
         owner: LogoutSpotsModule, serverSource: ServerStateManager, event: ConnectScreenConnectEvent
      ) {
         if (serverSource.serverData != null) {
            val connectingAddress = event.serverAddress
            val capturedServerSource = ClientContext.serverStateManager

            if (connectingAddress != capturedServerSource.serverData?.ip) {
               return
            }
         }

         val spots = owner.spotsByName
         spots.clear()
      }

      @JvmStatic
      fun isRenderingTexturedModel(): Boolean {
         return renderingTexturedModel
      }

      @JvmStatic
      fun bindFontsModule(moduleRegistry: ModuleRegistry) {
         fontsModule = moduleRegistry.getModule(FontsModule::class.java) as FontsModule?
      }
   }

   val distanceSetting: Setting<Float>
   private val modelSetting: Setting<ModelChoice>
   private val texturedSetting: Setting<Boolean>
   private val alphaSetting: Setting<Float>
   private val modelWidthSetting: Setting<Float>
   private val tracerSetting: Setting<Boolean>
   private val tracerWidthSetting: Setting<Float>
   private val nameTagSetting: Setting<Boolean>
   private val textScaleSetting: Setting<Float>
   private val positionSetting: Setting<PositionChoice>
   private val timeSetting: Setting<Boolean>
   private val healthSetting: Setting<Boolean>
   private val totemsSetting: Setting<Boolean>
   val notifySetting: Setting<Boolean>
   val loginSetting: Setting<Boolean>
   val loginSoundSetting: Setting<Boolean>
   val loginSoundPathSetting: Setting<NamedIdentifier>
   val logoutSetting: Setting<Boolean>
   val soundSetting: Setting<Boolean>
   val soundPathSetting: Setting<NamedIdentifier>
   private val colorsSetting: Setting<Boolean>
   private val modelFillSetting: Setting<Color>
   private val modelLineSetting: Setting<Color>
   private val boxFillSetting: Setting<Color>
   private val boxLineSetting: Setting<Color>
   private val nameTagColorSetting: Setting<Color>
   private val fillColorSetting: Setting<Color>
   private val filterSetting: Setting<Boolean>
   private val ignoreFriendsSetting: Setting<Boolean>
   private val ignoreNakedsSetting: Setting<Boolean>
   private val spotCleanupTimer: ElapsedTimer = ElapsedTimer()
   private val spotsByName: Object2ObjectOpenHashMap<String, LogoutSpot> = Object2ObjectOpenHashMap()
   private val pendingRemovals: MutableList<PendingRemoval> = ArrayList()

   fun getSpot(
      playerName: String
   ): LogoutSpot? {
      synchronized(this.spotsByName) {
         val spots = this.spotsByName
         return spots.getOrDefault(playerName, null)
      }
   }

   fun removeSpot(playerName: String) {
      removeSpot(this, playerName)
   }

   override fun onToggle() {
      val spots = this.spotsByName
      spots.clear()
   }

   fun putSpot(
      playerName: String, spot: LogoutSpot
   ) {
      synchronized(this.spotsByName) {
         val spots = this.spotsByName
         spots.put(playerName, spot)
      }
   }

   private fun formatSpotDetails(spot: LogoutSpot): String {
      val details = StringBuilder()
      if (positionSetting.getValue() == PositionChoice.COORDINATES) {
         val position = spot.position
         details.append(String.format(Locale.US, " X: %.1f, Y: %.1f, Z: %.1f", position.x, position.y, position.z))
      }

      if (positionSetting.getValue() == PositionChoice.DISTANCE) {
         val capturedPlayerPosition = ClientAccess.collectiveMinecraft.player!!.position()
         val spotPosition = spot.position
         val distance = capturedPlayerPosition.distanceTo(spotPosition)
         val distanceSeparator = " "
         details.append(distanceSeparator)

         val formattedDistance = String.format("%.1fm", distance)
         details.append(formattedDistance)
      }

      val showTime = timeSetting.getValue()
      if (showTime) {

         val timeValues = arrayOfNulls<Any>(1)

         val dateFormat = SimpleDateFormat("HH:mm")
         val logoutTime = Date(spot.timestamp)
         timeValues[0] = dateFormat.format(logoutTime)
         val formattedTime = String.format(" %s", *timeValues)
         details.append(formattedTime)
      }

      val showHealth = healthSetting.getValue()
      if (showHealth) {

         val healthPrefix = " ("
         details.append(healthPrefix)
         val health = spot.health
         details.append(health)

         val healthSuffix = "hp)"
         details.append(healthSuffix)
      }

      if (spot.totemPops > 0) {
         val showTotems = totemsSetting.getValue()
         if (showTotems) {

            val totemPrefix = " -"
            details.append(totemPrefix)
            val totemPops = spot.totemPops
            details.append(totemPops)
         }
      }

      return details.toString()
   }

   @EventHandler
   private fun onClientLevelRemoveEntityEvent(event: ClientLevelRemoveEntityEvent) {
      val capturedLevel = ClientAccess.collectiveMinecraft.level!!
      val entityId = event.id
      val removedPlayer = capturedLevel.getEntity(entityId) as? Player
      if (removedPlayer != null) {
         if (removedPlayer is FakeplayerCommand.SimulatedPlayer) {
            return
         }

         if (removedPlayer.deathTime > 0) {
            return
         }

         if (EntityUtils.getHealthWithAbsorption(removedPlayer) <= 0.0f) {
            return
         }

         val ignoreFriends = ignoreFriendsSetting.getValue()
         if (ignoreFriends) {
            val friends = ClientContext.socialRegistry
            if (friends.isFriendPlayer(removedPlayer)) {
               return
            }
         }

         val ignoreNakeds = ignoreNakedsSetting.getValue()
         if (ignoreNakeds && !hasArmor(removedPlayer)) {
            return
         }

         val pending = this.pendingRemovals
         pending.add(PendingRemoval(removedPlayer, System.currentTimeMillis(), AtomicBoolean()))
      }
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
      val pendingIterator = this.pendingRemovals.iterator()

      while (pendingIterator.hasNext()) {
         val pendingRemoval = pendingIterator.next()
         val removedPlayer = pendingRemoval.player

         if (isPlayerListed(removedPlayer)) {
            val processed = true
            val processedFlag = pendingRemoval.processedFlag
            processedFlag.set(processed)
            val capturedSpots = this.spotsByName
            val profile = removedPlayer.gameProfile
            val capturedPlayerName = profile.name

            val serverAddress = getCurrentServerAddress()
            val snapshot = LogoutSpot.capturePlayer(
               removedPlayer, serverAddress
            )
            capturedSpots.put(capturedPlayerName, snapshot)
            val notify = notifySetting.getValue()
            if (notify) {
               val logoutNotification = logoutSetting.getValue()
               if (logoutNotification) {
                  val playSound = soundSetting.getValue()
                  if (playSound) {
                     val capturedSoundManager = ClientContext.soundManager
                     val configuredSoundPath = soundPathSetting.getValue()

                     capturedSoundManager.playSound(configuredSoundPath, 1.0f)
                  }
               }
            }
         }
      }

      this.pendingRemovals.removeIf { pendingRemoval -> pendingRemoval.isReadyForRemoval() }
      val capturedCleanupTimer = this.spotCleanupTimer

      val cleanupUnit = TimeUnit.SECONDS
      if (capturedCleanupTimer.hasElapsedAndReset(5L, cleanupUnit)) {

         removeFirstUnlistedSpot(this)
      }
   }

   @EventHandler
   private fun onRenderWorld(event: WorldRenderEvent.Main) {
      GuiDrawUtils.flush()
      LineMeshRenderer.begin(event, this.tracerWidthSetting.getValue())
      synchronized(this.spotsByName) {
         val spotIterator = this.spotsByName.entries.iterator()

         while (spotIterator.hasNext()) {
            val entry = spotIterator.next()
            val serverSpot = entry.value
            val savedServerAddress = serverSpot.serverAddress

            if (savedServerAddress == getCurrentServerAddress()) {
               val dimensionSpot = entry.value
               val dimension = dimensionSpot.dimension

               if (dimension == WorldUtils.getDimension()) {
                  val distanceBox = entry.value.boundingBox
                  val capturedBoxCenter = distanceBox.center
                  val cameraPosition = ClientAccess.collectiveMinecraft.gameRenderer.mainCamera.position
                  val distance = capturedBoxCenter.distanceTo(cameraPosition)
                  val maximumDistance = this.distanceSetting.getValue()
                  if (distance > maximumDistance) {
                     val distanceSentinel = this.distanceSetting.getValue()

                     if (distanceSentinel != 301.0f) {
                        continue
                     }
                  }

                  val poses = event.poseStack!!
                  val simpleModelOption = this.modelSetting
                  if (simpleModelOption.getValue() == ModelChoice.SIMPLE ||
         this.modelSetting.getValue() == ModelChoice.BOTH) {
                     val boxFillSpot = entry.value
                     val fillBox = boxFillSpot.boundingBox
                     val boxFillOption = this.boxFillSetting
                     WorldBoxRenderer.fillBox(
                        poses, fillBox, boxFillOption.getValue()
                     )
                     val boxLineSpot = entry.value
                     val lineBox = boxLineSpot.boundingBox
                     val boxLineColor = this.boxLineSetting.getValue()
                     val boxWidth = this.modelWidthSetting.getValue()
                     WorldBoxRenderer.outlineBox(
                        poses, lineBox, boxLineColor, boxWidth
                     )
                  }

                  val complexModelOption = this.modelSetting
                  if (complexModelOption.getValue() == ModelChoice.COMPLEX ||
         this.modelSetting.getValue() == ModelChoice.BOTH) {
                     val modelSpot = entry.value
                     val playerModel = modelSpot.playerModel
                     val textured = this.texturedSetting.getValue()
                     if (textured) {
                        renderingTexturedModel = true

                        val alpha = this.alphaSetting.getValue()
                        RenderUtils.setRenderColor(
                           1.0f, 1.0f, 1.0f, alpha
                        )
                        val modelRenderer = RenderUtils.INSTANCE

                        val renderBuffers = ClientAccess.collectiveMinecraft.renderBuffers()
                        modelRenderer.renderEntity(
                           playerModel, 1.0f, poses, renderBuffers.bufferSource()
                        )
                        RenderUtils.INSTANCE
                           .flushEntities()

                        RenderUtils.setRenderColor(
                           1.0f, 1.0f, 1.0f, 1.0f
                        )
                        renderingTexturedModel = false
                     }

                     val modelWidth = this.modelWidthSetting.getValue()
                     EntityChamsRenderer.setLineWidth(modelWidth.toDouble())
                     val modelLineColor = this.modelLineSetting.getValue()
                     val modelFillOption = this.modelFillSetting
                     EntityChamsRenderer.setColors(
                        modelLineColor, modelFillOption.getValue()
                     )
                     EntityChamsRenderer.renderEntity(poses, playerModel)
                  }

                  val tracerOption = this.tracerSetting
                  val drawTracer = tracerOption.getValue()
                  if (drawTracer) {
                     val tracerSpot = entry.value
                     val tracerBox = tracerSpot.boundingBox
                     val tracerPosition = tracerBox.center
                     val tracerColorOption = this.modelLineSetting
                     LineMeshRenderer.addTracer(
                        tracerPosition, tracerColorOption.getValue()
                     )
                  }

                  val nameTagOption = this.nameTagSetting
                  val drawNameTag = nameTagOption.getValue()
                  if (drawNameTag) {
                     val tagCenterSpot = entry.value
                     val tagCenterBox = tagCenterSpot.boundingBox
                     val capturedTagCenter = tagCenterBox.center
                     val heightAxis = Axis.Y
                     val tagHeightSpot = entry.value
                     val tagBoxTop = tagHeightSpot.boundingBox.maxY

                     val tagHeight = tagBoxTop + 0.5
                     val tagPosition = capturedTagCenter.with(heightAxis, tagHeight)
                     val capturedNameResolver = ClientContext.aliasRegistry
                     val capturedTagName = entry.key
                     val alias: String? = capturedNameResolver.getPlayerAlias(capturedTagName)
                     var label = (alias ?: "null") + " logout spot"
                     val detailsSpot = entry.value
                     val details = this.formatSpotDetails(detailsSpot)
                     val fontModule = fontsModule
                     val computedFontOffset: Float
                     if (fontModule!!.isToggled()) {

                        computedFontOffset = 1.0f
                     } else {
                        computedFontOffset = 0.0f
                     }

                     val fontOffset = computedFontOffset
                     if (!details.isEmpty()) {
                        val detailsText = details
                        label = "\u0001\u0001"
                           .replaceFirst("\u0001", Matcher.quoteReplacement(label))
                           .replaceFirst("\u0001", Matcher.quoteReplacement(detailsText))
                     }

                     val tagCamera = ClientAccess.collectiveMinecraft.gameRenderer.mainCamera
                     val capturedTagCameraPosition = tagCamera.position
                     val textScaleOption = this.textScaleSetting
                     val configuredTextScale = textScaleOption.getValue()
                     val textScaleValue = configuredTextScale.toDouble()
                     val textScale = MathUtils.getDistanceScale(
                        capturedTagCameraPosition, tagPosition, textScaleValue
                     )
                     val backgroundRenderer = RenderUtils.INSTANCE

                     val backgroundTextWidth = FontRenderer.INSTANCE
                        .getWidth(label)

                     val backgroundHalfWidth = -(backgroundTextWidth / 2.0f)

                     val backgroundLeft = backgroundHalfWidth - 2.0f

                     val backgroundVerticalOffset = 5.0f + fontOffset

                     val backgroundScale = textScale * 2.0
                     val fillColorOption = this.fillColorSetting
                     backgroundRenderer.drawWorldRect(
                        poses, tagPosition, -0.03f, 0.0f, backgroundLeft, backgroundVerticalOffset, backgroundScale, fillColorOption.getValue()
                     )
                     val textRenderer = RenderUtils.INSTANCE
                     val textWidth = FontRenderer.INSTANCE
                        .getWidth(label)

                     val textLeft = -(textWidth / 2.0f)

                     val nameTagColorOption = this.nameTagColorSetting
                     textRenderer.drawWorldText(
                        poses, label, tagPosition, 0.0f, 0.0f, textLeft, 2.0f, textScale, nameTagColorOption.getValue(), true
                     )
                  }
               }
            }
         }
      }

      LineMeshRenderer.draw()
      RenderStateManager.addStates(
         RenderStateManager.POLYGON_OFFSET_THROUGH_WALLS
      )
      GuiDrawUtils.flush()
      RenderStateManager.reset()
   }

   fun getSpotPlayers(): List<SidePlayer> {
      synchronized(this.spotsByName) {
         val spots = this.spotsByName
         val players = spots.values.stream().map { spot: LogoutSpot -> spot.playerModel }
         return players.collect(Collectors.toUnmodifiableList())
      }
   }

   fun raycastSpot(): ObjectPair? {
      if (this.isWorldUnavailable()) {
         return null
      }

      var capturedPlayer = ClientAccess.collectiveMinecraft.player!!

      val eyePosition = capturedPlayer.getEyePosition(1.0f)
      capturedPlayer = ClientAccess.collectiveMinecraft.player!!
      val rayOffset = capturedPlayer.getViewVector(1.0f).scale(100.0)
      val rayEnd = eyePosition.add(rayOffset)
      synchronized(this.spotsByName) {
         val spotIterator = this.spotsByName.entries.iterator()

         while (spotIterator.hasNext()) {
            val entry = spotIterator.next()
            var bounds = entry.value.boundingBox
            val poseModel = entry.value.playerModel
            val pose = poseModel.pose
            if (pose == Pose.FALL_FLYING || pose == Pose.SWIMMING) {
               val lookModel = entry.value.playerModel
               val look = lookModel.lookAngle
               if (pose == Pose.FALL_FLYING) {
                  val movingBounds = bounds
                  bounds = movingBounds.move(look)
               }

               val lookX = look.x
               val absoluteLookX = Math.abs(lookX)

               val horizontalInflation = absoluteLookX * 0.8

               val verticalInflation: Double
               if (pose == Pose.SWIMMING) {

                  verticalInflation = 0.4
               } else {

                  verticalInflation = 0.6
               }

               val lookZ = look.z
               val absoluteLookZ = Math.abs(lookZ)

               val inflateZ = absoluteLookZ * 0.8
               val inflateY = verticalInflation
               val expandedBounds = bounds
               bounds = expandedBounds.inflate(horizontalInflation, inflateY, inflateZ)
            }

            val clippedBounds = bounds
            val hit = clippedBounds.clip(eyePosition, rayEnd)
            if (hit.isPresent) {
               return ObjectPair(entry.key, entry.value)
            }
         }

         return null
      }
   }

   @EventHandler
   private fun onPacketReceive(
      event: PacketEvent.Receive
   ) {
      if (event.packet is ClientboundPlayerInfoUpdatePacket) {
         val packet = event.packet as ClientboundPlayerInfoUpdatePacket
         val entries = packet.newEntries().iterator()

         while (entries.hasNext()) {
            val entry = entries.next()
            ClientAccess.collectiveMinecraft.executeIfPossible(Runnable {
               val profile = entry.profile()
               val profileName: String
               if (profile == null) {
                  profileName = ""
               } else {
                  profileName = profile.name
               }

               val playerName = profileName

               this.removeSpot(playerName)
            })
         }
      }
   }

   @EventHandler
   private fun onConnectScreenConnectEvent(event: ConnectScreenConnectEvent) {
      val serverSource = ClientContext.serverStateManager
      clearForServerConnection(this, serverSource, event)
   }

   fun executeWithSpotLock(action: Runnable) {
      ClientAccess.collectiveMinecraft.executeIfPossible(Runnable {
         synchronized(this.spotsByName) {
            action.run()
         }
      })
   }

   init {
      this.distanceSetting = this.add(NumberSetting<Float>("Distance", 128.0f, 16.0f, 301.0f).withUnit("m"))
      this.modelSetting = this.add(EnumSetting<ModelChoice>("Model", ModelChoice.NONE))
      val complexModelVisibility = Predicate<Any> {
         this.modelSetting.getValue() == ModelChoice.COMPLEX || this.modelSetting.getValue() == ModelChoice.BOTH
      }
      this.texturedSetting = this.add(BooleanSetting("Textured", false, complexModelVisibility).markVisibilityDependency())
      this.alphaSetting = this.add(NumberSetting<Float>("Alpha", 0.5f, 0.0f, 1.0f, complexModelVisibility).dependsOn(this.texturedSetting))
      val modelWidthVisibility = Predicate<Any> {
         this.modelSetting.getValue() != ModelChoice.NONE
      }
      this.modelWidthSetting = this.add(NumberSetting<Float>("Width", 1.0f, 0.5f, 3.0f, modelWidthVisibility))
      this.tracerSetting = this.add(BooleanSetting("Tracer", false).markVisibilityDependency())
      val tracerWidthVisibility = Predicate<Any> {
         this.tracerSetting.isExpanded()
      }
      this.tracerWidthSetting = this.add(NumberSetting<Float>("Width", 1.0f, 0.1f, 1.0f, tracerWidthVisibility))
      this.nameTagSetting = this.add(BooleanSetting("NameTag", true).markVisibilityDependency())
      val nameTagOptionsVisible = Predicate<Any> {
         this.nameTagSetting.isExpanded()
      }
      this.textScaleSetting = this.add(NumberSetting<Float>("TextScale", 1.0f, 0.5f, 3.0f, nameTagOptionsVisible))
      this.positionSetting = this.add(EnumSetting<PositionChoice>("Position", PositionChoice.NONE, nameTagOptionsVisible))
      this.timeSetting = this.add(BooleanSetting("Time", false, nameTagOptionsVisible))
      this.healthSetting = this.add(BooleanSetting("Health", true, nameTagOptionsVisible))
      this.totemsSetting = this.add(BooleanSetting("Totems", false).dependsOn(this.nameTagSetting))
      this.notifySetting = this.add(BooleanSetting("Notify", false).markVisibilityDependency())
      this.loginSetting = this.add(BooleanSetting("Login", true).markVisibilityDependency().dependsOn(this.notifySetting))
      this.loginSoundSetting = this.add(BooleanSetting("Sound", false).markVisibilityDependency().dependsOn(this.notifySetting, this.loginSetting))
      this.loginSoundPathSetting = this.add(IdentifierSetting("Sound Path", BuiltinSound.STEAM.identifier)
         .dependsOn(this.notifySetting, this.loginSetting, this.loginSoundSetting))
      this.logoutSetting = this.add(BooleanSetting("Logout", true).markVisibilityDependency().dependsOn(this.notifySetting))
      this.soundSetting = this.add(BooleanSetting("Sound", false).markVisibilityDependency().dependsOn(this.notifySetting, this.logoutSetting))
      this.soundPathSetting = this.add(IdentifierSetting("Sound Path", BuiltinSound.STEAM.identifier)
         .dependsOn(this.notifySetting, this.logoutSetting, this.soundSetting))
      this.colorsSetting = this.add(BooleanSetting("Colors", true).markAsGroup())
      this.modelFillSetting = this.add(ColorSetting("ModelFill", Color(1019058687, true), complexModelVisibility).dependsOn(this.colorsSetting))
      this.modelLineSetting = this.add(ColorSetting("ModelLine", Color(-423781889, true), complexModelVisibility).dependsOn(this.colorsSetting))
      val boxFillVisibility = Predicate<Any> {
         this.modelSetting.getValue() == ModelChoice.SIMPLE || this.modelSetting.getValue() == ModelChoice.BOTH
      }
      this.boxFillSetting = this.add(ColorSetting("BoxFill", Color(1019058687, true), boxFillVisibility).dependsOn(this.colorsSetting))
      this.boxLineSetting = this.add(ColorSetting("BoxLine", Color(-423781889, true), boxFillVisibility).dependsOn(this.colorsSetting))
      val nameTagColorVisibility = Predicate<Any> {
         this.colorsSetting.isExpanded()
      }
      this.nameTagColorSetting = this.add(ColorSetting("NameTagColor", Color(-289564161, true), nameTagColorVisibility))
      this.fillColorSetting = this.add(ColorSetting("FillColor", Color(1292503562, true), nameTagColorVisibility))
      this.filterSetting = this.add(BooleanSetting("Filter", false).markAsGroup())
      this.ignoreFriendsSetting = this.add(BooleanSetting("IgnoreFriends", false).dependsOn(this.filterSetting))
      this.ignoreNakedsSetting = this.add(BooleanSetting("IgnoreNakeds", false).dependsOn(this.filterSetting))
      this.distanceSetting.setBoundaryLabel("Unlimited", SettingLabelCondition.MAX)
      this.loginSoundSetting.setConfigName("LoginSoundPath")
      this.soundSetting.setConfigName("LogoutSoundPath")
      this.setDrawn(false)
   }

   enum class ModelChoice(private val displayName: String) : NamedEntry {
      NONE("None"),
      SIMPLE("Simple"),
      COMPLEX("Complex"),
      BOTH("Both");

      override fun getName(): String {
         return this.displayName
      }
   }

   data class LogoutSpot(
      val timestamp: Long,
      val boundingBox: AABB,
      val dimension: DimensionType,
      val health: Int,
      val totemPops: Int,
      val position: Vec3,
      val playerModel: SidePlayer,
      val serverAddress: String,
      val simulatedPlayer: Boolean
   ) {

      companion object {

         @JvmStatic
         fun fromBytes(
            bytes: ByteArray
         ): LogoutSpot? {
            return try {
               val byteStream = ByteArrayInputStream(bytes)
               val input = DataInputStream(byteStream)
               val timestamp = input.readLong()
               val bounds = AABB(
                  input.readDouble(), input.readDouble(), input.readDouble(),
                  input.readDouble(), input.readDouble(), input.readDouble()
               )
               val health = input.readInt()
               val totemPops = input.readInt()
               val position = Vec3(input.readDouble(), input.readDouble(), input.readDouble())
               val playerModel = SidePlayer(
                  ClientAccess.collectiveMinecraft.level!!
               )
               playerModel.readSnapshot(input)
               playerModel.hurtTime = 0
               playerModel.deathTime = 0

               LogoutSpot(
                  timestamp,
                  bounds,
                  WorldUtils.getDimension(),
                  health,
                  totemPops,
                  position,
                  playerModel,
                  ServerAddressUtils.getCurrentServerAddress(),
                  false
               )
            } catch (failure: Throwable) {
               null
            }
         }

         @JvmStatic
         fun capturePlayer(
            player: Player, serverAddress: String
         ): LogoutSpot {
            val playerModel = SidePlayer(player)
            playerModel.hurtTime = 0
            playerModel.deathTime = 0
            val timestamp = System.currentTimeMillis()
            val bounds = player.boundingBox

            val dimension = WorldUtils.getDimension()
            val rawHealth = EntityUtils.getHealthWithAbsorption(player)
            val health = Math.round(rawHealth)
            val totemTracker = ClientContext.totemPopTracker

            val totemPops = totemTracker.getPopCount(player)
            val position = player.position()
            val simulatedPlayer = player is FakeplayerCommand.SimulatedPlayer
            return LogoutSpot(timestamp, bounds, dimension, health, totemPops, position, playerModel, serverAddress, simulatedPlayer)
         }

      }

      fun toBytes(): ByteArray? {
         return try {
            val byteStream = ByteArrayOutputStream()
            val output = DataOutputStream(byteStream)
            output.writeLong(this.timestamp)
            output.writeDouble(this.boundingBox.minX)
            output.writeDouble(this.boundingBox.minY)
            output.writeDouble(this.boundingBox.minZ)
            output.writeDouble(this.boundingBox.maxX)
            output.writeDouble(this.boundingBox.maxY)
            output.writeDouble(this.boundingBox.maxZ)
            output.writeInt(this.health)
            output.writeInt(this.totemPops)
            output.writeDouble(this.position.x)
            output.writeDouble(this.position.y)
            output.writeDouble(this.position.z)
            val tickCountModel = this.playerModel
            output.writeInt(tickCountModel.getCapturedTickCount())
            output.writeDouble(this.playerModel.getCapturedPosition().x())
            output.writeDouble(this.playerModel.getCapturedPosition().y())
            output.writeDouble(this.playerModel.getCapturedPosition().z())
            output.writeDouble(this.playerModel.getX())
            output.writeDouble(this.playerModel.getY())
            output.writeDouble(this.playerModel.getZ())
            output.writeFloat(this.playerModel.yRotO)
            output.writeFloat(this.playerModel.xRotO)
            output.writeFloat(this.playerModel.getYRot())
            output.writeFloat(this.playerModel.getXRot())
            output.writeFloat(this.playerModel.yBodyRot)
            output.writeFloat(this.playerModel.yBodyRotO)
            output.writeFloat(this.playerModel.yHeadRot)
            output.writeFloat(this.playerModel.yHeadRotO)
            output.writeFloat(this.playerModel.walkAnimation.speed)
            output.writeFloat(this.playerModel.walkAnimation.position)
            val poseModel = this.playerModel
            output.writeInt(poseModel.pose.id())
            output.writeBoolean(this.playerModel.isShiftKeyDown)
            val mainHandModel = this.playerModel
            val mainHandStack = mainHandModel.mainHandItem
            output.writeUTF(mainHandStack.item.toString())
            output.writeUTF(this.playerModel.offhandItem.item.toString())
            val armorModel = this.playerModel
            val armor = EquipmentUtils.getArmorStacks(armorModel)
            output.writeInt(armor.size)
            for (armorStack in armor) {
               output.writeUTF(armorStack.item.toString())
            }
            output.writeFloat((this.playerModel as LivingEntityAccessor).`collective$getLeaningPitch`())
            output.writeFloat((this.playerModel as LivingEntityAccessor).`collective$getLastLeaningPitch`())
            output.writeBoolean(this.playerModel.isFallFlying)
            output.writeInt(this.playerModel.fallFlyingTicks)
            output.close()
            byteStream.toByteArray()
         } catch (failure: Throwable) {
            null
         }
      }

   }

   private data class PendingRemoval(
      val player: Player,
      val removalTime: Long,
      val processedFlag: AtomicBoolean
   ) {

      fun isReadyForRemoval(): Boolean {
         val ageMillis = System.currentTimeMillis() - this.removalTime
         return ageMillis >= 300L || this.processedFlag.get()
      }

   }

   enum class PositionChoice(private val displayName: String) : NamedEntry {
      NONE("None"),
      COORDINATES("Coordinates"),
      DISTANCE("Distance");

      override fun getName(): String {
         return this.displayName
      }
   }
}

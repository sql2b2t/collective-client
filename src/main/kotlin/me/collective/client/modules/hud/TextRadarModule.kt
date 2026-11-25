package me.collective.client.modules.hud


import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.render.GuiDrawUtils
import me.collective.client.utils.combat.TotemPopTracker
import me.collective.client.managers.RotationManager
import me.collective.client.render.RenderUtils
import me.collective.client.managers.SocialRegistry
import me.collective.client.render.FontRenderer
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.utils.inventory.EquipmentUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.render.ResizableHudWidgetRenderer
import me.collective.client.modules.HudWidgetModule
import me.collective.client.modules.client.FontsModule
import me.collective.client.modules.player.NameProtectModule
import me.collective.client.modules.render.NameTagsModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.HudAnchor

import com.mojang.authlib.GameProfile
import com.mojang.blaze3d.vertex.PoseStack
import java.awt.Color
import java.util.Comparator
import java.util.UUID
import net.minecraft.ChatFormatting
import net.minecraft.client.Camera
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.MultiBufferSource.BufferSource
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

class TextRadarModule : HudWidgetModule("TextRadar", "playerlist") {
   private val sortSetting: Setting<SortMode>
   private val healthSetting: Setting<Boolean>
   private val distanceSetting: Setting<Boolean>
   private val totemPopsSetting: Setting<Boolean>
   private val armorSetting: Setting<Boolean>
   private val headsSetting: Setting<Boolean>
   private val limitSetting: Setting<Boolean>
   private val maxSetting: Setting<Int>
   private val colorsSetting: Setting<Boolean>
   private val friendSetting: Setting<Boolean>
   private val enemySetting: Setting<Boolean>
   private val sneakingSetting: Setting<Boolean>
   private val ignoreSetting: Setting<Boolean>
   private val friendsSetting: Setting<Boolean>
   private val nakedsSetting: Setting<Boolean>
   private val botsSetting: Setting<Boolean>
   private val radarPlayers: MutableList<Player>

   override fun onEnable() {
      this.radarPlayers.clear()
   }

   private fun buildPlayerText(player: Player): Component {
      val text: MutableComponent = Component.empty()
      val health: Float = EntityUtils.getHealthWithAbsorption(player)
      val showHealth: Boolean = this.healthSetting.value!!
      if (showHealth) {
         val roundedHealth: Int = Math.round(health)
         val healthText: MutableComponent = Component.literal("$roundedHealth ")
         val healthFormatting: ChatFormatting = this.getHealthFormatting(health.toDouble())
         healthText.withStyle(healthFormatting)
         text.append(healthText)
      }

      var playerName: String = player.gameProfile.name
      if (nameProtect.isToggled()) {
         val previousNameProtectState: Boolean = NameProtectModule.replacementBypassed
         NameProtectModule.replacementBypassed = !previousNameProtectState

         playerName = nameProtect.protectNames(playerName) ?: playerName
         NameProtectModule.replacementBypassed = previousNameProtectState
      }

      val displayName: String = ClientContext.aliasRegistry.getPlayerAlias(playerName)
      text.append(displayName)
      val showPops: Boolean = this.totemPopsSetting.value!!
      if (showPops) {
         val popTracker: TotemPopTracker = ClientContext.totemPopTracker

         val totemPops: Int = popTracker.getPopCount(player)
         val popText: MutableComponent = Component.literal(" -$totemPops")

         val popColor: Int = NameTagsModule.getTotemPopColor(totemPops)
         popText.withColor(popColor)
         if (totemPops > 0) {
            text.append(popText)
         }
      }

      val showDistance: Boolean = this.distanceSetting.value!!
      if (showDistance) {
         val localPlayerForDistance = ClientAccess.collectiveMinecraft.player ?: return text
         val distance: Float = localPlayerForDistance.distanceTo(player)
         val distanceMeters: Int = Math.round(distance)
         val distanceText: MutableComponent = Component.literal(" ${distanceMeters}m")
         val distanceFormatting: ChatFormatting = ChatFormatting.WHITE
         distanceText.withStyle(distanceFormatting)
         text.append(distanceText)
      }

      val showArmor: Boolean = this.armorSetting.value!!
      if (showArmor) {
         var armorMarker = "G"
         for (armorStack in EquipmentUtils.getArmorStacks(player)) {
            if (armorStack.item == Items.ELYTRA) {
               armorMarker = "W"
               break
            }

            val equippableComponent = DataComponents.EQUIPPABLE
            if (armorStack.has(equippableComponent) &&
         (EquipmentUtils.isRepairMaterial(armorStack, Items.DIAMOND) ||
         EquipmentUtils.isRepairMaterial(armorStack, Items.NETHERITE_INGOT)) &&
         armorStack.isEnchanted
            ) {
               continue
            }

            armorMarker = ""
         }

         if (armorMarker.isNotEmpty()) {
            val armorText: MutableComponent = Component.literal(" [$armorMarker]")
            val armorFormatting: ChatFormatting = ChatFormatting.WHITE
            armorText.withStyle(armorFormatting)
            text.append(armorText)
         }
      }

      return text
   }

   private fun getHealthFormatting(health: Double): ChatFormatting {

      if (health >= 20.0) {
         return ChatFormatting.GREEN
      }

      if (health >= 16.0) {
         return ChatFormatting.DARK_GREEN
      }

      if (health >= 10.0) {
         return ChatFormatting.GOLD
      }

      return if (health >= 4.0) ChatFormatting.RED else ChatFormatting.DARK_RED
   }

   private fun createPlayerComparator(): Comparator<Entity> {
      val sortMode: SortMode = this.sortSetting.value!!
      return when (sortMode) {
         SortMode.DISTANCE ->
            Comparator.comparingDouble { entity: Entity ->
               val localPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return@comparingDouble 0.0
               entity.distanceToSqr(localPlayer)
            }

         SortMode.ANGLE ->
            Comparator.comparingDouble { entity: Entity ->
               val gameRenderer = ClientAccess.collectiveMinecraft.gameRenderer
               val camera: Camera = gameRenderer.mainCamera
               val cameraYaw: Float = camera.yRot

               val targetYaw: Float = RotationManager.getRotationsToEntity(entity)[0]
               Mth.degreesDifferenceAbs(cameraYaw, targetYaw).toDouble()
            }

         SortMode.ALPHABET ->
            Comparator.comparing { entity: Entity ->
               if (entity is Player) {
                  val profile: GameProfile = entity.gameProfile
                  profile.name
               } else {
                  val entityName: Component = entity.name
                  entityName.string
               }
            }
      }
   }

   override fun calculateDimensions(): FloatArray {
      var totalHeight = 0.0f
      var maximumWidth = 0.0f
      for (player in this.radarPlayers) {
         val textRenderer: FontRenderer = FontRenderer.INSTANCE

         var rowWidth: Float = textRenderer.getWidth(
            this.buildPlayerText(player)
         )
         val showHeads: Boolean = this.headsSetting.value!!
         if (showHeads) {
            val headFontHeight: Float = FontRenderer.INSTANCE
               .getHeight().toFloat()

            rowWidth += headFontHeight + 0.5f
         }

         totalHeight += FontRenderer.INSTANCE
            .getHeight() + 1
         if (rowWidth > maximumWidth) {
            maximumWidth = rowWidth
         }
      }

      return floatArrayOf(maximumWidth, totalHeight)
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
      synchronized(this.radarPlayers) {
         this.radarPlayers.clear()
         val level: ClientLevel = ClientAccess.collectiveMinecraft.level ?: return
         var players = level.players().stream()
            .filter { candidate: AbstractClientPlayer -> this.shouldIncludePlayer(candidate) }

         val playerComparator: Comparator<Entity> = this.createPlayerComparator()
         players = players.sorted(playerComparator)
         val limitEnabled: Boolean = this.limitSetting.value!!
         val maximumCount: Long = if (limitEnabled) {
            this.maxSetting.value!!.toLong()
         } else {

            Long.MAX_VALUE
         }

         players = players.limit(maximumCount)
         players.forEachOrdered { candidate: AbstractClientPlayer -> this.radarPlayers.add(candidate) }
      }
   }

   private fun drawHatLayer(
      poses: PoseStack, skin: ResourceLocation, size: Int
   ) {

      val textureU = 40.0f
      val textureVFloat = 8.0f
      GuiDrawUtils.drawTexture(
         poses, skin, 0, 0, textureU, textureVFloat, size, size, 8, 8, 64, 64, -1
      )
   }

   override fun renderContent(poses: PoseStack) {
      val headSpacing: Float = if (fonts.isToggled()) {
         0.0f
      } else {

         1.0f
      }

      val hudScale: Float = hud.getTextScale()
      var rowY: Float = this.renderer
         .getContentY(
            FontRenderer.INSTANCE
               .getHeight() * hudScale
         ) - this.renderer
         .getY()
      rowY /= hudScale

      for (player in this.radarPlayers) {
         val rowColor: Color = run rowColorSelection@{
            var selectedColor: Color = this@TextRadarModule.getHudColor(rowY)
            val friendLookup: SocialRegistry = ClientContext.socialRegistry
            if (friendLookup.isFriendPlayer(player)) {
               val colorFriends: Boolean = this@TextRadarModule.friendSetting.value!!
               if (colorFriends) {
                  val friendColorLookup: SocialRegistry = ClientContext.socialRegistry
                  val friendProfileName: String = player.gameProfile.name
                  selectedColor = friendColorLookup.getColor(friendProfileName, selectedColor)
                  return@rowColorSelection selectedColor
               }
            }

            val enemyLookup: SocialRegistry = ClientContext.socialRegistry

            if (enemyLookup.isEnemyPlayer(player)) {
               val colorEnemies: Boolean = this@TextRadarModule.enemySetting.value!!
               if (colorEnemies) {
                  val enemyColorLookup: SocialRegistry = ClientContext.socialRegistry
                  val enemyProfileName: String = player.gameProfile.name
                  selectedColor = enemyColorLookup.getColor(enemyProfileName, selectedColor)
                  return@rowColorSelection selectedColor
               }
            }
            if (player.isShiftKeyDown) {
               val colorSneaking: Boolean = this@TextRadarModule.sneakingSetting.value!!
               if (colorSneaking) {
                  selectedColor = SNEAKING_COLOR
               }
            }

            selectedColor
         }

         val text: Component = this.buildPlayerText(player)
         var headWidth = 0.0f
         var headTextOffset = 0.0f
         val includeHeadWidth: Boolean = this.headsSetting.value!!
         if (includeHeadWidth) {
            headWidth = FontRenderer.INSTANCE
               .getHeight() + headSpacing
            headTextOffset = if (this.renderer
                  .getHorizontalDirection() <= 0 &&
         this.renderer
                  .getAnchor() != HudAnchor.TOP_CENTER
            ) {
               -1.0f
            } else {
               headWidth * hudScale
            }

         }

         val rowWidth: Float = (
            FontRenderer.INSTANCE
               .getWidth(text) + headWidth
            ) * hudScale
         val textX: Float = (
            this.renderer
               .getContentX(rowWidth) - this.renderer
               .getX() + headTextOffset
            ) / hudScale
         val showHead: Boolean = this.headsSetting.value!!
         if (showHead) {

            val headY: Float = rowY - 0.5f
            this.renderPlayerHead(poses, player, headY, hudScale, headWidth, rowWidth)
         }

         FontRenderer.INSTANCE
            .drawWithShadow(poses, text, textX, rowY, rowColor)
         rowY += (FontRenderer.INSTANCE.getHeight() + 1) * this.renderer.getVerticalDirection()
      }

      val bufferSource: BufferSource = RenderUtils.getEntityBuffers()
      bufferSource.endBatch()
   }

   private fun renderPlayerHead(
      poses: PoseStack, player: Player, y: Float, hudScale: Float, headWidth: Float, rowWidth: Float
   ) {
      val fontHeight: Float = FontRenderer.INSTANCE
         .getHeight().toFloat()

      val headSize: Float = fontHeight + 0.5f
      val connection: ClientPacketListener = ClientAccess.collectiveMinecraft.player?.connection
         ?: return
      val playerId: UUID = player.uuid
      val playerInfo: PlayerInfo? = connection.getPlayerInfo(playerId)
      if (playerInfo != null) {
         var headX: Float = if (this.renderer
               .getHorizontalDirection() < 0
         ) {
            this.renderer
               .getDimensions()[0] / hudScale - headWidth
         } else {
            0.0f
         }
         if (this.renderer
               .getAnchor() == HudAnchor.TOP_CENTER
         ) {
            val remainingWidth: Float = (
               this.renderer
                  .getDimensions()[0] - rowWidth
               ) / hudScale

            headX = remainingWidth / 2.0f
         }

         poses.translate(headX, y, 0.0f)
         val skin = playerInfo.getSkin()
         val skinTexture: ResourceLocation = skin.texture()

         val pixelSize: Int = (headSize - 1.0f).toInt()
         val playerShowsHat: Boolean = playerInfo.showHat()
         this.drawSkinHead(
            poses, skinTexture, pixelSize, playerShowsHat
         )
         poses.translate(-headX, -y, 0.0f)
      }
   }

   private fun drawSkinHead(
      poses: PoseStack, skin: ResourceLocation, size: Int, showHat: Boolean
   ) {

      val textureU = 8.0f
      val textureVFloat = 8.0f
      GuiDrawUtils.drawTexture(
         poses, skin, 0, 0, textureU, textureVFloat, size, size, 8, 8, 64, 64, -1
      )
      if (showHat) {
         this.drawHatLayer(
            poses, skin, size
         )
      }
   }

   private fun shouldIncludePlayer(player: Player): Boolean {
      if (ClientAccess.collectiveMinecraft.player!! == player) {
         return this.isInHudEditor()
      }

      val ignoreFriends: Boolean = this.friendsSetting.value!!
      if (ignoreFriends) {
         val friendLookup: SocialRegistry = ClientContext.socialRegistry
         if (friendLookup.isFriendPlayer(player)) {
            return false
         }
      }

      if (this.isMissingFromPlayerList(player)) {
         val ignoreBots: Boolean = this.botsSetting.value!!
         if (ignoreBots) {
            return false
         }
      }

      val ignoreNakeds: Boolean = this.nakedsSetting.value!!
      if (ignoreNakeds) {

         if (!PlayerStateUtils.hasArmor(player)) {
            return false
         }
      }

      return true
   }

   private fun isMissingFromPlayerList(player: Player): Boolean {
      val connection: ClientPacketListener = ClientAccess.collectiveMinecraft.player?.connection
         ?: return true
      val listedPlayers = connection.getListedOnlinePlayers()
      for (playerInfo in listedPlayers) {
         val listedProfile: GameProfile = playerInfo.profile
         if (listedProfile == player.gameProfile) {
            return false
         }
      }

      return true
   }

   init {
      this.sortSetting = this.add(EnumSetting<SortMode>("Sort", SortMode.DISTANCE))

      this.healthSetting = this.add(BooleanSetting("Health", true))

      this.distanceSetting = this.add(BooleanSetting("Distance", false))

      this.totemPopsSetting = this.add(BooleanSetting("TotemPops", false))

      this.armorSetting = this.add(BooleanSetting("Armor", false))

      this.headsSetting = this.add(BooleanSetting("Heads", false))

      val limitOption = BooleanSetting("Limit", false)
      val limitDependency: Setting<Boolean> = limitOption.markVisibilityDependency()
      this.limitSetting = this.add(limitDependency)

      val maximumSetting = NumberSetting<Int>(
         "Max", 8, 1, 32
      )
      this.maxSetting = this.add(maximumSetting.dependsOn(this.limitSetting))

      val colorsOption = BooleanSetting("Colors", false)
      val colorsGroup: Setting<Boolean> = colorsOption.markAsGroup()
      this.colorsSetting = this.add(colorsGroup)

      val friendColorSetting = BooleanSetting("Friend", true)
      this.friendSetting = this.add(friendColorSetting.dependsOn(this.colorsSetting))

      val enemyColorSetting = BooleanSetting("Enemy", true)
      this.enemySetting = this.add(enemyColorSetting.dependsOn(this.colorsSetting))

      val sneakingColorSetting = BooleanSetting("Sneaking", true)
      this.sneakingSetting = this.add(sneakingColorSetting.dependsOn(this.colorsSetting))

      val ignoreOption = BooleanSetting("Ignore", false)
      val ignoreGroup: Setting<Boolean> = ignoreOption.markAsGroup()
      this.ignoreSetting = this.add(ignoreGroup)

      val ignoreFriendsSetting = BooleanSetting("Friends", false)
      this.friendsSetting = this.add(ignoreFriendsSetting.dependsOn(this.ignoreSetting))

      val ignoreNakedsSetting = BooleanSetting("Nakeds", false)
      this.nakedsSetting = this.add(ignoreNakedsSetting.dependsOn(this.ignoreSetting))

      val ignoreBotsSetting = BooleanSetting("Bots", true)
      this.botsSetting = this.add(ignoreBotsSetting.dependsOn(this.ignoreSetting))
      this.radarPlayers = ArrayList()
      val widget = ResizableHudWidgetRenderer(this)
      widget.enableHudScale()
      this.setRenderer(widget)
   }

   enum class SortMode(private val displayName: String) : NamedEntry {
      DISTANCE("Distance"),
      ANGLE("Angle"),
      ALPHABET("Alphabet");

      override fun getName(): String {
         return this.displayName
      }
   }

   companion object {

      private val fonts: FontsModule get() = ClientContext.getModule(FontsModule::class.java)
      private val nameProtect: NameProtectModule get() = ClientContext.getModule(NameProtectModule::class.java)

      private val SNEAKING_COLOR: Color = Color(
         10945959,
         false
      )
   }
}

package me.collective.client.modules.render


import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.commands.FakeplayerCommand
import me.collective.client.events.MinecraftRunTickEvent
import me.collective.client.events.NameTagRenderEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.WorldRenderEvent
import me.collective.client.managers.AliasRegistry
import me.collective.client.managers.ModuleRegistry
import me.collective.client.managers.RotationManager
import me.collective.client.render.ClientRenderTypes
import me.collective.client.render.ColorUtils
import me.collective.client.render.FontRenderer
import me.collective.client.render.GuiDrawUtils
import me.collective.client.render.MeshBuffer
import me.collective.client.render.NameTagItemRenderer
import me.collective.client.managers.RenderTargetRegistry
import me.collective.client.render.RenderTargetUtils
import me.collective.client.render.RenderUtils
import kotlin.math.min
import kotlin.math.max
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.client.ColorsModule
import me.collective.client.modules.client.FontsModule
import me.collective.client.modules.player.FreecamModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.combat.DamageUtils
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.utils.entity.PlayerState
import me.collective.client.utils.inventory.EnchantmentUtils
import me.collective.client.utils.inventory.EquipmentUtils
import me.collective.client.utils.math.MathConstants
import me.collective.client.utils.math.MathUtils
import me.collective.client.utils.math.Vec3Utils
import java.awt.Color
import java.util.Comparator
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.regex.Matcher
import net.minecraft.ChatFormatting
import net.minecraft.client.CameraType
import net.minecraft.client.gui.Font
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.SubmitNodeStorage
import net.minecraft.client.renderer.entity.EntityRenderDispatcher
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.AgeableMob
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.NeutralMob
import net.minecraft.world.entity.animal.fish.AbstractFish
import net.minecraft.world.entity.animal.squid.Squid
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.Rarity
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.authlib.GameProfile
import net.minecraft.client.renderer.rendertype.RenderType

class NameTagsModule : Module("NameTags", "Draws advanced nametags for living entities.", ModuleCategory.RENDER) {
   companion object {
      @JvmField
      val NAME_TAG_RENDER_TARGET: Supplier<*> = RenderTargetRegistry.createLazyRenderTarget("nametags_temp")

      private val ICON_TEXTURE: Identifier = Identifier.fromNamespaceAndPath("collective", "textures/collective.png")
      private val textScratch: StringBuilder = StringBuilder()

      private var animationsModule: AnimationsModule? = null
      private var freecamModule: FreecamModule? = null
      private var fontsModule: FontsModule? = null
      private var shader: ShaderModule? = null
      private var colorsModule: ColorsModule? = null

      @JvmStatic
      fun getTotemPopColor(pops: Int): Int {
         val fraction = Mth.clamp(pops / 10.0f, 0.0f, 1.0f)
         return ColorUtils.blend(Color.yellow, Color.red, fraction).rgb
      }

      private fun isEntityRefreshDue(timer: ElapsedTimer): Boolean {
         return timer.hasElapsedAndReset(50L)
      }

      @JvmStatic
      fun bindReferencedModules(modules: ModuleRegistry) {
         animationsModule = modules.getModuleOrNull(AnimationsModule::class.java)
         freecamModule = modules.getModuleOrNull(FreecamModule::class.java)
         fontsModule = modules.getModuleOrNull(FontsModule::class.java)
         shader = modules.getModuleOrNull(ShaderModule::class.java)
         colorsModule = modules.getModuleOrNull(ColorsModule::class.java)
      }
   }

   private val rangeSetting: Setting<Int>
   private val scaleSetting: Setting<Float>
   private val infoSetting: Setting<Boolean>
   private val nameSetting: Setting<Boolean>
   private val pingSetting: Setting<Boolean>
   private val colorSetting: Setting<Boolean>
   private val healthSetting: Setting<Boolean>
   private val decimalsSetting: Setting<Boolean>
   private val gamemodeSetting: Setting<Boolean>
   private val totemPopsSetting: Setting<Boolean>
   private val itemSetting: Setting<Boolean>
   private val armorSetting: Setting<Boolean>
   private val durabilitySetting: Setting<DurabilityChoice>
   private val itemsSetting: Setting<Boolean>
   private val showEatingSetting: Setting<Boolean>
   private val enchantsSetting: Setting<Boolean>
   private val hideMaxSetting: Setting<Boolean>
   private val itemNameSetting: Setting<Boolean>
   private val amountSetting: Setting<Boolean>
   private val targetsSetting: Setting<Boolean>
   private val playersSetting: Setting<Boolean>
   private val deadSetting: Setting<Boolean>
   private val selfSetting: Setting<Boolean>
   private val botsSetting: Setting<Boolean>
   private val animalsSetting: Setting<Boolean>
   private val hostilesSetting: Setting<Boolean>
   private val neutralsSetting: Setting<Boolean>
   private val colorsSetting: Setting<Boolean>
   private val friendSetting: Setting<Boolean>
   private val enemySetting: Setting<Boolean>
   private val fillSetting: Setting<Color>
   private val outlineSetting: Setting<Color>
   private val textSetting: Setting<Color>
   private val invisiblesSetting: Setting<Color>
   private val sneakSetting: Setting<Color>
   private val eatingSetting: Setting<Color>
   private val overlayMesh: MeshBuffer
   private val itemOverlayColor: Color
   private val visibleEntities: MutableList<Entity>
   private val equipmentItems: MutableList<ItemStack>
   private val entityRefreshTimer: ElapsedTimer
   private var hasEquipmentItem: Boolean = false
   private var hasDurabilityText: Boolean = false

   private fun collectEquipment(player: Player) {
      this.equipmentItems.clear()
      if (itemsSetting.getValue()) {
         val offhandCheck = player.offhandItem
         if (!offhandCheck.isEmpty) {
            this.equipmentItems.add(player.offhandItem)
         }
      }
      for (armorStack in EquipmentUtils.getArmorStacks(player)) {
         if (!armorStack.isEmpty) {
            if (armorSetting.getValue()) {
               this.equipmentItems.add(armorStack)
            }
         }
      }
      if (itemsSetting.getValue()) {
         val mainHandCheck = player.mainHandItem
         if (!mainHandCheck.isEmpty) {
            this.equipmentItems.add(player.mainHandItem)
         }
      }
   }

   private fun shouldRenderSelf(): Boolean {
      val freecam = freecamModule
      if (freecam!!.isToggled()) {
         return true
      }
      if (selfSetting.getValue()) {
         val options = ClientAccess.collectiveMinecraft.options
         if (options.cameraType != CameraType.FIRST_PERSON) {
            return true
         }
      }
      return false
   }

   private fun getBackgroundPadding(): Float {
      val configuredOutline = outlineSetting.getValue()
      return if (configuredOutline.alpha == 0) {
         val configuredFill = fillSetting.getValue()
         if (configuredFill.alpha == 0) {
            0.54f
         } else {
            0.58f
         }
      } else {
         0.6f
      }
   }

   private fun buildTagText(entity: LivingEntity): MutableComponent {
      val label = Component.empty()
      var entityName = entity.name.string
      if (entity is Player) {
         val profile = entity.gameProfile
         entityName = profile.name()
      }
      if (nameSetting.getValue()) {
         val aliasesSnapshot = ClientContext.aliasRegistry
         val alias = aliasesSnapshot.getPlayerAlias(entityName)
         label.append(alias)
      }
      if (entity is Player) {
         val infoPlayer = entity
         if (gamemodeSetting.getValue()) {
            label.append(" [")
            val gameModeConnectionSnapshot: ClientPacketListener = ClientAccess.collectiveMinecraft.player!!.connection
            val gameModeProfile = infoPlayer.gameProfile
            val gameModeUuid = gameModeProfile.id()
            val gameModePlayerInfo = gameModeConnectionSnapshot.getPlayerInfo(gameModeUuid)
            val gameModeLabel: String
            if (gameModePlayerInfo == null) {
               gameModeLabel = "S"
            } else {
               val playerGameMode = gameModePlayerInfo.gameMode
               gameModeLabel = this.getGameModeLabel(playerGameMode)
            }
            label.append(gameModeLabel)
            label.append("]")
         }
         if (pingSetting.getValue()) {
            val ping = ClientContext.serverStateManager.getPlayerLatencyMillis(infoPlayer)
            if (ping > 0) {
               label.append(" ")
               val latencyArgb = ColorUtils.getLatencyColor(ping).hashCode()
               if (colorSetting.getValue()) {
                  val coloredPingText = String.format("%dms", ping)
                  val coloredPingComponent = Component.literal(coloredPingText)
                  label.append(coloredPingComponent.withColor(latencyArgb))
               } else {
                  val plainPingText = String.format("%dms", ping)
                  label.append(Component.nullToEmpty(plainPingText))
               }
            }
         }
      }
      if (healthSetting.getValue()) {
         val health = EntityUtils.getHealthWithAbsorption(entity)
         val healthRatioSnapshot: Double = EntityUtils.getHealthFraction(entity).toDouble()
         val healthColorSnapshot = ColorUtils.getHealthColor(healthRatioSnapshot)
         val colorScheme = colorsModule!!.schemeSetting.getValue()
         val healthColor = colorScheme.transform(healthColorSnapshot)
         val configuredDecimals = decimalsSetting.getValue()
         val healthFormat = if (configuredDecimals) {
            "%.1f"
         } else {
            "%.0f"
         }
         val healthText = String.format(healthFormat, health)
         val healthLabelSnapshot = Component.literal(healthText)
         val healthComponent = healthLabelSnapshot.withColor(healthColor)
         label.append(" ")
         label.append(healthComponent)
      }
      if (totemPopsSetting.getValue() && entity is Player) {
         val pops = ClientContext.totemPopTracker.getPopCount(entity)
         if (pops > 0) {
            val popColor = getTotemPopColor(pops)
            val popText = " -$pops"
            val popComponentSnapshot = Component.literal(popText)
            label.append(popComponentSnapshot.withColor(popColor))
         }
      }
      return label
   }

   private fun renderEquipmentItem(
      player: Player, poses: PoseStack, stack: ItemStack, x: Float, y: Float, scale: Float
   ) {
      val durabilityPercent: Int = DamageUtils.getDurabilityPercent(stack)
      if (itemsSetting.getValue()) {
         if (showEatingSetting.getValue()) {
            if (player.isUsingItem) {
               val usedHand: InteractionHand = player.usedItemHand
               if (player.getItemInHand(usedHand) === stack && stack.has(DataComponents.FOOD)) {

                  val eatingBottomBaseY = y * scale
                  val eatingBottom = eatingBottomBaseY + 7.55f
                  val eatingTopBaseY = y * scale
                  val eatingTop = eatingTopBaseY - 7.55f
                  val elapsedUseMillis = System.currentTimeMillis() - (player as PlayerState).`collective$getLastEatingTime`()
                  val elapsedUseMillisAsFloat = elapsedUseMillis.toFloat()
                  val useDurationTicks: Float = stack.getUseDuration(player).toFloat()
                  val scaledUseDuration = useDurationTicks * 5e+01f
                  val useProgress = elapsedUseMillisAsFloat / (scaledUseDuration + 5e+01f)
                  val eatingProgressWidth = 0.9f * Mth.clamp(useProgress, 0.0f, 1.0f)
                  val eatingLeft = (x - 0.45f) * scale
                  val eatingRight = (x - 0.45f + eatingProgressWidth) * scale
                  val configuredEating = eatingSetting.getValue()
                  GuiDrawUtils.fillRect(
                     poses,
                     eatingLeft,
                     eatingTop,
                     eatingRight,
                     eatingBottom,
                     ColorUtils.toArgb(
                        configuredEating, 0.2f)
                  )
               }
            }
         }
      }
      val showItemDetails = durabilitySetting.getValue() != DurabilityChoice.ONLY ||
         (!stack.has(DataComponents.EQUIPPABLE) && !stack.`is`(Items.ELYTRA))
      if (showItemDetails) {

         val blockItem = stack.item is BlockItem
         val itemPoseScaleZ: Float
         if (blockItem) {
            itemPoseScaleZ = 0.1f
         } else {
            itemPoseScaleZ = 0.5f
         }
         val itemPoseScaleX = if (blockItem) -scale else scale
         val itemDisplayContext = if (blockItem) ItemDisplayContext.FIXED else ItemDisplayContext.GUI
         this.hasEquipmentItem = true
         poses.pushPose()
         val itemPoseX = x * scale
         val itemPoseY = y * scale
         poses.translate(itemPoseX, itemPoseY, 0.0f)
         val itemPoseScaleY = -scale
         poses.scale(itemPoseScaleX, itemPoseScaleY, itemPoseScaleZ)
         val itemRenderState = ItemStackRenderState()
         val itemBuffers: SubmitNodeStorage = NameTagItemRenderer.getSubmitNodes()
         ClientAccess.collectiveMinecraft.itemModelResolver.updateForTopItem(itemRenderState, stack, itemDisplayContext, ClientAccess.collectiveMinecraft.level!!, null, 0)
         itemRenderState.submit(poses, itemBuffers, 15728880, OverlayTexture.NO_OVERLAY, 0)
         poses.popPose()
      }
      if (amountSetting.getValue()) {
         if (stack.count > 1) {
            val itemCount = stack.count
            val countText = itemCount.toString()
            val countX = (x + 0.5f) * scale
            val countBaseY = y * scale
            val countY = countBaseY + 1.5f
            val countScaleAsDouble = scale.toDouble()
            val countTextScale = countScaleAsDouble * 2.25
            val countAlignment = TextAlignment.RIGHT
            val countColor = Color.white
            this.drawText(
               poses, countText, countX, countY, countTextScale, countColor, false, countAlignment
            )
         }
      }
      if (stack.isBarVisible && showItemDetails) {
         val barBaseY = y * scale
         val barY = barBaseY + 5.5f
         val barBackgroundLeft = (x - 0.45f) * scale
         val barBackgroundTop = barY - 0.05f
         val barBackgroundRight = (x + 0.45f) * scale
         val barBackgroundBottom = barY + 2.0f
         GuiDrawUtils.fillRect(
            poses, barBackgroundLeft, barBackgroundTop, barBackgroundRight, barBackgroundBottom, -16777216
         )
         val barFillLeft = (x - 0.45f) * scale
         val barFillTop = barY - 0.05f
         val vanillaBarWidth = stack.barWidth.toFloat()
         val barProgress = vanillaBarWidth / 13.0f
         val barFillRight = (x + Mth.lerp(barProgress, -0.45f, 0.45f)) * scale
         val barFillBottom = barY + 1.05f
         GuiDrawUtils.fillRect(
            poses, barFillLeft, barFillTop, barFillRight, barFillBottom, -16777216 or stack.barColor
         )
      }
      val enchantments: Set<Object2IntMap.Entry<Holder<Enchantment>>> = stack.enchantments.entrySet()

      var enchantRowOffset = -1.2f
      val enchantedGoldenApple: Item = Items.ENCHANTED_GOLDEN_APPLE
      if (stack.`is`(enchantedGoldenApple) && showItemDetails) {
         val goldenAppleLabelX = x * scale
         var goldenAppleLabelY = y * scale
         goldenAppleLabelY -= 1.0f
         goldenAppleLabelY += FontRenderer.INSTANCE.height.toFloat() * 0.6f * enchantRowOffset
         val goldenAppleTextScale = (scale * 1.3f).toDouble()
         val goldenAppleAlignment = TextAlignment.RIGHT
         val goldenAppleColor = itemOverlayColor
         this.drawText(
            poses, "God", goldenAppleLabelX, goldenAppleLabelY, goldenAppleTextScale, goldenAppleColor, false, goldenAppleAlignment
         )
      } else {
         if (showItemDetails) {
            if (enchantsSetting.getValue()) {
               val hideMaxEnchantments = hideMaxSetting.getValue() &&
         EquipmentUtils.hasRequiredEnchantments(stack)
               if (hideMaxEnchantments) {
                  val maxLabelX = (x - 0.5f) * scale
                  val maxLabelBaseY = y * scale
                  val maxLabelPaddedY = maxLabelBaseY - 2.0f
                  val maxLabelLineHeight = FontRenderer.INSTANCE.height.toFloat() * 0.6f
                  val maxLabelY = maxLabelPaddedY + maxLabelLineHeight / 1.2f * enchantRowOffset
                  val maxLabelTextScale = (scale * 1.3f).toDouble()
                  val maxLabelAlignment = TextAlignment.LEFT
                  val maxLabelColor = Color.RED
                  this.drawText(
                     poses, "Max", maxLabelX, maxLabelY, maxLabelTextScale, maxLabelColor, false, maxLabelAlignment
                  )
               }
               val enchantmentEntries = enchantments.iterator()
               while (enchantmentEntries.hasNext()) {
                  val enchantmentEntry = enchantmentEntries.next()
                  if (hideMaxEnchantments) {
                     break
                  }
                  val enchantment: Enchantment = enchantmentEntry.key.value()
                  val singleLevelEnchantment = enchantment.maxLevel == 1
                  var enchantmentName = Enchantment.getFullname(enchantmentEntry.key, enchantmentEntry.intValue).string
                  val enchantmentPrefixLimit = if (singleLevelEnchantment) {
                     3
                  } else {
                     2
                  }
                  val enchantmentNameLength = enchantmentName.length
                  val enchantmentPrefixLength = min(enchantmentPrefixLimit, enchantmentNameLength)
                  enchantmentName = enchantmentName.substring(0, enchantmentPrefixLength)
                  if (!enchantmentName.equals("Cu", ignoreCase = true)) {
                     if (!singleLevelEnchantment) {
                        val enchantmentLevel = enchantmentEntry.intValue.toString()

                        enchantmentName = "\u0001\u0001"
                           .replaceFirst("\u0001", Matcher.quoteReplacement(enchantmentName))
                           .replaceFirst("\u0001", Matcher.quoteReplacement(enchantmentLevel))
                     }
                     val enchantmentX = (x - 0.5f) * scale
                     val enchantmentBaseY = y * scale
                     val enchantmentPaddedY = enchantmentBaseY - 2.0f
                     val enchantmentLineHeight = FontRenderer.INSTANCE.height.toFloat() * 0.6f
                     val enchantmentY = enchantmentPaddedY + enchantmentLineHeight / 1.2f * enchantRowOffset
                     val enchantmentTextScale = (scale * 1.3f).toDouble()
                     val enchantmentAlignment = TextAlignment.LEFT
                     val enchantmentColor = Color.WHITE
                     this.drawText(
                        poses, enchantmentName, enchantmentX, enchantmentY, enchantmentTextScale, enchantmentColor, false, enchantmentAlignment
                     )
                     enchantRowOffset += 1.0f
                  }
               }
            }
         }
         if (stack.isDamageableItem) {
            if (durabilitySetting.getValue() != DurabilityChoice.HIDE) {
               val durabilityBaseY = y * scale
               val durabilityPaddedY = durabilityBaseY - 4.0f
               var durabilityY = durabilityPaddedY - FontRenderer.INSTANCE.height.toFloat()
               if (!showItemDetails) {
                  durabilityY = y - FontRenderer.INSTANCE.height.toFloat()
               }
               textScratch.setLength(0)
               textScratch.append(durabilityPercent)
               textScratch.append("%")
               val durabilityColorScheme = colorsModule!!.schemeSetting.getValue()
               val vanillaDurabilityColor = Color(stack.barColor, false)
               val durabilityColor = durabilityColorScheme.transform(vanillaDurabilityColor)
               this.hasDurabilityText = true
               val durabilityText = textScratch.toString()
               val durabilityBaseX = ((x.toDouble() - MathConstants.HALF) * scale).toFloat()
               val durabilityX = durabilityBaseX + 1.0f
               val durabilityTextScale = (scale * 1.5f).toDouble()
               val durabilityAlignment = TextAlignment.LEFT
               this.drawText(
                  poses, durabilityText, durabilityX, durabilityY, durabilityTextScale, durabilityColor, false, durabilityAlignment
               )
               return
            }
         }
      }
   }

   private fun renderEquipment(poses: PoseStack, player: Player) {
      var maximumEnchantRows = -3
      for (stack in this.equipmentItems) {
         var enchantRows = EnchantmentUtils.getLevels(stack).size
         if (hideMaxSetting.getValue()) {
            if (EquipmentUtils.hasRequiredEnchantments(stack)) {
               enchantRows = 0
            }
         }
         if (enchantRows > maximumEnchantRows) {
            maximumEnchantRows = enchantRows
         }
      }
      val rowsBeforeClamp = maximumEnchantRows
      maximumEnchantRows = max(rowsBeforeClamp, 0)
      if (durabilitySetting.getValue() == DurabilityChoice.ONLY) {
         maximumEnchantRows = 0
      } else {
         if (!enchantsSetting.getValue()) {
            maximumEnchantRows = 0
         }
      }

      val enchantRowCount = maximumEnchantRows.toFloat()
      val enchantFontHeight = enchantRowCount * FontRenderer.INSTANCE.height.toFloat()
      val scaledEnchantHeight = enchantFontHeight * 0.5f
      val dividedEnchantHeight = scaledEnchantHeight / 1.2f
      val enchantBlockHeight = dividedEnchantHeight / 17.25f
      val backgroundPadding = this.getBackgroundPadding()
      val equipmentY = -max(enchantBlockHeight, backgroundPadding)

      for (itemIndex in this.equipmentItems.indices) {
         val equipmentStack = this.equipmentItems[this.equipmentItems.size - 1 - itemIndex]
         var equipmentX = itemIndex.toFloat()
         equipmentX += 0.5f
         val itemCountForCentering = this.equipmentItems.size.toFloat()
         equipmentX -= itemCountForCentering * 0.5f
         this.renderEquipmentItem(player, poses, equipmentStack, equipmentX, equipmentY, 17.25f)
      }
      val mainHandForCheck = player.mainHandItem
      if (!mainHandForCheck.isEmpty) {
         if (itemNameSetting.getValue()) {

            var itemNameY: Float
            val scaledItemNameY = equipmentY * 17.25f
            val paddedItemNameY = scaledItemNameY - 4.0f
            itemNameY = paddedItemNameY - FontRenderer.INSTANCE.height.toFloat()
            val hasItemRow: Boolean
            if (armorSetting.getValue()) {
               hasItemRow = this.hasEquipmentItem &&
         this.durabilitySetting.getValue() != DurabilityChoice.ONLY
            } else {
               hasItemRow = this.hasEquipmentItem
            }
            if (!hasItemRow) {
               itemNameY = -FontRenderer.INSTANCE.height.toFloat()
            }
            if (this.hasDurabilityText) {
               val durabilityFontHeight = FontRenderer.INSTANCE.height.toFloat()
               itemNameY -= durabilityFontHeight * 0.5f
               if (!hasItemRow) {
                  itemNameY -= 1.0f
               }
            }
            val namedItem = player.mainHandItem
            val formattedItemName = namedItem.hoverName.string
            val plainItemName = ChatFormatting.stripFormatting(formattedItemName)
            val itemRarity = namedItem.rarity
            val rarityRgb: Int = when (itemRarity) {
               net.minecraft.world.item.Rarity.UNCOMMON -> 0xFFFF55
               net.minecraft.world.item.Rarity.RARE -> 0x55FFFF
               net.minecraft.world.item.Rarity.EPIC -> 0xFF55FF
               else -> -1
            }
            val itemNameColor = Color(rarityRgb)
            val itemNameAlignment = TextAlignment.CENTER
            this.drawText(
               poses, plainItemName ?: "", 0.0f, itemNameY, 22.424999177455902, itemNameColor, false, itemNameAlignment
            )
            return
         }
      }
   }

   private fun getGameModeLabel(gameMode: GameType): String {
      return when (gameMode) {
         GameType.SURVIVAL -> "S"
         GameType.CREATIVE -> "C"
         GameType.ADVENTURE -> "A"
         GameType.SPECTATOR -> "SP"
      }
   }

   @EventHandler(priority = -1100)
   private fun onWorldRender(event: WorldRenderEvent.Overlay) {
      val renderTarget = NAME_TAG_RENDER_TARGET.get() as RenderTarget?
      RenderUtils.getEntityBuffers().endBatch()
      val cameraPosition = ClientAccess.collectiveMinecraft.gameRenderer.mainCamera().position()
      val currentLevel = ClientAccess.collectiveMinecraft.level
      for (entity in this.visibleEntities) {
         if (entity.level() !== currentLevel) {
            continue
         }
         val partialTick = RenderUtils.getPartialTick()
         val interpolationPartialTick = partialTick.toDouble()
         val previousXSnapshot = entity.xo
         val currentX = entity.getX()
         val relativeX = Mth.lerp(interpolationPartialTick, previousXSnapshot, currentX) - cameraPosition.x
         val previousYSnapshot = entity.yo
         val currentY = entity.getY()
         val relativeY = Mth.lerp(interpolationPartialTick, previousYSnapshot, currentY) - cameraPosition.y
         val previousZSnapshot = entity.zo
         val currentZ = entity.getZ()
         val relativeZ = Mth.lerp(interpolationPartialTick, previousZSnapshot, currentZ) - cameraPosition.z
         val entityDispatcherSnapshot: EntityRenderDispatcher = ClientAccess.collectiveMinecraft.entityRenderDispatcher
         val entityRenderer = entityDispatcherSnapshot.getRenderer(entity) as EntityRenderer<Entity, EntityRenderState>
         val renderState: EntityRenderState = entityRenderer.createRenderState(entity, partialTick)
         val renderOffset = entityRenderer.getRenderOffset(renderState)
         event.poseStack!!.pushPose()
         val translatePosesSnapshot = event.poseStack!!
         val translatedXSnapshot = relativeX + renderOffset.x
         val translatedYSnapshot = relativeY + renderOffset.y
         val translatedZ = relativeZ + renderOffset.z
         translatePosesSnapshot.translate(translatedXSnapshot, translatedYSnapshot, translatedZ)
         val tagPosesSnapshot = event.poseStack!!
         val tagEntity = entity as LivingEntity
         this.renderTag(tagPosesSnapshot, tagEntity)
         event.poseStack!!.popPose()
      }
      val glintOptionSnapshot = ClientAccess.collectiveMinecraft.options.glintStrength()
      var originalGlint: Double = glintOptionSnapshot.get() as Double
      val reducedGlint = originalGlint * 0.5
      glintOptionSnapshot.set(reducedGlint)
      if (this.overlayMesh
         .isBuilding()
      ) {
         val overlayRenderTypeSnapshot = ClientRenderTypes.image(
            ICON_TEXTURE
         )
         val overlayMeshData: MeshData = this.overlayMesh
            .build()
         me.collective.client.render.MeshBuffer.drawMesh(overlayRenderTypeSnapshot, overlayMeshData)
      }
      var lightingSnapshot: Lighting = ClientAccess.collectiveMinecraft.gameRenderer.lighting()
      val flatLightingEntry = Lighting.Entry.ITEMS_FLAT
      lightingSnapshot.setupFor(flatLightingEntry)
      val shaderSnapshot = shader
      if (shaderSnapshot!!.includesNameTags()) {
         RenderTargetUtils.overrideRenderTargets(renderTarget!!)
         RenderUtils.clearColor(renderTarget!!)
         RenderUtils.clearDepth(renderTarget!!)
         NameTagItemRenderer.flush()
         RenderTargetUtils.restoreRenderTargets()
      } else {
         NameTagItemRenderer.flush()
      }
      lightingSnapshot = ClientAccess.collectiveMinecraft.gameRenderer.lighting()
      val levelLightingEntry = Lighting.Entry.LEVEL
      lightingSnapshot.setupFor(levelLightingEntry)
      originalGlint = glintOptionSnapshot.get() as Double
      val restoredGlint = originalGlint * 2.0
      glintOptionSnapshot.set(restoredGlint)
      GuiDrawUtils.flush(true)
   }

   @EventHandler
   private fun refreshVisibleEntities(event: MinecraftRunTickEvent) {
      if (this.isWorldUnavailable()) {
         this.visibleEntities.clear()
         return
      }
      val refreshTimerSnapshot = this.entityRefreshTimer
      if (!isEntityRefreshDue(refreshTimerSnapshot)) {
         return
      }
      this.visibleEntities.clear()
      val level = ClientAccess.collectiveMinecraft.level!!
      for (entity in level.entitiesForRendering()) {
         if (!this.shouldRenderEntity(entity)) {
            continue
         }
         val gameRenderer = ClientAccess.collectiveMinecraft.gameRenderer
         val cameraPositionSnapshot = gameRenderer.mainCamera().position()
         val entityPositionSnapshot = entity.position()
         val distance = Vec3Utils.horizontalDistance(cameraPositionSnapshot, entityPositionSnapshot)
         val maximumRange = this.rangeSetting.getValue()
         if (distance > maximumRange) {
            continue
         }
         val boundsSnapshot = entity.boundingBox
         if (RotationManager.isVisible(boundsSnapshot)) {
            this.visibleEntities.add(entity)
         }
      }
      this.visibleEntities.sortWith(Comparator.comparingDouble<Entity> { sortedEntity ->
         val sortCameraPositionSnapshot = ClientAccess.collectiveMinecraft.gameRenderer.mainCamera().position()
         val sortEntityPosition = sortedEntity.position()
         -sortCameraPositionSnapshot.distanceToSqr(sortEntityPosition)
      })
   }

   @EventHandler
   private fun onNameTagCheck(event: NameTagRenderEvent) {
      val entitySnapshot = event.entity ?: return
      if (this.shouldRenderEntity(entitySnapshot)) {
         event.cancel()
      }
   }

   private fun drawText(
      poses: PoseStack, text: String, x: Float, y: Float, scale: Double, color: Color, vanillaFont: Boolean, alignment: TextAlignment
   ) {
      poses.pushPose()
      poses.translate(x, y, 0.0f)

      val textScaleX = 0.025f * scale.toFloat()
      val textScaleY = 0.025f * scale.toFloat()
      poses.scale(textScaleX, textScaleY, 1.0f)

      val alignmentOffset = alignment.getOffset(text, vanillaFont)
      if (vanillaFont) {
         val vanillaOffset = alignmentOffset.toInt()
         GuiDrawUtils.drawText(
            poses, text, vanillaOffset, 0, color.hashCode(), true
         )
      } else {
         FontRenderer.INSTANCE
            .drawWithShadow(poses, text, alignmentOffset, 0.0f, color, Font.DisplayMode.SEE_THROUGH)
      }
      poses.popPose()
   }

   private fun renderTag(poses: PoseStack, entity: LivingEntity) {
      this.hasEquipmentItem = false
      this.hasDurabilityText = false
      val animationsSnapshot = animationsModule
      val animated = animationsSnapshot!!.isPlayerScalingEnabled() &&
         entity !== ClientAccess.collectiveMinecraft.player!!
      val entityHeight = entity.bbHeight
      val modelHeightScale: Float
      if (animated) {
         modelHeightScale = animationsModule!!.playerScaleSetting.getValue()
      } else {
         modelHeightScale = 1.0f
      }

      val scaledEntityHeight = entityHeight * modelHeightScale
      val anchorHeight = scaledEntityHeight + 0.5f
      val configuredTagScale = this.scaleSetting.getValue()
      val cameraPosition = ClientAccess.collectiveMinecraft.gameRenderer.mainCamera().position()
      val renderPartialTick = RenderUtils.getPartialTick()
      val interpolatedPosition = entity.getPosition(renderPartialTick)
      val scaleAsDouble = configuredTagScale.toDouble()
      val tagScale = MathUtils.getDistanceScale(
         cameraPosition, interpolatedPosition, scaleAsDouble
      ).toFloat()
      poses.pushPose()
      val scaleAboveOrigin = tagScale - 1.0f
      val scaleHeightOffset = scaleAboveOrigin * 0.25f
      val tagAnchorY = anchorHeight + scaleHeightOffset
      poses.translate(0.0f, tagAnchorY, 0.0f)
      val dispatcherCamera = ClientAccess.collectiveMinecraft.entityRenderDispatcher.camera
      poses.mulPose(dispatcherCamera!!.rotation())
      val poseScaleX = tagScale * 0.025f
      val negativeScaleY = -tagScale
      val poseScaleY = negativeScaleY * 0.025f
      val negativeScaleZ = -tagScale
      val poseScaleZ = negativeScaleZ * 0.025f
      poses.scale(poseScaleX, poseScaleY, poseScaleZ)
      val label = this.buildTagText(entity)
      val plainLabel = label.string
      var textX = -FontRenderer.INSTANCE.getWidth(plainLabel)
      textX *= 0.5f

      val outline = this.outlineSetting.getValue()
      val fillLeft = textX - 1.0f
      val textFillWidth = textX * -1.0f
      val fillWidth = textFillWidth + 1.0f
      val fillFontHeight = FontRenderer.INSTANCE.height.toFloat()
      val fillHeight = fillFontHeight + 1.0f
      GuiDrawUtils.fillRect(
         poses, fillLeft, -1.0f, fillWidth, fillHeight, fillSetting.getValue()
      )
      val outlineLeft = textX - 2.5f + 1.5f
      val textOutlineWidth = textX * -1.0f
      val outlineWidth = textOutlineWidth + 1.0f
      val outlineFontHeight = FontRenderer.INSTANCE.height.toFloat()
      GuiDrawUtils.drawLineRect(
         poses, outlineLeft, -1.0f, outlineWidth, outlineFontHeight + 1.0f, outline
      )

      var textColor = this.textSetting.getValue()
      if (entity.isShiftKeyDown) {
         textColor = this.sneakSetting.getValue()
      }
      if (entity.isInvisible) {
         textColor = this.invisiblesSetting.getValue()
      }
      val friendCheckManager = ClientContext.socialRegistry
      if (friendCheckManager.isFriendEntity(entity)) {
         if (friendSetting.getValue()) {
            val friendColorManager = ClientContext.socialRegistry
            textColor = friendColorManager.friendColor
         }
      }
      val enemyCheckManager = ClientContext.socialRegistry
      if (enemyCheckManager.isEnemyEntity(entity)) {
         if (enemySetting.getValue()) {
            val enemyColorManager = ClientContext.socialRegistry
            textColor = enemyColorManager.enemyColor
         }
      }

      val queriedFontsModule = fontsModule
      queriedFontsModule!!.isToggled()
      FontRenderer.INSTANCE
         .drawWithShadow(poses, label, textX, 1.0f, textColor, Font.DisplayMode.SEE_THROUGH)
      if (entity is Player) {
         this.collectEquipment(entity)
         this.renderEquipment(poses, entity)
      }
      poses.popPose()
   }

   private fun isBot(player: Player): Boolean {
      if (player is FakeplayerCommand.SimulatedPlayer) {
         return false
      }
      val connectionSnapshot = ClientAccess.collectiveMinecraft.player!!.connection
      val profile = player.gameProfile
      val uuid = profile.id()
      val playerInfo = connectionSnapshot.getPlayerInfo(uuid)
      val onlineConnection = ClientAccess.collectiveMinecraft.player!!.connection
      if (!onlineConnection.onlinePlayers.contains(playerInfo) || playerInfo == null) {
         return true
      }
      return playerInfo.profile == null && player.hasCustomName()
   }

   private fun shouldRenderEntity(entity: Entity): Boolean {
      if (entity !is LivingEntity) {
         return false
      }
      if (entity is Player) {
         if (entity === ClientAccess.collectiveMinecraft.player!!) {
            if (!this.shouldRenderSelf()) {
               return false
            }
         }
         if (entity.isDeadOrDying) {
            if (!deadSetting.getValue()) {
               return false
            }
         }
         if (!botsSetting.getValue()) {
            if (this.isBot(entity)) {
               return false
            }
         }
         return playersSetting.getValue()
      } else {
         if (entity is AgeableMob || entity is AbstractFish || entity is Squid) {
            if (animalsSetting.getValue()) {
               return true
            }
         }
         if (entity is Enemy) {
            if (hostilesSetting.getValue()) {
               return true
            }
         }
         if (entity is NeutralMob) {
            if (neutralsSetting.getValue()) {
               return true
            }
         }
         return false
      }
   }

   init {
      this.rangeSetting = this.add(NumberSetting<Int>("Range", 300, 1, 300).withUnit("m"))
      this.scaleSetting = this.add(NumberSetting<Float>("Scale", 1.2f, 0.1f, 3.0f))
      this.infoSetting = this.add(BooleanSetting("Info", true).markAsGroup())
      this.nameSetting = this.add(BooleanSetting("Name", true, Predicate<Any> {
         infoSetting.isExpanded()
      }))
      this.pingSetting = this.add(BooleanSetting("Ping", true, Predicate<Any> {
         infoSetting.isExpanded()
      }).markVisibilityDependency())
      this.colorSetting = this.add(BooleanSetting("Color", true, Predicate<Any> {
         infoSetting.isExpanded()
      }).dependsOn(this.pingSetting))
      this.healthSetting = this.add(BooleanSetting("Health", true, Predicate<Any> {
         infoSetting.isExpanded()
      }).markVisibilityDependency())
      this.decimalsSetting = this.add(BooleanSetting("Decimals", true, Predicate<Any> {
         infoSetting.isExpanded() && this.healthSetting.isExpanded()
      }))
      this.gamemodeSetting = this.add(BooleanSetting("Gamemode", true, Predicate<Any> {
         infoSetting.isExpanded()
      }))
      this.totemPopsSetting = this.add(BooleanSetting("TotemPops", false, Predicate<Any> {
         infoSetting.isExpanded()
      }))
      this.itemSetting = this.add(BooleanSetting("Item", false).markAsGroup())
      this.armorSetting = this.add(BooleanSetting("Armor", true).markVisibilityDependency().dependsOn(this.itemSetting))
      this.durabilitySetting = this.add(EnumSetting<DurabilityChoice>("Durability", DurabilityChoice.SHOW).dependsOn(this.itemSetting, this.armorSetting))
      this.itemsSetting = this.add(BooleanSetting("Items", true).dependsOn(this.itemSetting))
      this.showEatingSetting = this.add(BooleanSetting("ShowEating", false).dependsOn(this.itemSetting, this.itemsSetting))
      this.enchantsSetting = this.add(BooleanSetting("Enchants", true).dependsOn(this.itemSetting))
      this.hideMaxSetting = this.add(BooleanSetting("HideMax", false).dependsOn(this.itemSetting, this.enchantsSetting))
      this.itemNameSetting = this.add(BooleanSetting("Name", true).dependsOn(this.itemSetting))
      this.amountSetting = this.add(BooleanSetting("Amount", true).dependsOn(this.itemSetting))
      this.targetsSetting = this.add(BooleanSetting("Targets", true).markAsGroup())
      this.playersSetting = this.add(BooleanSetting("Players", true).markVisibilityDependency().dependsOn(this.targetsSetting))
      this.deadSetting = this.add(BooleanSetting("Dead", true).dependsOn(this.playersSetting, this.targetsSetting))
      this.selfSetting = this.add(BooleanSetting("Self", false).dependsOn(this.playersSetting, this.targetsSetting))
      this.botsSetting = this.add(BooleanSetting("Bots", true).dependsOn(this.playersSetting, this.targetsSetting))
      this.animalsSetting = this.add(BooleanSetting("Animals", false).dependsOn(this.targetsSetting))
      this.hostilesSetting = this.add(BooleanSetting("Hostiles", false).dependsOn(this.targetsSetting))
      this.neutralsSetting = this.add(BooleanSetting("Neutrals", false).dependsOn(this.targetsSetting))
      this.colorsSetting = this.add(BooleanSetting("Colors", true).markAsGroup())
      this.friendSetting = this.add(BooleanSetting("Friend", true).dependsOn(this.colorsSetting))
      this.enemySetting = this.add(BooleanSetting("Enemy", true).dependsOn(this.colorsSetting))
      val fillVisibility = Predicate<Any> {
         colorsSetting.isExpanded()
      }
      this.fillSetting = this.add(ColorSetting("Fill", Color(657930, true), fillVisibility))
      this.outlineSetting = this.add(ColorSetting("Outline", Color(12425727, true), Predicate<Any> {
         colorsSetting.isExpanded()
      }))
      this.textSetting = this.add(ColorSetting("Text", Color(-1, true), Predicate<Any> {
         colorsSetting.isExpanded()
      }))
      this.invisiblesSetting = this.add(ColorSetting("Invisibles", Color(-65536, true), Predicate<Any> {
         colorsSetting.isExpanded()
      }))
      this.sneakSetting = this.add(ColorSetting("Sneak", Color(-28416, true), Predicate<Any> {
         colorsSetting.isExpanded()
      }))
      this.eatingSetting = this.add(ColorSetting("Eating", Color(-16711936, true), Predicate<Any> {
         colorsSetting.isExpanded()
      }))
      this.overlayMesh = MeshBuffer.create()
         .setFormat(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
      this.itemOverlayColor = Color(153, 69, 58)
      this.visibleEntities = ObjectArrayList()
      this.equipmentItems = ObjectArrayList(6)
      this.entityRefreshTimer = ElapsedTimer()
      val identitySetting = this.itemNameSetting
      identitySetting.setConfigName("ItemName")
      val identitySetting2 = this.amountSetting
      identitySetting2.setConfigName("ItemAmount")
      val identitySetting3 = this.colorSetting
      identitySetting3.setConfigName("PingColor")
   }

   enum class DurabilityChoice(private val displayName: String) : NamedEntry {
      SHOW("Show"),
      ONLY("Only"),
      HIDE("Hide");

      override fun getName(): String {
         return this.displayName
      }
   }

   private enum class TextAlignment {
      CENTER, LEFT, RIGHT;

      fun getOffset(text: String, vanillaFont: Boolean): Float {
         val width: Float
         if (vanillaFont) {
            width = ClientAccess.collectiveMinecraft.font.width(text).toFloat()
         } else {
            width = FontRenderer.INSTANCE.getWidth(text)
         }
         return when (this) {
            CENTER -> -width * 0.5f
            LEFT -> 0.0f
            RIGHT -> -width
         }
      }
   }

}

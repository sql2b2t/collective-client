package me.collective.client.modules.movement

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import me.collective.client.ClientAccess
import me.collective.client.utils.math.MathConstants
import me.collective.client.ClientContext
import me.collective.client.events.ItemUseEvent
import me.collective.client.events.LocalPlayerTickPhaseEvent
import me.collective.client.events.PacketEvent
import me.collective.client.events.StatusEffectQueryEvent
import me.collective.client.events.WorldRenderEvent
import me.collective.client.events.InventoryActionEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerAiStepEvent
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.events.MultiPlayerGameModeHandleInventoryMouseClickEvent
import me.collective.client.events.MultiPlayerGameModeUseItemOnEvent
import me.collective.client.gui.panels.Panel
import me.collective.client.gui.panels.GamePanel
import me.collective.client.gui.screens.ClickGuiScreen
import me.collective.client.gui.screens.HudEditorScreen
import me.collective.client.utils.InterpolatedFloat
import me.collective.client.utils.network.PacketSender
import me.collective.client.utils.inventory.ChestSearchController
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.inventory.NoSlowInventoryNcpMode
import me.collective.client.utils.inventory.NoSlowInventoryMode
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.exploit.MultiTaskModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.NoSlowModeChoice
import me.collective.client.settings.options.NoSlowStrictInventoryChoice
import java.util.Iterator
import java.util.function.Predicate
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.inventory.AbstractCommandBlockEditScreen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.AnvilScreen
import net.minecraft.client.gui.screens.inventory.SignEditScreen
import net.minecraft.client.gui.screens.inventory.StructureBlockEditScreen
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.lwjgl.glfw.GLFW

class NoSlowModule : Module("NoSlow", "Cancels several things that may slow you down.", ModuleCategory.MOVEMENT) {
   @JvmField
   val guiMoveSetting: Setting<Boolean>

   @JvmField
   val containersSetting: Setting<Boolean>

   @JvmField
   val rotateSetting: Setting<Boolean>
   private val strictSetting: Setting<Boolean>

   @JvmField
   val modeSetting: Setting<NoSlowModeChoice>
   private val strictInventorySetting: Setting<NoSlowStrictInventoryChoice>
   private val playerSetting: Setting<Boolean>

   @JvmField
   val itemsSetting: Setting<Boolean>

   @JvmField
   val sneakSetting: Setting<Boolean>

   @JvmField
   val crawlSetting: Setting<Boolean>

   @JvmField
   val slownessSetting: Setting<Boolean>
   internal val blocksSetting: Setting<Boolean>

   @JvmField
   val iceSetting: Setting<Boolean>

   @JvmField
   val soulSandSetting: Setting<Boolean>

   @JvmField
   val honeySetting: Setting<Boolean>

   @JvmField
   val berryBushSetting: Setting<Boolean>

   @JvmField
   val slimeSetting: Setting<Boolean>

   @JvmField
   val noSlimeBounceSetting: Setting<Boolean>

   @JvmField
   var releasedUsingItemThisTick: Boolean = false
   private val pitchInterpolation: InterpolatedFloat
   private val yawInterpolation: InterpolatedFloat
   private val strictInventoryModes: EnumSelectionMap<NoSlowInventoryMode>

   @EventHandler
   private fun onInventoryClickAfterStackHash(
      event: MultiPlayerGameModeHandleInventoryMouseClickEvent.AfterStackHash
   ) {
      val strategy: NoSlowInventoryMode = this.strictInventoryModes.selected
      strategy.onInventoryClickIntercept(event)
   }

   @EventHandler
   private fun onTickBeforePassengerCheck(
      event: LocalPlayerTickPhaseEvent.BeforePassengerCheck
   ) {
      val strategy: NoSlowInventoryMode = this.strictInventoryModes.selected
      strategy.onTickBeforePassengerCheck()
   }

   @EventHandler
   private fun onBeforeContainerClick(
      event: MultiPlayerGameModeHandleInventoryMouseClickEvent.BeforeContainerClick
   ) {
      val strategy: NoSlowInventoryMode = this.strictInventoryModes.selected
      strategy.beforeMenuClick(event)
   }

   @EventHandler(priority = -100)
   private fun onLocalPlayerAiStepEvent(event: LocalPlayerAiStepEvent) {
      val strategy: NoSlowInventoryMode = this.strictInventoryModes.selected
      strategy.onPlayerAiStep()
   }

   @EventHandler
   private fun onInventoryAction(event: InventoryActionEvent) {
      val strategy: NoSlowInventoryMode = this.strictInventoryModes.selected
      strategy.onInventoryAction(event)
   }

   @EventHandler
   private fun onStatusEffectQuery(event: StatusEffectQueryEvent) {
      if (this.slownessSetting.value) {
         if (event.effect == MobEffects.SLOWNESS) {
            event.cancel()
         }
      }
   }

   @EventHandler(priority = 200)
   private fun onLocalPlayerPreTickEvent(event: LocalPlayerPreTickEvent) {
      this.releasedUsingItemThisTick = false
      val strategy: NoSlowInventoryMode = this.strictInventoryModes.selected
      strategy.onTickStart(event)
   }

   @EventHandler
   private fun onItemSlowdown(event: ItemUseEvent.Slowdown) {
      if (!this.itemsSetting.value) return
      if (!ClientAccess.collectiveMinecraft.player!!.isUsingItem) return
      if (ClientAccess.collectiveMinecraft.player!!.isPassenger) return

      var cancelSlowdown: Boolean = true
      if (this.modeSetting.value == NoSlowModeChoice.GRIMV3) {
         val useTicks: Int = ClientAccess.collectiveMinecraft.player!!.ticksUsingItem
         cancelSlowdown = useTicks % 2 == 0 && useTicks > 1
         if (ClientAccess.collectiveMinecraft.player!!.isFallFlying) cancelSlowdown = false
      }

      if (this.modeSetting.value == NoSlowModeChoice.GRIMOLD) {
         val handPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         val usedHand: InteractionHand = handPlayer.usedItemHand
         this.handleGrimItemUse(usedHand)
      }

      if (this.modeSetting.value == NoSlowModeChoice.CONSTANTIAM) cancelSlowdown = false
      if (this.modeSetting.value == NoSlowModeChoice._2B2T) {
         cancelSlowdown = this.handle2b2tItemUse()
      }
      if (cancelSlowdown) {
         event.cancel()
      }
   }

   @EventHandler
   private fun onMultiPlayerGameModeUseItemOnEvent(event: MultiPlayerGameModeUseItemOnEvent) {
      if (this.modeSetting.value != NoSlowModeChoice.CONSTANTIAM) return
      if (!this.itemsSetting.value) return
      if (ClientAccess.collectiveMinecraft.player!!.isFallFlying) return
      val handPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val usedHand: InteractionHand = event.hand
      if (!handPlayer.getItemInHand(usedHand).has(DataComponents.FOOD)) return
      val hitPos: BlockPos = event.hitResult.blockPos
      if (BlockInteractionUtils.requiresSneakForInteraction(hitPos)) return
      ClientAccess.collectiveMinecraft.gameMode!!.useItem(ClientAccess.collectiveMinecraft.player!!, InteractionHand.MAIN_HAND)
      event.cancel()
   }

   @EventHandler
   private fun onConnectionSendPacketEvent(event: PacketEvent.Send) {
      val strategy: NoSlowInventoryMode = this.strictInventoryModes.selected
      strategy.onPacketSend(event)
      if (event.packet !is ServerboundMovePlayerPacket) return
      val usedHand: InteractionHand = ClientAccess.collectiveMinecraft.player!!.usedItemHand
      val usedStack: ItemStack = ClientAccess.collectiveMinecraft.player!!.getItemInHand(usedHand)
      if (!ClientAccess.collectiveMinecraft.player!!.isUsingItem) return
      if (!this.itemsSetting.value) return
      if (ClientAccess.collectiveMinecraft.player!!.isHandsBusy) return
      if (ClientAccess.collectiveMinecraft.player!!.isFallFlying) return
      if (!usedStack.has(DataComponents.FOOD)) return
      if (this.modeSetting.value == NoSlowModeChoice.NCP) {
         val heldSlotPacket = ServerboundSetCarriedItemPacket(
            ClientAccess.collectiveMinecraft.player!!.inventory.selectedSlot
         )
         PacketSender.send(heldSlotPacket)
      }
   }

   private fun isFoodBowShieldOrPearl(stack: ItemStack): Boolean {
      if (stack.has(DataComponents.FOOD)) return true
      if (stack.`is`(Items.BOW)) return true
      if (stack.`is`(Items.SHIELD)) return true
      return stack.`is`(Items.ENDER_PEARL)
   }

   fun isGuiMoveAllowed(): Boolean {
      if (ClientAccess.collectiveMinecraft.screen is ClickGuiScreen) {
         val iterator = ClientContext.getClickGui().panels.iterator()
         while (iterator.hasNext()) {
            val panel: Panel = iterator.next()
            if (panel is GamePanel) {
               if (panel.isFullyExpanded) {
                  return false
               }
            }
         }
         if (ClientContext.getClickGui().searchBox?.isFocused == true) {
            return false
         }
         if (ClientContext.getClickGui().searchBox?.value?.isBlank() == false) {
            return false
         }
      }
      if (ClientAccess.collectiveMinecraft.screen is AbstractContainerScreen<*>) {
         if (!this.containersSetting.value) {
            val containerScreen: AbstractContainerScreen<*> = ClientAccess.collectiveMinecraft.screen as AbstractContainerScreen<*>
            return containerScreen.menu ==
         ClientAccess.collectiveMinecraft.player!!.inventoryMenu
         }
      }
      if (ClientAccess.collectiveMinecraft.screen != null &&
         !(ClientAccess.collectiveMinecraft.screen is ChatScreen) &&
         !(ClientAccess.collectiveMinecraft.screen is SignEditScreen) &&
         !(ClientAccess.collectiveMinecraft.screen is AnvilScreen) &&
         !(ClientAccess.collectiveMinecraft.screen is AbstractCommandBlockEditScreen) &&
         !(ClientAccess.collectiveMinecraft.screen is StructureBlockEditScreen)) {
         if (ChestSearchController.searchBox == null) {
            return true
         }
         if (!ChestSearchController.searchBox!!.canConsumeInput()) {
            return true
         }
      }
      return false
   }

   fun prepareForInventoryAction() {
      if (this.releasedUsingItemThisTick) return
      if (multiTask!!.isItemUseRestricted()) return
      if (multiTask!!.isInventoryAllowed()) return
      ClientAccess.collectiveMinecraft.gameMode!!.releaseUsingItem(ClientAccess.collectiveMinecraft.player!!)
      this.releasedUsingItemThisTick = true
   }

   val strictInventoryMode: NoSlowStrictInventoryChoice
      @JvmName("getStrictInventoryModeProp") get() = getStrictInventoryMode()

   fun getStrictInventoryMode(): NoSlowStrictInventoryChoice {
      return this.strictInventorySetting.value
   }

   fun isUsingItemInConstantiamMode(): Boolean {
      if (!ClientAccess.collectiveMinecraft.player!!.isUsingItem) return false
      return this.modeSetting.value == NoSlowModeChoice.CONSTANTIAM
   }

   fun isSlimeSlowdownDisabled(): Boolean {
      if (!this.isToggled()) return false
      return this.slimeSetting.value
   }

   private fun handle2b2tItemUse(): Boolean {
      val swapSlot: Int = this.find2b2tSwapSlot()
      if (swapSlot == -1 || ClientAccess.collectiveMinecraft.isLocalServer) return true

      val latencyTicks: Float = ClientContext.serverStateManager.peakLatencyMillis / 50.0f
      val tickThreshold: Int = (Math.ceil((latencyTicks / ClientContext.serverStateManager.tickRateScale).toDouble()) + 2.0).toInt()
      val useTicks: Int = ClientAccess.collectiveMinecraft.player!!.ticksUsingItem
      if (useTicks == tickThreshold) {
         InventoryUtils.clickSlot(InventoryUtils.toMenuSlot(InventoryUtils.getSelectedSlot()), swapSlot, ClickType.SWAP)
         PacketSender.sendPlayerAction(ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN)
         InventoryUtils.clickSlot(InventoryUtils.toMenuSlot(InventoryUtils.getSelectedSlot()), swapSlot, ClickType.SWAP)
      }
      return useTicks > tickThreshold
   }

   private fun find2b2tSwapSlot(): Int {
      if (!ClientAccess.collectiveMinecraft.player!!.offhandItem.isEmpty) return 40
      for (slot in 0 until 9) {
         if (slot != InventoryUtils.getSelectedSlot() && !ClientAccess.collectiveMinecraft.player!!.inventory.getItem(slot).isEmpty) {
            return slot
         }
      }
      return -1
   }

   private fun isAnyRotationKeyPressed(): Boolean {
      return this.isKeyPressed(GLFW.GLFW_KEY_DOWN) || this.isKeyPressed(GLFW.GLFW_KEY_UP) ||
         this.isKeyPressed(GLFW.GLFW_KEY_RIGHT) || this.isKeyPressed(GLFW.GLFW_KEY_LEFT)
   }

   private fun handleGrimItemUse(usedHand: InteractionHand) {
      val otherHand: InteractionHand = PlayerStateUtils.oppositeHand(usedHand)
      val otherHandStack: ItemStack = ClientAccess.collectiveMinecraft.player!!.getItemInHand(otherHand)
      if (this.isFoodBowShieldOrPearl(otherHandStack)) {
         if (otherHand == InteractionHand.MAIN_HAND) {
            val selectedSlot: Int = ClientAccess.collectiveMinecraft.player!!.inventory.selectedSlot
            var nextSlot: Int = selectedSlot + 1
            nextSlot %= 9
            InventoryUtils.selectHotbarSlot(nextSlot)
            PacketSender.sendUseItem(otherHand)
            InventoryUtils.selectHotbarSlot(selectedSlot)
            return
         }
         val inventoryPredicate: Predicate<ItemStack> = Predicate { stack: ItemStack -> !this.isFoodBowShieldOrPearl(stack) }
         val inventorySlot: Int = InventoryUtils.findInventorySlot(inventoryPredicate, true)
         val containerSlot: Int = InventoryUtils.toMenuSlot(inventorySlot)
         ClientAccess.collectiveMinecraft.gameMode!!.handleInventoryMouseClick(0, containerSlot, 40, ClickType.SWAP, ClientAccess.collectiveMinecraft.player!!)
         PacketSender.sendUseItem(otherHand)
         ClientAccess.collectiveMinecraft.gameMode!!.handleInventoryMouseClick(0, containerSlot, 40, ClickType.SWAP, ClientAccess.collectiveMinecraft.player!!)
      } else {
         PacketSender.sendUseItem(otherHand)
      }
   }

   private fun isKeyPressed(key: Int): Boolean {
      val handleCapture: Long = ClientAccess.collectiveMinecraft.window.handle()
      return GLFW.glfwGetKey(handleCapture, key) == GLFW.GLFW_PRESS
   }

   val isGrimStrictInventoryEnabled: Boolean @JvmName("isGrimStrictInventoryEnabledProp") get() = isGrimStrictInventoryEnabled()
   fun isGrimStrictInventoryEnabled(): Boolean {
      if (!this.isToggled()) return false
      return this.strictInventorySetting.value == NoSlowStrictInventoryChoice.GRIM_STRICT
   }

   @EventHandler
   private fun onWorldRender(event: WorldRenderEvent.Main) {
      if (!this.guiMoveSetting.value || !this.rotateSetting.value) {
         return
      }
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      if (this.isGuiMoveAllowed() &&
         !(ClientAccess.collectiveMinecraft.screen is ClickGuiScreen) &&
         !(ClientAccess.collectiveMinecraft.screen is HudEditorScreen)) {
         if (this.isKeyPressed(GLFW.GLFW_KEY_DOWN)) {
            val pitch: Float = Mth.lerp(event.partialTick, player.xRotO, player.getXRot())
            this.pitchInterpolation.setTarget(Math.min(pitch + 8.0f, MathConstants.NINETY_DEGREES.toFloat()))
         } else if (this.isKeyPressed(GLFW.GLFW_KEY_UP)) {
            val pitch: Float = Mth.lerp(event.partialTick, player.xRotO, player.getXRot())
            this.pitchInterpolation.setTarget(Math.max(pitch - 8.0f, -MathConstants.NINETY_DEGREES.toFloat()))
         }
         if (this.isKeyPressed(GLFW.GLFW_KEY_RIGHT)) {
            val yaw: Float = Mth.lerp(event.partialTick, player.yRotO, player.getYRot())
            this.yawInterpolation.setTarget(yaw + 8.0f)
         } else if (this.isKeyPressed(GLFW.GLFW_KEY_LEFT)) {
            val yaw: Float = Mth.lerp(event.partialTick, player.yRotO, player.getYRot())
            this.yawInterpolation.setTarget(yaw - 8.0f)
         }
         if (this.isAnyRotationKeyPressed()) {
            player.setXRot(this.pitchInterpolation.updateAndGet())
            player.setYRot(this.yawInterpolation.updateAndGet())
         }
      } else {
         this.yawInterpolation.setValue(player.getYRot())
         this.pitchInterpolation.setValue(player.getXRot())
      }
   }

   override fun fromJson(json: JsonElement) {
      var effectiveJson: JsonElement = json
      if (effectiveJson is JsonObject && effectiveJson.get("settings") is JsonObject) {
         val moduleJson: JsonObject = effectiveJson
         val settings: JsonObject = effectiveJson.get("settings") as JsonObject
         val itemModeKey: String = this.modeSetting.configName
         val inventoryModeKey: String = this.strictInventorySetting.configName
         val retiredItemMode: Boolean = isLegacyMode(settings.get(itemModeKey), "GrimStrict")
         val retiredInventoryMode: Boolean = isLegacyMode(settings.get(inventoryModeKey), "Constantiam")
         if (retiredItemMode || retiredInventoryMode) {
            val migrated: JsonObject = moduleJson.deepCopy()
            val migratedSettings: JsonObject = migrated.getAsJsonObject("settings")

            if (retiredItemMode) {
               migratedSettings.addProperty(itemModeKey, NoSlowModeChoice.GRIMV3.getName())
            }
            if (retiredInventoryMode) {
               migratedSettings.addProperty(inventoryModeKey, NoSlowStrictInventoryChoice.NCP.getName())
            }
            effectiveJson = migrated
         }
      }
      super.fromJson(effectiveJson)
   }

   companion object {
      private var multiTask: MultiTaskModule? = null

      private fun isLegacyMode(value: JsonElement?, retiredName: String): Boolean {
         return value is JsonPrimitive && value.isString &&
         retiredName.equals(value.asString, ignoreCase = true)
      }

      @JvmStatic
      fun bindReferencedModules(registry: ModuleRegistry) {
         multiTask = registry.getModule(MultiTaskModule::class.java) as MultiTaskModule
         NoSlowInventoryNcpMode.bindReferencedModules(registry)
      }
   }

   init {
      this.guiMoveSetting = this.add(BooleanSetting("GuiMove", true).markVisibilityDependency())
      this.containersSetting = this.add(BooleanSetting("Containers", true).dependsOn(this.guiMoveSetting))
      this.rotateSetting = this.add(BooleanSetting("Rotate", true).dependsOn(this.guiMoveSetting))
      this.strictSetting = this.add(BooleanSetting("Strict", true).markAsGroup().markVisibilityDependency())
      this.modeSetting = this.add(EnumSetting<NoSlowModeChoice>("Mode", NoSlowModeChoice.NONE).dependsOn(this.strictSetting))
      this.strictInventorySetting = this.add(EnumSetting<NoSlowStrictInventoryChoice>("StrictInventory", NoSlowStrictInventoryChoice.NONE).dependsOn(this.strictSetting))
      this.playerSetting = this.add(BooleanSetting("Player", false).markAsGroup().markVisibilityDependency())
      this.itemsSetting = this.add(BooleanSetting("Items", true).dependsOn(this.playerSetting))
      this.sneakSetting = this.add(BooleanSetting("Sneak", false).dependsOn(this.playerSetting))
      this.crawlSetting = this.add(BooleanSetting("Crawl", false).dependsOn(this.playerSetting))
      this.slownessSetting = this.add(BooleanSetting("Slowness", false).dependsOn(this.playerSetting))
      this.blocksSetting = this.add(BooleanSetting("Blocks", false).markAsGroup().markVisibilityDependency())
      val iceVisibility: Predicate<Any> = Predicate { ignored -> this.blocksSetting.isExpanded }
      this.iceSetting = this.add(BooleanSetting("Ice", false, iceVisibility))
      val soulSandVisibility: Predicate<Any> = Predicate { ignored -> this.blocksSetting.isExpanded }
      this.soulSandSetting = this.add(BooleanSetting("SoulSand", false, soulSandVisibility))
      val honeyVisibility: Predicate<Any> = Predicate { ignored -> this.blocksSetting.isExpanded }
      this.honeySetting = this.add(BooleanSetting("Honey", false, honeyVisibility))
      val berryBushVisibility: Predicate<Any> = Predicate { ignored -> this.blocksSetting.isExpanded }
      this.berryBushSetting = this.add(BooleanSetting("BerryBush", false, berryBushVisibility))
      val slimeVisibility: Predicate<Any> = Predicate { ignored -> this.blocksSetting.isExpanded }
      this.slimeSetting = this.add(BooleanSetting("Slime", false, slimeVisibility).markVisibilityDependency())
      this.noSlimeBounceSetting = this.add(BooleanSetting("NoSlimeBounce", true).dependsOn(this.blocksSetting, this.slimeSetting))

      this.pitchInterpolation = InterpolatedFloat(2.0f, true)
      this.yawInterpolation = InterpolatedFloat(2.0f, true)
      this.strictInventoryModes = EnumSelectionMap<NoSlowInventoryMode>(
         this.strictInventorySetting
      )
      this.strictInventoryModes.register(NoSlowStrictInventoryChoice.NONE, NoSlowInventoryMode(this))
      this.strictInventoryModes.register(NoSlowStrictInventoryChoice.NCP, NoSlowInventoryNcpMode(this))
      this.strictInventoryModes.register(NoSlowStrictInventoryChoice.GRIM_STRICT, NoSlowInventoryGrimStrictMode(this))

      this.setDrawn(false)
   }
}

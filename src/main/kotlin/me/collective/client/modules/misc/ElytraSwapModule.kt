package me.collective.client.modules.misc

import me.collective.client.ClientAccess
import me.collective.client.events.InputPressEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.modules.Module
import me.collective.client.modules.Keybind
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.KeybindSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.Setting
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.utils.inventory.ArmorSlot
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.network.PacketSender

import java.util.function.Predicate

import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.InteractionHand
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Свап элитры и нагрудника одним действием: в полёте — элита на теле,
 * по кнопке/посадке — нагрудник, при новом взлёте — обратно. Плюс выстрел
 * фейерверком с кнопки.
 *
 * Порт модуля Elytra Swap (CatLean 1.21.11, класс su.catlean.tk): настройки
 * mode/swap-mode/hotbar-swap-mode/switch-button/fire-work-button/fire-work-mode/
 * start-fire-work/swap-on-land восстановлены из runtime-расшифровки строк;
 * цели подмены нагрудника — от кожи до незерита (массив Items оригинала).
 */
class ElytraSwapModule : Module("ElytraSwap", "Свап элитры и нагрудника, выстрел фейерверком", ModuleCategory.MISC, "elytraswap") {
   private val modeSetting: Setting<ElytraSwapModeChoice>
   private val swapModeSetting: Setting<SwapModeChoice>
   private val hotBarSwapModeSetting: Setting<HotBarSwapModeChoice>
   private val fireWorkModeSetting: Setting<FireWorkModeChoice>
   private val switchButtonSetting: KeybindSetting
   private val fireWorkButtonSetting: KeybindSetting
   private val startFireWorkSetting: Setting<Boolean>
   private val swapOnLandSetting: Setting<Boolean>

   private var wasFallFlying = false

   companion object {
      private const val MESSAGE_PREFIX = "§7[§bElytraSwap§7] §f"

      /**
       * Нагрудники-цели, от лучшего к худшему (в оригинале — LEATHER..NETHERITE,
       * берётся первый найденный).
       */
      private val CHESTPLATE_TARGETS: Array<Item> = arrayOf(
         Items.NETHERITE_CHESTPLATE,
         Items.DIAMOND_CHESTPLATE,
         Items.IRON_CHESTPLATE,
         Items.GOLDEN_CHESTPLATE,
         Items.CHAINMAIL_CHESTPLATE,
         Items.LEATHER_CHESTPLATE
      )
   }

   init {
      this.modeSetting = this.add(EnumSetting<ElytraSwapModeChoice>("Mode", ElytraSwapModeChoice.ALWAYS))
      this.swapModeSetting = this.add(EnumSetting<SwapModeChoice>("SwapMode", SwapModeChoice.NORMAL))
      this.hotBarSwapModeSetting = this.add(EnumSetting<HotBarSwapModeChoice>("HotBarSwapMode", HotBarSwapModeChoice.SWAP))
      this.fireWorkModeSetting = this.add(EnumSetting<FireWorkModeChoice>("FireWorkMode", FireWorkModeChoice.NORMAL))
      this.switchButtonSetting = this.add(KeybindSetting("SwitchButton", Keybind.UNBOUND.code()))
      this.fireWorkButtonSetting = this.add(KeybindSetting("FireWorkButton", Keybind.UNBOUND.code()))
      this.startFireWorkSetting = this.add(BooleanSetting("StartFireWork", false, Predicate { ignored -> this.isAlwaysMode() }))
      this.swapOnLandSetting = this.add(BooleanSetting("SwapOnLand", true, Predicate { ignored -> this.isAlwaysMode() }))
   }

   enum class ElytraSwapModeChoice(private val displayName: String) : NamedEntry {
      ALWAYS("Always"),
      BIND("Bind");

      override fun getName(): String {
         return this.displayName
      }
   }

   enum class SwapModeChoice(private val displayName: String) : NamedEntry {
      NORMAL("Normal"),
      SILENT("Silent");

      override fun getName(): String {
         return this.displayName
      }
   }

   enum class HotBarSwapModeChoice(private val displayName: String) : NamedEntry {
      SWAP("Swap"),
      PICKUP("Pickup");

      override fun getName(): String {
         return this.displayName
      }
   }

   enum class FireWorkModeChoice(private val displayName: String) : NamedEntry {
      NORMAL("Normal"),
      SILENT("Silent");

      override fun getName(): String {
         return this.displayName
      }
   }

   private fun isAlwaysMode(): Boolean {
      return this.modeSetting.value == ElytraSwapModeChoice.ALWAYS
   }

   override fun onDisable() {
      this.wasFallFlying = false
   }

   override fun onToggle() {
      this.wasFallFlying = false
   }

   /**
    * Автоматика режима Always: посадка — свап элиты на нагрудник (SwapOnLand),
    * взлёт — выстрел ракетой (StartFireWork). В режиме Bind работают только кнопки.
    */
   @EventHandler
   private fun onLocalPlayerTick(event: LocalPlayerTickEvent) {
      if (this.isWorldUnavailable()) {
         this.wasFallFlying = false
         return
      }

      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return
      val fallFlying = player.isFallFlying

      if (this.isAlwaysMode()) {
         if (this.wasFallFlying && !fallFlying && this.swapOnLandSetting.value) {
            if (this.getWornChestStack().`is`(Items.ELYTRA)) {
               this.swapToChestplate()
            }
         }

         if (!this.wasFallFlying && fallFlying && this.startFireWorkSetting.value) {
            this.fireRocket()
         }
      }

      this.wasFallFlying = fallFlying
   }

   /** Кнопки работают вне экранов: SwitchButton — свап, FireWorkButton — ракета в полёте. */
   @EventHandler
   private fun onInputPress(event: InputPressEvent) {
      if (ClientAccess.collectiveMinecraft.gui.screen() != null) {
         return
      }

      if (this.matchesKeybind(event, this.switchButtonSetting.value)) {
         this.toggleSwap()
         return
      }

      if (this.matchesKeybind(event, this.fireWorkButtonSetting.value) &&
         ClientAccess.collectiveMinecraft.player?.isFallFlying == true) {
         this.fireRocket()
      }
   }

   private fun matchesKeybind(event: InputPressEvent, keybind: Keybind): Boolean {
      if (keybind.isUnbound()) {
         return false
      }

      return event.code == keybind.code() && event.isMouse == keybind.mouse()
   }

   /** На теле элита — надеть нагрудник, иначе — вернуть элиту. */
   private fun toggleSwap() {
      if (this.isWorldUnavailable()) {
         return
      }

      if (this.getWornChestStack().`is`(Items.ELYTRA)) {
         this.swapToChestplate()
      } else {
         this.swapToElytra()
      }
   }

   private fun getWornChestStack(): ItemStack {
      val player = ClientAccess.collectiveMinecraft.player ?: return ItemStack.EMPTY
      return player.inventory.getItem(ArmorSlot.CHESTPLATE.getInventoryIndex())
   }

   private fun swapToChestplate() {
      var chestplateHotbarSlot = -1
      for (target in CHESTPLATE_TARGETS) {
         chestplateHotbarSlot = InventoryUtils.findHotbarSlot(target)
         if (chestplateHotbarSlot != -1) {
            break
         }
      }

      if (chestplateHotbarSlot != -1) {
         this.armorSwapWithHotbar(chestplateHotbarSlot)
         this.printMessage("Свап на нагрудник.")
         return
      }

      var chestplateInventorySlot = -1
      for (target in CHESTPLATE_TARGETS) {
         chestplateInventorySlot = this.findInMainInventory(target)
         if (chestplateInventorySlot != -1) {
            break
         }
      }

      if (chestplateInventorySlot == -1) {
         this.printMessage("§cНет нагрудника в инвентаре.")
         return
      }

      val hotbarSlot = this.bringInventorySlotToHotbar(chestplateInventorySlot)
      if (hotbarSlot != -1) {
         this.armorSwapWithHotbar(hotbarSlot)
         this.printMessage("Свап на нагрудник.")
      }
   }

   private fun swapToElytra() {
      val elytraHotbarSlot = InventoryUtils.findHotbarSlot(Items.ELYTRA)
      if (elytraHotbarSlot != -1) {
         this.armorSwapWithHotbar(elytraHotbarSlot)
         this.printMessage("Свап на элиту.")
         return
      }

      val elytraInventorySlot = this.findInMainInventory(Items.ELYTRA)
      if (elytraInventorySlot == -1) {
         this.printMessage("§cНет элиты в инвентаре.")
         return
      }

      val hotbarSlot = this.bringInventorySlotToHotbar(elytraInventorySlot)
      if (hotbarSlot != -1) {
         this.armorSwapWithHotbar(hotbarSlot)
         this.printMessage("Свап на элиту.")
      }
   }

   /** Поиск по основному инвентарю (слоты 9–35, хотбар исключён). */
   private fun findInMainInventory(target: Item): Int {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return -1
      for (slot in 9 until 36) {
         if (player.inventory.getItem(slot).`is`(target)) {
            return slot
         }
      }

      return -1
   }

   /** SWAP-клик: слот брони (контейнерный 6) ↔ ячейка хотбара. */
   private fun armorSwapWithHotbar(hotbarSlot: Int) {
      if (hotbarSlot < 0 || hotbarSlot >= 9) {
         return
      }

      InventoryUtils.clickSlot(ArmorSlot.CHESTPLATE.getContainerSlot(), hotbarSlot, ContainerInput.SWAP)
   }

   /**
    * Перемещает предмет из основного инвентаря в выбранный слот хотбара
    * (HotBarSwapMode: Swap — один SWAP-клик, Pickup — классические три клика),
    * возвращает номер ячейки хотбара.
    */
   private fun bringInventorySlotToHotbar(inventorySlot: Int): Int {
      val selectedSlot = InventoryUtils.getSelectedSlot()
      val menuSlot = InventoryUtils.toMenuSlot(inventorySlot)

      if (this.hotBarSwapModeSetting.value == HotBarSwapModeChoice.SWAP) {
         InventoryUtils.swapIntoSelectedSlot(inventorySlot)
      } else {
         InventoryUtils.clickSlot(menuSlot, 0, ContainerInput.PICKUP)
         InventoryUtils.clickSlot(InventoryUtils.toMenuSlot(selectedSlot), 0, ContainerInput.PICKUP)
         InventoryUtils.clickSlot(menuSlot, 0, ContainerInput.PICKUP)
      }

      return selectedSlot
   }

   /**
    * Выстрел фейерверком (порт R()): ракета подносится к выбранному слоту свап-кликом,
    * use-item пакет + свинг; FireWorkMode Silent возвращает ракету на место.
    */
   private fun fireRocket() {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return

      if (player.offhandItem.`is`(Items.FIREWORK_ROCKET)) {
         PacketSender.sendUseItem(InteractionHand.OFF_HAND)
         PacketSender.sendSwing(InteractionHand.OFF_HAND)
         return
      }

      if (player.mainHandItem.`is`(Items.FIREWORK_ROCKET)) {
         PacketSender.sendUseItem(InteractionHand.MAIN_HAND)
         PacketSender.sendSwing(InteractionHand.MAIN_HAND)
         return
      }

      val rocketHotbarSlot = InventoryUtils.findHotbarSlot(Items.FIREWORK_ROCKET)
      if (rocketHotbarSlot != -1) {
         this.useRocketFromSlot(rocketHotbarSlot, true)
         return
      }

      val rocketInventorySlot = this.findInMainInventory(Items.FIREWORK_ROCKET)
      if (rocketInventorySlot == -1) {
         this.printMessage("§cНет фейерверков.")
         return
      }

      val hotbarSlot = this.bringInventorySlotToHotbar(rocketInventorySlot)
      if (hotbarSlot != -1) {
         this.useRocketFromSlot(hotbarSlot, false)
      }
   }

   private fun useRocketFromSlot(rocketHotbarSlot: Int, restoreOriginal: Boolean) {
      val selectedSlot = InventoryUtils.getSelectedSlot()

      if (rocketHotbarSlot != selectedSlot) {
         InventoryUtils.swapWithHotbarSlot(InventoryUtils.toMenuSlot(rocketHotbarSlot), selectedSlot)
      }

      PacketSender.sendUseItem(InteractionHand.MAIN_HAND)
      PacketSender.sendSwing(InteractionHand.MAIN_HAND)

      if (this.fireWorkModeSetting.value == FireWorkModeChoice.SILENT &&
         restoreOriginal &&
         rocketHotbarSlot != selectedSlot) {
         InventoryUtils.swapWithHotbarSlot(InventoryUtils.toMenuSlot(rocketHotbarSlot), selectedSlot)
      }
   }

   /** SwapMode Silent — без сообщений в чат. */
   private fun printMessage(text: String) {
      if (this.swapModeSetting.value == SwapModeChoice.SILENT) {
         return
      }

      val message: MutableComponent = Component.literal(MESSAGE_PREFIX + text)
      ChatUtils.printChat(message, ChatUtils.createSignature(-2), ChatUtils.MessagePriority.LOW)
   }
}

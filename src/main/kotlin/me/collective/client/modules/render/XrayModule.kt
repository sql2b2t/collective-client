package me.collective.client.modules.render

import me.collective.client.ClientAccess
import me.collective.client.render.IrisShaderBridge
import me.collective.client.render.GameOptionsOverrides
import me.collective.client.utils.world.WorldUtils
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.RegistrySetSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.ListFilterMode

import net.minecraft.client.OptionInstance
import net.minecraft.client.Options
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState

class XrayModule : Module("Xray", "Wallhack on ores.", ModuleCategory.RENDER) {
   private val blocksSetting: Setting<Set<Block>>
   private val selectionSetting: Setting<ListFilterMode>
   @JvmField
   val alphaSetting: Setting<Int>
   private val bypassSetting: Setting<Boolean>
   private var previousGamma: Double = 0.0

   override fun onEnable() {
      val previousGammaOption: OptionInstance<Double> = ClientAccess.collectiveMinecraft.options.gamma()
      this.previousGamma = previousGammaOption.get()!!
      val gammaOption: OptionInstance<Double> = ClientAccess.collectiveMinecraft.options.gamma()
      GameOptionsOverrides.forceSetOptionValue(gammaOption, 1000.0)
   }

   override fun onToggle() {
      WorldUtils.reloadRenderer()
   }

   override fun onDisable() {
      val options: Options = ClientAccess.collectiveMinecraft.options
      val gammaOption: OptionInstance<Double> = options.gamma()
      val savedGamma: Double = this.previousGamma
      GameOptionsOverrides.forceSetOptionValue(gammaOption, savedGamma)
   }

   private fun reloadRendererIfEnabled() {
      if (this.isToggled()) {
         WorldUtils.reloadRenderer()
      }
   }

   // Решение только по BlockState: вызывается из worker-потоков компиляции чанков,
   // поэтому здесь (и в решениях вокруг) нельзя читать живой level.
   fun shouldRenderBlock(
      state: BlockState
   ): Boolean {
      return this.shouldRenderBlock(state, true)
   }

   fun shouldRenderBlock(
      state: BlockState, exposed: Boolean
   ): Boolean {
      if (this.isWorldUnavailable()) {
         return false
      }
      val selectionMode: ListFilterMode = this.selectionSetting.getValue()!!
      if (selectionMode.isAny()) {
         return false
      }

      val mode: ListFilterMode = this.selectionSetting.getValue()!!
      val blocks: Setting<Set<Block>> = this.blocksSetting
      if (!ListFilterMode.matches(mode, state.block, blocks)) {
         return false
      }
      val bypass: Setting<Boolean> = this.bypassSetting
      if (!bypass.getValue()!!) {
         return true
      }

      // Bypass: блок виден только если он «открыт» (не окружён окклюдирующими
      // соседями). Соседи нам не доступны без чтения мира — вызывающая сторона
      // передаёт готовый признак видимости (например, по соседнему BlockState).
      return exposed
   }

   fun shouldHideUnselectedBlocks(): Boolean {
      val alpha: Setting<Int> = this.alphaSetting
      if (!alpha.isAtMaximum()) {
         if (IrisShaderBridge.isHandRendererAvailable() && IrisShaderBridge.isShaderPackPresent()) {
            return true
         }
      }

      return this.alphaSetting.getValue()!! == 0
   }

   init {
      this.blocksSetting = this.add(RegistrySetSetting<Block>("Blocks", BuiltInRegistries.BLOCK,
         Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
         Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
         Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
         Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE, Blocks.NETHER_QUARTZ_ORE, Blocks.ANCIENT_DEBRIS
      ))
      this.selectionSetting = this.add(EnumSetting<ListFilterMode>("Selection", ListFilterMode.WHITELIST))
      this.alphaSetting = this.add(NumberSetting<Int>("Alpha", 0, 0, 100).withUnit("%"))
      this.bypassSetting = this.add(BooleanSetting("Bypass", false))
      instance = this
      this.selectionSetting.setChangeCallback(this::reloadRendererIfEnabled)
      this.blocksSetting.setChangeCallback(this::reloadRendererIfEnabled)
      this.bypassSetting.setChangeCallback(this::reloadRendererIfEnabled)
      this.alphaSetting.setChangeCallback(this::reloadRendererIfEnabled)
   }

   companion object {
      private var instance: XrayModule? = null

      @JvmStatic
      fun getInstance(): XrayModule? {
         return instance
      }
   }
}

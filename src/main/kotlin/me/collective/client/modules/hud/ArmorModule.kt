package me.collective.client.modules.hud

import me.collective.client.utils.math.MathConstants
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.HudRenderEvent
import me.collective.client.events.EventHandler
import me.collective.client.render.RenderTargetUtils
import me.collective.client.render.GuiDrawUtils
import me.collective.client.utils.combat.DamageUtils
import me.collective.client.render.RenderUtils
import me.collective.client.render.FontRenderer
import me.collective.client.render.PostProcessShaderPass
import me.collective.client.utils.inventory.EquipmentUtils
import me.collective.client.render.ResizableHudWidgetRenderer
import me.collective.client.modules.HudWidgetModule
import me.collective.client.modules.client.ColorsModule
import me.collective.client.modules.client.FontsModule
import me.collective.client.modules.client.HUDModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.ColorsSchemeChoice
import me.collective.client.utils.FloatAnimation

import com.mojang.blaze3d.vertex.PoseStack
import java.awt.Color
import java.util.Arrays
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack

class ArmorModule : HudWidgetModule("Armor") {
   private val barSetting: Setting<Boolean>
   private val durabilitySetting: Setting<Boolean>
   private val percentageSetting: Setting<Boolean>
   private val barColorSetting: Setting<Boolean>
   private val animationSetting: Setting<Boolean>
   private val durabilityAnimations = arrayOf(
      FloatAnimation(), FloatAnimation(), FloatAnimation(), FloatAnimation()
   )
   private val durabilityInitialized = BooleanArray(4)

   private fun getArmorBottomOffset(): Int {
      val playerForSubmersion = ClientAccess.collectiveMinecraft.player ?: return 55
      if (!playerForSubmersion.isUnderWater &&
         playerForSubmersion.airSupply != playerForSubmersion.maxAirSupply) {
         if (!playerForSubmersion.isCreative) {
            return 65
         }
      }

      val playerForPassenger = ClientAccess.collectiveMinecraft.player ?: return 55
      if (playerForPassenger.isPassenger) {
         val playerForVehicle = ClientAccess.collectiveMinecraft.player ?: return 55
         val vehicle = playerForVehicle.vehicle
         if (vehicle is LivingEntity) {
            val mount = vehicle
            val mountOffsetBase: Double = MathConstants.FORTY_FIVE_DEGREES.toDouble()
            var adjustedMaximumHealth = mount.maxHealth

            adjustedMaximumHealth -= 1.0f

            val healthRows: Double = (adjustedMaximumHealth / 20.0f).toDouble()
            val mountHealthRows: Double = Math.ceil(healthRows)

            return (mountOffsetBase + mountHealthRows * 10.0).toInt()
         }
      }

      val playerForCreativeOffset = ClientAccess.collectiveMinecraft.player ?: return 55
      val creativeOffset: Int
      if (playerForCreativeOffset.isCreative) {
         creativeOffset = 17
      } else {
         creativeOffset = 0
      }

      return 55 - creativeOffset
   }

   override fun calculateDimensions(): FloatArray {
      val dimensions = FloatArray(2)
      dimensions[0] = 0.0f
      dimensions[1] = 0.0f
      return dimensions
   }

   @EventHandler(priority = 1)
   private fun onHudRender(event: HudRenderEvent) {
      if (ClientAccess.collectiveMinecraft.player == null) {
         Arrays.fill(this.durabilityInitialized, false)
         return
      }
      if (hudModule.isToggled() || this.isInHudEditor()) {
         val screenWidth = event.guiGraphics?.guiWidth() ?: ClientAccess.collectiveMinecraft.window.guiScaledWidth
         val screenHeight = event.guiGraphics?.guiHeight() ?: ClientAccess.collectiveMinecraft.window.guiScaledHeight

         var xOffset = 15
         RenderUtils.clearColor(
            PostProcessShaderPass.POST_TARGET
         )
         RenderUtils.clearDepth(
            PostProcessShaderPass.POST_TARGET
         )
         RenderTargetUtils.overrideRenderTargets(
            PostProcessShaderPass.POST_TARGET
         )
         val playerForArmor = ClientAccess.collectiveMinecraft.player ?: return
         val armorStacks = EquipmentUtils.getArmorStacks(playerForArmor)

         for (armorIndex in 3 downTo 0) {
            val stack = armorStacks[armorIndex]
            if (!stack.isEmpty) {
               val bottomOffset = this.getArmorBottomOffset()
               val itemPoses = event.poseStack!!

               val itemX = screenWidth / 2 + xOffset
               val itemY = screenHeight - bottomOffset
               this.renderArmorItem(itemPoses, stack, itemX, itemY)
               if (stack.isDamageableItem) {
                  val showDurability = this.durabilitySetting.getValue()
                  if (showDurability) {
                     val durabilityPercent = DamageUtils.getDurabilityPercent(stack)
                     var durabilityText = String.format(
                        "%d%s", this.getDisplayedDurability(armorIndex, durabilityPercent), "%"
                     )
                     val percentageTextScale: Float
                     if (FontsModule.INSTANCE!!.isToggled()) {
                        percentageTextScale = 0.7f
                     } else {
                        percentageTextScale = 0.65f
                     }

                     var textScale = percentageTextScale
                     val showPercentageForText = this.percentageSetting.getValue()
                     if (!showPercentageForText) {
                        durabilityText = durabilityText.substring(0, durabilityText.length - 1)

                        textScale = 1.0f
                     }

                     val colorScheme = colorsModule.schemeSetting.getValue()
                     val itemBarColor = Color(stack.barColor, false)
                     val durabilityColor = colorScheme.transform(itemBarColor)
                     val itemTop = (screenHeight - bottomOffset).toFloat()

                     val textYBeforePercentageOffset = itemTop - 3.8f
                     val showPercentageForPosition = this.percentageSetting.getValue()
                     val plainTextYOffset: Int
                     if (showPercentageForPosition) {
                        plainTextYOffset = 0
                     } else {
                        plainTextYOffset = 2
                     }

                     val textY = textYBeforePercentageOffset - plainTextYOffset
                     val textRenderer = FontRenderer.INSTANCE
                     val textPoses = event.poseStack!!

                     val centeredItemX = screenWidth / 2.0f + xOffset

                     val textCenterX = centeredItemX + 9.0f
                     val textWidth = FontRenderer.INSTANCE
                        .getWidth(durabilityText)

                     val textX = textCenterX - textWidth * 0.5f * textScale - textScale
                     val useBarColor = this.barColorSetting.getValue()
                     textRenderer.drawWithShadow(
                        textPoses,
                        durabilityText,
                        textX,
                        textY,
                        textScale,
                        if (useBarColor) durabilityColor else this.getHudColor(textY)
                     )
                  }
               }

               xOffset += 18
            } else {
               this.durabilityInitialized[armorIndex] = false
            }
         }

         RenderUtils.INSTANCE
            .flushEntities()
         RenderTargetUtils.restoreRenderTargets()
         RenderUtils.clearDepth(
            PostProcessShaderPass.POST_TARGET
         )
         RenderUtils.blitToMain(
            PostProcessShaderPass.POST_TARGET
         )
      }
   }

   private fun renderArmorItem(
      poses: PoseStack, stack: ItemStack, x: Int, y: Int
   ) {
      GuiDrawUtils.drawItem(poses, stack, x.toFloat(), y.toFloat(), 0.0f, false)
      val showBar = this.barSetting.getValue()
      if (showBar) {
         GuiDrawUtils.drawDurabilityBar(poses, stack, x.toFloat(), y.toFloat())
      }
   }

   private fun getDisplayedDurability(slot: Int, percentage: Int): Int {
      val animation = this.durabilityAnimations[slot]
      if (!this.animationSetting.getValue() || !this.durabilityInitialized[slot]) {
         animation.setValue(percentage.toFloat())
         this.durabilityInitialized[slot] = true
         return percentage
      }
      animation.setTarget(percentage.toFloat(), 150L)
      return Math.round(animation.getValue())
   }

   init {
      this.barSetting = this.add(BooleanSetting("Bar", true))
      this.durabilitySetting = this.add(BooleanSetting("Durability", true).markVisibilityDependency())
      this.animationSetting = this.add(BooleanSetting("Animation", false).dependsOn(this.durabilitySetting))
      this.percentageSetting = this.add(BooleanSetting("Percentage", true).dependsOn(this.durabilitySetting))
      this.barColorSetting = this.add(BooleanSetting("BarColor", true).dependsOn(this.durabilitySetting))
      this.setRenderer(object : ResizableHudWidgetRenderer(this@ArmorModule) {
         override fun isEnabled(): Boolean {
            return false
         }
      })
   }

   companion object {
      private val hudModule: HUDModule get() = ClientContext.getModule(HUDModule::class.java)
      private val colorsModule: ColorsModule get() = ClientContext.getModule(ColorsModule::class.java)
   }
}

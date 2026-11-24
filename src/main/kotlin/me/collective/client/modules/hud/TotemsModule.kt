package me.collective.client.modules.hud

import me.collective.client.ClientAccess
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.modules.HudWidgetModule
import me.collective.client.render.FontRenderer
import me.collective.client.render.GuiDrawUtils
import me.collective.client.render.ResizableHudWidgetRenderer
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.inventory.InventoryUtils

import com.mojang.blaze3d.vertex.PoseStack
import java.awt.Color
import java.util.function.Predicate
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

class TotemsModule : HudWidgetModule("Totems") {
   private var totemStack: ItemStack? = null
   private val whiteSetting: Setting<Boolean>

   init {
      this.whiteSetting = this.add(BooleanSetting("White", false))
      this.setRenderer(ResizableHudWidgetRenderer(this))
   }

   override fun calculateDimensions(): FloatArray {
      val dimensions = FloatArray(2)
      dimensions[0] = 16.0F
      dimensions[1] = 16.0F
      return dimensions
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(ignoredEvent: LocalPlayerTickEvent) {
      val localPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return
      if (!localPlayer.isSpectator()) {
         this.totemStack = ItemStack(
            Items.TOTEM_OF_UNDYING,
            InventoryUtils.countItems(Predicate<ItemStack> { candidateStack ->
               val totemItemForMatch = Items.TOTEM_OF_UNDYING
               candidateStack.`is`(totemItemForMatch)
            })
         )
      }
   }

   override fun renderContent(poses: PoseStack) {
      if (this.totemStack != null) {
         val stackForEmptyCheck = this.totemStack
         if (stackForEmptyCheck!!.count != 0) {
            val stackToRender = this.totemStack

            GuiDrawUtils.drawItem(
               poses, stackToRender!!, 0.0F, 0.0F, -150.0F, true
            )
            val stackForOverlayCheck = this.totemStack
            if (stackForOverlayCheck!!.count > 1) {
               val totemCount: Int = this.totemStack!!.count
               val countText: String = totemCount.toString()
               poses.pushPose()

               val textDepth: Double = 0.1
               val textTranslateY: Double = 0.0
               val textTranslateX: Double = 0.0
               poses.translate(textTranslateX, textTranslateY, textDepth)
               val font = FontRenderer.INSTANCE

               val textX: Float = 17.0F
                  - FontRenderer.INSTANCE.getWidth(countText)
               val textY: Float = 9.0F
               val useWhite: Boolean = this.whiteSetting.value!!
               val textColor: Color = if (useWhite) {
                  Color.white
               } else {

                  this.getHudColor(9.0F)
               }

               font.drawWithShadow(poses, countText, textX, textY, textColor)
               poses.popPose()
            }

            return
         }
      }
   }
}

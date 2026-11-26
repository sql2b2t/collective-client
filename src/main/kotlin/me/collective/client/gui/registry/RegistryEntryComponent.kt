package me.collective.client.gui.registry

import net.minecraft.world.entity.EntityTypes
import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.gui.ScissorStack
import me.collective.client.gui.components.ButtonComponent
import me.collective.client.gui.components.ComponentFlag
import me.collective.client.gui.panels.Panel
import me.collective.client.render.FontRenderer
import me.collective.client.render.GuiDrawUtils
import me.collective.client.utils.ClampedInterpolatedFloat
import me.collective.client.utils.FloatAnimation
import net.minecraft.core.Holder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.SpawnEggItem
import net.minecraft.world.item.alchemy.Potion
import net.minecraft.world.item.alchemy.PotionContents
import java.util.Locale
import kotlin.math.max

class RegistryEntryComponent<E : Any>(
    private val screen: RegistrySetScreen<E>,
    panel: Panel,
    identifier: Identifier,
    private val entry: E,
    private val availableEntry: Boolean
) : ButtonComponent(panel, createLabel(identifier)) {

    val visibilityAnimation: ClampedInterpolatedFloat = ClampedInterpolatedFloat(
        { this.uiModule.animSpeedSetting.value * 2.0f },
        true
    )
    protected val hoverScrollAnimation: FloatAnimation = FloatAnimation()
    private val iconStack: ItemStack = createIconStack(identifier, entry)
    private val iconItem: Item = this.iconStack.item
    private var resetVisibilityAnimation: Boolean = true
    private var visible: Boolean = false

    init {
        this.addFlags(ComponentFlag.SILENT_HOVER)
    }

    override fun update(mouseX: Double, mouseY: Double) {
        this.visible = this.screen.registrySetting.value.contains(this.entry)
        if (this.availableEntry) {
            this.visible = !this.visible
            val searchText = this.screen.searchComponent.text
            if (searchText.isNotEmpty() && !this.getLabel().lowercase(Locale.ROOT).contains(searchText.lowercase(Locale.ROOT))) {
                this.visible = false
                this.resetVisibilityAnimation = true
                this.visibilityAnimation.setValue(false)
                return
            }
        }

        if (this.resetVisibilityAnimation) {
            this.visibilityAnimation.setValue(this.visible)
            this.resetVisibilityAnimation = false
        } else {
            this.visibilityAnimation.setTarget(this.visible)
            this.visibilityAnimation.updateAndGet()
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int) {
        super.mouseClicked(mouseX, mouseY, button)
        if (this.isMouseOver(mouseX, mouseY) && button == 0) {
            if (this.availableEntry) {
                this.screen.registrySetting.addEntry(this.entry)
            } else {
                this.screen.registrySetting.removeEntry(this.entry)
            }
        }
    }

    override fun render(poseStack: PoseStack, mouseX: Double, mouseY: Double) {
        if (this.visible) {
            var iconOffset = 0.0f
            if (this.iconItem != Items.AIR) {
                GuiDrawUtils.drawItem(
                    poseStack,
                    this.iconStack,
                    (this.panel.x + 3).toFloat(),
                    (this.panel.y + this.yOffset + this.textYOffset - 2.0f),
                    0.0f
                )
                iconOffset = 20.0f
            }

            poseStack.pushPose()
            poseStack.translate(iconOffset, 1.5f, 0.0f)
            val hovered = this.isMouseOver(mouseX, mouseY)
            this.hoverScrollAnimation.setTarget(hovered, 500L)
            this.renderClippedLabel(poseStack, iconOffset + 3.0f, mouseX, mouseY)
            poseStack.popPose()
        }
    }

    override val height: Int
        get() = max((16.0f * this.visibilityAnimation.value).toInt(), if (this.visible) 1 else 0)

    private fun renderClippedLabel(poseStack: PoseStack, textOffset: Float, mouseX: Double, mouseY: Double) {
        val textWidth = FontRenderer.INSTANCE.getWidth(this.getLabel()) + textOffset
        val scrollProgress = this.hoverScrollAnimation.value
        val rightInset = if (scrollProgress > 0.0f) 0 else 2
        if (textWidth.toDouble() >= this.panel.width.toDouble() && scrollProgress > 0.0f) {
            GuiDrawUtils.flush()
            ScissorStack.pushIntersection(
                (this.panel.x + textOffset).toInt(),
                this.panel.y + this.yOffset,
                (this.panel.width - textOffset - rightInset).toInt(),
                this.height
            )
            poseStack.translate(-(textWidth - this.panel.width) * scrollProgress, 0.0f, 0.0f)
            super.render(poseStack, mouseX, mouseY)
            GuiDrawUtils.flush()
            ScissorStack.pop()
        } else {
            super.render(poseStack, mouseX, mouseY)
        }
    }

    companion object {
        private fun createIconStack(identifier: Identifier, entry: Any): ItemStack {
            if (entry is EntityType<*>) {
                if (entry == EntityTypes.ITEM) {
                    return Items.STICK.defaultInstance
                }
                val spawnEgg = SpawnEggItem.byId(entry)
                if (spawnEgg.isPresent) {
                    return spawnEgg.get().value().defaultInstance
                }
            }

            if (entry is Potion) {
                val potionHolder = BuiltInRegistries.POTION.wrapAsHolder(entry)
                return PotionContents.createItemStack(Items.SPLASH_POTION, potionHolder)
            }

            return BuiltInRegistries.ITEM.getValue(identifier)?.defaultInstance ?: Items.AIR.defaultInstance
        }

        private fun createLabel(identifier: Identifier): String {
            return FontRenderer.capitalizeWords(
                identifier.toShortLanguageKey().replace("_", " ")
            )
        }
    }
}

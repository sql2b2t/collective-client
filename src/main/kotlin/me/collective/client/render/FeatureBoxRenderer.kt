package me.collective.client.render

import com.mojang.blaze3d.vertex.PoseStack
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import java.awt.Color
import java.util.Objects
import me.collective.client.ClientAccess
import me.collective.client.NamedFeature
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.WorldRenderEvent
import me.collective.client.managers.AbstractRegistry
import me.collective.client.settings.ValueProvider
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB

class FeatureBoxRenderer : AbstractRegistry<FeatureBoxRenderer.RenderConfiguration>(ArrayList()), ClientAccess {
    private val boxesByConfiguration: MutableMap<RenderConfiguration, MutableSet<FadingBoxRenderState>> = HashMap()

    init {
        ClientAccess.eventBus.subscribe(this)
    }

    override fun register(entry: Any): Boolean {
        return register(entry as RenderConfiguration)
    }

    override fun unregister(entry: Any): Boolean {
        return unregister(entry as RenderConfiguration)
    }

    fun addBox(feature: NamedFeature, position: BlockPos?) {
        if (position != null) {
            addBox(feature, AABB(position))
        }
    }

    fun register(configuration: RenderConfiguration): Boolean {
        synchronized(boxesByConfiguration) {
            boxesByConfiguration.putIfAbsent(configuration, ObjectLinkedOpenHashSet())
        }
        return entries.add(configuration)
    }

    @EventHandler
    private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
        synchronized(boxesByConfiguration) {
            for ((configuration, boxes) in boxesByConfiguration) {
                boxes.removeIf { box ->
                    val fadeDuration = configuration.getFadeDuration()
                    box.getFadeFactor(fadeDuration) == 0.0f
                }
            }
        }
    }

    fun addBox(feature: NamedFeature, bounds: AABB?) {
        if (bounds != null) {
            val configuration = findFirstOrNull { candidate -> candidate.feature == feature }
                ?: throw NoSuchElementException()
            synchronized(boxesByConfiguration) {
                val boxes = boxesByConfiguration[configuration]!!
                if (configuration.shouldRetainPreviousBoxes()) {
                    for (box in boxes) {
                        if (bounds == box.bounds) {
                            box.refreshFade()
                            return
                        }
                    }
                } else {
                    boxes.clear()
                }
                val newBox = FadingBoxRenderState()
                newBox.bounds = bounds
                newBox.refreshFade()
                boxes.add(newBox)
            }
        }
    }

    fun unregister(configuration: RenderConfiguration): Boolean {
        synchronized(boxesByConfiguration) {
            boxesByConfiguration.remove(configuration)
        }
        return entries.remove(configuration)
    }

    @EventHandler
    private fun onRenderWorld(event: WorldRenderEvent.Main) {
        synchronized(boxesByConfiguration) {
            for ((configuration, boxes) in boxesByConfiguration) {
                for (box in boxes) {
                    box.setLineWidth(configuration.getLineWidth())
                    val poseStack: PoseStack = event.poseStack!!
                    val fillColor = configuration.getFillColor()
                    val outlineColor = configuration.getOutlineColor()
                    val fadeDuration = configuration.getFadeDuration()
                    val fadeEnabled = configuration.isFadeEnabled()
                    box.render(poseStack, fillColor, outlineColor, fadeDuration, fadeEnabled)
                }
            }
        }
    }

    @JvmRecord
    data class RenderConfiguration(
        val feature: NamedFeature,
        val fillColorSupplier: ValueProvider<Color>,
        val outlineColorSupplier: ValueProvider<Color>,
        val lineWidthSupplier: ValueProvider<Float>,
        val fadeDurationSupplier: ValueProvider<Float>,
        val retainPreviousBoxesSupplier: ValueProvider<Boolean>,
        val fadeEnabledSupplier: ValueProvider<Boolean>,
        val fadeTimeScale: Int
    ) {
        fun getLineWidth(): Float = lineWidthSupplier.getValue()

        fun isFadeEnabled(): Boolean = fadeEnabledSupplier.getValue()

        fun getOutlineColor(): Color = outlineColorSupplier.getValue()

        fun getFadeDuration(): Float {
            return if (fadeEnabledSupplier.getValue()) {
                fadeDurationSupplier.getValue() * fadeTimeScale
            } else {
                0.0f
            }
        }

        fun getFillColor(): Color = fillColorSupplier.getValue()

        fun shouldRetainPreviousBoxes(): Boolean = retainPreviousBoxesSupplier.getValue()

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other == null || javaClass != other.javaClass) {
                return false
            }
            val configuration = other as RenderConfiguration
            return feature == configuration.feature
        }

        override fun hashCode(): Int {
            return Objects.hash(feature)
        }
    }

    open class Builder(
        private val feature: NamedFeature,
        private val fillColorSupplier: ValueProvider<Color>,
        private val outlineColorSupplier: ValueProvider<Color>
    ) {
        private var lineWidthSupplier: ValueProvider<Float> = ValueProvider { 1.0f }
        private var fadeDurationSupplier: ValueProvider<Float> = ValueProvider { 1.0f }
        private var retainPreviousBoxesSupplier: ValueProvider<Boolean> = ValueProvider { true }
        private var fadeEnabledSupplier: ValueProvider<Boolean> = ValueProvider { true }
        private var fadeTimeScale: Int = 1000

        open fun withFadeEnabled(supplier: ValueProvider<Boolean>): Builder {
            this.fadeEnabledSupplier = supplier
            return this
        }

        open fun build(): RenderConfiguration {
            return RenderConfiguration(
                this.feature,
                this.fillColorSupplier,
                this.outlineColorSupplier,
                this.lineWidthSupplier,
                this.fadeDurationSupplier,
                this.retainPreviousBoxesSupplier,
                this.fadeEnabledSupplier,
                this.fadeTimeScale
            )
        }

        open fun withLineWidth(supplier: ValueProvider<Float>): Builder {
            this.lineWidthSupplier = supplier
            return this
        }

        open fun withFadeDuration(supplier: ValueProvider<Float>): Builder {
            this.fadeDurationSupplier = supplier
            return this
        }

        open fun withRetainPreviousBoxes(supplier: ValueProvider<Boolean>): Builder {
            this.retainPreviousBoxesSupplier = supplier
            return this
        }

        open fun withFadeTimeScale(fadeTimeScale: Int): Builder {
            this.fadeTimeScale = fadeTimeScale
            return this
        }
    }
}

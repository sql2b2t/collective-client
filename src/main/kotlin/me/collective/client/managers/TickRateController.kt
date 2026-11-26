package me.collective.client.managers

import me.collective.client.ClientAccess
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.ModuleToggleEvent
import me.collective.client.modules.Module
import net.minecraft.util.Mth
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

open class TickRateController : ClientAccess {
    private val moduleReleaseStates = ConcurrentHashMap<Module, Boolean>()
    private var multiplierValue: Float = 1.0f

    init {
        ClientAccess.eventBus.subscribe(this)
    }

    val multiplier: Float
        @JvmName("getMultiplierProp")
        get() = multiplierValue

    fun getMultiplier(): Float = multiplierValue

    @EventHandler
    private fun onModuleToggle(event: ModuleToggleEvent) {
        val module = event.module
        if (!module.isToggled) {
            releaseMultiplier(module)
        }
    }

    fun requestMultiplier(module: Module, requestedMultiplier: Float) {
        moduleReleaseStates.compute(module) { _, _ -> false }
        multiplierValue = requestedMultiplier
    }

    fun releaseMultiplier(module: Module) {
        moduleReleaseStates.compute(module) { _, previousReleased ->
            if (previousReleased == false) {
                multiplierValue = 1.0f
            }
            true
        }
    }

    class PlayerTicks : TickRateController() {
        private var fractionalTicks: Float = 0.0f

        @EventHandler(priority = -200)
        private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
            val rate = getMultiplier()
            if (rate <= 1.0f) {
                return
            }

            fractionalTicks += rate - rate.toInt()
            val extraTickCount = floor(rate.toDouble()) + floor(fractionalTicks.toDouble()) - 1.0
            var extraTicks = 0
            while (extraTicks < extraTickCount) {
                PlayerTickExecutor.runExtraTick()
                extraTicks++
            }

            if (fractionalTicks >= 1.0f) {
                fractionalTicks = Mth.clamp(fractionalTicks - 1.0f, 0.0f, 1.0f)
            }
        }
    }
}

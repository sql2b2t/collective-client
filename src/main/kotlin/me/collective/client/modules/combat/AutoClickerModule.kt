package me.collective.client.modules.combat

import com.mojang.blaze3d.platform.InputConstants
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.WorldRenderEvent
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.exploit.MultiTaskModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.math.MathUtils
import me.collective.mixin.MinecraftAccessor
import net.minecraft.client.KeyMapping
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import java.util.ArrayList

class AutoClickerModule : Module("AutoClicker", "Spam mouse buttons.", ModuleCategory.COMBAT) {
    private val modeSetting: Setting<ModeChoice> = add(EnumSetting("Mode", ModeChoice.LEFT))
    private val cpsSetting: Setting<Float> = add(NumberSetting("CPS", 8.0f, 1.0f, 20.0f))
    private val delaySetting: Setting<Float> = add(
        NumberSetting("Delay", 1.0f, 1.0f, 3.0f)
            .withUnit("x")
            .setBoundaryLabel("Off", SettingLabelCondition.MIN)
    )
    private val friendProtectSetting: Setting<Boolean> = add(BooleanSetting("FriendProtect", true))
    private val clickTimer = ElapsedTimer()
    private var delayMultiplier = 1.0f
    private val recentClickTimes: MutableList<Long> = ArrayList()

    override fun getInfo(): String {
        return recentClickTimes.size.toString()
    }

    @EventHandler
    private fun onWorldRender(event: WorldRenderEvent.BeforeBufferSource) {
        val now = System.currentTimeMillis()
        recentClickTimes.removeIf { timestamp -> now - timestamp >= 1000L }

        val clicksPerSecond = cpsSetting.value
        var clickIntervalMillis = (1000.0f / clicksPerSecond).toLong()

        if (!delaySetting.isAtMinimum) {
            clickIntervalMillis = (clickIntervalMillis * delayMultiplier).toLong()
        }

        if (clickTimer.hasElapsed(clickIntervalMillis)) {
            performClick()
            recentClickTimes.add(System.currentTimeMillis())
            clickTimer.reset()

            val upperBound = delaySetting.value
            delayMultiplier = MathUtils.randomFloat(1.0f, upperBound)
        }
    }

    private fun performClick() {
        val multiTask = multiTaskModule
        val player = ClientAccess.collectiveMinecraft.player
        if (multiTask != null && multiTask.isToggled) {
            if ((player != null && player.isUsingItem) || ClientAccess.collectiveMinecraft.screen != null) {
                modeSetting.value.clickDirectly()
                return
            }
        }

        val key = modeSetting.value.getKeyMapping().key
        KeyMapping.click(key)
    }

    fun shouldProtectFriend(target: Entity): Boolean {
        if (isToggled && friendProtectSetting.value && target is Player) {
            if (ClientContext.socialRegistry.isFriendPlayer(target)) {
                return true
            }
        }
        return false
    }

    enum class ModeChoice(private val displayName: String) : NamedEntry {
        LEFT("Left"),
        RIGHT("Right");

        override fun getName(): String = displayName

        fun getKeyMapping(): KeyMapping {
            return if (this == LEFT) {
                ClientAccess.collectiveMinecraft.options.keyAttack
            } else {
                ClientAccess.collectiveMinecraft.options.keyUse
            }
        }

        fun clickDirectly() {
            if (this == LEFT) {
                (ClientAccess.collectiveMinecraft as MinecraftAccessor).attack()
            } else {
                (ClientAccess.collectiveMinecraft as MinecraftAccessor).interact()
            }
        }
    }

    companion object {
        private var multiTaskModule: MultiTaskModule? = null

        init {
            multiTaskModule = ClientContext.getModuleOrNull(MultiTaskModule::class.java)
        }

        @JvmStatic
        fun bindModules(registry: ModuleRegistry) {
            multiTaskModule = registry.getModule(MultiTaskModule::class.java) as? MultiTaskModule
        }
    }
}

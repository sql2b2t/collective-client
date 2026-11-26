package me.collective.client.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import it.unimi.dsi.fastutil.booleans.BooleanConsumer
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.modules.Keybind
import me.collective.client.modules.Module
import me.collective.client.modules.client.FontsModule
import me.collective.client.modules.client.UIModule
import me.collective.client.modules.render.BlurModule
import me.collective.client.utils.chat.ChatUtils
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.network.chat.Component

class DefaultsCommand : Command("defaults") {

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val commandBuilder = builder as LiteralArgumentBuilder<Any?>
        val resetCommand = { _: com.mojang.brigadier.context.CommandContext<Any?> ->
            val confirmationScreen = ConfirmScreen(
                BooleanConsumer { confirmed ->
                    if (confirmed) {
                        val moduleRegistry = ClientContext.moduleRegistry
                        for (module in moduleRegistry.getRegistry().filterIsInstance<Module>()) {
                            for (setting in module.settings) {
                                setting.reset()
                            }

                            if (module is UIModule) {
                                val defaultUiBinding = Keybind(defaultUiKeyCode(), Keybind.Mode.TOGGLE, false)
                                module.keybind = defaultUiBinding
                            } else {
                                module.keybind = Keybind.UNBOUND
                            }

                            if (module !is FontsModule && module !is BlurModule) {
                                module.setToggled(false)
                            }
                        }
                        scheduleRotationTask {
                            ChatUtils.printChat(Component.literal("All modules have been reset to defaults."), ChatUtils.DEFAULT_SIGNATURE)
                        }
                    }
                    val client = ClientAccess.collectiveMinecraft
                    client.gui.setScreen(null)
                },
                resetConfirmationTitle(),
                Component.nullToEmpty("Continue?")
            )
            scheduleRotationTask {
                val client = ClientAccess.collectiveMinecraft
                client.gui.setScreen(confirmationScreen)
            }
            1
        }
        commandBuilder.executes(resetCommand)
    }

    companion object {
        private fun resetConfirmationTitle(): Component {
            return Component.nullToEmpty("Reset to defaults.")
        }

        private fun defaultUiKeyCode(): Int {
            return 344
        }
    }
}

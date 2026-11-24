package me.collective.client.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import me.collective.client.ClientContext
import me.collective.client.modules.HudWidgetModule
import me.collective.client.modules.Module
import me.collective.client.utils.chat.ChatUtils
import net.minecraft.network.chat.Component

class AlloffCommand : Command("alloff") {
    private val previouslyEnabledModules = HashSet<Module>()

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val commandBuilder = builder as LiteralArgumentBuilder<Any?>

        commandBuilder.executes {
            val registry = ClientContext.moduleRegistry
            for (module in (registry.getRegistry() as Collection<Module>)) {
                if (module !is HudWidgetModule && module.isToggled()) {
                    module.toggle()
                    previouslyEnabledModules.add(module)
                }
            }

            scheduleRotationTask {
                ChatUtils.printChat(
                    Component.literal("Disabled ")
                        .append(formatText(previouslyEnabledModules.size.toString()))
                        .append(" modules."),
                    ChatUtils.DEFAULT_SIGNATURE
                )
            }
            1
        }

        val restoreBuilder = aliasedLiteral("restore", "backup") as com.mojang.brigadier.builder.RequiredArgumentBuilder<Any?, *>
        val restoreBranch = restoreBuilder.executes {
            // Включаем только то, что выключал alloff: модули, включённые
            // пользователем после alloff, не трогаем.
            for (module in previouslyEnabledModules) {
                module.setToggled(true)
            }
            val restoredCount = previouslyEnabledModules.size
            previouslyEnabledModules.clear()

            scheduleRotationTask {
                ChatUtils.printChat(
                    Component.literal("Restored ")
                        .append(formatText(restoredCount.toString()))
                        .append(" modules."),
                    ChatUtils.DEFAULT_SIGNATURE
                )
            }
            1
        }
        commandBuilder.then(restoreBranch)
    }
}

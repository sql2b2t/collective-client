package me.collective.client.commands

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import me.collective.client.ClientContext
import me.collective.client.modules.Module
import me.collective.client.modules.client.ColorsModule
import me.collective.client.modules.client.UIModule
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.chat.ChatUtils
import net.minecraft.network.chat.Component
import java.util.function.BiConsumer

class GlobalCommand : Command("global") {

    init {
        setAliases("syncall")
    }

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        val colorCommand = literal<Any?>("color")
        val colorValue = argument("value", BoolArgumentType.bool()) as RequiredArgumentBuilder<Any?, *>
        val colorAction = { context: com.mojang.brigadier.context.CommandContext<Any?> ->
            val synced = BoolArgumentType.getBool(context, "value")
            val syncColor = BiConsumer<Module, Setting<*>> { module, setting ->
                if (module !is ColorsModule && module !is UIModule) {
                    if (setting is ColorSetting) {
                        setting.isSynced = synced
                    }
                }
            }
            forEachSetting(syncColor)
            ChatUtils.printChat(
                Component.literal("Color sync for all modules has been " + (if (synced) "enabled." else "disabled.")),
                ChatUtils.DEFAULT_SIGNATURE
            )
            1
        }
        val colorArguments = colorValue.executes(colorAction)
        val colorSubcommand = colorCommand.then(colorArguments)
        root.then(colorSubcommand)

        val lineWidthCommand = literal<Any?>("linewidth")
        val lineWidthValue = argument("value", FloatArgumentType.floatArg(0.1f, 5.0f)) as RequiredArgumentBuilder<Any?, *>
        val lineWidthAction = { context: com.mojang.brigadier.context.CommandContext<Any?> ->
            val lineWidth = context.getArgument("value", java.lang.Float::class.java).toFloat()
            val setLineWidth = BiConsumer<Module, Setting<*>> { _, setting ->
                if (setting.value is Float) {
                    if (setting.name.equals("LineWidth", ignoreCase = true)) {
                        @Suppress("UNCHECKED_CAST")
                        val number = setting as NumberSetting<Float>
                        number.number = lineWidth
                    }
                }
            }
            forEachSetting(setLineWidth)
            ChatUtils.printChat(
                Component.literal("LineWidth for all modules has been set to ")
                    .append(formatText(lineWidth.toString()))
                    .append("."),
                ChatUtils.DEFAULT_SIGNATURE
            )
            1
        }
        val lineWidthArguments = lineWidthValue.executes(lineWidthAction)
        val lineWidthSubcommand = lineWidthCommand.then(lineWidthArguments)
        root.then(lineWidthSubcommand)
    }

    private fun forEachSetting(action: BiConsumer<Module, Setting<*>>) {
        val registry = ClientContext.moduleRegistry
        for (module in registry.getRegistry()) {
            for (setting in module.settings) {
                action.accept(module, setting)
            }
        }
    }
}

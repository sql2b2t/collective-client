package me.collective.client.commands

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import me.collective.client.ClientContext
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.utils.chat.ChatUtils
import net.minecraft.network.chat.Component

class DrawnCommand : Command("drawn") {

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        val moduleToggleBuilder = argument("module", ModuleArgumentType()) as RequiredArgumentBuilder<Any?, *>
        val moduleToggleCallback = { context: com.mojang.brigadier.context.CommandContext<Any?> ->
            val module = context.getArgument("module", Module::class.java)
            val nextDrawn = !module.isDrawn()
            module.setDrawn(nextDrawn)

            val confirmationText = "Drawing for ${module.name} has been set to ${module.isDrawn()}."
            val confirmation = Component.literal(confirmationText)
            ChatUtils.printChat(confirmation, ChatUtils.createSignature(-1))
            1
        }
        val moduleToggleBranch = moduleToggleBuilder.executes(moduleToggleCallback)
        root.then(moduleToggleBranch)

        val allLiteral = literal<Any?>("all")
        val allStateActionBuilder = argument("state", BoolArgumentType.bool()) as RequiredArgumentBuilder<Any?, *>
        val allStateActionCallback = { context: com.mojang.brigadier.context.CommandContext<Any?> ->
            val drawn = BoolArgumentType.getBool(context, "state")
            val registry = ClientContext.moduleRegistry
            for (module in registry.getRegistry().filterIsInstance<Module>()) {
                module.setDrawn(drawn)
            }

            val confirmationText = "Drawing for all modules has been set to $drawn."
            val confirmation = Component.literal(confirmationText)
            ChatUtils.printChat(confirmation, ChatUtils.createSignature(-1))
            1
        }
        val allStateBranch = allStateActionBuilder.executes(allStateActionCallback)
        val allBranch = allLiteral.then(allStateBranch)
        root.then(allBranch)

        val categoryArgument = argument("category", EnumArgumentType(ModuleCategory::class.java, "Category")) as RequiredArgumentBuilder<Any?, *>
        val categoryStateActionBuilder = argument("state", BoolArgumentType.bool()) as RequiredArgumentBuilder<Any?, *>
        val categoryStateActionCallback = { context: com.mojang.brigadier.context.CommandContext<Any?> ->
            val category = context.getArgument("category", ModuleCategory::class.java)
            val drawn = BoolArgumentType.getBool(context, "state")
            val registry = ClientContext.moduleRegistry
            for (module in registry.getRegistry().filterIsInstance<Module>()) {
                if (category == module.category) {
                    module.setDrawn(drawn)
                }
            }

            val formattedText = "Drawing for all %s modules has been set to %b.".format(category.getName(), drawn)
            val confirmation = Component.literal(formattedText)
            ChatUtils.printChat(confirmation, ChatUtils.createSignature(-1))
            1
        }
        val categoryStateBranch = categoryStateActionBuilder.executes(categoryStateActionCallback)
        val categoryBranch = categoryArgument.then(categoryStateBranch)
        root.then(categoryBranch)
    }
}

package me.collective.client.commands

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import me.collective.client.modules.Module

class ToggleCommand : Command("toggle") {

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        val stateArgument = (argument("state", BoolArgumentType.bool()) as RequiredArgumentBuilder<Any?, *>).executes { context ->
            val module = context.getArgument("module", Module::class.java)
            val state = context.getArgument("state", java.lang.Boolean::class.java).booleanValue()
            module.isToggled = state
            1
        }

        val moduleArgument = (argument("module", ModuleArgumentType()) as RequiredArgumentBuilder<Any?, *>)
            .then(stateArgument)
            .executes { context ->
                val module = context.getArgument("module", Module::class.java)
                module.toggle()
                1
            }

        root.then(moduleArgument)
    }
}

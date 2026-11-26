package me.collective.scripting

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import me.collective.client.commands.Command
import java.util.function.Consumer

/** Простая строковая команда из скрипта: аргументы приходят как String[]. */
class ScriptCustomCommand(
    name: String,
    description: String,
    val action: Consumer<Array<String>>?,
    vararg aliases: String
) : Command(name) {
    init {
        setDescription(description)
        if (aliases.isNotEmpty()) {
            setAliases(*aliases)
        }
    }

    private fun run(argsString: String?) {
        if (action == null) return
        val args = if (argsString.isNullOrBlank()) emptyArray<String>() else argsString.trim().split(Regex("\\s+")).toTypedArray()
        try {
            action.accept(args)
        } catch (t: Throwable) {
            // Ошибки не должны ронять клиент
            t.printStackTrace()
        }
    }

    override fun exec(builder: LiteralArgumentBuilder<*>) {
        @Suppress("UNCHECKED_CAST")
        val root = builder as LiteralArgumentBuilder<Any?>
        root.executes {
            run("")
            1
        }
        root.then(
            Command.argument("args", StringArgumentType.greedyString()).executes { context ->
                run(StringArgumentType.getString(context, "args"))
                1
            }
        )
    }
}

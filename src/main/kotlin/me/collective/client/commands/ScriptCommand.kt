package me.collective.client.commands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import me.collective.client.utils.DesktopUtils
import me.collective.client.utils.chat.ChatUtils
import me.collective.scripting.ScriptManager
import net.minecraft.network.chat.Component
import java.awt.Desktop

/**
 * .script list|load|unload|reload|delete|examples|folder
 */
class ScriptCommand : Command("script") {

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        root.executes {
            reply("§7Использование: §f.script list|load|unload|reload|delete|examples|folder")
            1
        }

        root.then(
            literal<Any?>("list").executes {
                val manager = ScriptManager.getInstance()
                val files = manager.listScriptFiles()
                if (files.isEmpty()) {
                    reply("§7Скриптов нет. Папка: §f" + manager.scriptsFolder + "§7. Создать примеры: §f.script examples")
                } else {
                    val sb = StringBuilder("§bСкрипты (§f" + files.size + "§b):§f ")
                    sb.append(java.lang.String.join(", ", files))
                    reply(sb.toString())
                    val active = manager.activeContexts
                    reply("§7Активно: §f" + active.size)
                    val failures = manager.failures
                    for ((key, value) in failures) {
                        reply("§c$key§7: $value")
                    }
                }
                1
            }
        )

        root.then(
            literal<Any?>("load").then(
                (argument("name", StringArgumentType.string()) as RequiredArgumentBuilder<Any?, *>).executes { context ->
                    val name = StringArgumentType.getString(context, "name")
                    if (ScriptManager.getInstance().loadScriptByName(name)) {
                        reply("§aЗагружен: §f$name")
                    } else {
                        reply("§cНе загрузился — подробности: §f.script list")
                    }
                    1
                }
            )
        )

        root.then(
            literal<Any?>("unload").then(
                (argument("name", StringArgumentType.string()) as RequiredArgumentBuilder<Any?, *>).executes { context ->
                    val name = StringArgumentType.getString(context, "name")
                    if (ScriptManager.getInstance().unloadScript(name)) {
                        reply("§aВыгружен: §f$name")
                    } else {
                        reply("§cСкрипт не найден среди активных: §f$name")
                    }
                    1
                }
            )
        )

        root.then(
            literal<Any?>("delete").then(
                (argument("name", StringArgumentType.string()) as RequiredArgumentBuilder<Any?, *>).executes { context ->
                    val name = StringArgumentType.getString(context, "name")
                    if (ScriptManager.getInstance().deleteScript(name)) {
                        reply("§aУдалён: §f$name")
                    } else {
                        reply("§cФайл не найден: §f$name")
                    }
                    1
                }
            )
        )

        root.then(
            literal<Any?>("reload").executes {
                ScriptManager.getInstance().reload()
                1
            }
        )

        root.then(
            literal<Any?>("examples").executes {
                val written = ScriptManager.getInstance().writeMissingExamples()
                reply(
                    if (written > 0) {
                        "§aСоздано файлов: §f$written§7. Папка: §f" + ScriptManager.getInstance().scriptsFolder
                    } else {
                        "§7Все примеры уже на месте."
                    }
                )
                1
            }
        )

        root.then(
            literal<Any?>("folder").executes {
                try {
                    DesktopUtils.openPath(ScriptManager.getInstance().scriptsFolder)
                } catch (t: Throwable) {
                    reply("§cНе удалось открыть папку: §f" + t.message)
                }
                1
            }
        )
    }

    companion object {
        private fun reply(message: String) {
            ChatUtils.printChat(Component.literal(message), ChatUtils.createSignature(-1))
        }
    }
}

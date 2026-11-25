package me.collective.client.commands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import me.collective.addon.AddonCompiler
import me.collective.addon.AddonManager
import me.collective.client.ClientAccess
import me.collective.client.utils.DesktopUtils
import me.collective.client.utils.chat.ChatUtils
import net.minecraft.network.chat.Component
import java.nio.file.Files
import java.nio.file.Path

/** .addon list|load|unload|info|sources|build|reload|folder */
class AddonCommand : Command("addon") {

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        root.executes {
            reply("§7Использование: §f.addon list|load|unload|info|sources|build|reload|folder")
            1
        }

        root.then(literal<Any?>("list").executes {
            val manager = AddonManager.getInstance()
            val addons = manager.addons
            if (addons.isEmpty()) {
                reply("§7Аддоны не загружены. Папка: §f${manager.addonsFolder}")
            } else {
                val sb = StringBuilder("§bАддоны (§f${addons.size}§b):§f ")
                for (i in addons.indices) {
                    val addon = addons[i]
                    sb.append(addon.name).append(" §7v").append(addon.version).append("§f")
                    if (i < addons.size - 1) sb.append(", ")
                }
                reply(sb.toString())
            }
            val failures = manager.loadFailures
            for ((key, value) in failures) {
                reply("§c$key§7: $value")
            }
            1
        })

        root.then(
            literal<Any?>("load")
                .then(
                    (argument("jar", StringArgumentType.string()) as RequiredArgumentBuilder<Any?, *>).executes { context ->
                        val name = StringArgumentType.getString(context, "jar")
                        val manager = AddonManager.getInstance()
                        var file = manager.addonsFolder.resolve(name)
                        if (!Files.isRegularFile(file)) {
                            file = Path.of(name)
                        }
                        if (!Files.isRegularFile(file)) {
                            reply("§cJar не найден: §f$name")
                            return@executes 1
                        }
                        if (manager.loadJarAddon(file.toFile())) {
                            reply("§aЗагружено: §f${file.fileName}")
                        } else {
                            reply("§cНе загрузилось — подробности: §f.addon list")
                        }
                        1
                    }
                )
        )

        root.then(
            literal<Any?>("unload")
                .then(
                    (argument("name", StringArgumentType.string()) as RequiredArgumentBuilder<Any?, *>).executes { context ->
                        val name = StringArgumentType.getString(context, "name")
                        if (AddonManager.getInstance().unloadAddon(name)) {
                            reply("§aВыгружено: §f$name")
                        } else {
                            reply("§cАддон не найден: §f$name")
                        }
                        1
                    }
                )
        )

        root.then(
            literal<Any?>("info")
                .then(
                    (argument("name", StringArgumentType.string()) as RequiredArgumentBuilder<Any?, *>).executes { context ->
                        val addon = AddonManager.getInstance().getAddon(StringArgumentType.getString(context, "name"))
                        if (addon == null) {
                            reply("§cАддон не найден.")
                            return@executes 1
                        }
                        reply("§b${addon.name} §7v${addon.version} §fby §7${addon.author}")
                        if (addon.description.isNotBlank()) reply("§7${addon.description}")
                        if (!addon.website.isNullOrBlank()) reply("§9${addon.website}")
                        reply("§7Модулей: §f${addon.registeredModules.size}§7, команд: §f${addon.registeredCommands.size}")
                        1
                    }
                )
        )

        root.then(literal<Any?>("sources").executes {
            val sources = AddonManager.getInstance().listAddonSources()
            if (sources.isEmpty()) {
                reply("§7Исходников нет. Папка: §f${AddonManager.getInstance().addonsSrcFolder}")
            } else {
                reply("§bИсходники:§f " + sources.joinToString(", "))
            }
            1
        })

        root.then(
            literal<Any?>("build")
                .then(
                    (argument("name", StringArgumentType.string()) as RequiredArgumentBuilder<Any?, *>).executes { context ->
                        val name = StringArgumentType.getString(context, "name")
                        val manager = AddonManager.getInstance()
                        val source = manager.getAddonSource(name)
                        if (source == null) {
                            reply("§cИсходник не найден в collective/addons-src: §f$name")
                            return@executes 1
                        }
                        reply("§7Собираю §f${source.fileName}§7...")
                        ClientAccess.executorService.submit {
                            val result = AddonCompiler.build(source, name)
                            reply(if (result.success) "§a${result.message}" else "§c${result.message}")
                        }
                        1
                    }
                )
        )

        root.then(literal<Any?>("reload").executes {
            ClientAccess.executorService.submit { AddonManager.getInstance().reload() }
            1
        })

        root.then(literal<Any?>("folder").executes {
            try {
                DesktopUtils.openPath(AddonManager.getInstance().addonsFolder)
            } catch (t: Throwable) {
                reply("§cНе удалось открыть папку: §f${t.message}")
            }
            1
        })
    }

    companion object {
        private fun reply(message: String) {
            ChatUtils.printChat(
                Component.literal(message),
                ChatUtils.createSignature(-1)
            )
        }
    }
}

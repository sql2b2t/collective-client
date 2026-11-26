package me.collective.client.commands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import me.collective.client.ClientContext
import me.collective.client.modules.misc.IrcModule
import me.collective.client.utils.chat.ChatUtils
import net.minecraft.network.chat.Component

/**
 * .irc <message> — чат; .irc — статус; .irc nick <ник> — смена ника;
 * .irc server <host[:port]>; .irc token <ключ>.
 */
class IrcCommand : Command("irc") {

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        root.executes {
            val irc = ClientContext.getModule(IrcModule::class.java)
            if (irc == null) {
                reply("IRC module is missing.")
                return@executes 1
            }
            reply(irc.statusText())
            1
        }

        root.then(
            literal<Any?>("nick")
                .executes {
                    val irc = ClientContext.getModule(IrcModule::class.java)
                    val current = irc.nickname.value.trim()
                    reply("Ник IRC: §b" + (if (current.isEmpty()) "<авто — ник акка>" else current) + "§f. Ставить: §7.irc nick <ник>§f.")
                    1
                }
                .then(
                    (argument("name", StringArgumentType.string()) as RequiredArgumentBuilder<Any?, *>).executes { context ->
                        val irc = ClientContext.getModule(IrcModule::class.java)
                        val normalized = irc.normalizeNick(StringArgumentType.getString(context, "name"))
                        val previous = irc.nickname.value.trim()
                        if (previous == normalized) {
                            reply("Ник IRC уже: §b" + (if (normalized.isEmpty()) "<авто>" else normalized) + "§f.")
                            return@executes 1
                        }
                        irc.nickname.value = normalized
                        reply("Ник IRC теперь: §b" + (if (normalized.isEmpty()) "<авто — ник акка>" else normalized) + "§f. Смена применится при пересоединении.")
                        1
                    }
                )
        )

        root.then(
            literal<Any?>("server")
                .executes {
                    val irc = ClientContext.getModule(IrcModule::class.java)
                    reply("Сервер IRC: §b" + irc.server + "§7:§b" + irc.port + "§f. Менять: §7.irc server <host/ip> [port]§f.")
                    1
                }
                .then(
                    (argument("host", StringArgumentType.string()) as RequiredArgumentBuilder<Any?, *>)
                        .executes { context ->
                            val irc = ClientContext.getModule(IrcModule::class.java)
                            handleServer(irc, StringArgumentType.getString(context, "host"), null)
                            1
                        }
                        .then(
                            (argument("port", StringArgumentType.word()) as RequiredArgumentBuilder<Any?, *>).executes { context ->
                                val irc = ClientContext.getModule(IrcModule::class.java)
                                handleServer(
                                    irc,
                                    StringArgumentType.getString(context, "host"),
                                    StringArgumentType.getString(context, "port")
                                )
                                1
                            }
                        )
                )
        )

        root.then(
            literal<Any?>("token")
                .executes {
                    val irc = ClientContext.getModule(IrcModule::class.java)
                    reply("Ключ IRC (Token): §b" + irc.token + "§f. Менять: §7.irc token <ключ>§f.")
                    1
                }
                .then(
                    (argument("key", StringArgumentType.string()) as RequiredArgumentBuilder<Any?, *>).executes { context ->
                        val irc = ClientContext.getModule(IrcModule::class.java)
                        irc.setToken(StringArgumentType.getString(context, "key").trim())
                        reply("Ключ IRC изменён на §b" + irc.token + "§f. Переподключение...")
                        1
                    }
                )
        )

        root.then(
            (argument("message", StringArgumentType.greedyString()) as RequiredArgumentBuilder<Any?, *>).executes { context ->
                val irc = ClientContext.getModule(IrcModule::class.java)
                val text = StringArgumentType.getString(context, "message")
                if (!irc.isToggled) {
                    reply("IRC is off — enable the IRC module first.")
                    return@executes 1
                }
                if (!irc.sendChat(text)) {
                    reply(irc.statusText())
                }
                1
            }
        )
    }

    companion object {
        private fun reply(message: String) {
            ChatUtils.printChat(Component.literal(message), ChatUtils.createSignature(IrcModule.IRC_SIGNATURE_ID))
        }

        private fun handleServer(irc: IrcModule, hostArg: String, portArg: String?) {
            var host = hostArg.trim()
            var port = irc.port
            if (host.contains(":")) {
                val parts = host.split(":", limit = 2)
                host = parts[0]
                val parsed = parts[1].trim().toIntOrNull()
                if (parsed == null) {
                    reply("Порт IRC не распознан: §b" + parts[1] + "§f. Формат: §7.irc server <host> [port]§f.")
                    return
                }
                port = parsed
            }
            if (portArg != null) {
                val parsed = portArg.trim().toIntOrNull()
                if (parsed == null) {
                    reply("Порт IRC не распознан: §b" + portArg + "§f. Формат: §7.irc server <host> [port]§f.")
                    return
                }
                port = parsed
            }
            if (host.isEmpty()) {
                reply("Хост IRC пуст. Формат: §7.irc server <host> [port]§f.")
                return
            }
            if (port !in 1..65535) {
                reply("Порт IRC вне диапазона 1–65535: §b" + port + "§f.")
                return
            }
            irc.setServer(host, port)
            reply("Сервер IRC изменён на §b" + irc.server + "§7:§b" + irc.port + "§f. Переподключение...")
        }
    }
}

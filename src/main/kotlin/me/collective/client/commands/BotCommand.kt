package me.collective.client.commands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import me.collective.client.ClientAccess
import me.collective.client.bots.BotManager
import me.collective.client.bots.follow.BotFollowManager
import me.collective.client.bots.follow.BotFollowNameArgument
import me.collective.client.bots.follow.BotNameArgument
import me.collective.client.bots.gui.BotManagerScreen
import me.collective.client.bots.session.BotSessionManager
import me.collective.client.bots.session.BotSessionTransfer
import net.minecraft.network.chat.Component

class BotCommand : Command("bot") {

    init {
        setAliases("bots")
    }

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        // .bot gui
        val guiArg = literal<Any?>("gui").executes {
            ClientAccess.collectiveMinecraft.execute {
                ClientAccess.collectiveMinecraft.setScreen(BotManagerScreen())
            }
            1
        }
        root.then(guiArg)

        // .bot stop
        val stopArg = literal<Any?>("stop").executes {
            BotFollowManager.stopAll()
            sendClientMessage("Боты остановлены")
            1
        }
        root.then(stopArg)

        // .bot mirror <name|off>
        val mirrorNameArg = (argument("name", BotFollowNameArgument()) as RequiredArgumentBuilder<Any?, *>).executes { ctx ->
            val target = ctx.getArgument("name", String::class.java)
            if (target.equals(BotFollowNameArgument.OFF, ignoreCase = true)) {
                BotFollowManager.stopMirroring()
                sendClientMessage("Боты больше не повторяют действия")
            } else {
                BotFollowManager.startMirroring(target)
                sendClientMessage("Боты повторяют действия игрока: $target")
            }
            1
        }
        root.then(literal<Any?>("mirror").then(mirrorNameArg))

        // .bot follow <name|off>
        val followNameArg = (argument("name", BotFollowNameArgument()) as RequiredArgumentBuilder<Any?, *>).executes { ctx ->
            val target = ctx.getArgument("name", String::class.java)
            if (target.equals(BotFollowNameArgument.OFF, ignoreCase = true)) {
                BotFollowManager.stopFollowing()
                sendClientMessage("Боты больше не следуют за игроком")
            } else {
                BotFollowManager.startFollowing(target)
                sendClientMessage("Боты следуют за игроком: $target")
            }
            1
        }
        root.then(literal<Any?>("follow").then(followNameArg))

        // .bot chat <bot|all> <message>
        val chatMsgArg = (argument("message", StringArgumentType.greedyString()) as RequiredArgumentBuilder<Any?, *>).executes { ctx ->
            val botName = ctx.getArgument("bot", String::class.java)
            val rawMsg = StringArgumentType.getString(ctx, "message").trim()
            val msg = unquote(rawMsg)

            if (msg.isBlank()) {
                sendClientMessage("Сообщение пустое")
                return@executes 0
            }

            if (botName.equals(BotNameArgument.ALL, ignoreCase = true)) {
                val count = BotManager.sendChatToAll(msg)
                if (count == 0) {
                    sendClientMessage("Ни один бот не в игре")
                    0
                } else {
                    sendClientMessage("Сообщение отправлено от $count ботов")
                    1
                }
            } else {
                val bot = BotManager.findByName(botName)
                if (bot == null) {
                    sendClientMessage("Бота с ником $botName нет в списке")
                    0
                } else if (!bot.isOnline()) {
                    sendClientMessage("Бот с ником $botName не в игре")
                    0
                } else {
                    bot.sendChat(msg)
                    sendClientMessage("Сообщение отправлено от бота $botName")
                    1
                }
            }
        }
        val chatBotArg = (argument("bot", BotNameArgument()) as RequiredArgumentBuilder<Any?, *>).then(chatMsgArg)
        root.then(literal<Any?>("chat").then(chatBotArg))

        // .bot connect <name|all>
        val connectTargetArg = (argument("target", StringArgumentType.word()) as RequiredArgumentBuilder<Any?, *>).executes { ctx ->
            val target = StringArgumentType.getString(ctx, "target")
            if (target.equals("all", ignoreCase = true)) {
                BotManager.connectAll()
                sendClientMessage("Подключение всех ботов...")
            } else {
                val bot = BotManager.findByName(target)
                if (bot != null) {
                    BotManager.enqueueConnect(bot)
                    sendClientMessage("Подключение бота $target...")
                } else {
                    sendClientMessage("Бот $target не найден")
                }
            }
            1
        }
        root.then(literal<Any?>("connect").then(connectTargetArg))

        // .bot disconnect <name|all>
        val disconnectTargetArg = (argument("target", StringArgumentType.word()) as RequiredArgumentBuilder<Any?, *>).executes { ctx ->
            val target = StringArgumentType.getString(ctx, "target")
            if (target.equals("all", ignoreCase = true)) {
                BotManager.disconnectAll()
                sendClientMessage("Все боты отключены")
            } else {
                val bot = BotManager.findByName(target)
                if (bot != null) {
                    bot.disconnect("Disconnected via command")
                    sendClientMessage("Бот $target отключен")
                } else {
                    sendClientMessage("Бот $target не найден")
                }
            }
            1
        }
        root.then(literal<Any?>("disconnect").then(disconnectTargetArg))

        // .bot add <name> <address> [count]
        val addCountArg = (argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 100)) as RequiredArgumentBuilder<Any?, *>).executes { ctx ->
            val name = StringArgumentType.getString(ctx, "name")
            val address = StringArgumentType.getString(ctx, "address")
            val count = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "count")
            val added = BotManager.addBots(name, count, address)
            sendClientMessage("Добавлено ботов: ${added.size}")
            1
        }
        val addAddressArg = (argument("address", StringArgumentType.string()) as RequiredArgumentBuilder<Any?, *>).executes { ctx ->
            val name = StringArgumentType.getString(ctx, "name")
            val address = StringArgumentType.getString(ctx, "address")
            val bot = BotManager.addBot(name, address)
            if (bot != null) {
                sendClientMessage("Бот $name добавлен ($address)")
            } else {
                sendClientMessage("Не удалось добавить бота $name")
            }
            1
        }.then(addCountArg)
        val addNameArg = (argument("name", StringArgumentType.string()) as RequiredArgumentBuilder<Any?, *>).then(addAddressArg)
        root.then(literal<Any?>("add").then(addNameArg))

        // .bot remove/del <name>
        val removeArg = (argument("name", BotNameArgument()) as RequiredArgumentBuilder<Any?, *>).executes { ctx ->
            val name = ctx.getArgument("name", String::class.java)
            val bot = BotManager.findByName(name)
            if (bot != null) {
                BotManager.removeBot(bot)
                sendClientMessage("Бот $name удален")
            } else {
                sendClientMessage("Бот $name не найден")
            }
            1
        }
        root.then(literal<Any?>("remove").then(removeArg))
        root.then(literal<Any?>("del").then(removeArg))

        // .bot session <name>
        val sessionTargetArg = (argument("target", StringArgumentType.word()) as RequiredArgumentBuilder<Any?, *>).executes { ctx ->
            val target = StringArgumentType.getString(ctx, "target")
            val bot = BotManager.findByName(target)
            if (bot == null) {
                sendClientMessage("Бот $target не найден")
                return@executes 0
            }

            if (BotSessionManager.isBotControlled(bot)) {
                BotSessionTransfer.transferBackToBot(bot)
                sendClientMessage("Управление ботом $target завершено")
            } else {
                val success = BotSessionTransfer.transferToClient(bot)
                if (success) {
                    sendClientMessage("Управление переключено на бота $target")
                } else {
                    sendClientMessage("Не удалось переключить управление на бота $target")
                }
            }
            1
        }
        root.then(literal<Any?>("session").then(sessionTargetArg))

        root.executes {
            sendClientMessage("Используйте: .bot <gui|mirror|follow|chat|stop|connect|disconnect|session>")
            1
        }
    }

    private fun unquote(str: String): String {
        return if (str.length >= 2 && str.startsWith("\"") && str.endsWith("\"")) {
            str.substring(1, str.length - 1).trim()
        } else {
            str
        }
    }

    private fun sendClientMessage(text: String) {
        val player = ClientAccess.collectiveMinecraft.player
        player?.displayClientMessage(Component.literal("§7[§bCollective-Bots§7] §f$text"), false)
    }
}

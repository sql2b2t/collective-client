package me.collective.client.commands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import me.collective.client.ClientContext
import me.collective.client.utils.chat.ChatUtils
import net.minecraft.network.chat.Component

class WebhookCommand : Command("webhook") {

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        root.then(
            literal<Any?>("get").executes {
                reply(ClientContext.webhookManager.destinationDescription, -1035361)
                1
            }
        )

        root.then(
            literal<Any?>("set").then(
                (argument("url", StringArgumentType.greedyString()) as RequiredArgumentBuilder<Any?, *>).executes { context ->
                    var url = context.getArgument("url", String::class.java).trim()
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        url = "https://$url"
                    }
                    try {
                        val manager = ClientContext.webhookManager
                        manager.setUrl(url)
                        reply("Notification destination updated: " + manager.destinationDescription, -1035362)
                    } catch (invalid: IllegalArgumentException) {
                        reply("Invalid webhook URL. Telegram URLs require /bot<token>/sendMessage?chat_id=<id>.", -1035362)
                    }
                    1
                }
            )
        )

        root.then(
            literal<Any?>("telegram").then(
                (argument("botToken", FancyStringArgument.fancyString()) as RequiredArgumentBuilder<Any?, *>).then(
                    (argument("chatId", FancyStringArgument.fancyString()) as RequiredArgumentBuilder<Any?, *>).executes { context ->
                        try {
                            ClientContext.webhookManager.setTelegram(
                                context.getArgument("botToken", String::class.java),
                                context.getArgument("chatId", String::class.java)
                            )
                            reply("Telegram notifications configured. Start the bot or add it to the destination chat first.", -1035362)
                        } catch (invalid: IllegalArgumentException) {
                            reply("Invalid Telegram credentials. Use webhook telegram <bot-token> <chat-id>.", -1035362)
                        }
                        1
                    }
                )
            )
        )

        root.then(
            literal<Any?>("chatid")
                .then(
                    (argument("id", FancyStringArgument.fancyString()) as RequiredArgumentBuilder<Any?, *>).executes { context ->
                        val manager = ClientContext.webhookManager
                        if (!manager.isTelegram) {
                            reply("Chat ID is not supported for current webhook.", -1)
                            return@executes 1
                        }
                        val chatId = context.getArgument("id", String::class.java)
                        try {
                            manager.telegramChatId = chatId
                            ChatUtils.printChat(
                                Component.literal("Your webhook Chat ID has been updated to ").append(formatText(chatId)),
                                ChatUtils.DEFAULT_SIGNATURE
                            )
                        } catch (invalid: IllegalArgumentException) {
                            reply("Invalid Telegram chat ID.", -1)
                        }
                        1
                    }
                )
                .executes {
                    val manager = ClientContext.webhookManager
                    if (manager.isTelegram) {
                        ChatUtils.printChat(
                            Component.literal("Your webhook Chat ID is ").append(formatText(manager.telegramChatId)),
                            ChatUtils.DEFAULT_SIGNATURE
                        )
                    } else {
                        reply("Chat ID is not supported for current webhook.", -1)
                    }
                    1
                }
        )

        root.then(
            literal<Any?>("clear").executes {
                ClientContext.webhookManager.setUrl("")
                reply("Your notification destination has been cleared", -1035363)
                1
            }
        )
    }

    companion object {
        private fun reply(message: String, signature: Int) {
            ChatUtils.printChat(Component.literal(message), signature)
        }
    }
}

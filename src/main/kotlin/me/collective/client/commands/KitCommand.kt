package me.collective.client.commands

import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import me.collective.client.ClientContext
import me.collective.client.managers.KitRegistry
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.utils.inventory.Kit
import net.minecraft.network.chat.Component
import java.util.stream.Collectors

class KitCommand : Command("kit") {

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        val saveBuilder = aliasedLiteral("save", "add") as RequiredArgumentBuilder<Any?, *>
        val saveNameBranch = (argument("name", FancyStringArgument.fancyString()) as RequiredArgumentBuilder<Any?, *>).executes { context ->
            val name = context.getArgument("name", String::class.java)
            ClientContext.kitRegistry.captureInventory(name)
            ClientContext.kitRegistry.setCurrentKitName(name)

            val message = Component.literal("Kit %s has been saved".format(name))
            ChatUtils.printChat(message, ChatUtils.createSignature(-1))
            1
        }
        root.then(saveBuilder.then(saveNameBranch))

        val removeBuilder = aliasedLiteral("remove", "delete", "del") as RequiredArgumentBuilder<Any?, *>
        val removeKitBranch = (argument("kit", KitArgumentType()) as RequiredArgumentBuilder<Any?, *>).executes { context ->
            val kit = context.getArgument("kit", Kit::class.java)
            val registryList = ClientContext.kitRegistry.getRegistry() as MutableList<Kit>
            registryList.removeIf { candidate ->
                kit.name.equals(candidate.name, ignoreCase = true)
            }

            val message = Component.literal("Kit %s has been removed".format(kit.name))
            ChatUtils.printChat(message, ChatUtils.createSignature(-1))
            1
        }
        root.then(removeBuilder.then(removeKitBranch))

        val loadLiteralBuilder = literal<Any?>("load")
        val loadKitBranch = (argument("kit", KitArgumentType()) as RequiredArgumentBuilder<Any?, *>).executes { context ->
            val kit = context.getArgument("kit", Kit::class.java)
            ClientContext.kitRegistry.setCurrentKitName(kit.name)

            val message = Component.literal("Kit %s has been loaded".format(kit.name))
            ChatUtils.printChat(message, ChatUtils.createSignature(-1))
            1
        }
        root.then(loadLiteralBuilder.then(loadKitBranch))

        root.then(
            literal<Any?>("rename").then(
                (argument("kit", KitArgumentType()) as RequiredArgumentBuilder<Any?, *>).then(
                    (argument("name", FancyStringArgument.fancyString()) as RequiredArgumentBuilder<Any?, *>).executes { context ->
                        val kit = context.getArgument("kit", Kit::class.java)
                        val name = context.getArgument("name", String::class.java)
                        val currentKit = KitRegistry.getCurrentKit(ClientContext.kitRegistry) === kit
                        val registryList = ClientContext.kitRegistry.getRegistry() as MutableList<Kit>
                        registryList.removeIf { candidate ->
                            kit.name.equals(candidate.name, ignoreCase = true)
                        }
                        val renamedKit = Kit(name)
                        renamedKit.itemsBySlot.putAll(kit.itemsBySlot)
                        registryList.add(renamedKit)
                        if (currentKit) {
                            ClientContext.kitRegistry.setCurrentKitName(renamedKit.name)
                        }
                        val message = Component.literal("Kit %s has been renamed to %s".format(kit.name, renamedKit.name))
                        ChatUtils.printChat(message, ChatUtils.DEFAULT_SIGNATURE)
                        1
                    }
                )
            )
        )

        root.executes {
            val currentKit = KitRegistry.getCurrentKit(ClientContext.kitRegistry)
            val selectedName = currentKit?.name ?: "None"

            val registryList = ClientContext.kitRegistry.getRegistry() as List<Kit>
            val availableNames = registryList.stream()
                .filter { candidate -> candidate !== currentKit }
                .map(Kit::name)
                .collect(Collectors.joining(", "))

            val selectionMessage = Component.literal("Kit selected: %s".format(selectedName))
            ChatUtils.printChat(selectionMessage, ChatUtils.createSignature(-1))

            val availableMessage = Component.literal("Kits available: %s.".format(availableNames))
            ChatUtils.printChat(availableMessage, ChatUtils.createSignature(-341842))
            1
        }
    }
}

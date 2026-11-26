package me.collective.client.commands

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.modules.misc.SocialEntry
import me.collective.client.render.FontRenderer
import me.collective.client.settings.options.SocialRole
import me.collective.client.utils.chat.ChatUtils

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import java.io.FileNotFoundException
import java.util.Locale
import java.util.concurrent.CompletableFuture
import net.minecraft.ChatFormatting
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentUtils
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.EntityHitResult

class EnemyFriendCommand(private val role: SocialRole) : Command(role.name.lowercase(Locale.ROOT)) {
   @Suppress("UNCHECKED_CAST")
   override fun exec(builder: LiteralArgumentBuilder<*>) {
      val root = builder as LiteralArgumentBuilder<Any?>
      var commandBuilder: LiteralArgumentBuilder<Any?> = literal<Any?>("add")
         .executes { context: CommandContext<Any?> ->
            val hitResult = ClientAccess.collectiveMinecraft.hitResult
            if (hitResult is EntityHitResult) {
               val hitEntity = hitResult.entity
               if (hitEntity is Player) {
                  val profile = hitEntity.gameProfile
                  val name = profile.name()
                  val socials = ClientContext.socialRegistry

                  if (socials.getRole(name) != role) {
                     if (role == SocialRole.ENEMY) {
                        val enemySocials = ClientContext.socialRegistry

                        enemySocials.addEnemy(name)
                     } else {
                        val friendSocials = ClientContext.socialRegistry

                        friendSocials.addFriend(name)
                     }

                     val addedText = String.format("%s has been added to your %s list", name, role.pluralName)
                     val addedMessage = Component.nullToEmpty(addedText)

                     ChatUtils.printChat(addedMessage, ChatUtils.createSignature(-1))
                  } else {
                     val existingText = String.format("%s is already in your %s list", name, role.pluralName)
                     val existingMessage = Component.nullToEmpty(existingText)

                     ChatUtils.printChat(existingMessage, ChatUtils.createSignature(-1))
                  }
               }
            }

            return@executes 1
         }

      val addNamesCommand: ArgumentBuilder<Any?, *> = argument("names", ArrayArgumentType(PlayerNameArgumentType()))
         .executes { context: CommandContext<Any?> ->
            val names = context.getArgument("names", Array<String>::class.java)
            val addedNames: ArrayList<String> = ArrayList()
            val existingNames: ArrayList<String> = ArrayList()

            for (name in names) {
               val socials = ClientContext.socialRegistry

               if (socials.getRole(name) == role) {
                  existingNames.add(name)
               } else {
                  if (role == SocialRole.ENEMY) {
                     val enemySocials = ClientContext.socialRegistry

                     enemySocials.addEnemy(name)
                  } else {
                     val friendSocials = ClientContext.socialRegistry

                     friendSocials.addFriend(name)
                  }

                  addedNames.add(name)
               }
            }

            var messageId = -1
            if (!addedNames.isEmpty()) {
               val addedVerbChoice = if (addedNames.size == 1) "has" else "have"
               val addedList = this.formatNames(addedNames)
               val addedSuffixTemplate = " " + addedVerbChoice + " been added to your %s list"
               val addedSuffix = this.formatRoleTemplate(addedSuffixTemplate)
               val addedMessage = Component.empty().append(addedList).append(Component.literal(addedSuffix))

               val addedMessageId = messageId
               ChatUtils.printChat(addedMessage, ChatUtils.createSignature(addedMessageId))
               messageId--
            }

            if (!existingNames.isEmpty()) {
               val existingVerb = if (existingNames.size == 1) "is" else "are"
               val existingList = this.formatNames(existingNames)
               val existingSuffixTemplate = " " + existingVerb + " already in your %s list"
               val existingSuffix = this.formatRoleTemplate(existingSuffixTemplate)
               val existingMessage = Component.empty().append(existingList).append(Component.literal(existingSuffix))

               val existingMessageId = messageId
               ChatUtils.printChat(existingMessage, ChatUtils.createSignature(existingMessageId))
            }

            return@executes 1
         }
      val addBranch = commandBuilder.then(addNamesCommand)
      root.then(addBranch)

      val removeBuilder =
         aliasedLiteral("remove", "delete", "del").executes { context ->
            val hitResult = ClientAccess.collectiveMinecraft.hitResult
            if (hitResult is EntityHitResult) {
               val hitEntity = hitResult.entity
               if (hitEntity is Player) {
                  val profile = hitEntity.gameProfile
                  val name = profile.name()
                  val socials = ClientContext.socialRegistry

                  if (socials.getRole(name) == role) {
                     val removerSocials = ClientContext.socialRegistry

                     removerSocials.removeSocial(name)

                     val removedText = String.format("%s was removed from your %s list", name, role.pluralName)
                     val removedMessage = Component.nullToEmpty(removedText)

                     ChatUtils.printChat(removedMessage, ChatUtils.createSignature(-1))
                  } else {
                     val missingText = String.format("%s is not in your %s list", name, role.pluralName)
                     val missingMessage = Component.nullToEmpty(missingText)

                     ChatUtils.printChat(missingMessage, ChatUtils.createSignature(-1))
                  }
               }
            }

            return@executes 1
         }

      val removeNamesCommand: ArgumentBuilder<Any?, *> = argument("names", StringArgumentType.greedyString())
         .suggests { context: CommandContext<Any?>, builder: SuggestionsBuilder ->
            this.suggestRegisteredNames(context, builder)
         }
         .executes { context: CommandContext<Any?> ->
            val capturedInput = context.getArgument("names", String::class.java)
            val names = capturedInput.split(" ")
            val removedNames: ArrayList<String> = ArrayList()
            val missingNames: ArrayList<String> = ArrayList()

            for (name in names) {
               val socials = ClientContext.socialRegistry

               if (socials.getRole(name) == role) {
                  val capturedRemover = ClientContext.socialRegistry

                  capturedRemover.removeSocial(name)
                  removedNames.add(name)
               } else {
                  missingNames.add(name)
               }
            }

            var messageId = -1
            if (!removedNames.isEmpty()) {
               val removedList = this.formatNames(removedNames)

               val removedTemplate = " was removed from your %s list"
               val removedSuffix = this.formatRoleTemplate(removedTemplate)
               val removedMessage = Component.empty().append(removedList).append(Component.literal(removedSuffix))

               val removedMessageId = messageId
               ChatUtils.printChat(removedMessage, ChatUtils.createSignature(removedMessageId))
               messageId--
            }

            if (!missingNames.isEmpty()) {
               val missingVerb = if (missingNames.size == 1) "is" else "are"
               val missingList = this.formatNames(missingNames)
               val missingSuffixTemplate = " " + missingVerb + " not your %s"
               val missingSuffix = this.formatRoleTemplate(missingSuffixTemplate)
               val missingMessage = Component.empty().append(missingList).append(Component.literal(missingSuffix))

               val missingMessageId = messageId
               ChatUtils.printChat(missingMessage, ChatUtils.createSignature(missingMessageId))
            }

            return@executes 1
         }
      val removeBranch = removeBuilder.then(removeNamesCommand)
      root.then(removeBranch)

      val listCommand = literal<Any?>("list")
         .executes { context: CommandContext<Any?> ->
            val socials = ClientContext.socialRegistry
            val nameComponents = (socials.getRegistry() as List<SocialEntry>).stream()
               .filter { entry: SocialEntry ->
                  entry.role() == role
               }
               .map { entry: SocialEntry ->
                  val name = entry.getName()
                  Component.nullToEmpty(name)
               }
               .toList()

            val heading = this.formatRoleTemplate("%S list: ")
            var message = Component.literal(heading)

            message = message.append(ComponentUtils.formatList(nameComponents, Component.nullToEmpty(", ")))

            val messageId: Byte = -1
            ChatUtils.printChat(message, ChatUtils.createSignature(messageId.toInt()))
            return@executes 1
         }
      root.then(listCommand)
      if (role != SocialRole.ENEMY) {
         commandBuilder = literal("sync")

         val importerCommand: ArgumentBuilder<Any?, *> =
            argument("importer", EnumArgumentType(FriendImportFormat::class.java, "importer"))
               .executes { context: CommandContext<Any?> ->
                  val importer = context.getArgument("importer", FriendImportFormat::class.java)

                  val importedNames: List<String>
                  try {
                     importedNames = importer.readFriendNames()
                  } catch (failure: Exception) {
                     if (failure is FileNotFoundException) {
                        val missingFileText = String.format(
                           "Couldn't find %s friends file", importer.displayName
                        )
                        val missingFileMessage = Component.literal(missingFileText)
                           .withStyle { style -> style.withColor(ChatFormatting.RED) }

                        ChatUtils.printChat(
                           missingFileMessage, ChatUtils.createSignature(-1), ChatUtils.MessagePriority.LOW
                        )
                     } else {
                        failure.printStackTrace()

                        val failureText = String.format(
                           "Couldn't import friends from %s due to an unknown error, check your log file",
                           importer.displayName
                        )
                        val failureMessage = Component.literal(failureText)
                           .withStyle { style -> style.withColor(ChatFormatting.RED) }

                        ChatUtils.printChat(
                           failureMessage, ChatUtils.createSignature(-1), ChatUtils.MessagePriority.LOW
                        )
                     }

                     return@executes 1
                  }

                  var addedCount = 0
                  val namesIterator = importedNames.iterator()

                  while (namesIterator.hasNext()) {
                     val name = namesIterator.next()
                     val socials = ClientContext.socialRegistry

                     if (!socials.isFriendName(name)) {
                        val friendSocials = ClientContext.socialRegistry

                        friendSocials.addFriend(name)
                        addedCount++
                     }
                  }

                  val completionText = String.format(
                     "Added %d new friends from %s", addedCount, importer.displayName
                  )
                  val completionMessage = Component.literal(completionText)

                  ChatUtils.printChat(completionMessage, ChatUtils.createSignature(-1))
                  return@executes 1
               }
         val syncBranch = commandBuilder.then(importerCommand)
         root.then(syncBranch)
      }
   }

   private fun formatRoleTemplate(template: String): String {
      val lowercaseFormatted = template.replace("%s", role.pluralName)

      return lowercaseFormatted.replace("%S", FontRenderer.capitalize(role.pluralName))
   }

   private fun formatNames(names: Collection<String>): Component {
      val nameComponents = names.stream().map { text -> this.formatText(text) }.toList()

      return ComponentUtils.formatList(nameComponents, Component.nullToEmpty(", "))
   }

   private fun suggestRegisteredNames(
      unusedContext: CommandContext<Any?>, builder: SuggestionsBuilder
   ): CompletableFuture<Suggestions> {
      val fullInput = builder.input
      val lastSpaceOffset = fullInput.lastIndexOf(" ")
      val offset: Int
      if (lastSpaceOffset != -1) {
         val capturedInput = builder.input
         if (lastSpaceOffset != capturedInput.length) {
            offset = lastSpaceOffset + 1
         } else {
            offset = builder.start
         }
      } else {
         offset = builder.start
      }

      val offsetBuilder = builder.createOffset(offset)
      val socials = ClientContext.socialRegistry
      val registeredNames = (socials.getRegistry() as List<SocialEntry>).stream()
         .filter { entry: SocialEntry ->
            entry.role() == role
         }
         .map { entry: SocialEntry -> entry.getName() }
      return SharedSuggestionProvider.suggest(registeredNames, offsetBuilder)
   }
}

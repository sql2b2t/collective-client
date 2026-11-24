package me.collective.client.commands

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.modules.render.WaypointsModule
import me.collective.client.settings.options.DimensionType
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.utils.world.Waypoint
import me.collective.client.utils.world.WorldUtils

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.function.Predicate
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentUtils
import net.minecraft.world.phys.Vec3

class WaypointsCommand : Command("waypoints") {
   init {
      this.setAliases("wp")
   }

   @Suppress("UNCHECKED_CAST")
   override fun exec(root: LiteralArgumentBuilder<*>) {
      val rootBuilder = root as LiteralArgumentBuilder<Any?>
      var operationBuilder: LiteralArgumentBuilder<Any?> = literal("add")

      var operationArgument: RequiredArgumentBuilder<Any?, *> =
         argument("name", FancyStringArgument.fancyString())

      val positionBranch: RequiredArgumentBuilder<Any?, *> = argument("pos", PositionArgument())
         .executes { context: CommandContext<Any?> ->
            val name = context.getArgument("name", String::class.java)

            val position = context.getArgument("pos", Vec3::class.java)

            val currentDimension = WorldUtils.getDimension()
            val dimensionDisplayName = currentDimension.getDisplayName()
            val dimensionKey = dimensionDisplayName.lowercase(Locale.ROOT)

            val waypoint = Waypoint(
               name, position, dimensionKey, getCurrentServerKey()
            )
            val waypointRegistry = ClientContext.waypointRegistry
            waypointRegistry.registerWaypoint(waypoint)

            val messagePrefix = "Waypoint "
            val message = Component.literal(messagePrefix)
            val messageWithWaypoint = message.append(waypoint.getDisplayComponent())

            val messageSuffix = " has been created"
            val messageForSuffix = messageWithWaypoint
            ChatUtils.printChat(messageForSuffix.append(messageSuffix), -1)
            return@executes 1
         }

      val dimensionBranch: ArgumentBuilder<Any?, *> =
         argument("dimension", EnumArgumentType(DimensionType::class.java, "dimension"))
            .executes { context: CommandContext<Any?> ->
               val name = context.getArgument("name", String::class.java)

               val position = context.getArgument("pos", Vec3::class.java)

               val dimension = context.getArgument("dimension", DimensionType::class.java)
               val dimensionDisplayName = dimension.getDisplayName()
               val dimensionKey = dimensionDisplayName.lowercase(Locale.ROOT)

               val waypoint = Waypoint(
                  name, position, dimensionKey, getCurrentServerKey()
               )
               val waypointRegistry = ClientContext.waypointRegistry
               waypointRegistry.registerWaypoint(waypoint)

               val messagePrefix = "Waypoint "
               val message = Component.literal(messagePrefix)
               val messageWithWaypoint = message.append(waypoint.getDisplayComponent())

               val messageSuffix = " has been created"
               val messageForSuffix = messageWithWaypoint
               ChatUtils.printChat(messageForSuffix.append(messageSuffix), -1)
               return@executes 1
            }
      val positionWithDimension: ArgumentBuilder<Any?, *> = positionBranch.then(dimensionBranch)
      val addBranch: RequiredArgumentBuilder<Any?, *> = (operationArgument.then(positionWithDimension))
         .executes { context: CommandContext<Any?> ->
            val name = context.getArgument("name", String::class.java)

            val currentDimension = WorldUtils.getDimension()
            val dimensionDisplayName = currentDimension.getDisplayName()
            val dimensionKey = dimensionDisplayName.lowercase(Locale.ROOT)
            val playerForPosition = ClientAccess.collectiveMinecraft.player!!
            val playerPosition = playerForPosition.position()

            val waypoint = Waypoint(
               name, playerPosition, dimensionKey, getCurrentServerKey()
            )
            val waypointRegistry = ClientContext.waypointRegistry
            waypointRegistry.registerWaypoint(waypoint)

            val messagePrefix = "Waypoint "
            val message = Component.literal(messagePrefix)
            val messageWithWaypoint = message.append(waypoint.getDisplayComponent())

            val messageSuffix = " has been created"
            val messageForSuffix = messageWithWaypoint
            ChatUtils.printChat(messageForSuffix.append(messageSuffix), -1)
            return@executes 1
         }
      val addTree = operationBuilder.then(addBranch)
      var rootWithCommands: LiteralArgumentBuilder<Any?> = rootBuilder.then(addTree)

      val removeAliasBuilder: RequiredArgumentBuilder<Any?, *> = aliasedLiteral("remove", "delete", "del")

      operationArgument = argument("server", StringArgumentType.string())
         .suggests { context: CommandContext<Any?>, builder: SuggestionsBuilder ->
            suggestServers(context, builder)
         }

      val removeNameBranch: ArgumentBuilder<Any?, *> = argument("name", StringArgumentType.string())
         .suggests { context: CommandContext<Any?>, builder: SuggestionsBuilder ->
            suggestWaypointNames(context, builder)
         }
         .executes { context: CommandContext<Any?> ->
            val name = context.getArgument("name", String::class.java)

            val server = context.getArgument("server", String::class.java)
            val waypointRegistry = ClientContext.waypointRegistry
            (waypointRegistry.getRegistry() as MutableList<Waypoint>).removeIf { candidate: Waypoint ->
               val candidateServer = candidate.server
               if (server.equals(candidateServer, ignoreCase = true)) {
                  val candidateName = candidate.getName()
                  if (name.equals(candidateName, ignoreCase = true)) {
                     return@removeIf true
                  }
               }

               return@removeIf false
            }

            val messagePrefix = "Waypoint "
            val message = Component.literal(messagePrefix)
            val messageWithName = message.append(name)

            val messageSuffix = " has been removed"
            val messageForSuffix = messageWithName
            ChatUtils.printChat(messageForSuffix.append(messageSuffix), -1)
            return@executes 1
         }
      val removeServerTree = operationArgument.then(removeNameBranch)
      val removeTree = removeAliasBuilder.then(removeServerTree)
      rootWithCommands = rootWithCommands.then(removeTree)

      operationBuilder = literal("rename")

      operationArgument = argument("server", StringArgumentType.string())
         .suggests { context: CommandContext<Any?>, builder: SuggestionsBuilder ->
            suggestServers(context, builder)
         }

      val renameNameBuilder: RequiredArgumentBuilder<Any?, *> = argument("name", StringArgumentType.string())
         .suggests { context: CommandContext<Any?>, builder: SuggestionsBuilder ->
            suggestWaypointNames(context, builder)
         }

      val renameTargetBranch: ArgumentBuilder<Any?, *> = argument("target", StringArgumentType.string())
         .executes { context: CommandContext<Any?> ->
            val name = context.getArgument("name", String::class.java)

            val server = context.getArgument("server", String::class.java)

            val targetName = context.getArgument("target", String::class.java)
            val matchesWaypoint = Predicate<Waypoint> { candidate ->
               val candidateServer = candidate.server
               if (server.equals(candidateServer, ignoreCase = true)) {
                  val candidateName = candidate.getName()
                  if (name.equals(candidateName, ignoreCase = true)) {
                     return@Predicate true
                  }
               }

               return@Predicate false
            }
            val waypoint = ClientContext.waypointRegistry.findFirst(matchesWaypoint).orElse(null) as Waypoint?
            if (waypoint == null) {
               val missingWaypointMessage = "Waypoint not found"
               ChatUtils.printChat(Component.nullToEmpty(missingWaypointMessage), -1)
               return@executes 1
            } else {
               val registryForRemoval = ClientContext.waypointRegistry
               (registryForRemoval.getRegistry() as MutableList<Waypoint>).removeIf(matchesWaypoint)
               val registryForReplacement = ClientContext.waypointRegistry
               val replacementRegistry = registryForReplacement.getRegistry() as MutableList<Waypoint>

               val waypointPosition = waypoint.getPosition()

               val waypointDimension = waypoint.dimension

               replacementRegistry.add(
                  Waypoint(
                     targetName,
                     waypointPosition,
                     waypointDimension,
                     waypoint.server
                  )
               )

               val messagePrefix = "Waypoint "
               var renameMessage = Component.literal(messagePrefix)
               renameMessage = renameMessage.append(name)

               val messageSuffix = " has been renamed to"
               val messageForSuffix = renameMessage
               renameMessage = messageForSuffix.append(messageSuffix)
               renameMessage = renameMessage.append(targetName)
               ChatUtils.printChat(renameMessage, -1)
               return@executes 1
            }
         }
      val renameNameTree = renameNameBuilder.then(renameTargetBranch)
      val renameServerTree = operationArgument.then(renameNameTree)
      val renameTree = operationBuilder.then(renameServerTree)
      rootWithCommands = rootWithCommands.then(renameTree)

      val listBranch: ArgumentBuilder<Any?, *> = literal("list").executes { context: CommandContext<Any?> ->
         val waypointRegistry = ClientContext.waypointRegistry
         val waypointComponents = (waypointRegistry.getRegistry() as List<Waypoint>).stream()
            .sorted(Comparator.comparing<Waypoint, Boolean> { waypoint -> waypoint.toggled }.reversed())
            .map { waypoint -> waypoint.getDisplayComponent() }
            .toList()
         val message = Component.empty()

         val messagePrefix = "Waypoints list: "
         message.append(messagePrefix)

         message.append(ComponentUtils.formatList(waypointComponents, Component.literal(", ")))
         ChatUtils.printChat(message, -1)
         return@executes 1
      }
      rootWithCommands.then(listBranch)

      operationBuilder = literal("toggle")

      val toggleNameBranch: ArgumentBuilder<Any?, *> = argument("name", StringArgumentType.string())
         .suggests { context: CommandContext<Any?>, builder: SuggestionsBuilder ->
            suggestWaypointNames(context, builder)
         }
         .executes { context: CommandContext<Any?> ->
            val name = context.getArgument("name", String::class.java)

            val server = getCurrentServerKey()
            val matchesWaypoint = Predicate<Waypoint> { candidate ->
               val candidateServer = candidate.server
               if (server.equals(candidateServer, ignoreCase = true)) {
                  val candidateName = candidate.getName()
                  if (name.equals(candidateName, ignoreCase = true)) {
                     return@Predicate true
                  }
               }

               return@Predicate false
            }
            val waypoint = ClientContext.waypointRegistry.findFirst(matchesWaypoint).orElse(null) as Waypoint?
            if (waypoint == null) {
               val missingWaypointMessage = "Waypoint not found"
               ChatUtils.printChat(Component.nullToEmpty(missingWaypointMessage), -1)
               return@executes 1
            }

            val newVisibility = !waypoint.toggled
            waypoint.toggled = newVisibility

            val visibilityName: String
            if (waypoint.toggled) {
               visibilityName = "visible"
            } else {
               visibilityName = "invisible"
            }
            val toggleMessage = String.format("Made the waypoint %s %s.", name, visibilityName)
            ChatUtils.printChat(Component.literal(toggleMessage), -1)
            return@executes 1
         }
      val toggleTree = operationBuilder.then(toggleNameBranch)
      rootBuilder.then(toggleTree)
      if (ClientContext.baritoneBridge.isAvailable()) {
         val elytraCommand = ElytraCommand()
         this.addSubcommand(elytraCommand, root)
         val walkCommand = WaypointWalkCommand()
         this.addSubcommand(walkCommand, root)
      }
   }

   companion object {
      @JvmStatic
      fun getCurrentServerKey(): String {
         return me.collective.client.utils.ServerAddressUtils.getCurrentServerAddress()
      }

      @JvmStatic
      fun resolveWaypointPosition(context: CommandContext<Any?>): Vec3? {
         val name = context.getArgument("name", String::class.java)
         val waypointResult = ClientContext.waypointRegistry.findFirst(Predicate<Waypoint> { candidate ->
            val server = getCurrentServerKey()

            candidate.matches(name, server)
         })
         val waypoint = waypointResult.orElse(null) as Waypoint?
         if (waypoint == null) {
            return null
         }

         return WaypointsModule.getPositionInCurrentDimension(waypoint)
      }

      @JvmStatic
      fun suggestNamesForServer(
         context: CommandContext<Any?>, builder: SuggestionsBuilder, server: String
      ): CompletableFuture<Suggestions> {
         val waypointRegistry = ClientContext.waypointRegistry
         val names = (waypointRegistry.getRegistry() as List<Waypoint>).stream()
            .filter { candidate: Waypoint ->
               val candidateServerSnapshot = candidate.server
               candidateServerSnapshot.equals(server, ignoreCase = true)
            }
            .map { waypoint -> waypoint.getName() }
         return SharedSuggestionProvider.suggest(names, builder)
      }

      private fun formatServerSuggestion(waypoint: Waypoint): String {
         val server = waypoint.server
         val lowercaseServer = server.lowercase(Locale.ROOT)
         if (lowercaseServer.contains(":")) {
            return "\"" + lowercaseServer + "\""
         } else {
            return lowercaseServer
         }
      }

      private fun suggestServers(
         context: CommandContext<Any?>, builder: SuggestionsBuilder
      ): CompletableFuture<Suggestions> {
         val waypointRegistry = ClientContext.waypointRegistry
         val servers = (waypointRegistry.getRegistry() as List<Waypoint>).stream()
            .map { waypoint -> formatServerSuggestion(waypoint) }
            .distinct()
         return SharedSuggestionProvider.suggest(servers, builder)
      }

      private fun suggestWaypointNames(
         context: CommandContext<Any?>, builder: SuggestionsBuilder
      ): CompletableFuture<Suggestions> {
         val server: String
         try {
            server = context.getArgument("server", String::class.java)
         } catch (missingArgument: IllegalArgumentException) {
            return suggestNamesForServer(context, builder, getCurrentServerKey())
         }

         return suggestNamesForServer(context, builder, server)
      }
   }
}

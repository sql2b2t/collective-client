package me.collective.client.commands.arguments

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import me.collective.client.ClientContext
import me.collective.client.utils.world.Waypoint
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture

class WaypointArgumentType : ArgumentType<Waypoint> {

    @Throws(CommandSyntaxException::class)
    override fun parse(reader: StringReader): Waypoint {
        val requestedName = reader.readString()
        val waypoints = ClientContext.waypointRegistry.getRegistry() as? List<Waypoint>
        if (waypoints != null) {
            for (waypoint in waypoints) {
                if (waypoint != null && waypoint.name != null) {
                    if (waypoint.name.equals(requestedName, ignoreCase = true)) {
                        return waypoint
                    }
                }
            }
        }

        val unknownWaypointError = DynamicCommandExceptionType { argument ->
            Component.literal(String.format(UNKNOWN_WAYPOINT_FORMAT, argument))
        }
        throw unknownWaypointError.create(requestedName)
    }

    override fun <S> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val registry = ClientContext.waypointRegistry
        val waypoints = (registry.getRegistry() as? List<Waypoint>) ?: emptyList()
        val invalidWaypoints = ArrayList<Waypoint>()

        for (waypoint in waypoints) {
            if (waypoint != null && (waypoint.server == null || waypoint.dimension == null || waypoint.name == null)) {
                invalidWaypoints.add(waypoint)
            }
        }

        // Чистка реестра не в методе подсказок: Brigadier может звать его из
        // async-потока, а unregisterWaypoint мутирует реестр и пишет на диск.
        if (invalidWaypoints.isNotEmpty()) {
            net.minecraft.client.Minecraft.getInstance().execute {
                for (invalid in invalidWaypoints) {
                    registry.unregisterWaypoint(invalid)
                }
            }
        }

        val names = waypoints.filterNotNull().filter { !invalidWaypoints.contains(it) }.mapNotNull { it.name }
        return SharedSuggestionProvider.suggest(names, builder)
    }

    companion object {
        private const val UNKNOWN_WAYPOINT_FORMAT = "Waypoint %s doesn't exists"

        @JvmStatic
        fun waypoint(): WaypointArgumentType = WaypointArgumentType()
    }
}

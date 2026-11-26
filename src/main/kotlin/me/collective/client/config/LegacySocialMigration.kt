package me.collective.client.config

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.nio.file.Path
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.managers.ConfigurationManager
import me.collective.client.managers.SocialRegistry
import me.collective.client.utils.FileUtils

object LegacySocialMigration : ClientAccess {
    @JvmStatic
    @Throws(IOException::class)
    fun migrate() {
        val friendsPath: Path = ConfigurationManager.ROOT_DIRECTORY.resolve("friends.json")
        val socialsPath: Path = ConfigurationManager.ROOT_DIRECTORY.resolve("socials.json")
        if (!friendsPath.toFile().exists()) {
            return
        }
        if (socialsPath.toFile().exists()) {
            return
        }

        val friendsText = FileUtils.readUtf8(friendsPath)
        val friendsElement = try {
            JsonParser.parseString(friendsText)
        } catch (parseFailure: RuntimeException) {
            // Unreadable legacy file: keep it for manual recovery, skip migration.
            return
        }
        if (!friendsElement.isJsonObject) {
            return
        }
        val friendsRoot = friendsElement.asJsonObject
        val friends = friendsRoot.get("friends")?.takeIf { it.isJsonArray }?.asJsonArray
        if (friends != null) {
            val friendIterator = friends.iterator()
            while (friendIterator.hasNext()) {
                val friend: JsonElement = friendIterator.next()
                if (!friend.isJsonPrimitive) {
                    continue
                }
                try {
                    val registry: SocialRegistry = ClientContext.socialRegistry
                    val name = friend.asString
                    registry.addFriend(name)
                } catch (ignored: Exception) {
                }
            }
        }

        val gson = ClientAccess.gson
        val registry: SocialRegistry = ClientContext.socialRegistry
        val socials = registry.toJson()
        FileUtils.writeUtf8(socialsPath, gson.toJson(socials))
        friendsPath.toFile().delete()
    }
}

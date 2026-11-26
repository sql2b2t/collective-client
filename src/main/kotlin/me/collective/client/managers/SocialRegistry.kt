package me.collective.client.managers

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.config.EnumJsonConverter
import me.collective.client.modules.client.ColorsModule
import me.collective.client.modules.misc.SocialEntry
import me.collective.client.settings.JsonConfigurable
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.options.SocialRole
import me.collective.client.utils.entity.PlayerState
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import java.awt.Color
import java.util.function.Predicate

class SocialRegistry : Registry<SocialEntry>(), ClientAccess, JsonConfigurable {

    fun isFriendName(name: String): Boolean = getRole(name) == SocialRole.FRIEND

    fun addFriend(name: String) {
        removeSocial(name)
        registerSocial(SocialEntry(name, SocialRole.FRIEND))
    }

    val enemyColor: Color
        @JvmName("getEnemyColorProp")
        get() = getEnemyColor()

    val friendColor: Color
        @JvmName("getFriendColorProp")
        get() = getFriendColor()

    fun getEnemyColor(): Color = colorsModule().enemyColorSetting.getValue()

    fun getFriendColor(): Color = colorsModule().friendColorSetting.getValue()

    fun isFriendEntity(entity: Entity?): Boolean {
        return entity is Player && isFriendPlayer(entity)
    }

    override fun register(entry: Any): Boolean {
        return registerSocial(entry as SocialEntry)
    }

    fun registerSocial(entry: SocialEntry): Boolean {
        updatePlayerRole(entry.getName(), entry.role())
        return super.registerEntry(entry)
    }

    override fun fromJson(json: JsonElement) {
        val root = json.asJsonObject
        if (root.has("socials")) {
            val socials = root.getAsJsonArray("socials")
            for (entryElement in socials) {
                val entryObject = entryElement.asJsonObject
                if (entryObject.has("name") && entryObject.has("role")) {
                    val name = entryObject.get("name").asString
                    val role = roleConverter.fromJson(entryObject.get("role"))
                    registerSocial(SocialEntry(name, role))
                }
            }
        }
    }

    override fun toJson(): JsonElement {
        val root = JsonObject()
        val socials = JsonArray()
        for (social in entries) {
            val entryObject = JsonObject()
            entryObject.addProperty("name", social.getName())
            entryObject.add("role", roleConverter.toJson(social.role()))
            socials.add(entryObject)
        }
        root.add("socials", socials)
        return root
    }

    fun addEnemy(name: String) {
        removeSocial(name)
        registerSocial(SocialEntry(name, SocialRole.ENEMY))
    }

    fun isEnemyName(name: String): Boolean = getRole(name) == SocialRole.ENEMY

    override fun getConfigName(): String = "socials.json"

    fun getRole(name: String): SocialRole? {
        return findFirstOrNull(Predicate { entry ->
            entry.getName().equals(name, ignoreCase = true)
        })?.role()
    }

    fun isEnemyEntity(entity: Entity?): Boolean {
        return entity is Player && isEnemyPlayer(entity)
    }

    fun getColor(name: String, defaultColor: Color): Color {
        return when {
            isFriendName(name) -> getFriendColor()
            isEnemyName(name) -> getEnemyColor()
            else -> defaultColor
        }
    }

    @JvmSynthetic
    @Suppress("UNUSED_PARAMETER")
    fun getColor(name: String, defaultColor: Nothing?): Color? = getColorOrNull(name)

    fun getColorOrNull(name: String): Color? {
        return when {
            isFriendName(name) -> getFriendColor()
            isEnemyName(name) -> getEnemyColor()
            else -> null
        }
    }

    private fun updatePlayerRole(name: String, role: SocialRole?) {
        if (isWorldUnavailable()) {
            return
        }
        val level = ClientAccess.collectiveMinecraft.level ?: return
        for (player in level.players()) {
            val profile = player.gameProfile
            if (profile.name().equals(name, ignoreCase = true) && player is PlayerState) {
                if (role != null) {
                    player.`collective$setRole`(role)
                } else {
                    setRoleMethod.invoke(player, null)
                }
                return
            }
        }
    }

    override fun registerEntry(entry: NamedEntry?): Boolean {
        return registerSocial(entry as SocialEntry)
    }

    fun removeSocial(name: String): Boolean {
        updatePlayerRole(name, null)
        return entries.removeIf(Predicate { entry ->
            entry.getName().equals(name, ignoreCase = true)
        })
    }

    fun isEnemyPlayer(player: Player): Boolean {
        val roleHolder = player as PlayerState
        return roleHolder.`collective$getRole`() == SocialRole.ENEMY
    }

    fun isFriendPlayer(player: Player): Boolean {
        val roleHolder = player as PlayerState
        return roleHolder.`collective$getRole`() == SocialRole.FRIEND
    }

    companion object {
        private var colorsModule: ColorsModule? = null
        private val roleConverter = EnumJsonConverter(SocialRole::class.java)
        private val setRoleMethod by lazy {
            PlayerState::class.java.getMethod("collective\$setRole", SocialRole::class.java)
        }

        private fun colorsModule(): ColorsModule {
            if (colorsModule == null) {
                colorsModule = ClientContext.getModule(ColorsModule::class.java)
            }
            return colorsModule!!
        }
    }
}

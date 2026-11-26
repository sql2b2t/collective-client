package me.collective.client.bots

import com.mojang.authlib.GameProfile
import net.minecraft.client.User
import net.minecraft.core.UUIDUtil
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BotProfile(
    var name: String,
    var serverAddress: String,
    var proxyId: String = "",
    var accessToken: String = "",
    customUuid: UUID? = null,
    var taskStatus: BotTaskStatus = BotTaskStatus.IDLE,
    var role: BotRole = BotRole.BUYER,
    var protocolVersion: String = "",
    var skinSeed: Int = 101,
    var targetSlot: Int = -1,
    var anarchyServerNumber: Int = 0,
    var bankNumber: Int = 0,
    val selectedSlots: MutableSet<Int> = ConcurrentHashMap.newKeySet(),
    val id: String = UUID.randomUUID().toString()
) {
    var rawUuid: UUID? = customUuid

    val username: String get() = name

    val uuid: UUID get() = rawUuid ?: UUIDUtil.createOfflinePlayerUUID(name)

    fun getEffectiveUuid(): UUID = uuid

    val isOnlineMode: Boolean get() = isOnlineAuth()

    fun createUser(): User {
        val token = if (accessToken.isBlank() || accessToken == "0") "0" else accessToken
        return User(name, uuid, token, Optional.empty(), Optional.empty())
    }

    fun toGameProfile(): GameProfile {
        return GameProfile(uuid, name)
    }

    fun isOnlineAuth(): Boolean = accessToken.isNotBlank() && accessToken != "0"

    companion object {
        fun fromUser(user: User, serverAddress: String): BotProfile {
            return BotProfile(
                name = user.name,
                serverAddress = serverAddress,
                accessToken = user.accessToken,
                customUuid = user.profileId
            )
        }
    }
}

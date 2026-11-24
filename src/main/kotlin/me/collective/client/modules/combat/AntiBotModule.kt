package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.commands.FakeplayerCommand
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.Setting
import net.minecraft.world.entity.Entity.RemovalReason
import net.minecraft.world.entity.player.Player
import java.util.UUID

class AntiBotModule : Module("AntiBot", "Removes the bots that will be spawned by some anti cheats.", ModuleCategory.COMBAT) {
    private val illegalCharsSetting: Setting<Boolean> = add(BooleanSetting("IllegalChars", false))
    private val illegalNamesSetting: Setting<Boolean> = add(BooleanSetting("IllegalNames", false))
    private val firstSeenAt: MutableMap<UUID, Long> = HashMap()

    @EventHandler
    private fun onPlayerTick(event: LocalPlayerTickEvent) {
        val level = ClientAccess.collectiveMinecraft.level ?: return
        val now = System.currentTimeMillis()
        val presentIds = HashSet<UUID>()
        val bots = ArrayList<Player>()

        for (candidate in level.players()) {
            val candidateId = candidate.uuid
            presentIds.add(candidateId)
            firstSeenAt.putIfAbsent(candidateId, now)
            if (isBot(candidate, now - firstSeenAt[candidateId]!!)) {
                bots.add(candidate)
            }
        }

        // Флаги наблюдения снимаются вместе с исчезновением игрока из мира.
        firstSeenAt.keys.retainAll(presentIds)

        // Снимаются все найденные боты: раньше за тик удалялся только последний.
        for (bot in bots) {
            bot.setRemoved(RemovalReason.KILLED)
        }
    }

    private fun isBot(candidate: Player, observedMillis: Long): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        if (player === candidate || candidate is FakeplayerCommand.SimulatedPlayer) {
            return false
        }

        val name = candidate.gameProfile.name ?: return true

        val connectionSnapshot = player.connection
        val profileId = candidate.gameProfile.id
        val playerInfo = connectionSnapshot.getPlayerInfo(profileId)

        // Запись таб-листа с совпадающим профилем подтверждает игрока:
        // его имя и скин приходят из tablist, эвристики к нему не применяются.
        val tablistProfile = playerInfo?.profile
        if (tablistProfile != null && tablistProfile.name == name) {
            return false
        }

        val nameLength = name.length
        if (illegalNamesSetting.value) {
            if (nameLength < 3 || nameLength > 16) {
                return true
            }
        }

        if (illegalCharsSetting.value) {
            for (characterIndex in 0 until nameLength) {
                val character = name[characterIndex]
                val lowercaseCharacter = character.lowercaseChar()
                if (lowercaseCharacter in '0'..'9') {
                    continue
                }

                if (lowercaseCharacter != '_') {
                    if (lowercaseCharacter < 'a' || lowercaseCharacter > 'z') {
                        return true
                    }
                }
            }
        }

        // Без подтверждающей записи таб-листа бот помечается только после паузы:
        // при входе/телепорте запись может прийти позже сущности.
        return observedMillis >= TABLIST_GRACE_MILLIS
    }

    companion object {
        private const val TABLIST_GRACE_MILLIS = 3000L
    }
}

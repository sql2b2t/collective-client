package me.collective.client.bots.world

import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level

object BotEntityUtils {
    @JvmStatic
    fun isBotEntity(entity: Entity?): Boolean {
        return entity != null && entity.level() is BotClientLevel
    }

    @JvmStatic
    fun isBotLevel(level: Level?): Boolean {
        return level is BotClientLevel
    }
}

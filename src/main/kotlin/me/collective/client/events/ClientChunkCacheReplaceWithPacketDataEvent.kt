package me.collective.client.events

import net.minecraft.world.level.chunk.LevelChunk

class ClientChunkCacheReplaceWithPacketDataEvent(val chunk: LevelChunk) : CancellableEvent()

package me.collective.client

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import me.collective.client.events.bus.EventBus
import me.collective.client.utils.inventory.ItemRegistryJsonAdapter
import net.minecraft.client.Minecraft
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

interface ClientAccess {
    fun isWorldUnavailable(): Boolean {
        return collectiveMinecraft.player == null || collectiveMinecraft.level == null
    }

    companion object {
        @JvmField
        val logger: Logger = LoggerFactory.getLogger(CollectiveClient.NAME)

        @JvmField
        val collectiveMinecraft: Minecraft = Minecraft.getInstance()

        @JvmField
        val gson: Gson = GsonBuilder()
            .registerTypeAdapter(Item::class.java, ItemRegistryJsonAdapter.ItemAdapter())
            .registerTypeAdapter(BlockItem::class.java, ItemRegistryJsonAdapter.BlockItemAdapter())
            .setPrettyPrinting()
            .create()

        @JvmField
        val eventBus: EventBus = EventBus()

        @JvmField
        val executorService: ExecutorService = Executors.newCachedThreadPool()

        @JvmStatic
        fun hasBundledLanguageResource(): Boolean {
            return ClientAccess::class.java.getResource("/assets/collective/lang/en_us.ini") != null
        }

        @JvmStatic
        fun getVersion(): String {
            return CollectiveClient.VERSION
        }

        @JvmStatic
        fun isLocalPlayer(candidate: Any?): Boolean {
            return collectiveMinecraft.player === candidate
        }
    }
}

val ClientAccess.isWorldUnavailable: Boolean
    get() = isWorldUnavailable()

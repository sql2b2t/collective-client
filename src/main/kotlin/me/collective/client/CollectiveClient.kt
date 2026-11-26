package me.collective.client

import me.collective.client.bootstrap.ClientInitializer
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class CollectiveClient : ModInitializer, ClientModInitializer {
    override fun onInitialize() {
        INITIALIZER.initialize()
    }

    override fun onInitializeClient() {
        INITIALIZER.initializeClient()
    }

    companion object {
        const val MOD_ID: String = "collective"
        const val NAME: String = "Collective"
        const val PROJECT_URL: String = "https://github.com/Cypphi/open-collective"

        @JvmField
        val VERSION: String = FabricLoader.getInstance().getModContainer(MOD_ID)
            .orElseThrow { IllegalStateException("Collective mod metadata is missing") }
            .metadata.version.friendlyString

        @JvmField
        val USER_AGENT: String = "$NAME/$VERSION"

        @JvmField
        val LOGGER: Logger = LoggerFactory.getLogger(NAME)

        private val INITIALIZER = ClientInitializer()
    }
}

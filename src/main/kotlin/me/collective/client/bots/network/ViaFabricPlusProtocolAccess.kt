package me.collective.client.bots.network

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.network.Connection
import java.net.InetSocketAddress

object ViaFabricPlusProtocolAccess {
    private var cachedIsLoaded: Boolean? = null
    private var cachedProtocols: List<String>? = null

    fun isLoaded(): Boolean {
        var loaded = cachedIsLoaded
        if (loaded == null) {
            loaded = checkLoaded()
            cachedIsLoaded = loaded
        }
        return loaded
    }

    private fun checkLoaded(): Boolean {
        if (!FabricLoader.getInstance().isModLoaded("viafabricplus")) return false
        return try {
            Class.forName("com.viaversion.viaversion.api.protocol.version.ProtocolVersion")
            Class.forName("com.viaversion.viafabricplus.ViaFabricPlus")
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun getProtocols(): List<String> {
        var list = cachedProtocols
        if (list == null) {
            list = loadProtocols()
            cachedProtocols = list
        }
        return list
    }

    private fun loadProtocols(): List<String> {
        if (!isLoaded()) return emptyList()
        return try {
            val protoVerClass = Class.forName("com.viaversion.viaversion.api.protocol.version.ProtocolVersion")
            val protocols = protoVerClass.getMethod("getProtocols").invoke(null) as? List<*> ?: return emptyList()
            val isKnownMethod = protoVerClass.getMethod("isKnown")
            val getNameMethod = protoVerClass.getMethod("getName")

            val result = ArrayList<String>(protocols.size)
            for (p in protocols) {
                if (p != null && (isKnownMethod.invoke(p) as? Boolean) == true) {
                    val name = getNameMethod.invoke(p) as? String
                    if (name != null) result.add(name)
                }
            }
            result.reversed()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun getCurrentVersionName(): String {
        if (!isLoaded()) return ""
        return try {
            val vfp = Class.forName("com.viaversion.viafabricplus.ViaFabricPlus").getMethod("getImpl").invoke(null)
            val connection = Minecraft.getInstance().connection?.connection
            val target = if (connection != null) {
                vfp.javaClass.getMethod("getTargetVersion", Connection::class.java).invoke(vfp, connection)
                    ?: vfp.javaClass.getMethod("getTargetVersion").invoke(vfp)
            } else {
                vfp.javaClass.getMethod("getTargetVersion").invoke(vfp)
            }
            if (target != null) {
                target.javaClass.getMethod("getName").invoke(target) as? String ?: ""
            } else ""
        } catch (_: Throwable) {
            ""
        }
    }

    fun injectProtocol(connection: Connection, serverAddress: ServerAddress, socketAddress: InetSocketAddress, requestedVersion: String) {
        setupProtocol(connection, serverAddress, socketAddress, requestedVersion)
    }

    fun setupProtocol(connection: Connection, serverAddress: ServerAddress, socketAddress: InetSocketAddress, requestedVersion: String) {
        if (!isLoaded()) return
        try {
            val protoVerClass = Class.forName("com.viaversion.viaversion.api.protocol.version.ProtocolVersion")
            val targetVersion: Any? = if (requestedVersion.isBlank()) {
                val vfp = Class.forName("com.viaversion.viafabricplus.ViaFabricPlus").getMethod("getImpl").invoke(null)
                val detectorClass = Class.forName("com.viaversion.viafabricplus.protocoltranslator.util.ProtocolVersionDetector")
                val autoDetect = detectorClass.getField("AUTO_DETECT_VERSION").get(null)
                val current = vfp.javaClass.getMethod("getTargetVersion").invoke(vfp)
                if (current == autoDetect) {
                    val nativeVer = Class.forName("com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslationImpl").getField("NATIVE_VERSION").get(null)
                    detectorClass.getMethod("get", ServerAddress::class.java, InetSocketAddress::class.java, Class::class.java)
                        .invoke(null, serverAddress, socketAddress, nativeVer)
                } else {
                    current
                }
            } else {
                protoVerClass.getMethod("getClosest", String::class.java).invoke(null, requestedVersion)
            }

            if (targetVersion != null) {
                val setter = connection.javaClass.getMethod("viaFabricPlus\$setTargetVersion", protoVerClass)
                setter.invoke(connection, targetVersion)
            }
        } catch (_: Throwable) {
        }
    }
}

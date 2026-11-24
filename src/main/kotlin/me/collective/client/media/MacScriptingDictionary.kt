package me.collective.client.media

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.LinkedHashSet
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import org.xml.sax.InputSource

object MacScriptingDictionary {
    enum class TimeUnit { SECONDS, MILLISECONDS }

    data class TimeProperty(
        @get:JvmName("code") val code: Int,
        @get:JvmName("unit") val unit: TimeUnit
    )

    data class Schema(
        @get:JvmName("playerState") val playerState: Int,
        @get:JvmName("currentTrack") val currentTrack: Int,
        @get:JvmName("title") val title: Int,
        @get:JvmName("artist") val artist: Int,
        @get:JvmName("playing") val playing: Int,
        @get:JvmName("paused") val paused: Int?,
        @get:JvmName("stopped") val stopped: Int?,
        @get:JvmName("position") val position: TimeProperty?,
        @get:JvmName("duration") val duration: TimeProperty?
    )

    @JvmStatic
    @Throws(IOException::class)
    fun readVendorFile(resourceDirectory: String, dictionaryFile: String): ByteArray {
        if (resourceDirectory.isBlank() || dictionaryFile.isBlank()) throw IOException("Installed app does not expose a static scripting dictionary")
        val resources: Path = Path.of(resourceDirectory).toRealPath()
        val relative: Path = Path.of(dictionaryFile)
        if (relative.isAbsolute) throw IOException("Dictionary path must be within app resources")
        for (part in relative) {
            if (part.toString() == "..") throw IOException("Dictionary path cannot escape app resources")
        }
        val lexical: Path = resources.resolve(relative).normalize()
        if (!lexical.startsWith(resources)) throw IOException("Dictionary path cannot escape app resources")
        val file: Path = lexical.toRealPath()
        if (!file.startsWith(resources) || !file.fileName.toString().endsWith(".sdef") || !Files.isRegularFile(file)) throw IOException("Unsupported app scripting dictionary path")
        Files.newInputStream(file).use { input ->
            val bytes = input.readNBytes(2097153)
            if (bytes.size > 2097152) throw IOException("Vendor dictionary exceeds bounded size")
            return bytes
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun parse(definition: ByteArray): Schema {
        if (definition.isEmpty() || definition.size > 2 * 1024 * 1024) throw IOException("Unsupported scripting dictionary size")
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            factory.setExpandEntityReferences(false)
            factory.isXIncludeAware = false
            val builder = factory.newDocumentBuilder()
            builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
            val document = builder.parse(ByteArrayInputStream(definition))
            if (document.documentElement.tagName != "dictionary") throw IOException("Invalid vendor scripting dictionary root")
            val properties = document.getElementsByTagName("property")
            val state = uniqueProperty(properties, "player state", null, true)
                ?: throw IOException("Vendor property unavailable: player state")
            val track = uniqueProperty(properties, "current track", null, true)
                ?: throw IOException("Vendor property unavailable: current track")
            val title = uniqueProperty(properties, "name", "text", true)
                ?: throw IOException("Vendor property unavailable: name")
            val artist = uniqueProperty(properties, "artist", "text", true)
                ?: throw IOException("Vendor property unavailable: artist")
            var enumDefinition: Element? = null
            val enums = document.getElementsByTagName("enumeration")
            for (n in 0 until enums.length) {
                val value = enums.item(n) as Element
                if (value.getAttribute("name") == state.getAttribute("type")) {
                    if (enumDefinition != null) throw IOException("Ambiguous vendor player-state enum")
                    enumDefinition = value
                }
            }
            if (enumDefinition == null) throw IOException("Vendor player-state enum unavailable")
            val playing = enumerator(enumDefinition, "playing")
            val paused = enumerator(enumDefinition, "paused")
            val stopped = enumerator(enumDefinition, "stopped")
            if (playing == null) throw IOException("Vendor playing enum unavailable")
            return Schema(
                code(state.getAttribute("code")), code(track.getAttribute("code")),
                code(title.getAttribute("code")), code(artist.getAttribute("code")),
                playing, paused, stopped,
                timeProperty(uniqueProperty(properties, "player position", null, false)),
                timeProperty(uniqueProperty(properties, "duration", null, false))
            )
        } catch (expected: IOException) {
            throw expected
        } catch (malformed: Exception) {
            throw IOException("Unsupported vendor scripting dictionary", malformed)
        }
    }

    @Throws(IOException::class)
    private fun uniqueProperty(properties: NodeList, name: String, type: String?, required: Boolean): Element? {
        var found: Element? = null
        val codes: MutableSet<Int> = LinkedHashSet()
        for (n in 0 until properties.length) {
            val value = properties.item(n) as Element
            if (value.getAttribute("name") != name) continue
            if (type != null && value.getAttribute("type") != type) continue
            codes.add(code(value.getAttribute("code")))
            if (found == null) found = value
        }
        if (codes.size > 1) throw IOException("Ambiguous vendor property: " + name)
        if (found == null && required) throw IOException("Vendor property unavailable: " + name)
        return found
    }

    @Throws(IOException::class)
    private fun enumerator(definition: Element, name: String): Int? {
        var found: Int? = null
        val values = definition.getElementsByTagName("enumerator")
        for (n in 0 until values.length) {
            val value = values.item(n) as Element
            if (value.getAttribute("name") == name) {
                val code = code(value.getAttribute("code"))
                if (found != null && found != code) throw IOException("Ambiguous vendor enum value")
                found = code
            }
        }
        return found
    }

    @Throws(IOException::class)
    private fun timeProperty(property: Element?): TimeProperty? {
        if (property == null) return null
        val description = (property.getAttribute("description") + " " + property.textContent).lowercase(Locale.ROOT)
        val milliseconds = description.contains("millisecond")
        val seconds = description.replace("millisecond", "").contains("second")
        if (milliseconds == seconds) return null
        if (property.getAttribute("type") !in setOf("real", "integer", "number")) return null
        return TimeProperty(code(property.getAttribute("code")), if (milliseconds) TimeUnit.MILLISECONDS else TimeUnit.SECONDS)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun code(value: String): Int {
        if (value.length != 4) throw IOException("Vendor property requires a four-character code")
        var code = 0
        for (n in 0 until 4) {
            val c = value[n]
            if (c.code < 32 || c.code > 126) throw IOException("Unsupported vendor four-character code")
            code = (code shl 8) or c.code
        }
        return code
    }

    @JvmStatic
    @Throws(IOException::class)
    fun millis(value: Double?, property: TimeProperty?): Long {
        if (value == null || property == null) return 0
        if (!value.isFinite() || value < 0) throw IOException("Invalid vendor media time")
        val millis = if (property.unit == TimeUnit.SECONDS) value * 1000 else value
        if (millis > Long.MAX_VALUE) throw IOException("Vendor media time exceeds long range")
        return millis.toLong()
    }
}

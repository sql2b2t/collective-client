package me.collective.client.media

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

internal class DbusMessageCodec {
    class Variant(signature: String, value: Any?) {
        private val signatureValue: String = signature
        private val variantValue: Any? = value

        fun signature(): String {
            return signatureValue
        }

        fun value(): Any? {
            return variantValue
        }
    }

    data class Layout(val order: ByteOrder, val type: Int, val fieldsBytes: Int, val bodyBytes: Int) {
        fun paddedFieldsBytes(): Int {
            return (fieldsBytes + 7) and -8
        }
    }

    data class Message(val type: Int, val replySerial: Long, val headers: Map<Int, Variant>, val arguments: List<Any?>)

    companion object {
        internal const val MAX_MESSAGE_BYTES = 16 * 1024 * 1024

        internal fun layout(fixed: ByteArray): Layout {
            if (fixed.size != 16) throw invalid("Fixed header length")
            val order = when (fixed[0].toInt()) {
                'l'.code -> ByteOrder.LITTLE_ENDIAN
                'B'.code -> ByteOrder.BIG_ENDIAN
                else -> null
            }
            if (order == null || fixed[3].toInt() != 1 || (fixed[2].toInt() and -8) != 0) throw invalid("Version, flags or byte order")
            val buffer = ByteBuffer.wrap(fixed).order(order)
            val body = buffer.getInt(4)
            val serial = buffer.getInt(8)
            val fields = buffer.getInt(12)
            if (serial == 0 || body < 0 || fields < 0 || fields > MAX_MESSAGE_BYTES - 23 || body > MAX_MESSAGE_BYTES - 23 - fields) throw invalid("Message size or serial")
            return Layout(order, fixed[1].toInt() and 255, fields, body)
        }

        internal fun decode(layout: Layout, paddedFields: ByteArray, body: ByteArray): Message {
            if (paddedFields.size != layout.paddedFieldsBytes() || body.size != layout.bodyBytes) throw invalid("Message lengths")
            val fields = Reader(paddedFields, layout.order)
            val headers = LinkedHashMap<Int, Variant>()
            while (fields.position < layout.fieldsBytes) {
                fields.align(8)
                if (fields.position >= layout.fieldsBytes) throw invalid("Trailing header alignment")
                val id = fields.byteValue()
                val value = fields.variant(0)
                if (id == 0 || headers.putIfAbsent(id, value) != null || fields.position > layout.fieldsBytes) throw invalid("Header field")
                val expected = when (id) {
                    1 -> "o"
                    2, 3, 4, 6, 7 -> "s"
                    5, 9 -> "u"
                    8 -> "g"
                    else -> null
                }
                if (expected != null && expected != value.signature()) throw invalid("Header field type")
            }
            fields.align(8)
            if (fields.position != paddedFields.size) throw invalid("Header padding")
            if (headers.containsKey(9) && headers[9]!!.value() as Long != 0L) throw invalid("Unix FD passing not negotiated")
            val hasPath = headers.containsKey(1)
            val hasMember = headers.containsKey(3)
            if (layout.type == 1 && (!hasPath || !hasMember) || layout.type == 4 && (!hasPath || !hasMember || !headers.containsKey(2))) throw invalid("Required request/signal headers")
            if ((layout.type == 2 || layout.type == 3) && !headers.containsKey(5) || layout.type == 3 && !headers.containsKey(4)) throw invalid("Required reply headers")
            val signature = if (headers.containsKey(8)) headers[8]!!.value() as String else ""
            val reader = Reader(body, layout.order)
            val arguments = ArrayList<Any?>()
            for (type in types(signature)) arguments.add(reader.value(type, 0))
            if (reader.position != body.size) throw invalid("Body signature/length")
            val replySerial = if (headers.containsKey(5)) headers[5]!!.value() as Long else 0L
            return Message(layout.type, replySerial, java.util.Collections.unmodifiableMap(headers), java.util.List.copyOf(arguments))
        }

        internal fun methodCall(serial: Int, destination: String, path: String, iface: String, member: String, arguments: List<String>): ByteArray {
            if (serial == 0) throw invalid("Zero serial")
            val fields = Writer()
            val body = Writer()
            fields.field(1, "o", path)
            fields.field(2, "s", iface)
            fields.field(3, "s", member)
            fields.field(6, "s", destination)
            val signature = "s".repeat(arguments.size)
            if (signature.isNotEmpty()) fields.field(8, "g", signature)
            for (argument in arguments) body.string(argument)
            val fieldsBytes = fields.bytes.size()
            fields.align(8)
            val fixed = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)

            fixed.put('l'.code.toByte()).put(1).put(2).put(1).putInt(body.bytes.size()).putInt(serial).putInt(fieldsBytes)
            val all = ByteArrayOutputStream()
            all.writeBytes(fixed.array())
            all.writeBytes(fields.bytes.toByteArray())
            all.writeBytes(body.bytes.toByteArray())
            if (all.size() > MAX_MESSAGE_BYTES) throw invalid("Request too large")
            return all.toByteArray()
        }

        private fun types(signature: String): List<String> {
            if (signature.length > 255) throw invalid("Signature length")
            val result = ArrayList<String>()
            var at = 0
            while (at < signature.length) {
                val end = typeEnd(signature, at, 0, false)
                result.add(signature.substring(at, end))
                at = end
            }
            return result
        }

        private fun typeEnd(signature: String, at: Int, depth: Int, dictionaryAllowed: Boolean): Int {
            if (depth > 32 || at >= signature.length) throw invalid("Signature nesting")
            val c = signature[at]
            val after = at + 1
            if ("ybnqiuxtdsoghv".indexOf(c) >= 0) return after
            if (c == 'a') return typeEnd(signature, after, depth + 1, true)
            if (c == '(' || c == '{' && dictionaryAllowed) {
                var position = after
                val start = after
                var count = 0
                val close = if (c == '(') ')' else '}'
                while (position < signature.length && signature[position] != close) {
                    position = typeEnd(signature, position, depth + 1, false)
                    count++
                }
                if (position >= signature.length || position == start || c == '{' && (count != 2 || "ybnqiuxtdsogh".indexOf(signature[start]) < 0)) throw invalid("Container signature")
                return position + 1
            }
            throw invalid("Unsupported signature type")
        }

        private fun alignment(type: String): Int {
            return when (type[0]) {
                'y', 'g', 'v' -> 1
                'n', 'q' -> 2
                'b', 'i', 'u', 's', 'o', 'a', 'h' -> 4
                'x', 't', 'd', '(', '{' -> 8
                else -> throw invalid("Type alignment")
            }
        }

        private fun invalid(detail: String): IOException {
            return IOException("Invalid D-Bus message: $detail")
        }

        private class Reader(bytes: ByteArray, order: ByteOrder) {
            private val bytes: ByteArray = bytes
            private val buffer: ByteBuffer = ByteBuffer.wrap(bytes).order(order)
            var position: Int = 0

            @Throws(IOException::class)
            fun require(count: Int) {
                if (count < 0 || count > bytes.size - position) throw invalid("Truncated value")
            }

            @Throws(IOException::class)
            fun align(alignment: Int) {
                val next = (position + alignment - 1) and -alignment
                require(next - position)
                while (position < next) {
                    if (bytes[position++] != 0.toByte()) throw invalid("Nonzero padding")
                }
            }

            @Throws(IOException::class)
            fun byteValue(): Int {
                require(1)
                return bytes[position++].toInt() and 255
            }

            @Throws(IOException::class)
            fun unsignedInt(): Long {
                require(4)
                val n = Integer.toUnsignedLong(buffer.getInt(position))
                position += 4
                return n
            }

            @Throws(IOException::class)
            fun size(): Int {
                val n = unsignedInt()
                if (n > MAX_MESSAGE_BYTES) throw invalid("Value too large")
                return n.toInt()
            }

            @Throws(IOException::class)
            fun text(signature: Boolean): String {
                val count = if (signature) byteValue() else size()
                require(count + 1)
                val text: String
                try {
                    text = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes, position, count))
                        .toString()
                } catch (malformed: CharacterCodingException) {
                    throw invalid("Invalid UTF-8")
                }
                position += count
                if (byteValue() != 0 || text.indexOf(0.toChar()) >= 0) throw invalid("String terminator")
                if (signature) types(text)
                return text
            }

            @Throws(IOException::class)
            fun variant(depth: Int): Variant {
                val signature = text(true)
                if (types(signature).size != 1) throw invalid("Variant must contain one type")
                return Variant(signature, value(signature, depth + 1))
            }

            @Throws(IOException::class)
            fun value(type: String, depth: Int): Any? {
                if (depth > 64) throw invalid("Value nesting")
                align(alignment(type))
                return when (type[0]) {
                    'y' -> byteValue()
                    'b' -> {
                        val n = unsignedInt()
                        if (n != 0L && n != 1L) throw invalid("Boolean value")
                        n == 1L
                    }
                    'n' -> {
                        require(2)
                        val n = buffer.getShort(position)
                        position += 2
                        n
                    }
                    'q' -> {
                        require(2)
                        val n = buffer.getShort(position).toInt() and 0xFFFF
                        position += 2
                        n
                    }
                    'i' -> {
                        require(4)
                        val n = buffer.getInt(position)
                        position += 4
                        n
                    }
                    'u' -> unsignedInt()
                    'x', 't' -> {
                        require(8)
                        val n = buffer.getLong(position)
                        position += 8
                        n
                    }
                    'd' -> {
                        require(8)
                        val n = buffer.getDouble(position)
                        position += 8
                        n
                    }
                    's', 'o' -> text(false)
                    'g' -> text(true)
                    'v' -> variant(depth)
                    'h' -> throw invalid("Unix FD type not negotiated")
                    'a' -> {
                        val length = size()
                        val element = type.substring(1)
                        align(alignment(element))
                        val end = position + length
                        if (end < position || end > bytes.size) throw invalid("Array length")
                        val values = ArrayList<Any?>()
                        while (position < end) {
                            values.add(value(element, depth + 1))
                            if (position > end) throw invalid("Array element length")
                        }
                        if (element[0] != '{') return java.util.List.copyOf(values)
                        val dictionary = LinkedHashMap<Any?, Any?>()
                        for (item in values) {
                            val pair = item as List<*>
                            if (dictionary.putIfAbsent(pair[0], pair[1]) != null) throw invalid("Duplicate dictionary key")
                        }
                        java.util.Collections.unmodifiableMap(dictionary)
                    }
                    '(', '{' -> {
                        val members = ArrayList<Any?>()
                        for (member in types(type.substring(1, type.length - 1))) members.add(value(member, depth + 1))
                        java.util.List.copyOf(members)
                    }
                    else -> throw invalid("Value type")
                }
            }
        }

        private class Writer {
            val bytes = ByteArrayOutputStream()

            fun align(alignment: Int) {
                while (bytes.size() and (alignment - 1) != 0) bytes.write(0)
            }

            fun integer(value: Int) {
                align(4)
                for (shift in 0 until 32 step 8) bytes.write(value ushr shift)
            }

            @Throws(IOException::class)
            fun string(value: String?) {
                if (value == null || value.indexOf(0.toChar()) >= 0) throw invalid("Outgoing string")
                val utf8 = value.toByteArray(StandardCharsets.UTF_8)
                integer(utf8.size)
                bytes.writeBytes(utf8)
                bytes.write(0)
            }

            @Throws(IOException::class)
            fun signature(value: String) {
                types(value)
                bytes.write(value.length)
                bytes.writeBytes(value.toByteArray(StandardCharsets.US_ASCII))
                bytes.write(0)
            }

            @Throws(IOException::class)
            fun field(code: Int, type: String, value: String?) {
                align(8)
                bytes.write(code)
                signature(type)
                if (type == "g") signature(value!!) else string(value)
            }
        }
    }
}

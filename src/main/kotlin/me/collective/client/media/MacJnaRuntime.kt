package me.collective.client.media

import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.PointerByReference
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

internal class MacJnaRuntime : MacApplicationMediaSource.RuntimeApi {
    private val foundation: NativeLibrary
    private val appkit: NativeLibrary
    private val appleEvents: NativeLibrary
    private val objc: NativeLibrary
    private val objcClass: Function
    private val selector: Function
    private val message: Function
    private val permission: Function
    private val owner: Thread = Thread.currentThread()
    private var scopeActive = false

    @Throws(IOException::class)
    constructor() {
        if (Native.POINTER_SIZE != 8) throw IOException("Unsupported Cocoa pointer ABI")
        foundation = NativeLibrary.getInstance(FOUNDATION)
        appkit = NativeLibrary.getInstance(APPKIT)
        appleEvents = NativeLibrary.getInstance(APPLE_EVENTS)
        objc = NativeLibrary.getInstance("objc", mapOf(Library.OPTION_STRING_ENCODING to StandardCharsets.UTF_8.name()))
        objcClass = objc.getFunction("objc_getClass")
        selector = objc.getFunction("sel_registerName")
        message = objc.getFunction("objc_msgSend")
        permission = appleEvents.getFunction("AEDeterminePermissionToAutomateTarget")
    }

    @Throws(IOException::class)
    override fun begin(): MacApplicationMediaSource.Scope {
        requireOwner()
        if (scopeActive) throw IOException("Nested Cocoa media read scope")
        val pool = required(pointer(type("NSAutoreleasePool"), "new"), "autorelease pool")
        scopeActive = true
        val drained = AtomicBoolean()
        return object : MacApplicationMediaSource.Scope {
            @Throws(IOException::class)
            override fun close() {
                requireOwner()
                if (drained.compareAndSet(false, true)) {
                    try {
                        voidMessage(pool, "drain")
                    } finally {
                        scopeActive = false
                    }
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun requireOwner() {
        if (Thread.currentThread() !== owner) throw IOException("Cocoa media objects are worker-owned")
    }

    @Throws(IOException::class)
    private fun requireScope() {
        requireOwner()
        if (!scopeActive) throw IOException("Cocoa media read scope absent")
        if (Thread.currentThread().isInterrupted()) throw IOException("Cocoa media read interrupted")
    }

    @Throws(IOException::class)
    override fun alreadyRunning(): List<MacApplicationMediaSource.Application> {
        requireScope()
        val result = ArrayList<MacApplicationMediaSource.Application>()
        for (id in MacApplicationMediaSource.SUPPORTED_APPLICATIONS) {
            val apps = required(pointer(type("NSRunningApplication"), "runningApplicationsWithBundleIdentifier:", nsString(id)), "running app list")
            val count = longMessage(apps, "count")
            if (count < 0 || count > 256) throw IOException("Unexpected supported-app process count")
            for (index in 0 until count) {
                val app = required(pointer(apps, "objectAtIndex:", index), "running app")
                if (boolMessage(app, "isTerminated") || !boolMessage(app, "isFinishedLaunching")) continue
                val pid = intMessage(app, "processIdentifier")
                if (pid <= 0 || id != text(pointer(app, "bundleIdentifier"))) continue
                val bundle = required(pointer(type("NSBundle"), "bundleWithURL:", required(pointer(app, "bundleURL"), "app bundle URL")), "app bundle")
                val resources = text(pointer(bundle, "resourcePath"))
                val definition = text(pointer(bundle, "objectForInfoDictionaryKey:", nsString("OSAScriptingDefinition")))
                result.add(MacApplicationMediaSource.Application(pid, id, resources, definition))
            }
        }
        return java.util.List.copyOf(result)
    }

    @Throws(IOException::class)
    override fun dictionary(application: MacApplicationMediaSource.Application): ByteArray {
        requireScope()
        checkPid(application)
        return MacScriptingDictionary.readVendorFile(application.resourceDirectory, application.dictionaryFile)
    }

    @Throws(IOException::class)
    override fun permissionForGetData(application: MacApplicationMediaSource.Application, askUserIfNeeded: Boolean): Int {
        requireScope()
        if (askUserIfNeeded) throw IOException("Media provider never requests Automation consent")
        checkPid(application)
        val target = pidDescriptor(application.pid)
        val address = required(pointer(target, "aeDesc"), "PID address descriptor")
        return permission.invokeInt(arrayOf<Any?>(address, code("core"), code("getd"), 0.toByte()))
    }

    @Throws(IOException::class)
    override fun openPidTarget(application: MacApplicationMediaSource.Application, sendMode: Int, replyTimeoutTicks: Long): MacApplicationMediaSource.Session {
        requireScope()
        checkPid(application)
        if (sendMode != MacApplicationMediaSource.READ_SEND_MODE || replyTimeoutTicks != MacApplicationMediaSource.REPLY_TIMEOUT_TICKS) throw IOException("Unreviewed Apple event options")
        val target = pidDescriptor(application.pid)
        return object : MacApplicationMediaSource.Session {
            @Throws(IOException::class)
            private fun query(propertyPath: List<Int>): Pointer {
                requireScope()
                checkPid(application)
                val request = MacGetDataRequest.create(application.pid, propertyPath, sendMode, replyTimeoutTicks)
                var container = required(pointer(type("NSAppleEventDescriptor"), "nullDescriptor"), "null container")
                for (property in request.propertyPath) {
                    val record = required(pointer(type("NSAppleEventDescriptor"), "recordDescriptor"), "property specifier record")
                    voidMessage(record, "setDescriptor:forKeyword:", typeDescriptor(code("prop")), code("want"))
                    voidMessage(record, "setDescriptor:forKeyword:", container, code("from"))
                    voidMessage(record, "setDescriptor:forKeyword:", enumDescriptor(code("prop")), code("form"))
                    voidMessage(record, "setDescriptor:forKeyword:", typeDescriptor(property!!), code("seld"))
                    container = required(pointer(record, "coerceToDescriptorType:", code("obj ")), "property specifier")
                }
                val event = required(
                    pointer(
                        type("NSAppleEventDescriptor"),
                        "appleEventWithEventClass:eventID:targetDescriptor:returnID:transactionID:",
                        request.eventClass, request.eventId, target, (-1).toShort(), 0
                    ),
                    "get-data event"
                )
                voidMessage(event, "setParamDescriptor:forKeyword:", container, code("----"))
                val error = PointerByReference()
                val reply = pointer(event, "sendEventWithOptions:timeout:error:", request.sendMode.toLong(), request.timeoutSeconds, error)
                if (error.value != null) throw IOException("Mac media get-data unavailable: " + this@MacJnaRuntime.text(pointer(error.value, "localizedDescription")))
                required(reply, "get-data reply")
                val errorNumber = pointer(reply, "paramDescriptorForKeyword:", code("errn"))
                if (errorNumber != null && intMessage(errorNumber, "int32Value") != 0) throw IOException("Mac media app rejected get-data: " + intMessage(errorNumber, "int32Value"))
                return required(pointer(reply, "paramDescriptorForKeyword:", code("----")), "get-data value")
            }

            @Throws(IOException::class)
            override fun enumValue(property: Int): Int {
                val value = query(listOf(property))
                if (intMessage(value, "descriptorType") != code("enum")) throw IOException("Vendor player state is not an enumeration")
                return intMessage(value, "enumCodeValue")
            }

            @Throws(IOException::class)
            override fun text(propertyPath: List<Int>): String {
                val value = query(propertyPath)
                val descriptorType = intMessage(value, "descriptorType")
                if (!listOf(code("TEXT"), code("cstr"), code("utxt"), code("ut16"), code("utf8")).contains(descriptorType)) throw IOException("Vendor media property is not text")
                return this@MacJnaRuntime.text(required(pointer(value, "stringValue"), "media string"))
            }

            @Throws(IOException::class)
            override fun number(propertyPath: List<Int>): Double? {
                val value = query(propertyPath)
                val descriptorType = intMessage(value, "descriptorType")
                if (!listOf(code("shor"), code("long"), code("magn"), code("comp"), code("ucom"), code("sing"), code("doub")).contains(descriptorType)) throw IOException("Vendor media property is not numeric")
                return doubleMessage(value, "doubleValue")
            }
        }
    }

    @Throws(IOException::class)
    private fun checkPid(application: MacApplicationMediaSource.Application) {
        if (!MacApplicationMediaSource.SUPPORTED_APPLICATIONS.contains(application.bundleId) || application.pid <= 0) throw IOException("Unsupported target app")
        val app = required(pointer(type("NSRunningApplication"), "runningApplicationWithProcessIdentifier:", application.pid), "current PID app")
        if (boolMessage(app, "isTerminated") || !boolMessage(app, "isFinishedLaunching") || application.bundleId != text(pointer(app, "bundleIdentifier"))) throw IOException("Media app is no longer the same running PID target")
    }

    @Throws(IOException::class)
    private fun pidDescriptor(pid: Int): Pointer {
        return required(pointer(type("NSAppleEventDescriptor"), "descriptorWithProcessIdentifier:", pid), "PID target")
    }

    @Throws(IOException::class)
    private fun typeDescriptor(code: Int): Pointer {
        return required(pointer(type("NSAppleEventDescriptor"), "descriptorWithTypeCode:", code), "type descriptor")
    }

    @Throws(IOException::class)
    private fun enumDescriptor(code: Int): Pointer {
        return required(pointer(type("NSAppleEventDescriptor"), "descriptorWithEnumCode:", code), "enum descriptor")
    }

    @Throws(IOException::class)
    private fun type(name: String): Pointer {
        return required(objcClass.invokePointer(arrayOf<Any?>(name)), "Cocoa class " + name)
    }

    @Throws(IOException::class)
    private fun nsString(text: String): Pointer {
        return required(pointer(type("NSString"), "stringWithUTF8String:", text), "NSString")
    }

    private fun arguments(receiver: Pointer, name: String, vararg arguments: Any): Array<Any?> {
        val all = arrayOfNulls<Any>(arguments.size + 2)
        all[0] = receiver
        all[1] = selector.invokePointer(arrayOf<Any?>(name))
        System.arraycopy(arguments, 0, all, 2, arguments.size)
        return all
    }

    private fun pointer(receiver: Pointer, name: String, vararg arguments: Any): Pointer {
        return message.invokePointer(arguments(receiver, name, *arguments))
    }

    private fun intMessage(receiver: Pointer, name: String, vararg arguments: Any): Int {
        return message.invokeInt(arguments(receiver, name, *arguments))
    }

    private fun longMessage(receiver: Pointer, name: String, vararg arguments: Any): Long {
        return message.invokeLong(arguments(receiver, name, *arguments))
    }

    private fun doubleMessage(receiver: Pointer, name: String, vararg arguments: Any): Double {
        return message.invokeDouble(arguments(receiver, name, *arguments))
    }

    private fun boolMessage(receiver: Pointer, name: String): Boolean {
        return (message.invoke(Byte::class.java, arguments(receiver, name)) as Number).toByte() != 0.toByte()
    }

    private fun voidMessage(receiver: Pointer, name: String, vararg arguments: Any) {
        message.invokeVoid(arguments(receiver, name, *arguments))
    }

    @Throws(IOException::class)
    private fun text(string: Pointer?): String {
        if (string == null) return ""
        if (!boolMessageWithClass(string, "NSString")) throw IOException("Unexpected Cocoa string class")
        val length = longMessage(string, "length")
        if (length < 0 || length > 1048576) throw IOException("Cocoa string exceeds bounded size")
        if (length == 0L) return ""
        Memory(length * 2).use { units ->
            voidMessage(string, "getCharacters:range:", units, Range(0L, length))
            val characters = CharArray(length.toInt())
            for (n in characters.indices) characters[n] = (units.getShort(n * 2L).toInt() and 65535).toChar()
            return String(characters)
        }
    }

    @Throws(IOException::class)
    private fun boolMessageWithClass(`object`: Pointer, name: String): Boolean {
        return (message.invoke(Byte::class.java, arguments(`object`, "isKindOfClass:", type(name))) as Number).toByte() != 0.toByte()
    }

    @Structure.FieldOrder("location", "length")
    class Range() : Structure(), Structure.ByValue {
        @JvmField
        var location: Long = 0

        @JvmField
        var length: Long = 0

        constructor(location: Long, length: Long) : this() {
            this.location = location
            this.length = length
        }
    }

    companion object {
        private const val FOUNDATION = "/System/Library/Frameworks/Foundation.framework/Foundation"
        private const val APPKIT = "/System/Library/Frameworks/AppKit.framework/AppKit"
        private const val APPLE_EVENTS = "/System/Library/Frameworks/CoreServices.framework/Frameworks/AE.framework/AE"

        @Throws(IOException::class)
        private fun required(pointer: Pointer?, name: String): Pointer {
            if (pointer == null || Pointer.nativeValue(pointer) == 0L) throw IOException("Unavailable " + name)
            return pointer
        }

        @Throws(IOException::class)
        private fun code(value: String): Int {
            return MacScriptingDictionary.code(value)
        }
    }
}

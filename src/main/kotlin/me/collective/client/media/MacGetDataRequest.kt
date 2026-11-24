package me.collective.client.media

import java.io.IOException

data class MacGetDataRequest(
    @get:JvmName("eventClass") val eventClass: Int,
    @get:JvmName("eventId") val eventId: Int,
    @get:JvmName("propertyPath") val propertyPath: List<Int?>,
    @get:JvmName("sendMode") val sendMode: Int,
    @get:JvmName("timeoutSeconds") val timeoutSeconds: Double
) {
    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun create(pid: Int, path: List<Int?>?, mode: Int, timeoutTicks: Long): MacGetDataRequest {
            if (pid <= 0 || path == null || path.isEmpty() || path.size > 2 || path.any { it == null }) {
                throw IOException("Unsupported PID property request")
            }
            if (mode != MacApplicationMediaSource.READ_SEND_MODE || timeoutTicks != MacApplicationMediaSource.REPLY_TIMEOUT_TICKS) {
                throw IOException("Unreviewed Apple event options")
            }
            return MacGetDataRequest(
                MacScriptingDictionary.code("core"), MacScriptingDictionary.code("getd"),
                java.util.List.copyOf(path), mode, timeoutTicks / 60.0
            )
        }
    }
}

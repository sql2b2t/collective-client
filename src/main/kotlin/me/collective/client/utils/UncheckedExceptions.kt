package me.collective.client.utils

class UncheckedExceptions private constructor() {
    companion object {
        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        fun <T : Throwable> rethrow(failure: Throwable): RuntimeException {
            throw failure as T
        }
    }
}

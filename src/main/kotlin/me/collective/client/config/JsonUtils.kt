package me.collective.client.config

import com.google.gson.JsonObject

object JsonUtils {

    @JvmStatic
    fun isRecursivelyEmpty(jsonObject: JsonObject): Boolean {
        for ((_, value) in jsonObject.entrySet()) {
            if (value !is JsonObject) {
                return false
            }
            if (!isRecursivelyEmpty(value)) {
                return false
            }
        }
        return true
    }

    @JvmStatic
    fun hasProperties(jsonObject: JsonObject, vararg propertyNames: String): Boolean {
        for (propertyName in propertyNames) {
            if (!jsonObject.has(propertyName)) {
                return false
            }
        }

        return true
    }

    @JvmStatic
    fun removeProperties(jsonObject: JsonObject, vararg propertyNames: String) {
        for (propertyName in propertyNames) {
            jsonObject.remove(propertyName)
        }
    }
}

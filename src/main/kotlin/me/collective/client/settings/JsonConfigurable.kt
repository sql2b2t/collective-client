package me.collective.client.settings

import com.google.gson.JsonElement

interface JsonConfigurable {
    fun fromJson(json: JsonElement)

    fun toJson(): JsonElement

    fun getConfigName(): String = ""
}

val JsonConfigurable.configName: String
    get() = getConfigName()

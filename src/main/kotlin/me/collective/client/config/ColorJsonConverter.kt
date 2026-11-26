package me.collective.client.config

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import java.awt.Color

class ColorJsonConverter {
    fun fromJson(json: JsonElement): Color {
        if (json != null && json.isJsonArray) {
            val array = json.getAsJsonArray()

            if (array.size() >= 4) {
                return try {
                    Color(
                        array.get(0).getAsInt().coerceIn(0, 255),
                        array.get(1).getAsInt().coerceIn(0, 255),
                        array.get(2).getAsInt().coerceIn(0, 255),
                        array.get(3).getAsInt().coerceIn(0, 255)
                    )
                } catch (ignored: Exception) {
                    throw IllegalArgumentException()
                }
            }
        }

        throw IllegalArgumentException()
    }

    fun toJson(color: Color): JsonElement {
        val array = JsonArray()
        array.add(color.getRed())
        array.add(color.getGreen())
        array.add(color.getBlue())
        array.add(color.getAlpha())
        return array
    }
}

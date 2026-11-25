package me.collective.client.config

import me.collective.client.settings.NamedEntry
import me.collective.client.utils.ExperimentalFeatures

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive

class EnumJsonConverter<E : Enum<E>>(private val enumClass: Class<E>) {

    fun toJson(value: Enum<*>): JsonElement {
        if (value is NamedEntry) {
            return JsonPrimitive(value.getName())
        } else {
            throw IllegalArgumentException()
        }
    }

    fun fromJson(json: JsonElement): E {
        val constants = enumClass.enumConstants
        if (!json.isJsonPrimitive) {
            return constants[0]
        }
        val jsonName = json.getAsString()
        for (candidate in constants) {
            if (candidate !is NamedEntry) {
                throw IllegalArgumentException()
            }

            // Hidden constants still match when reading a saved value,
            // so a value stored before the option was hidden keeps loading.
            val candidateName = candidate.getName()
            val candidateEnumName = (candidate as Enum<*>).name
            if (jsonName.equals(candidateName, ignoreCase = true) ||
                jsonName.equals(candidateEnumName, ignoreCase = true) ||
                jsonName.replace("_", "").equals(candidateName.replace("_", ""), ignoreCase = true)) {
                return candidate
            }
        }

        return constants[0]
    }

    companion object {
        @JvmStatic
        fun findSelectableIndex(value: Enum<*>): Int {
            var index = 0

            while (true) {
                val constants =
                    (value as java.lang.Enum<*>).getDeclaringClass().enumConstants as Array<out Enum<*>>
                if (index >= constants.size) {
                    return -1
                }

                val candidate = constants[index]

                if (!ExperimentalFeatures.isEnumConstantHidden(candidate)) {
                    val candidateName = candidate.name
                    val valueName = value.name
                    if (candidateName.equals(valueName, ignoreCase = true)) {
                        return index
                    }
                }

                index++
            }
        }

        @JvmStatic
        fun nextValue(value: Enum<*>): Enum<*> {
            val currentIndex = findSelectableIndex(value)
            var index = 0

            while (true) {
                val constants =
                    (value as java.lang.Enum<*>).getDeclaringClass().enumConstants as Array<out Enum<*>>
                if (index >= constants.size) {
                    return constants[0]
                }

                val candidate = constants[index]

                if (!ExperimentalFeatures.isEnumConstantHidden(candidate) && index > currentIndex) {
                    return candidate
                }

                index++
            }
        }

        @JvmStatic
        fun getName(value: Enum<*>): String {
            if (value is NamedEntry) {
                return value.getName()
            } else {
                throw IllegalArgumentException()
            }
        }
    }
}

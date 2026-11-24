package me.collective.client.settings

import me.collective.client.render.TextUtils

interface CyclingTextSetting {
    fun matchesText(text: String): Boolean

    fun setValueFromText(text: String)

    fun parseValue(text: String) {
        val values = TextUtils.splitQuotedArguments(text)
        if (values.size > 1) {
            this.cycleValues(values)
        } else {
            this.setValueFromText(text)
        }
    }

    private fun cycleValues(values: Array<String>) {
        for (index in values.indices) {
            val candidate = values[index]
            if (this.matchesText(candidate)) {
                this.setValueFromText(values[(index + 1) % values.size])
                return
            }
        }

        this.setValueFromText(values[0])
    }
}

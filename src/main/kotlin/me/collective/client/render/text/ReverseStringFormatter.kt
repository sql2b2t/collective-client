package me.collective.client.render.text

class ReverseStringFormatter {
    private val arguments = ArrayList<String>()

    private fun prependString(value: String) {
        arguments.add(0, value)
    }

    fun prepend(value: Boolean): ReverseStringFormatter {
        prependString(value.toString())
        return this
    }

    fun prepend(value: Float): ReverseStringFormatter {
        prependString(value.toString())
        return this
    }

    fun prepend(value: Char): ReverseStringFormatter {
        prependString(value.toString())
        return this
    }

    fun prepend(value: Short): ReverseStringFormatter {
        prependString(value.toString())
        return this
    }

    fun prepend(value: Int): ReverseStringFormatter {
        prependString(value.toString())
        return this
    }

    fun prepend(value: Long): ReverseStringFormatter {
        prependString(value.toString())
        return this
    }

    fun prepend(value: Any?): ReverseStringFormatter {
        prependString(value.toString())
        return this
    }

    fun format(template: String): String {
        var result = template
        var argumentIndex = 0
        while (true) {
            val placeholder = result.indexOf(ARGUMENT_PLACEHOLDER)
            if (placeholder < 0 || argumentIndex == arguments.size) {
                return result
            }

            val argument = arguments[argumentIndex]
            result = StringBuilder(result)
                .replace(placeholder, placeholder + 1, argument)
                .toString()
            argumentIndex++
        }
    }

    private companion object {
        private const val ARGUMENT_PLACEHOLDER = '\u0001'
    }
}

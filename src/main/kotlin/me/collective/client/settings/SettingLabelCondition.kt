package me.collective.client.settings

enum class SettingLabelCondition {
    MIN {
        override fun matches(setting: Setting<*>): Boolean {
            val value = setting.getValue()
            return value == setting.getMinimumValue()
        }
    },
    MAX {
        override fun matches(setting: Setting<*>): Boolean {
            val value = setting.getValue()
            return value == setting.getMaximumValue()
        }
    };

    open fun matches(setting: Setting<*>): Boolean = false
}

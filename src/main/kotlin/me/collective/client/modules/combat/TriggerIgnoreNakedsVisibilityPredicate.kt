package me.collective.client.modules.combat

import java.util.function.Predicate

class TriggerIgnoreNakedsVisibilityPredicate(private val trigger: TriggerModule) : Predicate<Any?> {
    override fun test(ignoredValue: Any?): Boolean {
        if (!trigger.targetsSetting.isExpanded) {
            return false
        }
        return trigger.playersSetting.isExpanded
    }
}

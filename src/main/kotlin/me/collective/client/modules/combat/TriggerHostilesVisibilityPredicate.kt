package me.collective.client.modules.combat

import java.util.function.Predicate

class TriggerHostilesVisibilityPredicate(private val trigger: TriggerModule) : Predicate<Any?> {
    override fun test(ignoredValue: Any?): Boolean {
        return trigger.targetsSetting.isExpanded
    }
}

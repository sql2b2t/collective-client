package me.collective.client.utils.inventory

interface AutoMineProvider {
    fun collectCandidates(candidates: AutoMineCandidates)

    fun isEnabled(): Boolean
}

val AutoMineProvider.isEnabled: Boolean
    get() = isEnabled()

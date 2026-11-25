package me.collective.client.events

import net.minecraft.client.gui.screens.Screen

open class ScreenChangeEvent(
    val previousScreen: Screen?,
    val newScreen: Screen?
) : CancellableEvent() {

    class Before(
        previousScreen: Screen?,
        newScreen: Screen?
    ) : ScreenChangeEvent(previousScreen, newScreen)

    class AfterInit(
        previousScreen: Screen?,
        newScreen: Screen?
    ) : ScreenChangeEvent(previousScreen, newScreen)
}

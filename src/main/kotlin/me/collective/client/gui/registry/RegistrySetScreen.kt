package me.collective.client.gui.registry

import me.collective.client.ClientAccess
import me.collective.client.gui.CloseState
import me.collective.client.gui.components.ActionButtonComponent
import me.collective.client.gui.components.StringSettingComponent
import me.collective.client.gui.panels.Panel
import me.collective.client.gui.screens.ClientScreen
import me.collective.client.settings.RegistrySetSetting
import me.collective.client.settings.StringSetting
import me.collective.client.utils.KeyUtils
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey

class RegistrySetScreen<E : Any>(
    parentScreen: Screen,
    val registrySetting: RegistrySetSetting<E>
) : ClientScreen() {

    private val availablePanel: Panel = RegistryListPanel("Available")
    private val addedPanel: Panel = RegistryListPanel("Added")
    private val extraPanel: Panel = RegistryListPanel("Extra")
    private val catalogEntries: RegistryEntryCatalog.EntryList = RegistryEntryCatalog.entriesFor(registrySetting.registry)

    val searchComponent: StringSettingComponent = StringSettingComponent(
        this.extraPanel,
        CloseState.ALWAYS_OPEN,
        this.createSearchSetting()
    )

    init {
        for (entry in registrySetting.registry) {
            val entryKey: ResourceKey<*>? = registrySetting.registry.getResourceKey(entry).orElse(null)
            if (entryKey != null && !this.catalogEntries.contains(entry)) {
                this.addEntryPair(entryKey.identifier(), entry, false)
            }
        }

        for (catalogEntry in this.catalogEntries.orderedEntries) {
            val entry = registrySetting.registry.stream().filter { it === catalogEntry }.findFirst().orElse(null)
            val catalogEntryKey: ResourceKey<*>? = if (entry == null) null else registrySetting.registry.getResourceKey(entry).orElse(null)
            if (catalogEntryKey != null && entry != null) {
                this.addEntryPair(catalogEntryKey.identifier(), entry, true)
            }
        }

        this.initializePanels()
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (KeyUtils.isControlDown() && event.key() == 70) {
            this.searchComponent.beginEditing()
        }
        return super.keyPressed(event)
    }

    override fun closeWithTransition() {
        val parent = this.parentScreen
        if (parent is ClientScreen) {
            parent.open()
        } else {
            ClientAccess.collectiveMinecraft.gui.setScreen(parent)
        }
    }

    private fun addEntryPair(identifier: Identifier, entry: E, prepend: Boolean) {
        val availableEntry = RegistryEntryComponent(this, this.availablePanel, identifier, entry, true)
        val addedEntry = RegistryEntryComponent(this, this.addedPanel, identifier, entry, false)
        if (prepend) {
            this.availablePanel.children.add(0, availableEntry)
            this.addedPanel.children.add(0, addedEntry)
        } else {
            this.availablePanel.registerChild(availableEntry)
            this.addedPanel.registerChild(addedEntry)
        }
    }

    private fun initializePanels() {
        this.addedPanel.x = this.availablePanel.x + this.availablePanel.width + 3
        this.extraPanel.x = this.addedPanel.x + this.addedPanel.width + 3
        this.panels.add(this.availablePanel)
        this.panels.add(this.addedPanel)
        this.panels.add(this.extraPanel)
        this.extraPanel.registerChild(this.searchComponent)
        this.extraPanel.registerChild(
            ActionButtonComponent(this.extraPanel, "Clear") {
                (this.registrySetting.value as MutableSet<E>).clear()
            }
        )
    }

    private fun createSearchSetting(): StringSetting {
        val searchSetting = StringSetting("Search", "")
        searchSetting.setChangeCallback {
            if (searchSetting.value.isNotEmpty()) {
                searchSetting.parseValue("")
            }
        }
        return searchSetting
    }
}

package me.collective.client.render

import me.collective.client.ClientAccess
import me.collective.client.access.OptionValueAccess
import me.collective.client.settings.NamedEntry
import net.minecraft.client.CameraType
import net.minecraft.client.OptionInstance

object GameOptionsOverrides : ClientAccess {
    private val CAMERA_OVERRIDE_OWNERS: MutableSet<String> = HashSet()
    private var savedCameraType: CameraType? = null

    @JvmStatic
    fun forceSetOptionValue(option: OptionInstance<*>, value: Any?) {
        val optionValue = option as OptionValueAccess
        optionValue.forceSetValue(value)
    }

    @JvmStatic
    fun registerCameraOverride(owner: NamedEntry) {
        if (CAMERA_OVERRIDE_OWNERS.isEmpty()) {
            val gameOptions = ClientAccess.collectiveMinecraft.options
            savedCameraType = gameOptions.cameraType
        }

        CAMERA_OVERRIDE_OWNERS.add(owner.getName())
    }

    @JvmStatic
    fun unregisterCameraOverride(owner: NamedEntry) {
        CAMERA_OVERRIDE_OWNERS.remove(owner.getName())
        val cameraTypeToRestore = savedCameraType
        if (CAMERA_OVERRIDE_OWNERS.isEmpty() && cameraTypeToRestore != null) {
            val gameOptions = ClientAccess.collectiveMinecraft.options
            gameOptions.cameraType = cameraTypeToRestore
            savedCameraType = null
        }
    }
}

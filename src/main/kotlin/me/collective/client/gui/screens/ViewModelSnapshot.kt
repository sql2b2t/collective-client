package me.collective.client.gui.screens

import me.collective.client.modules.render.ViewModelModule

@JvmRecord
data class ViewModelSnapshot(
    val mainX: Float,
    val mainY: Float,
    val mainZ: Float,
    val offX: Float,
    val offY: Float,
    val offZ: Float,
    val mainScaleX: Float,
    val mainScaleY: Float,
    val mainScaleZ: Float,
    val offScaleX: Float,
    val offScaleY: Float,
    val offScaleZ: Float,
    val mainRotateX: Float,
    val mainRotateY: Float,
    val mainRotateZ: Float,
    val offRotateX: Float,
    val offRotateY: Float,
    val offRotateZ: Float
) {
    fun apply(module: ViewModelModule) {
        module.mainXSetting.value = this.mainX
        module.mainYSetting.value = this.mainY
        module.mainZSetting.value = this.mainZ
        module.offXSetting.value = this.offX
        module.offYSetting.value = this.offY
        module.offZSetting.value = this.offZ
        module.mainScaleXSetting.value = this.mainScaleX
        module.mainScaleYSetting.value = this.mainScaleY
        module.mainScaleZSetting.value = this.mainScaleZ
        module.offScaleXSetting.value = this.offScaleX
        module.offScaleYSetting.value = this.offScaleY
        module.offScaleZSetting.value = this.offScaleZ
        module.mainRotateXSetting.value = this.mainRotateX
        module.mainRotateYSetting.value = this.mainRotateY
        module.mainRotateZSetting.value = this.mainRotateZ
        module.offRotateXSetting.value = this.offRotateX
        module.offRotateYSetting.value = this.offRotateY
        module.offRotateZSetting.value = this.offRotateZ
    }

    companion object {
        @JvmStatic
        fun capture(module: ViewModelModule): ViewModelSnapshot {
            return ViewModelSnapshot(
                module.mainXSetting.value,
                module.mainYSetting.value,
                module.mainZSetting.value,
                module.offXSetting.value,
                module.offYSetting.value,
                module.offZSetting.value,
                module.mainScaleXSetting.value,
                module.mainScaleYSetting.value,
                module.mainScaleZSetting.value,
                module.offScaleXSetting.value,
                module.offScaleYSetting.value,
                module.offScaleZSetting.value,
                module.mainRotateXSetting.value,
                module.mainRotateYSetting.value,
                module.mainRotateZSetting.value,
                module.offRotateXSetting.value,
                module.offRotateYSetting.value,
                module.offRotateZSetting.value
            )
        }

        @JvmStatic
        fun createDefaults(): ViewModelSnapshot {
            return ViewModelSnapshot(
                0.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 0.0f,
                1.0f, 1.0f, 1.0f,
                1.0f, 1.0f, 1.0f,
                0.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 0.0f
            )
        }
    }
}

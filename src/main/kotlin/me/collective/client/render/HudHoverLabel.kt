package me.collective.client.render

class HudHoverLabel {
    var text: String = ""
    var horizontalDirection: Int = 0
    var x: Float = 0.0f
    var y: Float = 0.0f
    var isVisible: Boolean = false

    fun show(text: String, x: Float, y: Float, direction: Int) {
        this.text = text
        this.x = x
        this.y = y
        this.horizontalDirection = direction
        this.isVisible = true
    }
}

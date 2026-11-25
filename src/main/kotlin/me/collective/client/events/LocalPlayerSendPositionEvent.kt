package me.collective.client.events

class LocalPlayerSendPositionEvent private constructor(
    val stage: EventStage,
    posX: Double,
    posY: Double,
    posZ: Double,
    rotYaw: Float,
    rotPitch: Float,
    ground: Boolean,
    val originalX: Double,
    val originalY: Double,
    val originalZ: Double,
    val originalYaw: Float,
    val originalPitch: Float,
    private val originalOnGround: Boolean
) : CancellableEvent() {
    private var posX: Double = posX
    private var posY: Double = posY
    private var posZ: Double = posZ
    private var rotYaw: Float = rotYaw
    private var rotPitch: Float = rotPitch
    private var ground: Boolean = ground

    var modified: Boolean = false
        private set

    var x: Double
        get() = posX
        set(value) {
            posX = value
            modified = true
        }

    var y: Double
        get() = posY
        set(value) {
            posY = value
            modified = true
        }

    var z: Double
        get() = posZ
        set(value) {
            posZ = value
            modified = true
        }

    var yaw: Float
        get() = rotYaw
        set(value) {
            rotYaw = value
            modified = true
        }

    var pitch: Float
        get() = rotPitch
        set(value) {
            rotPitch = value
            modified = true
        }

    var onGround: Boolean
        get() = ground
        set(value) {
            ground = value
            modified = true
        }

    fun isOnGround(): Boolean = this.onGround
    fun isModified(): Boolean = this.modified

    constructor() : this(EventStage.Pre, 0.0, 0.0, 0.0, 0f, 0f, false, 0.0, 0.0, 0.0, 0f, 0f, false)

    constructor(
        stage: EventStage,
        x: Double,
        y: Double,
        z: Double,
        yaw: Float,
        pitch: Float,
        onGround: Boolean
    ) : this(stage, x, y, z, yaw, pitch, onGround, x, y, z, yaw, pitch, onGround)

    constructor(stage: EventStage, event: LocalPlayerSendPositionEvent) : this(
        stage,
        event.x,
        event.y,
        event.z,
        event.yaw,
        event.pitch,
        event.onGround,
        event.x,
        event.y,
        event.z,
        event.yaw,
        event.pitch,
        event.onGround
    )

    fun wasOnGround(): Boolean = originalOnGround

    fun getCurrentYaw(): Float = yaw

    fun getCurrentPitch(): Float = pitch

    fun setRotation(angles: FloatArray?) {
        if (angles != null && angles.size == 2) {
            yaw = angles[0]
            pitch = angles[1]
        }
    }
}

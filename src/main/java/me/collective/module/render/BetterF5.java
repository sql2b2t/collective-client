package me.collective.module.render;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.NumberSetting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class BetterF5 extends Module {
    public static BetterF5 INSTANCE;

    public final NumberSetting distance = add(new NumberSetting("Distance", 8.5, 2.0, 15.0, 0.1));
    public final NumberSetting height = add(new NumberSetting("Height", 0.1, -2.0, 3.0, 0.1));

    private double currentDistance;
    private double currentHeight;
    private double smoothX;
    private double smoothY;
    private double smoothZ;
    private float currentYawOffset;
    private double targetDistance;
    private double targetHeight;
    private float targetYawOffset;
    private long jumpTime;
    private CameraType lastPerspective;
    private boolean needsInit;
    private long lastFrame;
    private boolean breakingBlock;

    private float resultDistance;
    private float resultYaw;
    private float resultPitch;
    private boolean rotationModified;
    private boolean positionModified;

    public BetterF5() {
        super("BetterF5", "Плавное управление камерой от третьего лица.", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    public void onJump() {
        if (isToggled()) {
            this.jumpTime = System.currentTimeMillis();
        }
    }

    public void onCameraUpdate(Camera camera) {
        rotationModified = false;
        positionModified = false;

        if (!isToggled()) {
            reset();
            return;
        }

        CameraType perspective = mc.options.getCameraType();
        if (perspective != this.lastPerspective) {
            if (perspective != CameraType.FIRST_PERSON) {
                this.needsInit = true;
            }
            this.lastPerspective = perspective;
        }

        if (perspective == CameraType.FIRST_PERSON) {
            return;
        }

        long now = System.currentTimeMillis();
        float delta = this.lastFrame == 0L
            ? 0.016F
            : Mth.clamp((float) (now - this.lastFrame) / 1000.0F, 0.001F, 0.1F);
        this.lastFrame = now;

        if (camera.entity() instanceof Player player) {
            if (this.needsInit) {
                init(camera);
                this.needsInit = false;
            }
            computeTargets(player);
            smooth(delta);
            apply(camera);
        }
    }

    public float getDistance() {
        return resultDistance;
    }

    public boolean isRotationModified() {
        return rotationModified;
    }

    public float getRotationYaw() {
        return resultYaw;
    }

    public float getRotationPitch() {
        return resultPitch;
    }

    public boolean isPositionModified() {
        return positionModified;
    }

    public double getSmoothX() {
        return smoothX;
    }

    public double getSmoothY() {
        return smoothY;
    }

    public double getSmoothZ() {
        return smoothZ;
    }

    private void init(Camera camera) {
        double d = this.distance.getValue();
        double h = this.height.getValue();

        Vec3 pos = camera.position();
        this.currentDistance = d;
        this.currentHeight = h;
        this.smoothX = pos.x;
        this.smoothY = pos.y;
        this.smoothZ = pos.z;
        this.currentYawOffset = 0.0F;
        this.targetDistance = d;
        this.targetHeight = h;
        this.targetYawOffset = 0.0F;
        this.jumpTime = 0L;
        this.lastFrame = System.currentTimeMillis();
    }

    private void computeTargets(Player player) {
        double baseDist = this.distance.getValue();
        double baseHeight = this.height.getValue();
        boolean frontView = mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT;

        double dist = baseDist;
        double h = baseHeight;
        float yawOff = 0.0F;

        Vec3 vel = player.getDeltaMovement();
        double speed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);

        if (player.isSprinting() && speed > 0.100000001116534) {
            double t = Mth.clamp(speed / 0.30000002483330174, 0.0, 1.0);
            dist += (frontView ? 1.2000005543231964 : 0.7999999) * easeOutQuart(t);
        } else if (speed > 0.010000003756227357) {
            double t = Mth.clamp(speed / 0.1500000901677722, 0.0, 1.0);
            dist += (frontView ? 0.47999983021081966 : 0.4) * easeInOutCos(t);
        }

        long elapsed = System.currentTimeMillis() - this.jumpTime;
        if ((float) elapsed > 100.0F && (float) elapsed < 700.0F) {
            double progress = ((float) elapsed - 100.0F) / 600.0;
            double v = easeOutQuart(progress) * (1.0 - easeInOutCubic(progress));
            h += 0.5 * v * 2.0;
        }

        if (!player.onGround() && vel.y < -0.10000000304602849) {
            double t = Mth.clamp(Math.abs(vel.y) / 0.5, 0.0, 1.0);
            h += 0.25 * easeOutQuart(t);
        }

        if (!player.onGround() && vel.y > 0.1000000000116534) {
            double t = Mth.clamp(vel.y / 0.39999996007461613, 0.0, 1.0);
            h += 0.1500000901677722 * t;
        }

        this.breakingBlock = mc.gameMode != null && mc.gameMode.isDestroying();
        if (this.breakingBlock) {
            BlockHitResult blockHit = getCrosshairBlockHit();
            if (blockHit != null) {
                Direction side = blockHit.getDirection();
                yawOff = switch (side) {
                    case NORTH, EAST -> -40.0F;
                    case SOUTH, WEST -> 40.0F;
                    default -> player.yBodyRot > player.getYRot() ? -40.0F : 40.0F;
                };
                dist += 0.60000026;
                h += 0.1500000901677722;
            }
        }

        this.targetDistance = dist;
        this.targetHeight = h;
        this.targetYawOffset = yawOff;
    }

    private void smooth(float delta) {
        double factor = this.breakingBlock ? 0.06 : 0.08000003;
        double heightTime = 1.0 / (factor * 10.0);
        double distTime = 2.0;

        if (Math.abs(this.currentDistance - this.targetDistance) > 9.99999425615245E-4) {
            this.currentDistance = smoothDamp(this.currentDistance, this.targetDistance, distTime, delta);
            if (Math.abs(this.currentDistance - this.targetDistance) < 9.99999425615245E-4) {
                this.currentDistance = this.targetDistance;
            }
        }

        if (Math.abs(this.currentHeight - this.targetHeight) > 9.99999425615245E-4) {
            this.currentHeight = smoothDamp(this.currentHeight, this.targetHeight, heightTime, delta);
            if (Math.abs(this.currentHeight - this.targetHeight) < 9.99999425615245E-4) {
                this.currentHeight = this.targetHeight;
            }
        }

        if (Math.abs(this.currentYawOffset - this.targetYawOffset) > 0.010000003756227357) {
            this.currentYawOffset = (float) smoothDamp(this.currentYawOffset, this.targetYawOffset, heightTime, delta);
            if (Math.abs(this.currentYawOffset - this.targetYawOffset) < 0.010000003756227357) {
                this.currentYawOffset = this.targetYawOffset;
            }
        }
    }

    private void apply(Camera camera) {
        this.resultDistance = (float) this.currentDistance;

        Vec3 pos = camera.position();
        double targetX = pos.x;
        double targetY = pos.y + this.currentHeight;
        double targetZ = pos.z;

        double time = 0.8333331447600631;
        if (Math.abs(targetX - this.smoothX) > 9.99999425615245E-4
            || Math.abs(targetY - this.smoothY) > 9.99999425615245E-4
            || Math.abs(targetZ - this.smoothZ) > 9.99999425615245E-4) {
            this.smoothX = smoothDamp(this.smoothX, targetX, time, 0.01599999F);
            this.smoothY = smoothDamp(this.smoothY, targetY, time, 0.01599999F);
            this.smoothZ = smoothDamp(this.smoothZ, targetZ, time, 0.01599999F);

            if (Math.abs(targetX - this.smoothX) < 9.99999425615245E-4) this.smoothX = targetX;
            if (Math.abs(targetY - this.smoothY) < 9.99999425615245E-4) this.smoothY = targetY;
            if (Math.abs(targetZ - this.smoothZ) < 9.99999425615245E-4) this.smoothZ = targetZ;

            positionModified = true;
        }

        if (Math.abs(this.currentYawOffset) > 0.5F) {
            this.resultYaw = camera.yRot() + this.currentYawOffset;
            this.resultPitch = camera.xRot();
            this.rotationModified = true;
        }
    }

    private void reset() {
        double d = this.distance.getValue();
        double h = this.height.getValue();

        this.currentDistance = d;
        this.currentHeight = h;
        this.smoothX = 0.0;
        this.smoothY = 0.0;
        this.smoothZ = 0.0;
        this.currentYawOffset = 0.0F;
        this.targetDistance = d;
        this.targetHeight = h;
        this.targetYawOffset = 0.0F;
        this.jumpTime = 0L;
        this.needsInit = true;
        this.lastFrame = 0L;
        this.breakingBlock = false;

        this.resultYaw = 0.0F;
        this.resultPitch = 0.0F;
        this.rotationModified = false;
        this.positionModified = false;
    }

    private BlockHitResult getCrosshairBlockHit() {
        if (mc.hitResult instanceof BlockHitResult blockHit) {
            return blockHit;
        }
        return null;
    }

    private static double smoothDamp(double current, double target, double time, double dt) {
        return time <= 0.0 ? target : target + (current - target) * Math.exp(-0.6931471266636435 / time * dt);
    }

    private static double easeOutQuart(double x) {
        double oneMinus = 1.0 - x;
        return 1.0 - oneMinus * oneMinus * oneMinus * oneMinus;
    }

    private static double easeInOutCos(double x) {
        return -(Math.cos(Math.PI * x) - 1.0) / 2.0;
    }

    private static double easeInOutCubic(double x) {
        return x < 0.5 ? 4.0 * x * x * x : 1.0 - Math.pow(-2.0 * x + 2.0, 3.0) / 2.0;
    }
}
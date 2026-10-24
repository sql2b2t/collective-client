package me.collective.mixin;

import me.collective.module.render.BetterF5;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class MixinCamera {
    @Shadow private Vec3 position;
    @Shadow private Entity entity;
    @Shadow private boolean detached;

    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow protected abstract void setPosition(Vec3 pos);

    @ModifyVariable(method = "getMaxZoom", at = @At("HEAD"), argsOnly = true)
    private float collective$modifyCameraDistance(float desiredDistance) {
        me.collective.module.render.Freecam freecam = me.collective.module.render.Freecam.INSTANCE;
        if (freecam != null && freecam.isToggled()) {
            return 0.0f;
        }

        BetterF5 module = BetterF5.INSTANCE;
        if (module == null || !module.isToggled()) {
            return desiredDistance;
        }

        module.onCameraUpdate((Camera) (Object) this);

        if (module.isRotationModified()) {
            setRotation(module.getRotationYaw(), module.getRotationPitch());
        }

        return module.getDistance();
    }

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void collective$modifyFov(float tickDelta, CallbackInfoReturnable<Float> cir) {
        me.collective.module.render.Zoom zoom = me.collective.module.render.Zoom.INSTANCE;
        if (zoom != null) {
            float modified = zoom.modifyFov(cir.getReturnValueF(), tickDelta);
            if (modified != cir.getReturnValueF()) {
                cir.setReturnValue(modified);
            }
        }
    }

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void collective$onAlignWithEntity(float tickProgress, CallbackInfo ci) {
        me.collective.module.render.Freecam freecam = me.collective.module.render.Freecam.INSTANCE;
        if (freecam != null && freecam.isToggled()) {
            Vec3 freecamPos = freecam.getInterpolatedPos(tickProgress);
            setPosition(freecamPos);
            setRotation(freecam.getInterpolatedYaw(tickProgress), freecam.getInterpolatedPitch(tickProgress));
            this.detached = true;
            return;
        }

        me.collective.module.render.FreeLook freeLook = me.collective.module.render.FreeLook.INSTANCE;
        if (freeLook != null && freeLook.isActive()) {
            setPosition(freeLook.orbitPos(tickProgress));
            setRotation(freeLook.getOrbitYaw(), freeLook.getOrbitPitch());
            return;
        }

        BetterF5 module = BetterF5.INSTANCE;
        if (module != null && module.isToggled()) {
            module.onCameraUpdate((Camera) (Object) this);

            double smoothX = module.getSmoothX();
            double smoothY = module.getSmoothY();
            double smoothZ = module.getSmoothZ();
            if (module.isPositionModified() && (smoothX != this.position.x || smoothY != this.position.y || smoothZ != this.position.z)) {
                setPosition(new Vec3(smoothX, smoothY, smoothZ));
            }

            if (module.isRotationModified() && !this.detached) {
                setRotation(module.getRotationYaw(), module.getRotationPitch());
            }
        }

        // ElytraBounce SmoothCamera
        me.collective.module.movement.ElytraBounce bounce = me.collective.module.movement.ElytraBounce.INSTANCE;
        if (bounce != null && bounce.isToggled() && bounce.smoothCamera.getValue()
                && this.entity instanceof net.minecraft.world.entity.player.Player bouncePlayer) {
            double realY = bouncePlayer.getPosition(tickProgress).y;
            double delta = bounce.smoothVisualDeltaY(realY, true);
            if (delta != 0.0) {
                setPosition(new Vec3(this.position.x, this.position.y - delta, this.position.z));
            }
        }
    }
}
package me.collective.mixin;

import me.collective.module.render.BetterF5;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class MixinCamera {
    @Shadow private Vec3d pos;

    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow protected abstract void setPos(Vec3d pos);

    @ModifyArg(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;clipToSpace(F)F"), index = 0)
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

    @Inject(method = "update", at = @At("TAIL"))
    private void collective$onCameraUpdate(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
        me.collective.module.render.Freecam freecam = me.collective.module.render.Freecam.INSTANCE;
        if (freecam != null && freecam.isToggled()) {
            Vec3d freecamPos = freecam.getInterpolatedPos(tickProgress);
            setPos(freecamPos);
            setRotation(freecam.getInterpolatedYaw(tickProgress), freecam.getInterpolatedPitch(tickProgress));
            return;
        }

        BetterF5 module = BetterF5.INSTANCE;
        if (module == null || !module.isToggled()) {
            return;
        }

        module.onCameraUpdate((Camera) (Object) this);

        double smoothX = module.getSmoothX();
        double smoothY = module.getSmoothY();
        double smoothZ = module.getSmoothZ();
        if (module.isPositionModified() && (smoothX != this.pos.x || smoothY != this.pos.y || smoothZ != this.pos.z)) {
            setPos(new Vec3d(smoothX, smoothY, smoothZ));
        }

        if (module.isRotationModified() && !thirdPerson) {
            setRotation(module.getRotationYaw(), module.getRotationPitch());
        }
    }
}

package me.collective.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.collective.module.movement.ElytraBounce;
import me.collective.module.movement.NoJumpDelay;
import me.collective.module.movement.RocketBoost;
import me.collective.util.RotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow
    private int jumpingCooldown;

    @Inject(at = @At("HEAD"), method = "tickMovement")
    private void collective$tickMovement(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.equals(self)) {
            ElytraBounce efly = ElytraBounce.INSTANCE;
            NoJumpDelay njd = NoJumpDelay.INSTANCE;
            if ((efly != null && efly.enabled()) || (njd != null && njd.isToggled())) {
                this.jumpingCooldown = 0;
            }
        }
    }

    @WrapOperation(method = "calcGlidingVelocity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getPitch()F"))
    private float collective$wrapGlidingPitch(LivingEntity entity, Operation<Float> original) {
        Float pitch = RotationUtils.getInstance().getMovementPitch();
        Minecraft mc = Minecraft.getInstance();
        if (entity != mc.player || pitch == null) {
            return original.call(entity);
        }
        return pitch;
    }

    @WrapOperation(method = "travelGliding", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;calcGlidingVelocity(Lnet/minecraft/util/math/Vec3;)Lnet/minecraft/util/math/Vec3;"))
    private Vec3 collective$rocketBoost(LivingEntity entity, Vec3 oldVelocity, Operation<Vec3> original) {
        RocketBoost boost = RocketBoost.INSTANCE;
        Minecraft mc = Minecraft.getInstance();
        if (boost == null || !boost.isToggled() || entity != mc.player) {
            return original.call(entity, oldVelocity);
        }
        Vec3 vanilla = original.call(entity, oldVelocity);
        Vec3 boosted = boost.glideVelocity(oldVelocity, vanilla);
        return boosted != null ? boosted : vanilla;
    }

    @WrapOperation(method = "jump", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getYaw()F"))
    private float collective$wrapSprintJumpYaw(LivingEntity entity, Operation<Float> original) {
        RotationUtils rm = RotationUtils.getInstance();
        Minecraft mc = Minecraft.getInstance();
        if (entity == mc.player && me.collective.module.render.BetterF5.INSTANCE != null) {
            me.collective.module.render.BetterF5.INSTANCE.onJump();
        }
        return entity == mc.player && rm.isRotating() ? rm.getRotationYaw() : original.call(entity);
    }
}
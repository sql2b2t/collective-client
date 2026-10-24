package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.collective.util.RotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @ModifyExpressionValue(method = "updateVelocity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw()F"))
    private float collective$modifyUpdateVelocityYaw(float original) {
        if ((Object) this == Minecraft.getInstance().player) {
            RotationUtils rotUtils = RotationUtils.getInstance();
            Float moveYaw = rotUtils.getMoveYaw();
            if (moveYaw != null) {
                return moveYaw;
            }
        }
        return original;
    }

    @ModifyVariable(method = "updateVelocity", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float collective$modifyUpdateVelocitySpeed(float speed) {
        if ((Object) this == Minecraft.getInstance().player) {
            RotationUtils rotUtils = RotationUtils.getInstance();
            if (rotUtils.isRotating()) {
                return speed * rotUtils.getMoveSpeedScale();
            }
        }
        return speed;
    }
}
package me.collective.mixin;

import me.collective.module.movement.ElytraBounce;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class MixinPlayerEntity {
    @Inject(method = "updatePose", at = @At("HEAD"), cancellable = true)
    private void collective$lookLikeFalling(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (!ElytraBounce.lookLikeFalling()) return;
        if (self != net.minecraft.client.Minecraft.getInstance().player) return;
        if (!self.isGliding()) return;

        self.setPose(EntityPose.STANDING);
        ci.cancel();
    }
}
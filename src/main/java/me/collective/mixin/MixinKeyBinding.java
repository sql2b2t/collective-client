package me.collective.mixin;

import me.collective.module.movement.ElytraBounce;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyMapping.class)
public abstract class MixinKeyBinding {
    @Inject(method = "isDown", at = @At("RETURN"), cancellable = true)
    private void collective$isPressed(CallbackInfoReturnable<Boolean> cir) {
        ElytraBounce efly = ElytraBounce.INSTANCE;
        if (efly != null && efly.isToggled() && efly.enabled()) {
            KeyMapping self = (KeyMapping) (Object) this;
            String id = self.getName();
            if ("key.forward".equals(id)) {
                cir.setReturnValue(true);
            } else if ("key.jump".equals(id) && efly.shouldAutoJump()) {
                cir.setReturnValue(efly.isJumpKeyForcedDown());
            }
        }
    }
}
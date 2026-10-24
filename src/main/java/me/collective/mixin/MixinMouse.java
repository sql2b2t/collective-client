package me.collective.mixin;

import me.collective.event.EventBus;
import me.collective.event.events.MouseClickEvent;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MixinMouse {
    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void onMouseButtonHook(long window, MouseInput input, int action, CallbackInfo ci) {
        if (action == 1) { // GLFW.GLFW_PRESS
            EventBus.INSTANCE.post(new MouseClickEvent(input.button()));
        }
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScrollHook(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (vertical != 0) {
            me.collective.event.events.MouseScrollEvent event = new me.collective.event.events.MouseScrollEvent(vertical);
            EventBus.INSTANCE.post(event);
            if (event.isCancelled()) {
                ci.cancel();
            }
        }
    }
}


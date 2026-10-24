package me.collective.mixin;

import me.collective.CollectiveClient;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class MixinKeyboard {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void onKeyHook(long window, int action, KeyEvent keyInput, CallbackInfo ci) {
        if (action == 1) { // GLFW.GLFW_PRESS
            CollectiveClient.onKeyPressed(keyInput.key());
            me.collective.event.EventBus.INSTANCE.post(new me.collective.event.events.KeyInputEvent(keyInput.key()));
        }
    }
}
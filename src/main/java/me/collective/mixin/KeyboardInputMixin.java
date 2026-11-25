package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.utils.SoftwareKeyMapping;
import me.collective.client.utils.KeyUtils;
import me.collective.client.events.KeyboardInputTickEvent;
import me.collective.client.modules.movement.NoSlowModule;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin extends ClientInput {
    private static NoSlowModule NO_SLOW;
    private static NoSlowModule NO_SLOW() {
       if (NO_SLOW == null) {
          NO_SLOW = ClientContext.getModule(NoSlowModule.class);
       }
       return NO_SLOW;
    }
    @Unique
    private final SoftwareKeyMapping softwareKey = new SoftwareKeyMapping();
    @Shadow
    @Final
    private Options options;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTickHead(CallbackInfo callback) {
        KeyboardInputTickEvent.Pre preEvent = new KeyboardInputTickEvent.Pre((KeyboardInput)(Object)this);
        ClientAccess.eventBus.post(preEvent);
        if (preEvent.isCancelled()) {
            this.leftImpulse = 0.0F;
            this.forwardImpulse = 0.0F;
            KeyboardInputTickEvent event = new KeyboardInputTickEvent(preEvent.getInput());
            ClientAccess.eventBus.post(event);
            callback.cancel();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickTail(CallbackInfo callback) {
        KeyboardInputTickEvent event = new KeyboardInputTickEvent((KeyboardInput)(Object)this);
        ClientAccess.eventBus.post(event);
    }

    @ModifyReceiver(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z"))
    private KeyMapping useGuiMovementKey(KeyMapping key) {
        if (NO_SLOW().isToggled()
            && NO_SLOW().isGuiMoveAllowed()
            && NO_SLOW().guiMoveSetting.getValue()
            && key != this.options.keyShift) {
            int keyCode = ((KeyMappingAccessor)key).getKey().getValue();
            if (keyCode == -1) {
                return key;
            }

            this.softwareKey.setDown(KeyUtils.isPhysicalKeyDown(keyCode));
            return this.softwareKey;
        } else {
            return key;
        }
    }
}

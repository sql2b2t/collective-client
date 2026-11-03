package me.collective.mixin;

import me.collective.module.render.Ambience;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public class MixinWorld {
    @Inject(method = "getTimeOfDay", at = @At("RETURN"), cancellable = true)
    private void collective$onGetTimeOfDay(CallbackInfoReturnable<Long> cir) {
        if (!((World) (Object) this).isClient()) return;

        Ambience ambience = Ambience.INSTANCE;
        if (ambience != null && ambience.isToggled()) {
            Long time = ambience.getModifiedTime();
            if (time != null) {
                cir.setReturnValue(time);
            }
        }
    }

    @Inject(method = "getRainGradient", at = @At("HEAD"), cancellable = true)
    private void collective$onGetRainGradient(float delta, CallbackInfoReturnable<Float> cir) {
        if (!((World) (Object) this).isClient()) return;

        Ambience ambience = Ambience.INSTANCE;
        if (ambience != null && ambience.isToggled()) {
            Float rain = ambience.getModifiedRain();
            if (rain != null) {
                cir.setReturnValue(rain);
            }
        }
    }

    @Inject(method = "getThunderGradient", at = @At("HEAD"), cancellable = true)
    private void collective$onGetThunderGradient(float delta, CallbackInfoReturnable<Float> cir) {
        if (!((World) (Object) this).isClient()) return;

        Ambience ambience = Ambience.INSTANCE;
        if (ambience != null && ambience.isToggled()) {
            Float thunder = ambience.getModifiedThunder();
            if (thunder != null) {
                cir.setReturnValue(thunder);
            }
        }
    }
}

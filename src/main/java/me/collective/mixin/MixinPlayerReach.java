package me.collective.mixin;

import me.collective.module.combat.Reach;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Часть порта Reach из ThunderHack.
 *
 * <p>Подменяет дальность взаимодействия с блоками и сущностями для себя.
 */
@Mixin(Player.class)
public abstract class MixinPlayerReach {

    /** Модуль кэшируется один раз: проверки дальности вызываются на каждый блок/сущность. */
    private Reach collective$module;

    private Reach reach$module() {
        Reach mod = this.collective$module;
        if (mod == null) this.collective$module = mod = Reach.INSTANCE;
        return mod;
    }

    @Inject(method = "getBlockInteractionRange", at = @At("HEAD"), cancellable = true)
    private void reach$blocks(CallbackInfoReturnable<Double> cir) {
        if ((Object) this != Minecraft.getInstance().player) return;

        Reach mod = reach$module();
        if (mod == null || !mod.isToggled()) return;
        cir.setReturnValue(mod.creative.getValue()
            ? (double) mod.creativeBlocksRange.compute()
            : (double) mod.blocksRange.compute());
    }

    @Inject(method = "getEntityInteractionRange", at = @At("HEAD"), cancellable = true)
    private void reach$entities(CallbackInfoReturnable<Double> cir) {
        if ((Object) this != Minecraft.getInstance().player) return;

        Reach mod = reach$module();
        if (mod == null || !mod.isToggled()) return;
        cir.setReturnValue(mod.creative.getValue()
            ? (double) mod.creativeEntityRange.compute()
            : (double) mod.entityRange.compute());
    }
}
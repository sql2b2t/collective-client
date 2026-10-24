package me.collective.mixin;

import me.collective.module.movement.NoWeb;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CobwebBlock.class)
public class CobwebBlockMixin {
    @Inject(method = "onEntityCollision", at = @At("HEAD"), cancellable = true)
    private void collective$onEntityCollision(BlockState state, Level world, BlockPos pos, Entity entity,
                                              EntityCollisionHandler handler, boolean bl, CallbackInfo ci) {
        if (NoWeb.isIgnoreActive() && entity == Minecraft.getInstance().player) {
            ci.cancel();
            NoWeb.onEntityCollideCobweb(pos);
        }
    }
}
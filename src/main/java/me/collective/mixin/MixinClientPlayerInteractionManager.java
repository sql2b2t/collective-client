package me.collective.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.collective.event.EventBus;
import me.collective.event.events.StartBreakingBlockEvent;
import me.collective.module.player.NoInteract;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class MixinClientPlayerInteractionManager {
    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void onStartDestroyBlock(BlockPos blockPos, Direction direction, CallbackInfoReturnable<Boolean> info) {
        StartBreakingBlockEvent event = new StartBreakingBlockEvent(blockPos, direction);
        EventBus.INSTANCE.post(event);
        if (event.isCancelled()) {
            info.setReturnValue(false);
            info.cancel();
        }
    }

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void onInteractEntity(Player player, Entity entity, EntityHitResult hitResult, InteractionHand hand, CallbackInfoReturnable<InteractionResult> info) {
        me.collective.event.events.InteractEntityEvent event = new me.collective.event.events.InteractEntityEvent(player, entity, hand);
        EventBus.INSTANCE.post(event);
        if (event.isCancelled()) {
            info.setReturnValue(InteractionResult.FAIL);
            info.cancel();
        }
    }

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void onUseItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        if (hitResult != null) {
            me.collective.util.tracker.ContainerIndexRecorder.onBlockInteract(hitResult.getBlockPos());
        }
    }

    /** NoInteract, строгий режим: ручной ПКМ по блоку полностью игнорируется. */
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void onNoInteractStrict(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> info) {
        NoInteract module = NoInteract.INSTANCE;
        if (module != null && module.shouldBlockManual() && module.isStrict()) {
            info.setReturnValue(InteractionResult.FAIL);
            info.cancel();
        }
    }
}
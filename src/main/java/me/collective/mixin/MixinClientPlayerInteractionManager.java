package me.collective.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.collective.event.EventBus;
import me.collective.event.events.StartBreakingBlockEvent;
import me.collective.module.player.NoInteract;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class MixinClientPlayerInteractionManager {
    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    private void onAttackBlock(BlockPos blockPos, Direction direction, CallbackInfoReturnable<Boolean> info) {
        StartBreakingBlockEvent event = new StartBreakingBlockEvent(blockPos, direction);
        EventBus.INSTANCE.post(event);
        if (event.isCancelled()) {
            info.setReturnValue(false);
            info.cancel();
        }
    }

    @Inject(method = "interactEntity", at = @At("HEAD"), cancellable = true)
    private void onInteractEntity(net.minecraft.entity.player.Player player, net.minecraft.entity.Entity entity, net.minecraft.util.InteractionHand hand, CallbackInfoReturnable<net.minecraft.util.InteractionResult> info) {
        me.collective.event.events.InteractEntityEvent event = new me.collective.event.events.InteractEntityEvent(player, entity, hand);
        EventBus.INSTANCE.post(event);
        if (event.isCancelled()) {
            info.setReturnValue(net.minecraft.util.InteractionResult.FAIL);
            info.cancel();
        }
    }

    @Inject(method = "interactBlock", at = @At("HEAD"))
    private void onInteractBlock(net.minecraft.client.network.LocalPlayer player, net.minecraft.util.InteractionHand hand, net.minecraft.util.hit.BlockHitResult hitResult, CallbackInfoReturnable<net.minecraft.util.InteractionResult> cir) {
        if (hitResult != null) {
            me.collective.util.tracker.ContainerIndexRecorder.onBlockInteract(hitResult.getBlockPos());
        }
    }

    /** NoInteract, строгий режим: ручной ПКМ по блоку полностью игнорируется. */
    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void onNoInteractStrict(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> info) {
        NoInteract module = NoInteract.INSTANCE;
        if (module != null && module.shouldBlockManual() && module.isStrict()) {
            info.setReturnValue(InteractionResult.FAIL);
            info.cancel();
        }
    }

    /**
     * NoInteract: глушит ветку onUse блока, чтобы при ручном ПКМ не открывались ГУИ
     * и не срабатывали двери, кнопки и рычаги. Установка блоков и использование
     * предметов на блоках продолжают работать (ветка useOnBlock не затрагивается).
     */
    @WrapOperation(method = "interactBlockInternal", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;onUseWithItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/Level;Lnet/minecraft/entity/player/Player;Lnet/minecraft/util/InteractionHand;Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/InteractionResult;"))
    private InteractionResult onNoInteractBlockUse(BlockState blockState, ItemStack stack, Level world, Player player, InteractionHand hand, BlockHitResult hitResult, Operation<InteractionResult> original) {
        InteractionResult result = original.call(blockState, stack, world, player, hand, hitResult);
        NoInteract module = NoInteract.INSTANCE;
        if (module != null && module.shouldBlockManual() && !module.isStrict()
                && result instanceof InteractionResult.PassToDefaultBlockAction) {
            return InteractionResult.PASS;
        }
        return result;
    }
}
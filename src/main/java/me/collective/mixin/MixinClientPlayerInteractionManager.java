package me.collective.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.collective.event.EventBus;
import me.collective.event.events.StartBreakingBlockEvent;
import me.collective.module.player.NoInteract;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
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
    private void onInteractEntity(net.minecraft.entity.player.PlayerEntity player, net.minecraft.entity.Entity entity, net.minecraft.util.Hand hand, CallbackInfoReturnable<net.minecraft.util.ActionResult> info) {
        me.collective.event.events.InteractEntityEvent event = new me.collective.event.events.InteractEntityEvent(player, entity, hand);
        EventBus.INSTANCE.post(event);
        if (event.isCancelled()) {
            info.setReturnValue(net.minecraft.util.ActionResult.FAIL);
            info.cancel();
        }
    }

    @Inject(method = "interactBlock", at = @At("HEAD"))
    private void onInteractBlock(net.minecraft.client.network.ClientPlayerEntity player, net.minecraft.util.Hand hand, net.minecraft.util.hit.BlockHitResult hitResult, CallbackInfoReturnable<net.minecraft.util.ActionResult> cir) {
        if (hitResult != null) {
            me.collective.util.tracker.ContainerIndexRecorder.onBlockInteract(hitResult.getBlockPos());
        }
    }

    /** NoInteract, строгий режим: ручной ПКМ по блоку полностью игнорируется. */
    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void onNoInteractStrict(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> info) {
        NoInteract module = NoInteract.INSTANCE;
        if (module != null && module.shouldBlockManual() && module.isStrict()) {
            info.setReturnValue(ActionResult.FAIL);
            info.cancel();
        }
    }

    /**
     * NoInteract: глушит ветку onUse блока, чтобы при ручном ПКМ не открывались ГУИ
     * и не срабатывали двери, кнопки и рычаги. Установка блоков и использование
     * предметов на блоках продолжают работать (ветка useOnBlock не затрагивается).
     */
    @WrapOperation(method = "interactBlockInternal", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;onUseWithItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ActionResult;"))
    private ActionResult onNoInteractBlockUse(BlockState blockState, ItemStack stack, World world, PlayerEntity player, Hand hand, BlockHitResult hitResult, Operation<ActionResult> original) {
        ActionResult result = original.call(blockState, stack, world, player, hand, hitResult);
        NoInteract module = NoInteract.INSTANCE;
        if (module != null && module.shouldBlockManual() && !module.isStrict()
                && result instanceof ActionResult.PassToDefaultBlockAction) {
            return ActionResult.PASS;
        }
        return result;
    }
}

package me.collective.mixin;

import me.collective.client.ClientAccess;
import me.collective.client.events.ItemUseEvent;
import me.collective.client.managers.GlintRegistry;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
   @Shadow
   public abstract Item getItem();

   @Inject(method = "finishUsingItem", at = @At("HEAD"))
   private void postBeforeItemUseFinish(
      Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> callback
   ) {
      if (entity instanceof LocalPlayer) {
         ItemUseEvent.BeforeFinish event = new ItemUseEvent.BeforeFinish((ItemStack)(Object)this);
         ClientAccess.eventBus
            .post(event);
      }
   }

   @Inject(method = "hasFoil", at = @At("HEAD"), cancellable = true)
   private void overrideItemFoil(CallbackInfoReturnable<Boolean> callback) {
      GlintRegistry.GlintMode glintMode = GlintRegistry.getOverride(this.getItem());
      if (glintMode != null) {
         callback.setReturnValue(glintMode == GlintRegistry.GlintMode.ALWAYS);
         callback.cancel();
      }
   }
}

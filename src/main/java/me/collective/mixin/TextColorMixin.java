package me.collective.mixin;

import me.collective.client.render.TextColorSupplierAccess;
import java.util.function.Supplier;
import net.minecraft.network.chat.TextColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TextColor.class)
public class TextColorMixin implements TextColorSupplierAccess {
   @Unique
   private Supplier colorSupplier = null;

   @Inject(method = "getValue", at = @At("HEAD"), cancellable = true)
   private void applySuppliedTextColor(CallbackInfoReturnable<Integer> callback) {
      if (this.colorSupplier != null) {
         callback.setReturnValue((Integer)this.colorSupplier.get());
      }
   }

   @Override
   public Supplier getSupplier() {
      return this.colorSupplier;
   }

   @Override
   public void setSupplier(Supplier supplier) {
      this.colorSupplier = supplier;
   }
}

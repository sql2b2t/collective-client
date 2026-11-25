package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.collective.client.utils.inventory.ChestSearchController;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EditBox.class)
public class EditBoxMixin {
   @ModifyExpressionValue(method = "renderWidget", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/EditBox;isBordered()Z", ordinal = 0))
   public boolean collective$hideSearchBoxBorder(boolean original) {
      EditBox editBox = (EditBox)(Object)this;
      return editBox == ChestSearchController.searchBox ? false : original;
   }
}

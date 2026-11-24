package me.collective.mixin;

import me.collective.client.render.TextColorSupplierAccess;
import me.collective.client.render.DynamicColorStyle;
import java.util.function.Supplier;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Style.class)
public class StyleMixin implements DynamicColorStyle {
   @Unique
   @Override
   public Style collective$withColor(Supplier supplier) {
      Style style = (Style)(Object)this;
      if (style.getColor() != null) {
         ((TextColorSupplierAccess)(Object)style.getColor()).setSupplier(supplier);
      }

      return style;
   }
}

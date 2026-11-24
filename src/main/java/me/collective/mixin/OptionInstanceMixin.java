package me.collective.mixin;

import me.collective.client.access.OptionValueAccess;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(OptionInstance.class)
public class OptionInstanceMixin implements OptionValueAccess {
   @Shadow
   Object value;

   @Override
   public void forceSetValue(Object newValue) {
      this.value = newValue;
   }
}

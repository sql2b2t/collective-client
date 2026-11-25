package me.collective.mixin;

import me.collective.client.utils.inventory.PalettedContainerAccess;
import me.collective.client.utils.inventory.PalettedContainerDataAccess;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainer.Data;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PalettedContainer.class)
public class PalettedContainerMixin implements PalettedContainerAccess {
   @Shadow
   private volatile Data data;

   @Override
   public PalettedContainerDataAccess collective$data() {
      return (PalettedContainerDataAccess)(Object)this.data;
   }
}

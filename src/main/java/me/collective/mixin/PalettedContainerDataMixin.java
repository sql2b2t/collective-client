package me.collective.mixin;

import me.collective.client.utils.inventory.PalettedContainerDataAccess;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer.Data;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Data.class)
public class PalettedContainerDataMixin implements PalettedContainerDataAccess {
   @Shadow
   @Final
   BitStorage storage;
   @Shadow
   @Final
   Palette palette;

   @Override
   public BitStorage collective$storage() {
      return this.storage;
   }

   @Override
   public Palette collective$palette() {
      return this.palette;
   }
}

package me.collective.mixin;

import me.collective.client.utils.network.ImportantPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerboundUseItemPacket.class)
public class ServerboundUseItemPacketMixin implements ImportantPacket {
   @Unique
   private boolean important;

   @Override
   public void collective$markImportant() {
      this.important = true;
   }

   @Override
   public boolean collective$isImportant() {
      return this.important;
   }
}

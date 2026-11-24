package me.collective.mixin;

import me.collective.client.utils.network.ImportantPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerboundPlayerActionPacket.class)
public class ServerboundPlayerActionPacketMixin implements ImportantPacket {
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

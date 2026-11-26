package me.collective.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.collective.client.integrations.ProtocolVersionAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(ProtocolVersion.class)
public abstract class ProtocolVersionMixin implements ProtocolVersionAccess {
   @Shadow(remap = false)
   public abstract boolean olderThanOrEqualTo(ProtocolVersion version);

   @Override
   public boolean olderEq_1_20_6() {
      return this.olderThanOrEqualTo(ProtocolVersion.v1_20_5);
   }

   @Override
   public boolean olderEq_1_21_4() {
      return this.olderThanOrEqualTo(ProtocolVersion.v1_21_4);
   }

   @Override
   public boolean olderEq_1_21_5() {
      return this.olderThanOrEqualTo(ProtocolVersion.v1_21_5);
   }
}

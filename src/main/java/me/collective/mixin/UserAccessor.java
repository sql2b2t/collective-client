package me.collective.mixin;

import java.util.UUID;
import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(User.class)
public interface UserAccessor {
   @Mutable
   @Accessor("name")
   void setUsername(String name);

   @Mutable
   @Accessor("uuid")
   void setUuid(UUID uuid);
}

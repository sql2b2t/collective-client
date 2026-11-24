package me.collective.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientLevel.class)
public interface ClientLevelAccessor {
   @Accessor("environmentAttributes")
   @Mutable
   void collective$setEnvironmentAttributes(EnvironmentAttributeSystem attributes);

   @Invoker("addEnvironmentAttributeLayers")
   EnvironmentAttributeSystem.Builder collective$addEnvironmentAttributeLayers(EnvironmentAttributeSystem.Builder builder);

   @Accessor("blockStatePredictionHandler")
   BlockStatePredictionHandler getPendingUpdateManager();
}

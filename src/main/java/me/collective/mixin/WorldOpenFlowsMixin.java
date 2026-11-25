package me.collective.mixin;

import me.collective.client.ClientContext;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldOpenFlows.class)
public class WorldOpenFlowsMixin {

   @Inject(method = "openWorld", at = @At("HEAD"))
   public void clearServerDataForWorldOpen(
      String worldName, Runnable onCancel, CallbackInfo callback
   ) {
      ClientContext.serverStateManager
         .setServerData((ServerData)null);
   }
}

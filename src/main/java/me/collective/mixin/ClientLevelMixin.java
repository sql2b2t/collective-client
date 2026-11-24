package me.collective.mixin;

import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.events.ClientLevelAddEntityEvent;
import me.collective.client.events.ClientLevelRemoveEntityEvent;
import me.collective.client.modules.render.NoRenderModule;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
   private static NoRenderModule noRenderModule;
   private static NoRenderModule noRenderModule() {
      if (noRenderModule == null) {
         noRenderModule = ClientContext.getModule(NoRenderModule.class);
      }
      return noRenderModule;
   }

   @Inject(method = "removeEntity", at = @At("HEAD"), cancellable = true)
   private void postRemoveEntityEvent(
      int entityId, RemovalReason reason, CallbackInfo callback
   ) {
      ClientLevel self = (ClientLevel)(Object)this;
      Entity entity = self.getEntity(entityId);
      ClientLevelRemoveEntityEvent event = new ClientLevelRemoveEntityEvent(entityId, entity != null);
      ClientAccess.eventBus
         .post(event);
      // Отмена полного removeEntity оставляет сущность живой (призрак), поэтому
      // подавляем удаление только для реально существующей сущности и только пока
      // этот мир ещё активен — на дисконнекте/смене мира удаление проходит штатно.
      if (event.isCancelled()
         && entity != null
         && !entity.isRemoved()
         && ClientAccess.collectiveMinecraft.level == self) {
         callback.cancel();
      }
   }

   @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
   private void postAddEntityEvent(Entity entity, CallbackInfo callback) {
      ClientLevelAddEntityEvent event = new ClientLevelAddEntityEvent(entity);
      ClientAccess.eventBus
         .post(event);
      if (event.isCancelled()) {
         callback.cancel();
      }
   }

   @Inject(method = "addBreakingBlockEffect", at = @At("HEAD"), cancellable = true)
   private void suppressBreakingBlockEffect(
      BlockPos blockPos, Direction face, CallbackInfo callback
   ) {
      if (noRenderModule().shouldHideBlockBreakingParticles()) {
         callback.cancel();
      }
   }

   @Inject(method = "addDestroyBlockEffect", at = @At("HEAD"), cancellable = true)
   private void suppressDestroyBlockEffect(
      BlockPos blockPos, BlockState blockState, CallbackInfo callback
   ) {
      if (noRenderModule().shouldHideBlockBreakingParticles()) {
         callback.cancel();
      }
   }
}

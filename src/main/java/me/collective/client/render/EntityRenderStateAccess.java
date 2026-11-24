package me.collective.client.render;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

public interface EntityRenderStateAccess {
   Entity collective$getEntity();

   void collective$setEntity(Entity entity);

   static Entity getEntity(EntityRenderState renderState) {
      EntityRenderStateAccess access = (EntityRenderStateAccess)renderState;
      return access.collective$getEntity();
   }

   static void setEntity(EntityRenderState renderState, Entity entity) {
      EntityRenderStateAccess access = (EntityRenderStateAccess)renderState;
      access.collective$setEntity(entity);
   }
}

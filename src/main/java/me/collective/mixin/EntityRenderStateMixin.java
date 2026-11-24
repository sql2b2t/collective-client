package me.collective.mixin;

import me.collective.client.render.EntityRenderStateAccess;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class EntityRenderStateMixin implements EntityRenderStateAccess {
   @Unique
   private Entity renderedEntity;

   @Override
   public Entity collective$getEntity() {
      return this.renderedEntity;
   }

   @Override
   public void collective$setEntity(Entity entity) {
      this.renderedEntity = entity;
   }
}

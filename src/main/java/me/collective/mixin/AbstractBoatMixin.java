package me.collective.mixin;

import me.collective.client.ClientAccess;
import me.collective.client.utils.math.Vec3Utils;
import me.collective.client.events.VehicleMoveEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractBoat.class)
public abstract class AbstractBoatMixin extends Entity implements ClientAccess {
   @Unique
   private VehicleMoveEvent vehicleMoveEvent;

   public AbstractBoatMixin(EntityType entityType, Level level) {
      super(entityType, level);
   }

   @Inject(
      method = "tick",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/vehicle/boat/AbstractBoat;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", shift = Shift.BEFORE)
   )
   private void postVehicleMovement(CallbackInfo callback) {
      if (VehicleMoveEvent.isLocalPlayerVehicle(this)) {
         this.vehicleMoveEvent = new VehicleMoveEvent(this.getDeltaMovement());
         ClientAccess.eventBus.post(
            this.vehicleMoveEvent
         );
         Vec3Utils.setComponents(
            this.getDeltaMovement(),
            this.vehicleMoveEvent
               .getX(),
            this.vehicleMoveEvent
               .getY(),
            this.vehicleMoveEvent
               .getZ()
         );
      }
   }
}

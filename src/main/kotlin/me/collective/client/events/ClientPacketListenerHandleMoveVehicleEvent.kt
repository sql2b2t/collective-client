package me.collective.client.events

import net.minecraft.world.phys.Vec3

class ClientPacketListenerHandleMoveVehicleEvent(val position: Vec3) : CancellableEvent()

package me.collective.client.events

import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.BlockHitResult

class MultiPlayerGameModeUseItemOnEvent(val hitResult: BlockHitResult, val hand: InteractionHand) : CancellableEvent()

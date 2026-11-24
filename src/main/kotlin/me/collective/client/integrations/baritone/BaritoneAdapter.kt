package me.collective.client.integrations.baritone

import me.collective.client.utils.chat.ChatUtils

import me.collective.client.ClientAccess
import me.collective.client.NamedFeature
import me.collective.client.events.bus.EventBus
import baritone.api.BaritoneAPI
import baritone.api.IBaritone
import baritone.api.behavior.IPathingBehavior
import baritone.api.pathing.calc.IPathingControlManager
import baritone.api.pathing.goals.Goal
import baritone.api.pathing.goals.GoalBlock
import baritone.api.pathing.goals.GoalXZ
import baritone.api.process.IBaritoneProcess
import baritone.api.process.IElytraProcess
import baritone.api.process.PathingCommand
import baritone.api.process.PathingCommandType
import baritone.api.selection.ISelection
import baritone.api.utils.BetterBlockPos
import baritone.api.utils.interfaces.IGoalRenderPos
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import it.unimi.dsi.fastutil.objects.ObjectSet
import it.unimi.dsi.fastutil.objects.ObjectSets
import java.util.function.Consumer
import net.minecraft.client.GuiMessageTag
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class BaritoneAdapter : ClientAccess, BaritoneBridge {
   private val pauseRequests: ObjectSet<NamedFeature> = ObjectSets.synchronize(ObjectOpenHashSet<NamedFeature>())

   override fun pathElytraTo(position: BlockPos) {
      val process: IElytraProcess = BaritoneAPI.getProvider().primaryBaritone.elytraProcess
      process.pathTo(position)
   }

   override fun setGoal(position: BlockPos) {
      val goal = GoalBlock(position)
      BaritoneAPI.getProvider().primaryBaritone.customGoalProcess.setGoal(goal)
   }

   override fun isPathingOrCalculating(): Boolean {
      val pathing: IPathingBehavior = BaritoneAPI.getProvider().primaryBaritone.pathingBehavior
      return pathing.isPathing || pathing.inProgress.isPresent
   }

   override fun isInSelection(position: BlockPos): Boolean {
      val baritone: IBaritone = BaritoneAPI.getProvider().primaryBaritone
      if (baritone.selectionManager.lastSelection == null) {
         return false
      }

      val selections = baritone.selectionManager.selections
      for (selection in selections) {
         val minimum: BetterBlockPos = selection.min()
         val maximum: BetterBlockPos = selection.max()
         val bounds = AABB.encapsulatingFullBlocks(minimum, maximum)
         val center: Vec3 = position.center
         if (bounds.contains(center)) {
            return true
         }
      }

      return false
   }

   override fun isPauseRequested(): Boolean {
      return !this.pauseRequests.isEmpty()
   }

   override fun getGoalPosition(): BlockPos? {
      val goal: Goal = BaritoneAPI.getProvider().primaryBaritone.pathingBehavior.goal
      return when (goal) {
         is IGoalRenderPos -> goal.goalPos
         is GoalXZ -> {
            val x = goal.x.toDouble()
            val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            val y = player.y
            val z = goal.z.toDouble()
            BlockPos.containing(x, y, z)
         }
         else -> null
      }
   }

   override fun pathTo(position: BlockPos) {
      val goal = GoalBlock(position)
      BaritoneAPI.getProvider().primaryBaritone.customGoalProcess.setGoalAndPath(goal)
   }

   override fun cancelPathing() {
      val pathing: IPathingBehavior = BaritoneAPI.getProvider().primaryBaritone.pathingBehavior
      pathing.cancelEverything()
      pathing.forceCancel()
   }

   override fun isAvailable(): Boolean {
      return true
   }

   init {
      val eventBus: EventBus = ClientAccess.eventBus
      eventBus.subscribe(this)
      val controlManager: IPathingControlManager = BaritoneAPI.getProvider().primaryBaritone.pathingControlManager
      controlManager.registerProcess(PauseProcess())
      BaritoneAPI.getSettings().useMessageTag.value = false
      BaritoneAPI.getSettings().logger.value = Consumer { message: Component ->
         val copiedMessage: MutableComponent = message.copy()
         val siblings = copiedMessage.siblings
         siblings.addFirst(Component.literal(" "))
         copiedMessage.siblings.addFirst(ChatUtils.createPrefix())
         val gui: Gui = ClientAccess.collectiveMinecraft.gui
         val chat: ChatComponent = gui.chat
         val tag: GuiMessageTag = ChatUtils.MESSAGE_TAG
         chat.addMessage(copiedMessage, null, tag)
      }
   }

   override fun requestPause(feature: NamedFeature) {
      if (!this.pauseRequests.contains(feature)) {
         this.pauseRequests.add(feature)
      }
   }

   override fun releasePause(feature: NamedFeature) {
      if (this.pauseRequests.contains(feature)) {
         this.pauseRequests.remove(feature)
      }
   }

   override fun pathForward() {
      val playerPosition: Vec3 = ClientAccess.collectiveMinecraft.player!!.position()
      val playerYaw = ClientAccess.collectiveMinecraft.player!!.yRot
      val goal = GoalXZ.fromDirection(playerPosition, playerYaw, 5.0)
      BaritoneAPI.getProvider().primaryBaritone.customGoalProcess.setGoalAndPath(goal)
   }

   private inner class PauseProcess : IBaritoneProcess {
      override fun priority(): Double {
         return 999.0
      }

      override fun isActive(): Boolean {
         return !this@BaritoneAdapter.pauseRequests.isEmpty()
      }

      override fun isTemporary(): Boolean {
         return true
      }

      override fun onLostControl() {
      }

      override fun displayName0(): String {
         return DISPLAY_NAME
      }

      override fun onTick(calculationFailed: Boolean, safeToCancel: Boolean): PathingCommand {
         return PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
      }
   }

   private companion object {
      const val DISPLAY_NAME: String = "Collective pause service"
   }
}

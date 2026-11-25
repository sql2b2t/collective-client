package me.collective.client.managers

import java.util.ArrayDeque
import java.util.Collections
import java.util.Queue
import me.collective.client.ClientAccess
import me.collective.client.events.EventHandler
import me.collective.client.events.MinecraftRunTickEvent
import me.collective.client.events.RotationRequestEvent
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.util.thread.BlockableEventLoop

class TaskScheduler : ClientAccess {
   private val rotationTasks = ClientThreadTaskQueue()
   private val runTickTasks = ClientThreadTaskQueue()

   init {
      ClientAccess.eventBus.subscribe(this)
   }

   fun scheduleRotationTask(task: Runnable, delay: Int) {
      this.rotationTasks.enqueue(task, delay)
   }

   fun scheduleRunTickTask(task: Runnable) {
      this.runTickTasks.enqueue(task, 1)
   }

   @EventHandler(priority = -100)
   private fun onRotationEvent(event: RotationRequestEvent) {
      this.rotationTasks.run()
   }

   @EventHandler(priority = -100)
   private fun onMinecraftRunTickEvent(event: MinecraftRunTickEvent) {
      this.runTickTasks.run()
   }

   fun scheduleScreenChange(screen: Screen?) {
      this.scheduleRunTickTask(Runnable {
         ClientAccess.collectiveMinecraft.setScreen(screen)
      })
   }

   private class ClientThreadTaskQueue {
      private val activeTasks: MutableList<CountdownTask> = Collections.synchronizedList(ArrayList())
      private val pendingTasks: Queue<CountdownTask> = ArrayDeque()

      fun run() {
         synchronized(this.activeTasks) {
            while (this.pendingTasks.isNotEmpty()) {
               this.activeTasks.add(this.pendingTasks.poll())
            }
            this.activeTasks.removeIf { it.runIfReady() }
         }
      }

      private fun enqueue(task: CountdownTask) {
         synchronized(this.activeTasks) {
            this.pendingTasks.add(task)
         }
      }

      @Suppress("UNCHECKED_CAST")
      fun enqueue(task: Runnable, delay: Int) {
         val minecraft = Minecraft.getInstance()
         val countdownTask = CountdownTask(task, delay)
         if (minecraft.isSameThread) {
            this.enqueue(countdownTask)
         } else {
            (minecraft as BlockableEventLoop<Runnable>).executeIfPossible { this.enqueue(countdownTask) }
         }
      }
   }

   private class CountdownTask(private val task: Runnable, private var remainingPasses: Int) {
      fun runIfReady(): Boolean {
         if (--this.remainingPasses <= 0) {
            try {
               this.task.run()
            } catch (taskFailure: Throwable) {
               // Одна упавшая задача не должна срывать выполнение остальных из очереди.
               ClientAccess.logger.error("Scheduled task failed: {}", taskFailure.toString())
            }
            return true
         }
         return false
      }
   }
}

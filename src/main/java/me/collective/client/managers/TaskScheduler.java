package me.collective.client.managers;

import me.collective.client.ClientAccess;
import me.collective.client.events.EventHandler;
import me.collective.client.events.MinecraftRunTickEvent;
import me.collective.client.events.RotationRequestEvent;
import me.collective.client.events.bus.EventBus;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.thread.BlockableEventLoop;

public final class TaskScheduler implements ClientAccess {
   private final ClientThreadTaskQueue rotationTasks = new ClientThreadTaskQueue();
   private final ClientThreadTaskQueue runTickTasks = new ClientThreadTaskQueue();

   public TaskScheduler() {
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribe(this);
   }

   public void scheduleRotationTask(Runnable task, int delay) {
      this.rotationTasks.enqueue(task, delay);
   }

   public void scheduleRunTickTask(Runnable task) {
      this.runTickTasks.enqueue(task, 1);
   }

   @EventHandler(priority = -100)
   private void onRotationEvent(RotationRequestEvent event) {
      this.rotationTasks.run();
   }

   @EventHandler(priority = -100)
   private void onMinecraftRunTickEvent(MinecraftRunTickEvent event) {
      this.runTickTasks.run();
   }

   public void scheduleScreenChange(Screen screen) {
      this.scheduleRunTickTask(() -> {
         ClientAccess.collectiveMinecraft.setScreen(screen);
      });
   }

   private static final class ClientThreadTaskQueue {
      private final List<CountdownTask> activeTasks = Collections.synchronizedList(new ArrayList<>());
      private final Queue<CountdownTask> pendingTasks = new ArrayDeque<>();

      private void run() {
         synchronized (this.activeTasks) {
            while (!this.pendingTasks.isEmpty()) {
               this.activeTasks.add(this.pendingTasks.poll());
            }

            this.activeTasks.removeIf(CountdownTask::runIfReady);
         }
      }

      private void enqueue(CountdownTask task) {
         synchronized (this.activeTasks) {
            this.pendingTasks.add(task);
         }
      }

      private void enqueue(Runnable task, int delay) {
         Minecraft minecraft = Minecraft.getInstance();
         CountdownTask countdownTask = new CountdownTask(task, delay);
         if (minecraft.isSameThread()) {
            this.enqueue(countdownTask);
         } else {
            ((BlockableEventLoop<Runnable>)minecraft).executeIfPossible(() -> this.enqueue(countdownTask));
         }
      }
   }

   private static final class CountdownTask {
      private final Runnable task;
      private int remainingPasses;

      private CountdownTask(Runnable task, int remainingPasses) {
         this.task = task;
         this.remainingPasses = remainingPasses;
      }

      private boolean runIfReady() {
         if (--this.remainingPasses <= 0) {
            try {
               this.task.run();
            } catch (Throwable taskFailure) {
               // Одна упавшая задача не должна срывать выполнение остальных из очереди.
               ClientAccess.logger.error("Scheduled task failed: {}", taskFailure.toString());
            }
            return true;
         } else {
            return false;
         }
      }
   }
}

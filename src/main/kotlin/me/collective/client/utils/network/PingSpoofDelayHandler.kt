package me.collective.client.utils.network

import me.collective.client.modules.exploit.PingSpoofModule
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.util.ReferenceCountUtil
import io.netty.util.concurrent.ScheduledFuture
import java.nio.channels.ClosedChannelException
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier

class PingSpoofDelayHandler(
   private val module: PingSpoofModule,
   private val inPlay: BooleanSupplier
) : ChannelDuplexHandler() {

   companion object {
      const val NAME = "collective_delayed_handler"
   }

   private lateinit var context: ChannelHandlerContext
   private lateinit var outbound: DelayQueue
   private lateinit var inbound: DelayQueue

   override fun handlerAdded(context: ChannelHandlerContext) {
      this.context = context
      this.outbound = DelayQueue({ writeQueued(it) }, { context.flush() })
      this.inbound = DelayQueue({ readQueued(it) }, { context.fireChannelReadComplete() })
   }

   override fun handlerRemoved(context: ChannelHandlerContext) {
      this.outbound.drain()
      this.inbound.drain()
   }

   override fun channelInactive(context: ChannelHandlerContext) {
      this.inbound.drain()
      this.outbound.discard()
      super.channelInactive(context)
   }

   override fun write(context: ChannelHandlerContext, message: Any, promise: ChannelPromise) {
      if (message !is ByteBuf) {
         context.write(message, promise)
         return
      }
      val delayMillis = this.getDelayMillis()
      if (delayMillis <= 0L && this.outbound.isEmpty()) {
         context.write(message, promise)
         return
      }
      this.outbound.enqueue(message, promise, delayMillis)
   }

   override fun flush(context: ChannelHandlerContext) {
      if (this.outbound.isEmpty()) {
         context.flush()
      }
   }

   private fun writeQueued(queued: DelayedMessage) {
      if (this.context.channel().isActive) {
         this.context.write(queued.message, queued.promise)
      } else {
         ReferenceCountUtil.release(queued.message)
         if (queued.promise != null) {
            queued.promise.tryFailure(ClosedChannelException())
         }
      }
   }

   override fun channelRead(context: ChannelHandlerContext, message: Any) {
      if (message !is ByteBuf) {
         context.fireChannelRead(message)
         return
      }
      val delayMillis = this.getDelayMillis()
      if (delayMillis <= 0L && this.inbound.isEmpty()) {
         context.fireChannelRead(message)
         return
      }
      this.inbound.enqueue(message, null, delayMillis)
   }

   override fun channelReadComplete(context: ChannelHandlerContext) {
      if (this.inbound.isEmpty()) {
         context.fireChannelReadComplete()
      }
   }

   private fun readQueued(queued: DelayedMessage) {
      if (this.context.channel().isActive) {
         this.context.fireChannelRead(queued.message)
      } else {
         ReferenceCountUtil.release(queued.message)
      }
   }

   private fun getDelayMillis(): Long {
      if (this.module.isToggled() && this.module.isFullMode() && this.inPlay.asBoolean) {
         return (this.module.sampleDelayMillis() / 2).toLong()
      }
      return 0L
   }

   private inner class DelayQueue(
      val consumer: (DelayedMessage) -> Unit,
      val onBatchComplete: Runnable
   ) {
      private val messages = ArrayDeque<DelayedMessage>()
      private var scheduled: ScheduledFuture<*>? = null
      private var lastDeadline = System.nanoTime()

      fun isEmpty(): Boolean {
         return this.messages.isEmpty()
      }

      fun enqueue(message: Any, promise: ChannelPromise?, delayMillis: Long) {
         val now = System.nanoTime()
         var deadline = now + TimeUnit.MILLISECONDS.toNanos(delayMillis)
         if (deadline - this.lastDeadline < 0L) {
            deadline = this.lastDeadline
         }
         this.lastDeadline = deadline
         this.messages.add(DelayedMessage(message, promise, deadline))
         if (this.scheduled == null) {
            this.schedule(deadline - now)
         }
      }

      private fun schedule(delayNanos: Long) {
         this.scheduled = context.executor().schedule({ releaseExpired() }, maxOf(delayNanos, 0L), TimeUnit.NANOSECONDS)
      }

      fun releaseExpired() {
         this.scheduled = null
         var released = false
         var queued: DelayedMessage? = this.messages.peek()
         while (queued != null && queued.deadline - System.nanoTime() <= 0L) {
            this.messages.poll()
            this.consumer(queued)
            released = true
            queued = this.messages.peek()
         }
         if (released) {
            this.onBatchComplete.run()
         }
         val next = this.messages.peek()
         if (next != null) {
            this.schedule(next.deadline - System.nanoTime())
         }
      }

      fun drain() {
         this.cancelScheduled()
         var released = false
         while (true) {
            val queued = this.messages.poll() ?: break
            this.consumer(queued)
            released = true
         }
         if (released && context.channel().isActive) {
            this.onBatchComplete.run()
         }
      }

      fun discard() {
         this.cancelScheduled()
         while (true) {
            val queued = this.messages.poll() ?: break
            ReferenceCountUtil.release(queued.message)
            if (queued.promise != null) {
               queued.promise.tryFailure(ClosedChannelException())
            }
         }
      }

      private fun cancelScheduled() {
         val future = this.scheduled
         if (future != null) {
            future.cancel(false)
            this.scheduled = null
         }
      }
   }

   private data class DelayedMessage(
      val message: Any,
      val promise: ChannelPromise?,
      val deadline: Long
   )
}

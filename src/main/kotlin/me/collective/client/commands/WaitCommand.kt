package me.collective.client.commands

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.CommandExecuteEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.MinecraftTickEvent
import me.collective.client.managers.CommandRegistry
import java.util.ArrayList

class WaitCommand : Command("wait") {
    private val pendingCommands = ArrayList<String>()
    private var hook = false
    private var delayTicks = 0

    init {
        ClientAccess.eventBus.subscribe(this)
    }

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>
        val tickType = IntegerArgumentType.integer(0)
        val ticksArgument = argument("ticks", tickType)
        val waitingArgument = ticksArgument.executes { context ->
            val ticks = context.getArgument("ticks", Int::class.javaObjectType)
            this.delayTicks = ticks
            this.hook = true
            1
        }
        root.then(waitingArgument)
    }

    @EventHandler
    private fun onMinecraftTick(event: MinecraftTickEvent) {
        if (this.hook) {
            this.hook = false
            val commands = ArrayList(this.pendingCommands)
            this.pendingCommands.clear()
            val scheduler = ClientContext.taskScheduler
            val ticks = this.delayTicks
            scheduler.scheduleRotationTask({
                for (cmd in commands) {
                    CommandRegistry.executeCommand(cmd)
                }
            }, ticks)
        }
    }

    @EventHandler
    private fun onCommandExecute(event: CommandExecuteEvent) {
        if (this.hook) {
            this.pendingCommands.add(event.command)
            event.cancel()
        }
    }
}

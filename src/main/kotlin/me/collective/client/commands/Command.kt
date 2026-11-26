package me.collective.client.commands

import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.NamedFeature
import me.collective.client.utils.chat.ChatUtils
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

abstract class Command(name: String) : NamedFeature(name), ClientAccess {
    @JvmField
    var aliases: Array<String> = emptyArray()

    abstract fun exec(builder: LiteralArgumentBuilder<*>)

    fun getAliases(): Array<String> = this.aliases

    protected fun setAliases(vararg aliases: String) {
        this.aliases = arrayOf(*aliases)
    }

    protected fun scheduleRotationTask(task: Runnable) {
        ClientContext.taskScheduler.scheduleRotationTask(task, 0)
    }

    protected fun scheduleScreenChange(screen: Screen): Int {
        ClientContext.taskScheduler.scheduleScreenChange(screen)
        return 1
    }

    protected fun addSubcommand(command: Command, parent: LiteralArgumentBuilder<*>) {
        val child = literal(command.name)
        command.exec(child)
        (parent as LiteralArgumentBuilder<Any?>).then(child)
    }

    protected fun formatText(text: String): MutableComponent {
        return Component.empty().append(ChatUtils.watermarkText(text))
    }

    companion object {
        @JvmStatic
        fun <T> argument(name: String, type: ArgumentType<T>): RequiredArgumentBuilder<Any?, T> {
            return RequiredArgumentBuilder.argument(name, type)
        }

        @JvmStatic
        fun literal(name: String): LiteralArgumentBuilder<Any?> {
            return LiteralArgumentBuilder.literal(name)
        }

        @JvmStatic
        @JvmName("literalGeneric")
        fun <S : Any?> literal(name: String): LiteralArgumentBuilder<S> {
            return LiteralArgumentBuilder.literal(name)
        }

        @JvmStatic
        fun aliasedLiteral(vararg names: String): RequiredArgumentBuilder<Any?, String> {
            val name = names[0]
            val type = AliasedLiteralArgument(*names)
            return argument(name, type)
        }
    }
}

package me.collective.client.commands

import com.mojang.brigadier.arguments.ArgumentType

interface ReifiedArgumentType<T> : ArgumentType<T> {
   fun getReifiedType(): Class<T>
}

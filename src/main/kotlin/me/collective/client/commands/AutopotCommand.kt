package me.collective.client.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import me.collective.client.ClientContext
import me.collective.client.modules.combat.AutoPotModule
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.alchemy.Potion
import java.util.Collections

class AutopotCommand : Command("autopot") {

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>
        val throwBranch = literal<Any?>("throw")
        val potionRegistry = BuiltInRegistries.POTION
        val potionArgument = RegistryEntryArgument.registry(potionRegistry)
        val effectArgument = argument("effect", potionArgument) as RequiredArgumentBuilder<Any?, *>
        val effectBranch = effectArgument.executes { context -> executeThrow(context) }
        val configuredThrowBranch = throwBranch.then(effectBranch)
        root.then(configuredThrowBranch)
    }

    private fun executeThrow(context: CommandContext<*>): Int {
        val potion = context.getArgument("effect", Potion::class.java) as Potion
        val scheduler = ClientContext.taskScheduler
        val throwTask = Runnable {
            val autoPotInstance = autoPot ?: return@Runnable
            if (!autoPotInstance.hasNearbySplashPotion()) {
                val allowedPotions: Set<Potion> = Collections.singleton(potion)
                autoPotInstance.throwPotion(allowedPotions)
            }
        }
        scheduler.scheduleRotationTask(throwTask, 0)
        return 1
    }

    companion object {
        private val autoPot: AutoPotModule? by lazy {
            ClientContext.getModule(AutoPotModule::class.java)
        }
    }
}

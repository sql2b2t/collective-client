package me.collective.client.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import me.collective.client.ClientContext
import me.collective.client.modules.player.StashMover
import me.collective.client.utils.chat.ChatUtils
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component

class MoverCommand : Command("перенос") {

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        root.executes {
            sendHelp()
            1
        }

        root.then(
            literal<Any?>("вход")
                .executes {
                    sendHelp()
                    1
                }
                .then(
                    literal<Any?>("1").executes {
                        setCorner(true, 1)
                        1
                    }
                )
                .then(
                    literal<Any?>("2").executes {
                        setCorner(true, 2)
                        1
                    }
                )
                .then(
                    literal<Any?>("очистить").executes {
                        val mover = mover()
                        if (mover != null) {
                            mover.clearAreas()
                            send("§aЗоны сброшены.")
                        }
                        1
                    }
                )
        )

        root.then(
            literal<Any?>("выход")
                .executes {
                    sendHelp()
                    1
                }
                .then(
                    literal<Any?>("1").executes {
                        setCorner(false, 1)
                        1
                    }
                )
                .then(
                    literal<Any?>("2").executes {
                        setCorner(false, 2)
                        1
                    }
                )
                .then(
                    literal<Any?>("очистить").executes {
                        val mover = mover()
                        if (mover != null) {
                            mover.clearAreas()
                            send("§aЗоны сброшены.")
                        }
                        1
                    }
                )
        )

        root.then(
            literal<Any?>("старт").executes {
                val mover = mover()
                if (mover != null) mover.isToggled = !mover.isToggled
                1
            }
        )

        root.then(
            literal<Any?>("помощь").executes {
                sendHelp()
                1
            }
        )
    }

    companion object {
        private fun mover(): StashMover? {
            return ClientContext.getModule(StashMover::class.java)
        }

        private fun setCorner(input: Boolean, corner: Int) {
            val mover = mover() ?: return

            val pos: BlockPos? = mover.lookedAtBlock()
            if (pos == null) {
                send("§cСмотри на угол и повтори.")
                return
            }

            mover.setAreaCorner(input, corner, pos)
            send("§a" + (if (input) "Вход" else "Выход") + ", угол " + corner + ": §f" + pos.toShortString())
        }

        private fun sendHelp() {
            send("§6=== Настройка StashMover ===")
            send("§e.перенос вход 1§7 / §e2§7 — углы зоны входа (смотри на блок)")
            send("§e.перенос выход 1§7 / §e2§7 — углы зоны выхода")
            send("§e.перенос вход очистить§7 — сбросить зоны")
            send("§e.перенос старт§7 — включить или выключить перенос")
        }

        private fun send(text: String) {
            val message = Component.literal(text)
            ChatUtils.printChat(message, ChatUtils.createSignature(-1))
        }
    }
}

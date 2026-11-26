package me.collective.client.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import me.collective.client.ClientContext
import me.collective.client.modules.player.StashSorter
import me.collective.client.utils.chat.ChatUtils
import net.minecraft.network.chat.Component

class SorterCommand : Command("сорт") {

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        root.executes {
            sendHelp()
            1
        }

        root.then(
            literal<Any?>("зона")
                .executes {
                    val sorter = sorter()
                    sorter?.setSelectionCorner()
                    1
                }
                .then(
                    literal<Any?>("очистить").executes {
                        val sorter = sorter()
                        if (sorter != null) {
                            val removed = sorter.clearZones()
                            send(if (removed > 0) "§aЗон удалено: $removed" else "§7Зон и не было.")
                        }
                        1
                    }
                )
        )

        root.then(
            literal<Any?>("метка")
                .executes {
                    val sorter = sorter()
                    sorter?.pinLookedChest()
                    1
                }
                .then(
                    literal<Any?>("снять").executes {
                        val sorter = sorter()
                        sorter?.clearLookedPin()
                        1
                    }
                )
        )

        root.then(
            literal<Any?>("старт").executes {
                val sorter = sorter()
                if (sorter != null) {
                    sorter.startSorting()
                    send("§aСортировщик запущен.")
                }
                1
            }
        )

        root.then(
            literal<Any?>("стоп").executes {
                val sorter = sorter()
                if (sorter != null) {
                    sorter.stopSorting()
                    send("§cСортировщик остановлен.")
                }
                1
            }
        )

        root.then(
            literal<Any?>("пауза").executes {
                val sorter = sorter()
                if (sorter != null) {
                    sorter.pauseSorting()
                    send("§eСортировщик на паузе.")
                }
                1
            }
        )

        root.then(
            literal<Any?>("продолжить").executes {
                val sorter = sorter()
                if (sorter != null) {
                    sorter.resumeSorting()
                    send("§aСортировщик продолжен.")
                }
                1
            }
        )

        root.then(
            literal<Any?>("очистить").executes {
                val sorter = sorter()
                if (sorter != null) {
                    val removed = sorter.clearZones()
                    send(if (removed > 0) "§aЗон удалено: $removed" else "§7Зон и не было.")
                }
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
        private fun sorter(): StashSorter? {
            return ClientContext.getModule(StashSorter::class.java)
        }

        private fun sendHelp() {
            send("§6=== Настройка StashSorter ===")
            send("§e.сорт зона§7 — задать угол зоны (клик 1 и клик 2)")
            send("§e.сорт зона очистить§7 — удалить все зоны")
            send("§e.сорт метка§7 — закрепить категорию за сундуком")
            send("§e.сорт метка снять§7 — снять метку с сундука")
            send("§e.сорт старт / стоп§7 — включить/выключить сортировщик")
            send("§e.сорт пауза / продолжить§7 — пауза и продолжение")
            send("§e.сорт очистить§7 — удалить все зоны")
        }

        private fun send(text: String) {
            val message = Component.literal(text)
            ChatUtils.printChat(message, ChatUtils.createSignature(-1))
        }
    }
}

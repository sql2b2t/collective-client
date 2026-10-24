package me.collective.util.printer;

import net.minecraft.world.item.ItemStack;

/**
 * Пара «предмет + сколько не хватает» для отчёта по материалам схемы (порт из BepHax).
 */
public record PrinterMaterial(ItemStack stack, int missing) {
}
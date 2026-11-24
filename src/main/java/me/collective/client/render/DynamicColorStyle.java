package me.collective.client.render;

import java.util.function.Supplier;
import net.minecraft.network.chat.Style;

public interface DynamicColorStyle {
   Style collective$withColor(Supplier colorSupplier);
}

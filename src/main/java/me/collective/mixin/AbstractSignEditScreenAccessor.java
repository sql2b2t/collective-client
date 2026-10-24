package me.collective.mixin;

import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Доступ к табличке экрана редактирования (для AutoSign). */
@Mixin(AbstractSignEditScreen.class)
public interface AbstractSignEditScreenAccessor {
    @Accessor("sign")
    SignBlockEntity collective$getSign();
}
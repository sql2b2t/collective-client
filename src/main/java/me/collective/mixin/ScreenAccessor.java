package me.collective.mixin;

import java.util.List;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Screen.class)
public interface ScreenAccessor {
   @Accessor("renderables")
   List getDrawables();

   @Accessor("renderables")
   void setDrawables(List renderables);

   @Invoker("addRenderableWidget")
   GuiEventListener addDrawableChildHook(GuiEventListener child);
}

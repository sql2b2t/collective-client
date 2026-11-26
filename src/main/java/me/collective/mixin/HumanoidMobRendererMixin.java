package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.collective.client.ClientContext;
import me.collective.client.modules.render.NoRenderModule;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(HumanoidMobRenderer.class)
public class HumanoidMobRendererMixin {
   private static NoRenderModule noRenderModule;
   private static NoRenderModule noRenderModule() {
      if (noRenderModule == null) {
         noRenderModule = ClientContext.getModule(NoRenderModule.class);
      }
      return noRenderModule;
   }

   @ModifyReturnValue(method = "getEquipmentIfRenderable", at = @At("RETURN"))
   private static ItemStack suppressElytraEquipment(ItemStack originalStack) {
      return noRenderModule().isToggled()
            && noRenderModule().elytraSetting.getValue()
            && originalStack.is(Items.ELYTRA)
         ? ItemStack.EMPTY
         : originalStack;
   }
}

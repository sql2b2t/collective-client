package me.collective.mixin;

import me.collective.module.render.NoRender;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Хуки NoRender для экранных оверлеев InGameHud. */
@Mixin(InGameHud.class)
public abstract class MixinInGameHud {

    private static NoRender collective$noRender() {
        return NoRender.INSTANCE;
    }

    @Inject(method = "renderPortalOverlay(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At("HEAD"), cancellable = true)
    private void collective$noRenderPortal(GuiGraphics context, float nauseaStrength, CallbackInfo ci) {
        NoRender mod = collective$noRender();
        if (mod != null && mod.isToggled() && mod.portal.getValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderNauseaOverlay(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At("HEAD"), cancellable = true)
    private void collective$noRenderNausea(GuiGraphics context, float nauseaStrength, CallbackInfo ci) {
        NoRender mod = collective$noRender();
        if (mod != null && mod.isToggled() && mod.nausea.getValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderVignetteOverlay(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void collective$noRenderVignette(GuiGraphics context, Entity entity, CallbackInfo ci) {
        NoRender mod = collective$noRender();
        if (mod != null && mod.isToggled() && mod.vignette.getValue()) {
            ci.cancel();
        }
    }

    /**
     * renderOverlay используется для накладок экипировки (тыква) и порошкового
     * снега — фильтруем по пути текстуры.
     */
    @Inject(method = "renderOverlay(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/util/Identifier;F)V", at = @At("HEAD"), cancellable = true)
    private void collective$noRenderOverlay(GuiGraphics context, Identifier texture, float opacity, CallbackInfo ci) {
        NoRender mod = collective$noRender();
        if (mod == null || !mod.isToggled()) return;
        String path = texture.getPath();
        if (path.contains("pumpkinblur") && mod.pumpkin.getValue()) {
            ci.cancel();
        } else if (path.contains("powder_snow") && mod.powderSnow.getValue()) {
            ci.cancel();
        }
    }
}
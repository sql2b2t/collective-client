package me.collective.mixin;

import me.collective.module.render.NoRender;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Хуки NoRender для экранных оверлеев Gui. */
@Mixin(Gui.class)
public abstract class MixinInGameHud {

    private static NoRender collective$noRender() {
        return NoRender.INSTANCE;
    }

    @Inject(method = "renderPortalOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;F)V", at = @At("HEAD"), cancellable = true)
    private void collective$noRenderPortal(GuiGraphicsExtractor context, float nauseaStrength, CallbackInfo ci) {
        NoRender mod = collective$noRender();
        if (mod != null && mod.isToggled() && mod.portal.getValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderNauseaOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;F)V", at = @At("HEAD"), cancellable = true)
    private void collective$noRenderNausea(GuiGraphicsExtractor context, float nauseaStrength, CallbackInfo ci) {
        NoRender mod = collective$noRender();
        if (mod != null && mod.isToggled() && mod.nausea.getValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderVignetteOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void collective$noRenderVignette(GuiGraphicsExtractor context, Entity entity, CallbackInfo ci) {
        NoRender mod = collective$noRender();
        if (mod != null && mod.isToggled() && mod.vignette.getValue()) {
            ci.cancel();
        }
    }

    /**
     * renderOverlay используется для накладок экипировки (тыква) и порошкового
     * снега — фильтруем по пути текстуры.
     */
    @Inject(method = "renderOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/util/Identifier;F)V", at = @At("HEAD"), cancellable = true)
    private void collective$noRenderOverlay(GuiGraphicsExtractor context, Identifier texture, float opacity, CallbackInfo ci) {
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
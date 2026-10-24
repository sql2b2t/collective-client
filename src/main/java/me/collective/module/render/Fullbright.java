package me.collective.module.render;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

public class Fullbright extends Module {
    private net.minecraft.client.network.ClientPlayerEntity owner;
    private StatusEffectInstance ownedEffect;

    public Fullbright() {
        super("Fullbright", "Делает всё вокруг максимально ярким, убирая тени и темноту.", Category.RENDER);
    }

    @Override
    public void onEnable() {
        applyNightVision();
    }

    @Override
    public void onDisable() {
        // Never remove a server effect that replaced our instance.
        if (owner != null && ownedEffect != null
                && owner.getStatusEffect(StatusEffects.NIGHT_VISION) == ownedEffect
                && !ownedEffect.shouldShowIcon() && !ownedEffect.shouldShowParticles()) {
            owner.removeStatusEffect(StatusEffects.NIGHT_VISION);
        }
        owner = null;
        ownedEffect = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        applyNightVision();
    }

    private void applyNightVision() {
        if (mc.player != owner) {
            owner = mc.player;
            ownedEffect = null;
        }
        if (owner == null) return;
        StatusEffectInstance current = owner.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (current == null || (current == ownedEffect && current.getDuration() < 1000)) {
            StatusEffectInstance effect = new StatusEffectInstance(StatusEffects.NIGHT_VISION, 10000, 0, false, false, false);
            owner.addStatusEffect(effect);
            ownedEffect = owner.getStatusEffect(StatusEffects.NIGHT_VISION);
        } else if (current != ownedEffect || current.shouldShowIcon() || current.shouldShowParticles()) {
            // A visible/server-supplied effect is not ours to clear.
            ownedEffect = null;
        }
    }
}

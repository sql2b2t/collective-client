package me.collective.module.render;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class Fullbright extends Module {
    private LocalPlayer owner;
    private MobEffectInstance ownedEffect;

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
                && owner.getEffect(MobEffects.NIGHT_VISION) == ownedEffect
                && !ownedEffect.showIcon() && !ownedEffect.isVisible()) {
            owner.removeEffect(MobEffects.NIGHT_VISION);
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
        MobEffectInstance current = owner.getEffect(MobEffects.NIGHT_VISION);
        if (current == null || (current == ownedEffect && current.getDuration() < 1000)) {
            MobEffectInstance effect = new MobEffectInstance(MobEffects.NIGHT_VISION, 10000, 0, false, false, false);
            owner.addEffect(effect);
            ownedEffect = owner.getEffect(MobEffects.NIGHT_VISION);
        } else if (current != ownedEffect || current.showIcon() || current.isVisible()) {
            // A visible/server-supplied effect is not ours to clear.
            ownedEffect = null;
        }
    }
}
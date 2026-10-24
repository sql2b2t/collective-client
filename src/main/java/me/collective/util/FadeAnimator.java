package me.collective.util;

import net.minecraft.util.Mth;

public class FadeAnimator {
    private float alpha;
    private long lastNanos;

    public void update(boolean targetVisible, double durationSeconds, boolean enabled) {
        long now = System.nanoTime();

        float dt = this.lastNanos == 0L ? 0.0f : (float) (now - this.lastNanos) / 1.0E9f;
        this.lastNanos = now;
        dt = Mth.clamp(dt, 0.0f, 0.1f);

        if (!enabled) {
            this.alpha = targetVisible ? 1.0f : 0.0f;
            return;
        }

        float target = targetVisible ? 1.0f : 0.0f;
        float step = durationSeconds <= 0.0 ? 1.0f : (float) ((double) dt / durationSeconds);

        if (this.alpha < target) {
            this.alpha = Math.min(target, this.alpha + step);
        } else if (this.alpha > target) {
            this.alpha = Math.max(target, this.alpha - step);
        }
    }

    public float alpha() {
        return this.alpha;
    }

    public boolean rendering() {
        return this.alpha > 0.001f;
    }

    public int apply(int argb) {
        int a = argb >>> 24 & 0xFF;
        a = Mth.clamp(Math.round((float) a * this.alpha), 0, 255);
        return a << 24 | argb & 0xFFFFFF;
    }
}
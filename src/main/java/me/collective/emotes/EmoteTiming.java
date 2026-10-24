package me.collective.emotes;

public class EmoteTiming {
    private final long startNanos;
    private long touchNanos;
    private boolean touched;

    public EmoteTiming(long startNanos) {
        this.startNanos = startNanos;
    }

    public void touch(long nowNanos) {
        if (!touched || nowNanos > touchNanos) {
            touchNanos = nowNanos;
            touched = true;
        }
    }

    public boolean isExpired(long nowNanos, float durationSec, boolean loop) {
        if (loop) {
            return false;
        }
        long durationNanos = (long) ((double) durationSec * 1_000_000_000.0);
        long naturalEnd = startNanos + durationNanos;
        long effectiveEnd = naturalEnd;
        if (touched) {
            long prolongedEnd = touchNanos + durationNanos;
            if (prolongedEnd > effectiveEnd) {
                effectiveEnd = prolongedEnd;
            }
        }
        return nowNanos >= effectiveEnd;
    }

    public float animTime(long nowNanos) {
        return (nowNanos - startNanos) / 1_000_000_000f;
    }

    public float blend(long nowNanos) {
        float t = (nowNanos - startNanos) / 250_000_000f;
        if (t < 0f) {
            return 0f;
        }
        return t > 1f ? 1f : t;
    }
}
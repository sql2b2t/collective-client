package me.collective.emotes;

public final class Easing {
    private Easing() {
    }

    public static float apply(String name, float t) {
        if (name == null) {
            return t;
        }
        switch (name) {
            case "linear":
                return t;
            case "easeInQuad":
                return t * t;
            case "easeOutQuad":
                return 1.0f - (1.0f - t) * (1.0f - t);
            case "easeInOutQuad":
                if (t < 0.5f) {
                    return 2.0f * t * t;
                }
                return 1.0f - (-2.0f * t + 2.0f) * (-2.0f * t + 2.0f) / 2.0f;
            case "easeInCubic":
                return t * t * t;
            case "easeOutCubic":
                return 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
            case "easeInOutCubic":
                if (t < 0.5f) {
                    return 4.0f * t * t * t;
                }
                return 1.0f - (-2.0f * t + 2.0f) * (-2.0f * t + 2.0f) * (-2.0f * t + 2.0f) / 2.0f;
            case "easeInSine":
                return (float) (1.0 - Math.cos(t * Math.PI / 2.0));
            case "easeInOutSine":
                return (float) (-(Math.cos(Math.PI * t) - 1.0) / 2.0);
            case "step":
                return 0.0f;
            default:
                return t;
        }
    }
}
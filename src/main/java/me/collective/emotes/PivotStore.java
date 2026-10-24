package me.collective.emotes;

import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelPart;

public final class PivotStore {
    private PivotStore() {
    }

    private static final WeakHashMap<ModelPart, float[]> SNAP = new WeakHashMap<>();

    public static synchronized void snapshot(ModelPart p) {
        if (p == null || SNAP.containsKey(p)) {
            return;
        }
        SNAP.put(p, new float[]{p.pitch, p.yaw, p.roll, p.originX, p.originY, p.originZ});
    }

    public static synchronized void restore(ModelPart p) {
        if (p == null) {
            return;
        }
        float[] s = SNAP.get(p);
        if (s == null) {
            return;
        }
        p.originX = s[3];
        p.originY = s[4];
        p.originZ = s[5];
    }

    /** Полный сброс (пивоты + повороты) — только при окончании эмоута. */
    public static synchronized void restoreAll(ModelPart p) {
        if (p == null) {
            return;
        }
        float[] s = SNAP.get(p);
        if (s == null) {
            return;
        }
        p.pitch = s[0];
        p.yaw = s[1];
        p.roll = s[2];
        p.originX = s[3];
        p.originY = s[4];
        p.originZ = s[5];
    }

    public static synchronized void clear() {
        SNAP.clear();
    }

    /** Снапшот (может быть null). Индексы: 0-2 повороты, 3-5 позиция. */
    public static synchronized float[] get(ModelPart p) {
        if (p == null) return null;
        return SNAP.get(p);
    }
}
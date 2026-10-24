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
        SNAP.put(p, new float[]{p.xRot, p.yRot, p.zRot, p.x, p.y, p.z});
    }

    public static synchronized void restore(ModelPart p) {
        if (p == null) {
            return;
        }
        float[] s = SNAP.get(p);
        if (s == null) {
            return;
        }
        p.x = s[3];
        p.y = s[4];
        p.z = s[5];
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
        p.xRot = s[0];
        p.yRot = s[1];
        p.zRot = s[2];
        p.x = s[3];
        p.y = s[4];
        p.z = s[5];
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
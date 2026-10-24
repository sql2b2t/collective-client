package me.collective.emotes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

public final class EmoteData {
    private final Map<String, BoneTracks> bones;
    private final float duration;
    private final float loopStart;
    private final float loopEnd;
    private final boolean easingBefore;

    private EmoteData(Map<String, BoneTracks> bones, float duration, float loopStart, float loopEnd, boolean easingBefore) {
        this.bones = bones;
        this.duration = Math.max(0.05f, duration);
        this.loopStart = MathHelper.clamp(loopStart, 0.0f, this.duration);
        this.loopEnd = Math.max(this.duration, loopEnd);
        this.easingBefore = easingBefore;
    }

    public record BoneSample(Vector3f rotation, Vector3f position, float bendDirection, float bend, boolean hasRotation, boolean hasPosition, boolean hasBend) {
    }

    public BoneSample sample(String bone, float time) {
        BoneTracks tracks = this.bones.get(bone);
        if (tracks == null) {
            return new BoneSample(new Vector3f(), new Vector3f(), 0.0f, 0.0f, false, false, false);
        }
        return new BoneSample(
            tracks.rotation.sample(time, this.loopStart, this.loopEnd, this.easingBefore),
            tracks.position.sample(time, this.loopStart, this.loopEnd, this.easingBefore),
            tracks.bendDirection.sample(time, this.loopStart, this.loopEnd, this.easingBefore),
            tracks.bend.sample(time, this.loopStart, this.loopEnd, this.easingBefore),
            !tracks.rotation.frames().isEmpty(),
            !tracks.position.frames().isEmpty(),
            !tracks.bend.frames().isEmpty());
    }

    public float duration() {
        return this.duration;
    }

    public float mapTime(float t, boolean loop) {
        if (!loop) {
            return Math.min(t, this.duration);
        }
        if (t < this.loopEnd) {
            return t;
        }
        float span = this.loopEnd - this.loopStart;
        if (span <= 1.0E-4f) {
            return this.duration;
        }
        return this.loopStart + (t - this.loopStart) % span;
    }

    public static EmoteData load(Emote emote, String animName) {
        Identifier id = Identifier.of("collective", "player_animation/" + emote.file() + ".json");
        try (BufferedReader reader = Minecraft.getInstance().getResourceManager().getResource(id).orElseThrow().getReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject allAnims = root.getAsJsonObject("animations");
            JsonObject anim = allAnims.getAsJsonObject(animName);
            if (anim == null) {
                // Имя не совпало (напр. sit) — берём первую анимацию файла
                var keys = new ArrayList<>(allAnims.keySet());
                System.out.println("[Emotes] anim '" + animName + "' not found in " + emote.file()
                    + ", available=" + keys + ", fallback=" + (keys.isEmpty() ? "none" : keys.get(0)));
                if (keys.isEmpty()) return new EmoteData(Map.of(), 0.05f, 0.0f, 0.05f, true);
                anim = allAnims.getAsJsonObject(keys.get(0));
            }
            JsonObject bends = loadVariant(emote, animName, "_bends");
            JsonObject overrides = loadVariant(emote, animName, "_overrides");
            Map<String, BoneTracks> map = new HashMap<>();
            float animLength = anim.has("animation_length") ? anim.get("animation_length").getAsFloat() : 0.0f;
            float loopStart = anim.has("loop_start") ? anim.get("loop_start").getAsFloat() : 0.0f;
            float loopEnd = anim.has("loop_end") ? anim.get("loop_end").getAsFloat()
                : (bends != null && bends.has("loop_end") ? bends.get("loop_end").getAsFloat() : animLength);
            boolean easingBefore = anim.has("easing_before") ? anim.get("easing_before").getAsBoolean()
                : bends == null || !bends.has("easing_before") || bends.get("easing_before").getAsBoolean();
            JsonObject baseBones = anim.getAsJsonObject("bones");
            JsonObject bendsBones = bends == null ? null : bends.getAsJsonObject("bones");
            JsonObject overridesBones = overrides == null ? null : overrides.getAsJsonObject("bones");
            for (Map.Entry<String, JsonElement> entry : baseBones.entrySet()) {
                JsonObject baseBone = entry.getValue().getAsJsonObject();
                JsonObject bendsBone = bendsBones == null || !bendsBones.has(entry.getKey()) ? baseBone : bendsBones.getAsJsonObject(entry.getKey());
                JsonObject overridesBone = overridesBones == null || !overridesBones.has(entry.getKey()) ? null : overridesBones.getAsJsonObject(entry.getKey());
                VecTrack rotation = parseVec(overridesBone != null && overridesBone.has("rotation") ? overridesBone.get("rotation") : baseBone.get("rotation"));
                VecTrack position = parseVec(overridesBone != null && overridesBone.has("position") ? overridesBone.get("position") : baseBone.get("position"));
                FloatTrack bendDirection = parseFloat(getAlias(bendsBone, "bend_direction", "bendDirection"));
                FloatTrack bend = parseFloat(bendsBone.get("bend"));
                map.put(entry.getKey(), new BoneTracks(rotation, position, bendDirection, bend));
                animLength = Math.max(animLength, Math.max(rotation.end(), position.end()));
            }
            return new EmoteData(map, animLength, loopStart, loopEnd, easingBefore);
        } catch (Exception e) {
            return new EmoteData(Map.of(), 0.05f, 0.0f, 0.05f, true);
        }
    }

    private static JsonObject loadVariant(Emote emote, String animName, String suffix) {
        Identifier id = Identifier.of("collective", "player_animation/" + emote.file() + suffix + ".json");
        try (BufferedReader reader = Minecraft.getInstance().getResourceManager().getResource(id).orElseThrow().getReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            return root.getAsJsonObject("animations").getAsJsonObject(animName);
        } catch (Exception e) {
            return null;
        }
    }

    private static JsonElement getAlias(JsonObject obj, String primary, String alias) {
        if (obj.has(primary)) {
            return obj.get(primary);
        }
        return obj.get(alias);
    }

    private static VecTrack parseVec(JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return VecTrack.EMPTY;
        }
        if (el.isJsonArray()) {
            return new VecTrack(List.of(new VecKey(0.0f, toVector(el), "linear")));
        }
        JsonObject obj = el.getAsJsonObject();
        if (obj.has("vector")) {
            String easing = obj.has("easing") ? obj.get("easing").getAsString() : "linear";
            return new VecTrack(List.of(new VecKey(0.0f, toVector(obj.get("vector")), easing)));
        }
        List<VecKey> keys = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            if (entry.getKey().equals("easing")) {
                continue;
            }
            JsonElement value = entry.getValue();
            Vector3f vec;
            String easing = "linear";
            if (value.isJsonObject()) {
                JsonObject keyObj = value.getAsJsonObject();
                vec = toVector(keyObj.get("vector"));
                if (keyObj.has("easing")) {
                    easing = keyObj.get("easing").getAsString();
                }
            } else {
                vec = toVector(value);
            }
            keys.add(new VecKey(Float.parseFloat(entry.getKey()), vec, easing));
        }
        keys.sort(Comparator.comparingDouble(VecKey::time));
        return new VecTrack(keys);
    }

    private static FloatTrack parseFloat(JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return FloatTrack.EMPTY;
        }
        if (el.isJsonPrimitive()) {
            return new FloatTrack(List.of(new FloatKey(0.0f, el.getAsFloat(), "linear")));
        }
        JsonObject obj = el.getAsJsonObject();
        if (obj.has("value")) {
            String easing = obj.has("easing") ? obj.get("easing").getAsString() : "linear";
            return new FloatTrack(List.of(new FloatKey(0.0f, obj.get("value").getAsFloat(), easing)));
        }
        List<FloatKey> keys = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            if (entry.getKey().equals("easing")) {
                continue;
            }
            JsonElement value = entry.getValue();
            float v;
            String easing = "linear";
            if (value.isJsonObject()) {
                JsonObject keyObj = value.getAsJsonObject();
                v = keyObj.get("value").getAsFloat();
                if (keyObj.has("easing")) {
                    easing = keyObj.get("easing").getAsString();
                }
            } else {
                v = value.getAsFloat();
            }
            keys.add(new FloatKey(Float.parseFloat(entry.getKey()), v, easing));
        }
        keys.sort(Comparator.comparingDouble(FloatKey::time));
        return new FloatTrack(keys);
    }

    private static Vector3f toVector(JsonElement el) {
        if (el == null || !el.isJsonArray()) {
            return new Vector3f();
        }
        JsonArray arr = el.getAsJsonArray();
        return new Vector3f(arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat());
    }

    private record BoneTracks(VecTrack rotation, VecTrack position, FloatTrack bendDirection, FloatTrack bend) {
    }

    private record VecKey(float time, Vector3f value, String easing) {
    }

    private record FloatKey(float time, float value, String easing) {
    }

    private record VecTrack(List<VecKey> frames) {
        private static final VecTrack EMPTY = new VecTrack(List.of());

        Vector3f sample(float t, float loopStart, float loopEnd, boolean easingBefore) {
            if (this.frames.isEmpty()) {
                return new Vector3f();
            }
            if (this.frames.size() == 1 || t <= this.frames.getFirst().time) {
                return new Vector3f(this.frames.getFirst().value);
            }
            for (int i = 1; i < this.frames.size(); i++) {
                VecKey cur = this.frames.get(i);
                if (!(t <= cur.time)) {
                    continue;
                }
                VecKey prev = this.frames.get(i - 1);
                float a = (t - prev.time) / Math.max(1.0E-4f, cur.time - prev.time);
                a = Easing.apply(easingBefore ? cur.easing : prev.easing, MathHelper.clamp(a, 0.0f, 1.0f));
                return new Vector3f(prev.value).lerp(cur.value, a);
            }
            if (loopEnd > this.frames.getLast().time) {
                VecKey first = this.frames.stream().filter(k -> k.time >= loopStart).findFirst().orElse(null);
                if (first != null) {
                    VecKey last = this.frames.getLast();
                    float wrapped = first.time + loopEnd - loopStart;
                    float a = (t - last.time) / Math.max(1.0E-4f, wrapped - last.time);
                    a = Easing.apply(easingBefore ? first.easing : last.easing, MathHelper.clamp(a, 0.0f, 1.0f));
                    return new Vector3f(last.value).lerp(first.value, a);
                }
            }
            return new Vector3f(this.frames.getLast().value);
        }

        float end() {
            return this.frames.isEmpty() ? 0.0f : this.frames.getLast().time;
        }
    }

    private record FloatTrack(List<FloatKey> frames) {
        private static final FloatTrack EMPTY = new FloatTrack(List.of());

        float sample(float t, float loopStart, float loopEnd, boolean easingBefore) {
            if (this.frames.isEmpty()) {
                return 0.0f;
            }
            if (this.frames.size() == 1 || t <= this.frames.getFirst().time) {
                return this.frames.getFirst().value;
            }
            for (int i = 1; i < this.frames.size(); i++) {
                FloatKey cur = this.frames.get(i);
                if (!(t <= cur.time)) {
                    continue;
                }
                FloatKey prev = this.frames.get(i - 1);
                float a = (t - prev.time) / Math.max(1.0E-4f, cur.time - prev.time);
                a = Easing.apply(easingBefore ? cur.easing : prev.easing, MathHelper.clamp(a, 0.0f, 1.0f));
                return MathHelper.lerp(a, prev.value, cur.value);
            }
            if (loopEnd > this.frames.getLast().time) {
                FloatKey first = this.frames.stream().filter(k -> k.time >= loopStart).findFirst().orElse(null);
                if (first != null) {
                    FloatKey last = this.frames.getLast();
                    float wrapped = first.time + loopEnd - loopStart;
                    float a = (t - last.time) / Math.max(1.0E-4f, wrapped - last.time);
                    a = Easing.apply(easingBefore ? first.easing : last.easing, MathHelper.clamp(a, 0.0f, 1.0f));
                    return MathHelper.lerp(a, last.value, first.value);
                }
            }
            return this.frames.getLast().value;
        }
    }
}
package me.collective.emotes;

import me.collective.module.misc.EmotesModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.Mth;
import com.mojang.math.Axis;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Движок эмоутов: проигрывание, тайминг, наложение поз на модель,
 * сдвиг тела. Анимирует ТОЛЬКО локального игрока.
 */
public final class EmoteEngine {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final float DEG = (float) Math.PI / 180.0F;

    /** Render-state -> UUID игрока (заполняется из updateRenderState). */
    private static final Map<Object, UUID> STATE_UUID =
        Collections.synchronizedMap(new WeakHashMap<>());

    /** Счётчик вариантов анимаций на эмоут (как в оригинале). */
    private static final Map<String, Integer> VARIANT = new HashMap<>();

    private record ActiveEmote(Emote emote, EmoteData data, String anim, EmoteTiming timing) {
    }

    private static ActiveEmote active = null;
    private static long lastPlayMs = 0L;
    private static boolean originsLogged = false;
    /** Был активен на прошлом кадре — нужен разовый сброс поворотов при стопе. */
    private static boolean wasActive = false;

    /**
     * Эмоуты, запущенные другими пользователями чита: UUID игрока -> запущенная
     * анимация. Наполняется от релея (модуль IRC), проигрывается поверх чужих
     * моделей тем же кодом наложения.
     */
    private static final Map<UUID, ActiveEmote> REMOTES = new ConcurrentHashMap<>();
    /** Кто-то закончил играть — разовый сброс пивотов в его кадре. */
    private static final Set<UUID> remoteWasActive = ConcurrentHashMap.newKeySet();
    private static int remoteSweepTicks;

    /**
     * Мост на сеть: когда локально стартует/останавливается эмоут, слушатель
     * (модуль IRC) рассылает это остальным пользователям чита.
     */
    public interface PlaybackSync {
        void onLocalStart(String emoteId);

        void onLocalStop(String emoteId);
    }

    private static volatile PlaybackSync syncListener;

    public static void setSyncListener(PlaybackSync listener) {
        syncListener = listener;
    }

    private EmoteEngine() {
    }

    private static boolean debug() {
        try {
            EmotesModule mod = EmotesModule.INSTANCE;
            return mod == null || mod.debugLog.getValue();
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static void log(String msg) {
        if (debug()) System.out.println("[Emotes] " + msg);
    }

    public static void mapState(Object state, UUID uuid) {
        if (state != null && uuid != null) STATE_UUID.put(state, uuid);
    }

    public static boolean isLocal(Object state) {
        if (mc.player == null) return false;
        UUID uuid = STATE_UUID.get(state);
        return uuid != null && uuid.equals(mc.player.getUUID());
    }

    public static void play(Emote emote) {
        if (emote == null || emote.animations().isEmpty()) return;
        int n = VARIANT.getOrDefault(emote.id(), 0);
        String anim = emote.animations().get(n % emote.animations().size());
        VARIANT.put(emote.id(), (n + 1) % emote.animations().size());
        EmoteData data;
        try {
            data = EmoteData.load(emote, anim);
        } catch (Exception e) {
            log("play FAILED exception for " + emote.id() + "/" + anim + ": " + e);
            return;
        }
        if (data == null) {
            log("play FAILED: no data for " + emote.id() + "/" + anim);
            return;
        }
        active = new ActiveEmote(emote, data, anim, new EmoteTiming(System.nanoTime()));
        lastPlayMs = System.currentTimeMillis();
        PlaybackSync listener = syncListener;
        if (listener != null) listener.onLocalStart(emote.id());
        if (debug()) {
            String[] bones = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg", "torso"};
            StringBuilder audit = new StringBuilder();
            for (String b : bones) {
                EmoteData.BoneSample s = data.sample(b, 0f);
                audit.append(b).append(s.hasRotation() ? "[R]" : "[ ]")
                    .append(s.hasPosition() ? "[P]" : "[ ]").append(' ');
            }
            log("play id=" + emote.id() + " anim=" + anim + " dur=" + data.duration()
                + " loop=" + emote.loop() + " tracks: " + audit);
        }
    }

    public static void playSelected() {
        if (mc.gui.screen() instanceof EmoteWheelScreen wheel) {
            Emote selected = wheel.getSelectedEmote();
            if (selected != null) {
                mc.gui.setScreen(null);
                play(selected);
            }
        }
    }

    public static void stop() {
        if (active != null) {
            log("stop id=" + active.emote().id());
            PlaybackSync listener = syncListener;
            if (listener != null) listener.onLocalStop(active.emote().id());
        }
        active = null;
    }

    public static boolean isActive() {
        return active != null;
    }

    /** Мс с последнего запуска (антидребезг колеса). */
    public static long millisSincePlay() {
        return System.currentTimeMillis() - lastPlayMs;
    }

    /** Тик движка: автостоп при движении и в конце не-лупа. */
    public static void tick() {
        ActiveEmote a = active;
        if (a == null || mc.player == null) {
            /* локального драматурга нет — удалённые живут своей жизнью */
        } else {
            EmotesModule mod = EmotesModule.INSTANCE;
            boolean keep = mod != null && mod.keepWhileMoving();
            if (!keep && isMoving()) {
                log("autostop: movement");
                stop();
                return;
            }
            long now = System.nanoTime();
            float t = a.timing.animTime(now);
            if (!a.emote.loop() && t >= a.data.duration()) {
                log("autostop: end t=" + t + " dur=" + a.data.duration());
                stop();
                return;
            }
        }

        if (mc.level == null) {
            if (!REMOTES.isEmpty()) {
                log("world gone: clear " + REMOTES.size() + " remote emotes");
                REMOTES.clear();
                remoteWasActive.clear();
            }
            return;
        }

        // Автостоп не-лупа у удалённых (по длительности самой анимации).
        if (!REMOTES.isEmpty()) {
            long now = System.nanoTime();
            REMOTES.values().removeIf(remote -> !remote.emote.loop()
                && remote.data.duration() <= remote.timing.animTime(now));
        }

        // Разыгрывающие, ушедшие из мира, подметаются редко: раз в 100 тиков.
        if (++remoteSweepTicks >= 100) {
            remoteSweepTicks = 0;
            HashSet<UUID> present = new HashSet<>();
            for (var player : mc.level.players()) {
                present.add(player.getUUID());
            }
            REMOTES.keySet().retainAll(present);
            remoteWasActive.retainAll(present);
        }
    }

    private static boolean isMoving() {
        if (mc.player == null || mc.options == null) return false;
        return mc.options.keyUp.isDown()
            || mc.options.keyDown.isDown()
            || mc.options.keyLeft.isDown()
            || mc.options.keyRight.isDown()
            || mc.options.keyJump.isDown()
            || mc.options.keyShift.isDown()
            || mc.player.isFallFlying()
            || mc.player.isSwimming();
    }

    /**
     * Наложение позы на модель. Вызывается из setAngles TAIL для ВСЕХ
     * двуногих — чужим только сбрасываем пивоты, себе накладываем эмоут.
     */
    public static void applyToModel(HumanoidModel model, Object state) {
        ModelPart head = model.head;
        ModelPart body = model.body;
        ModelPart rightArm = model.rightArm;
        ModelPart leftArm = model.leftArm;
        ModelPart rightLeg = model.rightLeg;
        ModelPart leftLeg = model.leftLeg;

        ModelPart jacket = null;
        ModelPart rightSleeve = null;
        ModelPart leftSleeve = null;
        ModelPart rightPants = null;
        ModelPart leftPants = null;
        if (model instanceof net.minecraft.client.model.player.PlayerModel pm) {
            jacket = pm.jacket;
            rightSleeve = pm.rightSleeve;
            leftSleeve = pm.leftSleeve;
            rightPants = pm.rightPants;
            leftPants = pm.leftPants;
        }

        ModelPart[] parts = {head, body, rightArm, leftArm, rightLeg, leftLeg,
            jacket, rightSleeve, leftSleeve, rightPants, leftPants, model.hat};
        for (ModelPart p : parts) {
            if (p == null) continue;
            PivotStore.snapshot(p);
            PivotStore.restore(p);
        }
        if (!originsLogged && debug()) {
            originsLogged = true;
            String[] names = {"head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg",
                "jacket", "rSleeve", "lSleeve", "rPants", "lPants", "hat"};
            StringBuilder sb = new StringBuilder("origins:");
            for (int i = 0; i < parts.length; i++) {
                if (parts[i] == null) continue;
                sb.append(' ').append(names[i]).append("=(")
                    .append(parts[i].x).append(',')
                    .append(parts[i].y).append(',')
                    .append(parts[i].z).append(')');
            }
            log(sb.toString());
        }

        ActiveEmote a;
        boolean local = isLocal(state);
        if (local) {
            a = active;
            if (a == null) {
                // Переход "играло -> стоп": разово сбросить замороженные повороты
                if (wasActive) {
                    wasActive = false;
                    for (ModelPart p : parts) {
                        if (p != null) PivotStore.restoreAll(p);
                    }
                }
                return;
            }
        } else {
            UUID stateUuid = STATE_UUID.get(state);
            a = stateUuid == null ? null : REMOTES.get(stateUuid);
            if (a == null) {
                // Чей-то эмоут кончился: разово вернуть пивоты
                if (stateUuid != null && remoteWasActive.remove(stateUuid)) {
                    for (ModelPart p : parts) {
                        if (p != null) PivotStore.restoreAll(p);
                    }
                }
                return;
            }
            remoteWasActive.add(stateUuid);
        }
        if (local) wasActive = true;

        long now = System.nanoTime();
        float t = a.data.mapTime(a.timing.animTime(now), a.emote.loop());
        float blend = a.timing.blend(now);

        // Тело: чего нет в "body" — добираем из "torso" (там живут
        // перевороты типа сальто, в оригинале они шли в bend геометрии,
        // у нас bend'а нет — крутим кость целиком)
        EmoteData.BoneSample bodyS = a.data.sample("body", t);
        EmoteData.BoneSample torsoS = a.data.sample("torso", t);
        boolean useTorsoRot = !bodyS.hasRotation() && torsoS.hasRotation();
        boolean useTorsoPos = !bodyS.hasPosition() && torsoS.hasPosition();
        EmoteData.BoneSample bodyMerged = new EmoteData.BoneSample(
            useTorsoRot ? torsoS.rotation() : bodyS.rotation(),
            useTorsoPos ? torsoS.position() : bodyS.position(),
            0f, 0f,
            bodyS.hasRotation() || torsoS.hasRotation(),
            bodyS.hasPosition() || torsoS.hasPosition(),
            false);

        applyPart(head, a.data.sample("head", t), blend, HEAD_BASE);
        applyPart(body, bodyMerged, blend, BODY_BASE);
        applyPart(rightArm, a.data.sample("right_arm", t), blend, ARM_R_BASE);
        applyPart(leftArm, a.data.sample("left_arm", t), blend, ARM_L_BASE);
        applyPart(rightLeg, a.data.sample("right_leg", t), blend, LEG_R_BASE);
        applyPart(leftLeg, a.data.sample("left_leg", t), blend, LEG_L_BASE);
        applyPart(jacket, bodyMerged, blend, BODY_BASE);
        applyPart(rightSleeve, a.data.sample("right_arm", t), blend, ARM_R_BASE);
        applyPart(leftSleeve, a.data.sample("left_arm", t), blend, ARM_L_BASE);
        applyPart(rightPants, a.data.sample("right_leg", t), blend, LEG_R_BASE);
        applyPart(leftPants, a.data.sample("left_leg", t), blend, LEG_L_BASE);

        // Шапка следует за головой
        model.hat.xRot = head.xRot;
        model.hat.yRot = head.yRot;
        model.hat.zRot = head.zRot;
    }

    /**
     * Запустить эмоут на другом игроке (UUID + id эмоута из релея IRC).
     * Если эмоут такой у этого клиента не описан — тихо игнорируется.
     */
    public static void playRemote(UUID uuid, String emoteId) {
        if (uuid == null || emoteId == null) return;
        if (mc.player != null && uuid.equals(mc.player.getUUID())) return;

        Emote emote = null;
        for (Emote candidate : Emote.ALL) {
            if (candidate.id().equals(emoteId)) {
                emote = candidate;
                break;
            }
        }
        if (emote == null || emote.animations().isEmpty()) {
            log("remote: unknown emote '" + emoteId + "'");
            return;
        }

        String anim = emote.animations().get(
            ThreadLocalRandom.current().nextInt(emote.animations().size()));
        EmoteData data;
        try {
            data = EmoteData.load(emote, anim);
        } catch (Exception e) {
            log("remote load FAILED " + emoteId + "/" + anim + ": " + e);
            return;
        }
        if (data == null) {
            log("remote load FAILED: no data for " + emoteId + "/" + anim);
            return;
        }

        REMOTES.put(uuid, new ActiveEmote(emote, data, anim, new EmoteTiming(System.nanoTime())));
    }

    /** Остановить эмоут другого игрока (из релея IRC). */
    public static void stopRemote(UUID uuid) {
        if (uuid != null) REMOTES.remove(uuid);
    }

    /** Полная чистка удалённых эмоутов (отключение сети, выход из мира). */
    public static void clearRemote() {
        if (!REMOTES.isEmpty()) {
            REMOTES.clear();
            remoteWasActive.clear();
        }
    }

    public static boolean isRemoteActive(UUID uuid) {
        return uuid != null && REMOTES.containsKey(uuid);
    }

    // Запасные базы на случай отсутствия снапшота (обычно снапшот есть всегда)
    private static final float[] HEAD_BASE = {0f, 0f, 0f};
    private static final float[] BODY_BASE = {0f, 0f, 0f};
    private static final float[] ARM_R_BASE = {-5f, 2f, 0f};
    private static final float[] ARM_L_BASE = {5f, 2f, 0f};
    private static final float[] LEG_R_BASE = {-1.9f, 12f, 0f};
    private static final float[] LEG_L_BASE = {1.9f, 12f, 0f};

    private static void applyPart(ModelPart part, EmoteData.BoneSample s, float blend, float[] fallbackBase) {
        if (part == null) return;
        // База — живой снапшот ваниллы, а не хардкод (стороны/пивоты берём как есть)
        float[] snap = PivotStore.get(part);
        float bx = snap != null ? snap[3] : fallbackBase[0];
        float by = snap != null ? snap[4] : fallbackBase[1];
        float bz = snap != null ? snap[5] : fallbackBase[2];
        if (s.hasRotation() && valid(s.rotation().x) && valid(s.rotation().y) && valid(s.rotation().z)) {
            part.xRot = Mth.lerp(blend, part.xRot, s.rotation().x * DEG);
            part.yRot = Mth.lerp(blend, part.yRot, s.rotation().y * DEG);
            part.zRot = Mth.lerp(blend, part.zRot, s.rotation().z * DEG);
        }
        if (s.hasPosition() && valid(s.position().x) && valid(s.position().y) && valid(s.position().z)) {
            part.x = Mth.lerp(blend, part.x, bx + s.position().x);
            part.y = Mth.lerp(blend, part.y, by - s.position().y);
            part.z = Mth.lerp(blend, part.z, bz + s.position().z);
        }
    }

    private static boolean valid(float v) {
        return !Float.isNaN(v) && !Float.isInfinite(v);
    }

    /** Сдвиг/поворот всего тела (после ванильных трансформов). */
    public static void applyBodyTransform(Object state, PoseStack matrices) {
        boolean local = isLocal(state);
        UUID stateUuid = local ? null : STATE_UUID.get(state);
        ActiveEmote a = local ? active : (stateUuid == null ? null : REMOTES.get(stateUuid));
        if (a == null) return;

        long now = System.nanoTime();
        float t = a.data.mapTime(a.timing.animTime(now), a.emote.loop());
        float blend = a.timing.blend(now);
        EmoteData.BoneSample s = a.data.sample("body", t);

        float dx = s.hasPosition() ? -s.position().x * blend / 16f : 0f;
        float dy = s.hasPosition() ? s.position().y * blend / 16f : 0f;
        float dz = s.hasPosition() ? s.position().z * blend / 16f : 0f;
        float rx = s.hasRotation() ? -s.rotation().x * blend : 0f;
        float ry = s.hasRotation() ? -s.rotation().y * blend : 0f;
        float rz = s.hasRotation() ? s.rotation().z * blend : 0f;

        matrices.translate(dx, dy + 0.7f, dz);
        matrices.rotate(Axis.ZP.rotationDegrees(rz));
        matrices.rotate(Axis.YP.rotationDegrees(ry));
        matrices.rotate(Axis.XP.rotationDegrees(rx));
        matrices.translate(0f, -0.7f, 0f);
    }
}
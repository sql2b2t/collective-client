package me.collective.module.render;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.Render3DEvent;
import me.collective.module.Module;
import me.collective.module.client.Colors;
import me.collective.module.combat.KillAura;
import me.collective.render.ColorUtil;
import me.collective.render.RenderUtil;
import me.collective.render.RenderUtil3D;
import me.collective.setting.BooleanSetting;
import me.collective.setting.ColorSetting;
import me.collective.setting.ModeSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import com.mojang.math.Axis;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TargetESP extends Module {
    public static TargetESP INSTANCE;

    // Single setting: only mode selection, no complicated clutter!
    public final ModeSetting mode = add(new ModeSetting("Вид", "Призраки",
        "Призраки", "Маркер", "Кольцо", "Созвездие", "Bloom", "Мечи", "Молния", "Test",
        "ThunderHack", "ThunderHackV2", "NurikZapen", "CelkaPasta"));

    // Параметры режима ThunderHackV2 (длина/плотность/тряска/амплитуда роя)
    public final NumberSetting espLength = add(new NumberSetting("ESPLength", 14.0, 1.0, 40.0, 1.0));
    public final NumberSetting espFactor = add(new NumberSetting("ESPFactor", 8.0, 1.0, 20.0, 1.0));
    public final NumberSetting espShaking = add(new NumberSetting("ESPShaking", 1.8, 1.5, 10.0, 0.1));
    public final NumberSetting espAmplitude = add(new NumberSetting("ESPAmplitude", 3.0, 0.1, 8.0, 0.1));

    // Свои цвета вместо темы клиента
    public final BooleanSetting customColors = add(new BooleanSetting("Свои цвета", false));
    public final ColorSetting colorOne = add(new ColorSetting("Цвет 1", new Color(189, 153, 255, 255)));
    public final ColorSetting colorTwo = add(new ColorSetting("Цвет 2", new Color(255, 85, 85, 255)));

    // Состояние вращения метки NurikZapen (как CaptureMark в TH)
    private float markValue = 1f;
    private float markPrev = 1f;
    private float markSpeed = 1f;
    private boolean markFlip = false;

    private static final Identifier GHOST_TEXTURE = Identifier.fromNamespaceAndPath("collective", "textures/esp/ghost.png");
    private static final Identifier GLOW_TEXTURE = Identifier.fromNamespaceAndPath("collective", "textures/esp/glow.png");
    private static final Identifier STAR_TEXTURE = Identifier.fromNamespaceAndPath("collective", "textures/esp/star.png");
    private static final Identifier FIREFLY_TEXTURE = Identifier.fromNamespaceAndPath("collective", "textures/esp/firefly.png");
    private static final Identifier CAPTURE_TEXTURE = Identifier.fromNamespaceAndPath("collective", "textures/esp/capture.png");
    private static final Identifier SWORD_TEXTURE_ID = Identifier.fromNamespaceAndPath("collective", "textures/dynamic/sword");

    private static final int MAX_SWORDS = 8;
    private static final float SWORDS_TAU = (float) (Math.PI * 2.0);
    private static final int MAX_BLOOM_PARTICLES = 36;
    private static final BloomSeed[] BLOOM_SEEDS = new BloomSeed[MAX_BLOOM_PARTICLES];

    static {
        for (int i = 0; i < MAX_BLOOM_PARTICLES; i++) {
            long seed = (long) i * 2654435761L + 987654321L;
            Random rnd = new Random(seed);
            float h = (float) i / MAX_BLOOM_PARTICLES + (rnd.nextFloat() - 0.5F) * 0.06F;
            float r = 0.45F + rnd.nextFloat() * 0.55F;
            float ang = rnd.nextFloat() * (float) (Math.PI * 2.0);
            float dir = i % 2 == 0 ? 1.0F : -1.0F;
            BLOOM_SEEDS[i] = new BloomSeed(
                h,
                r,
                ang,
                dir * (0.8F + rnd.nextFloat() * 1.2F),
                0.25F + rnd.nextFloat() * 0.45F,
                2.0F + rnd.nextFloat() * 3.5F,
                rnd.nextFloat() * (float) (Math.PI * 2.0),
                (rnd.nextFloat() - 0.5F) * 90.0F,
                rnd.nextFloat() * 360.0F,
                0.75F + rnd.nextFloat() * 0.5F,
                1.2F + rnd.nextFloat() * 2.0F,
                0.04F + rnd.nextFloat() * 0.09F
            );
        }
    }

    private LivingEntity displayedTarget;
    private final List<Bolt> bolts = new ArrayList<>();
    private LivingEntity boltTarget;
    private long nextBoltAt;

    private final SwordState[] swordStates = new SwordState[MAX_SWORDS];
    private LivingEntity swordTarget;
    private int swordPrevHurt;
    private long swordLastUpdate;
    private final Vec3[] swordWorld = new Vec3[MAX_SWORDS];
    private final int[] swordColors = new int[MAX_SWORDS];
    private final float[] swordRolls = new float[MAX_SWORDS];
    private final float[] swordPitchs = new float[MAX_SWORDS];
    private float swordHalfW;
    private float swordHalfH;
    private DynamicTexture swordTexture;
    private boolean swordTextureRegistered = false;

    private long animationChangedAt = System.currentTimeMillis();
    private boolean appearing;
    private long epoch = System.currentTimeMillis();
    private long lastFrame = System.currentTimeMillis();
    private float hurtProgress;
    private float circleStep;
    private float circleSpeed;
    private boolean reverseSpeed;
    private double smoothY;
    private double smoothY2;

    private final Vec3[] ghostHistory = new Vec3[256];
    private final long[] ghostHistoryTimes = new long[256];
    private Vec3 ghostAnchor;
    private Vec3 ghostVelocity = Vec3.ZERO;
    private int ghostHistoryCursor = -1;
    private int ghostHistoryCount;
    private LivingEntity ghostTrackedTarget;
    private Vec3 ghostFlightFrom;
    private Vec3 ghostFlightSide = Vec3.ZERO;
    private long ghostFlightStartedAt;
    private long ghostFlightDurationMs;
    private float ghostFlightSideAmount;
    private float ghostFlightArcHeight;
    private boolean ghostSwitching;
    private float ghostFlightStrength;
    private float ghostVisibility;
    private long ghostEpoch = System.currentTimeMillis();
    private Object ghostWorld;

    public TargetESP() {
        super("TargetESP", "Красивый анимированный маркер таргета и призраки вокруг цели", Category.RENDER);
        INSTANCE = this;
        for (int i = 0; i < MAX_SWORDS; i++) {
            this.swordStates[i] = new SwordState();
            this.swordWorld[i] = Vec3.ZERO;
        }
    }

    @Override
    public void onEnable() {
        this.epoch = System.currentTimeMillis();
        this.ghostEpoch = this.epoch;
        this.lastFrame = this.epoch;
        this.resetGhostMotion();
        this.ghostVisibility = 0.0F;
        this.ghostWorld = mc.level;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        this.displayedTarget = null;
        this.smoothY = this.smoothY2 = 0.0;
        this.resetGhostMotion();
        this.resetSwords();
        this.resetLightning();
        this.ghostVisibility = 0.0F;
        this.ghostWorld = null;
        super.onDisable();
    }

    private LivingEntity getTarget() {
        if (KillAura.textureRun != null && KillAura.textureRun.isAlive() && !KillAura.textureRun.isRemoved()) {
            return KillAura.textureRun;
        }
        if (mc.crosshairPickEntity instanceof LivingEntity living && living.isAlive() && !living.isRemoved() && !(living == mc.player)) {
            return living;
        }
        return null;
    }

    @EventHandler
    public void onRender(Render3DEvent event) {
        if (event == null || mc.player == null || mc.level == null) return;

        long now = System.currentTimeMillis();
        if (this.ghostWorld != mc.level) {
            this.displayedTarget = null;
            this.appearing = false;
            this.animationChangedAt = now;
            this.resetGhostMotion();
            this.resetSwords();
            this.resetLightning();
            this.ghostVisibility = 0.0F;
            this.ghostWorld = mc.level;
        }

        float elapsedSeconds = Mth.clamp((float) (now - this.lastFrame) / 1000.0F, 0.001F, 0.1F);
        float frame = elapsedSeconds * 60.0F;
        this.lastFrame = now;

        LivingEntity target = getTarget();
        this.updateAnimationTarget(target, now);
        float alpha = this.animation(now, elapsedSeconds);

        if (this.displayedTarget != null && !(alpha <= 0.01F)) {
            this.hurtProgress = this.displayedTarget.hurtTime > 0 ? this.displayedTarget.hurtTime / 10.0F : Math.max(0.0F, this.hurtProgress - 0.1F * frame);
            this.circleStep += 0.06F * frame;
            if (this.circleSpeed > 25.0F) {
                this.reverseSpeed = true;
            }
            if (this.circleSpeed < -25.0F) {
                this.reverseSpeed = false;
            }
            this.circleSpeed = this.circleSpeed + (this.reverseSpeed ? -0.5F : 0.5F) * frame;
            this.circleStep = this.circleStep + this.circleSpeed * 8.0E-4F * frame;

            String currentMode = this.mode.getValue();
            // Тик вращения метки NurikZapen
            this.markPrev = this.markValue;
            this.markValue += this.markSpeed;
            if (this.markSpeed > 25) this.markFlip = true;
            if (this.markSpeed < -25) this.markFlip = false;
            this.markSpeed = this.markFlip ? this.markSpeed - 0.5F : this.markSpeed + 0.5F;
            try {
                switch (currentMode) {
                    case "Призраки" -> this.renderGhosts(event, this.displayedTarget, alpha, now, elapsedSeconds);
                    case "Маркер" -> this.renderMarker(event, this.displayedTarget, alpha, now);
                    case "Bloom" -> this.renderBloom(event, this.displayedTarget, alpha);
                    case "Мечи" -> this.renderSwords(event, this.displayedTarget, alpha);
                    case "Созвездие" -> this.renderConstellation(event, this.displayedTarget, alpha, now);
                    case "Test" -> this.renderTest(event, this.displayedTarget, alpha);
                    case "ThunderHack" -> this.renderThunderHack(event, this.displayedTarget, alpha, now);
                    case "ThunderHackV2" -> this.renderThunderHackV2(event, this.displayedTarget, alpha, now);
                    case "NurikZapen" -> this.renderNurikZapen(event, this.displayedTarget, alpha);
                    case "CelkaPasta" -> this.renderCelkaPasta(event, this.displayedTarget, alpha, now);
                    default -> {}
                }
            } catch (Exception ignored) {
                // Битый кадр буфера (Not building) — пропускаем, стек уже сбалансирован в finally
            }
        }
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor context, float delta) {
        if (!isToggled() || mc.player == null || this.displayedTarget == null) return;

        long now = System.currentTimeMillis();
        float alpha = "Призраки".equals(this.mode.getValue())
            ? this.ghostVisibility
            : (this.appearing ? 1.0F : 1.0F - this.smoothStep(Mth.clamp((float) (now - this.animationChangedAt) / 300.0F, 0.0F, 1.0F)));

        if (alpha <= 0.01F) return;

        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        String currentMode = this.mode.getValue();
        if ("Кольцо".equals(currentMode)) {
            this.renderRingOverlay(context, this.displayedTarget, alpha, delta, width, height);
        } else if ("Молния".equals(currentMode)) {
            this.renderLightningOverlay(context, this.displayedTarget, alpha, delta, width, height);
        }
    }

    private void updateAnimationTarget(LivingEntity target, long now) {
        if (target != null) {
            if (this.displayedTarget != target) {
                boolean replacingVisibleTarget = this.displayedTarget != null && this.appearing && "Призраки".equals(this.mode.getValue());
                this.displayedTarget = target;
                if (!replacingVisibleTarget) {
                    this.animationChangedAt = now;
                }
                this.epoch = now;
            }
            if (!this.appearing) {
                this.animationChangedAt = now;
            }
            this.appearing = true;
        } else if (this.appearing) {
            this.appearing = false;
            this.animationChangedAt = now;
        }
    }

    private float animation(long now, float elapsedSeconds) {
        if ("Призраки".equals(this.mode.getValue())) {
            float goal = this.appearing ? 1.0F : 0.0F;
            float rate = this.appearing ? 12.0F : 6.0F;
            float response = 1.0F - (float) Math.exp(-elapsedSeconds * rate);
            this.ghostVisibility = this.ghostVisibility + (goal - this.ghostVisibility) * response;
            if (!this.appearing && this.ghostVisibility <= 0.01F) {
                this.ghostVisibility = 0.0F;
                this.displayedTarget = null;
                this.resetGhostMotion();
            }
            return this.ghostVisibility;
        } else {
            float t = Mth.clamp((float) (now - this.animationChangedAt) / 300.0F, 0.0F, 1.0F);
            if (!this.appearing && t >= 1.0F) {
                this.displayedTarget = null;
                this.resetGhostMotion();
            }
            return this.appearing ? this.easeOutBack(t) : 1.0F - this.smoothStep(t);
        }
    }

    // --- GHOSTS RENDERING ---
    private void renderGhosts(Render3DEvent event, LivingEntity target, float alpha, long now, float elapsedSeconds) {
        if (event.consumers == null) return;
        VertexConsumer consumer = event.consumers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(GHOST_TEXTURE));
        PoseStack stack = event.matrices;
        if (stack == null) return;

        Vec3 camera = event.camera != null ? event.camera.position() : Vec3.ZERO;
        float flight = this.updateGhostMotion(target, event.tickDelta, elapsedSeconds, now);
        float time = (float) (now - this.ghostEpoch) / 1000.0F;
        float hurtScale = 1.0F + this.hurtProgress * 0.18F;
        int[] palette = this.colors(alpha);

        for (int lane = 0; lane < 3; lane++) {
            float laneOffset = lane * (float) (Math.PI * 2.0 / 3.0);

            for (int particle = 0; particle < 30; particle++) {
                float trail = particle / 29.0F;
                long delay = (long) (particle * (18.0F + flight * 16.0F));
                float delayedTime = (float) (now - delay - this.ghostEpoch) / 1000.0F;
                Vec3 base = this.sampleGhostHistory(now - delay);
                float phase = delayedTime * (0.72F + flight * 0.1F) + laneOffset - trail * 0.32F;
                float radius = (0.72F + 0.1F * Mth.sin(delayedTime * 1.4F + lane)) * hurtScale * (1.0F - flight * 0.18F);
                double px = base.x + Mth.sin(phase) * radius;
                double pz = base.z + Mth.cos(phase) * radius;
                double py = base.y
                    + target.getBbHeight() * (0.24F + lane * 0.24F)
                    + Mth.sin(delayedTime * 1.35F + particle * 0.12F + laneOffset) * (0.13F + flight * 0.05F);
                float pulse = 0.94F + 0.06F * Mth.sin(time * 4.2F + laneOffset - trail * 2.0F);
                float size = (0.27F - trail * 0.16F) * pulse * (1.0F + this.hurtProgress * 0.22F + flight * (1.0F - trail) * 0.16F);
                float tailAlpha = 0.08F + 0.92F * (float) Math.pow(1.0F - trail, 1.45F);
                int color = ColorUtil.multiplyAlpha(ColorUtil.interpolate(palette[0], palette[1], 0.5F + 0.5F * Mth.sin(phase + trail * 2.0F)), tailAlpha);
                float rotation = time * 16.0F + particle * 5.0F + lane * 18.0F + flight * Mth.sin(time * 1.4F + laneOffset) * 10.0F;
                float stretchX = 1.0F + flight * (1.0F - trail) * 0.62F;
                float stretchY = 1.0F - flight * (1.0F - trail) * 0.12F;
                Vec3 particlePos = new Vec3(px, py, pz);

                if (particle == 0) {
                    int glow = ColorUtil.multiplyAlpha(color, 0.2F + flight * 0.18F);
                    this.drawGhostSprite(stack, consumer, particlePos, camera, size * 2.15F, size * 1.9F, rotation * 0.72F, glow);
                }

                this.drawGhostSprite(stack, consumer, particlePos, camera, size * stretchX, size * stretchY, rotation, color);
            }
        }
    }

    private float updateGhostMotion(LivingEntity target, float tickDelta, float elapsedSeconds, long now) {
        if (this.ghostAnchor == null || target.isAlive() && !target.isRemoved()) {
            Vec3 targetPosition = this.lerped(target, tickDelta);
            if (this.ghostAnchor != null && this.finite(this.ghostAnchor)) {
                double anchorDistance = targetPosition.distanceTo(this.ghostAnchor);
                if (Double.isFinite(anchorDistance) && !(anchorDistance > 14.0)) {
                    boolean switchedTarget = this.ghostTrackedTarget != target;
                    if (switchedTarget) {
                        Vec3 toTarget = targetPosition.subtract(this.ghostAnchor);
                        double distance = toTarget.length();
                        Vec3 side = new Vec3(-toTarget.z, 0.0, toTarget.x);
                        if (side.lengthSqr() < 1.0E-6) {
                            side = new Vec3((target.getId() & 1) == 0 ? 1.0 : -1.0, 0.0, 0.0);
                        } else {
                            side = side.normalize();
                        }

                        double direction = (target.getId() & 1) == 0 ? 1.0 : -1.0;
                        this.ghostFlightFrom = this.ghostAnchor;
                        this.ghostFlightSide = side.scale(direction);
                        this.ghostFlightStartedAt = now;
                        this.ghostFlightDurationMs = (long) Math.max(1150.0, Math.min(1900.0, 1050.0 + distance * 95.0));
                        this.ghostFlightSideAmount = (float) Math.min(1.35, 0.22 + distance * 0.14);
                        this.ghostFlightArcHeight = (float) Math.min(1.15, 0.24 + distance * 0.09);
                        this.ghostSwitching = true;
                        this.ghostVelocity = this.ghostVelocity.scale(0.2);
                        this.ghostTrackedTarget = target;
                        this.ghostFlightStrength = Math.max(this.ghostFlightStrength, 0.25F);
                    }

                    Vec3 targetMotion = target.getDeltaMovement();
                    double targetMotionLength = targetMotion.length();
                    if (targetMotionLength > 0.8) {
                        targetMotion = targetMotion.scale(0.8 / targetMotionLength);
                    }

                    double distanceBefore = targetPosition.distanceTo(this.ghostAnchor);
                    double leadTicks = 2.1 + Math.min(distanceBefore * 0.16, 2.2);
                    Vec3 desiredPosition = targetPosition.add(targetMotion.scale(leadTicks));
                    double totalStep = Math.max(0.001, Math.min(elapsedSeconds, 0.1));
                    float switchVisual = 0.0F;

                    if (this.ghostSwitching && this.ghostFlightFrom != null) {
                        float progress = Mth.clamp((float) (now - this.ghostFlightStartedAt) / (float) this.ghostFlightDurationMs, 0.0F, 1.0F);
                        float eased = this.smootherStep(progress);
                        float arc = Mth.sin(progress * (float) Math.PI);
                        Vec3 nextAnchor = this.lerp(this.ghostFlightFrom, desiredPosition, eased)
                            .add(this.ghostFlightSide.scale(arc * this.ghostFlightSideAmount))
                            .add(0.0, arc * this.ghostFlightArcHeight, 0.0);
                        this.ghostVelocity = nextAnchor.subtract(this.ghostAnchor).scale(1.0 / totalStep);
                        this.ghostAnchor = nextAnchor;
                        switchVisual = arc;
                        if (progress >= 1.0F) {
                            this.ghostSwitching = false;
                            this.ghostVelocity = targetMotion.scale(20.0);
                        }
                    } else {
                        int subSteps = Math.max(1, (int) Math.ceil(totalStep / 0.016666666666666666));
                        double step = totalStep / subSteps;

                        for (int i = 0; i < subSteps; i++) {
                            Vec3 offset = desiredPosition.subtract(this.ghostAnchor);
                            double distance = offset.length();
                            double stiffness = 25.0 + Math.min(distance * 2.0, 22.0);
                            double damping = 2.0 * Math.sqrt(stiffness) * 1.18;
                            Vec3 acceleration = offset.scale(stiffness).subtract(this.ghostVelocity.scale(damping));
                            this.ghostVelocity = this.ghostVelocity.add(acceleration.scale(step));
                            double speed = this.ghostVelocity.length();
                            double maxSpeed = 4.2 + Math.min(distance * 1.8, 14.0);
                            if (speed > maxSpeed) {
                                this.ghostVelocity = this.ghostVelocity.scale(maxSpeed / speed);
                            }
                            this.ghostAnchor = this.ghostAnchor.add(this.ghostVelocity.scale(step));
                        }
                    }

                    double remainingDistance = targetPosition.distanceTo(this.ghostAnchor);
                    double speed = this.ghostVelocity.length();
                    float targetFlight = Mth.clamp((float) ((remainingDistance - 0.18) / 3.8 + speed / 28.0 * 0.55), 0.0F, 1.0F);
                    if (this.ghostSwitching) {
                        targetFlight = Math.max(targetFlight, 0.42F + switchVisual * 0.38F);
                    }

                    float response = 1.0F - (float) Math.exp(-elapsedSeconds * (targetFlight > this.ghostFlightStrength ? 5.0F : 2.4F));
                    this.ghostFlightStrength = this.ghostFlightStrength + (targetFlight - this.ghostFlightStrength) * response;
                    this.rememberGhostAnchor(now);
                    return Mth.clamp(this.ghostFlightStrength, 0.0F, 1.0F);
                } else {
                    this.ghostAnchor = targetPosition;
                    this.ghostVelocity = Vec3.ZERO;
                    this.ghostTrackedTarget = target;
                    this.ghostSwitching = false;
                    this.ghostFlightFrom = null;
                    this.ghostFlightStrength = 0.0F;
                    this.ghostHistoryCursor = -1;
                    this.ghostHistoryCount = 0;
                    this.rememberGhostAnchor(now);
                    return 0.0F;
                }
            } else {
                this.ghostAnchor = targetPosition;
                this.ghostVelocity = Vec3.ZERO;
                this.ghostTrackedTarget = target;
                this.ghostFlightStrength = 0.0F;
                this.ghostHistoryCursor = -1;
                this.ghostHistoryCount = 0;
                this.rememberGhostAnchor(now);
                return 0.0F;
            }
        } else {
            this.ghostSwitching = false;
            double damping = Math.exp(-elapsedSeconds * 6.0);
            this.ghostVelocity = this.ghostVelocity.scale(damping);
            this.ghostAnchor = this.ghostAnchor.add(this.ghostVelocity.scale(elapsedSeconds));
            this.ghostFlightStrength = this.ghostFlightStrength * (float) Math.exp(-elapsedSeconds * 2.5);
            this.rememberGhostAnchor(now);
            return Mth.clamp(this.ghostFlightStrength, 0.0F, 1.0F);
        }
    }

    private void rememberGhostAnchor(long now) {
        if (this.ghostAnchor != null) {
            if (this.ghostHistoryCount > 0 && this.ghostHistoryTimes[this.ghostHistoryCursor] == now) {
                this.ghostHistory[this.ghostHistoryCursor] = this.ghostAnchor;
            } else {
                this.ghostHistoryCursor = (this.ghostHistoryCursor + 1) % 256;
                this.ghostHistory[this.ghostHistoryCursor] = this.ghostAnchor;
                this.ghostHistoryTimes[this.ghostHistoryCursor] = now;
                if (this.ghostHistoryCount < 256) {
                    this.ghostHistoryCount++;
                }
            }
        }
    }

    private Vec3 sampleGhostHistory(long sampleTime) {
        if (this.ghostHistoryCount > 0 && this.ghostHistoryCursor >= 0 && this.ghostAnchor != null) {
            int newest = this.ghostHistoryCursor;
            if (sampleTime >= this.ghostHistoryTimes[newest]) {
                return this.ghostHistory[newest];
            }

            for (int i = 0; i < this.ghostHistoryCount - 1; i++) {
                int newer = (newest - i + 256) % 256;
                int older = (newer - 1 + 256) % 256;
                long newerTime = this.ghostHistoryTimes[newer];
                long olderTime = this.ghostHistoryTimes[older];
                if (sampleTime >= olderTime) {
                    double progress = newerTime == olderTime ? 1.0 : (double) (sampleTime - olderTime) / (newerTime - olderTime);
                    return this.lerp(this.ghostHistory[older], this.ghostHistory[newer], progress);
                }
            }

            int oldest = (newest - this.ghostHistoryCount + 1 + 256) % 256;
            return this.ghostHistory[oldest];
        } else {
            return this.ghostAnchor == null ? Vec3.ZERO : this.ghostAnchor;
        }
    }

    private void drawGhostSprite(PoseStack stack, VertexConsumer consumer, Vec3 position, Vec3 camera, float width, float height, float rotation, int color) {
        Vec3 relative = position.subtract(camera);
        stack.pushPose();
        try {
            stack.translate(relative.x, relative.y, relative.z);
            if (mc.gameRenderer != null && mc.gameRenderer.mainCamera() != null) {
                stack.rotate(mc.gameRenderer.mainCamera().rotation());
            }
            stack.rotate(Axis.ZP.rotationDegrees(rotation));
            stack.scale(width, height, 1.0F);
            PoseStack.Pose entry = stack.last();

            consumer.addVertex(entry, -1.0F, -1.0F, 0.0F).setColor(color).setUv(0.0F, 0.0F).setOverlay(0).setLight(0xF000F0).setNormal(entry, 0.0f, 1.0f, 0.0f);
            consumer.addVertex(entry, -1.0F, 1.0F, 0.0F).setColor(color).setUv(0.0F, 1.0F).setOverlay(0).setLight(0xF000F0).setNormal(entry, 0.0f, 1.0f, 0.0f);
            consumer.addVertex(entry, 1.0F, 1.0F, 0.0F).setColor(color).setUv(1.0F, 1.0F).setOverlay(0).setLight(0xF000F0).setNormal(entry, 0.0f, 1.0f, 0.0f);
            consumer.addVertex(entry, 1.0F, -1.0F, 0.0F).setColor(color).setUv(1.0F, 0.0F).setOverlay(0).setLight(0xF000F0).setNormal(entry, 0.0f, 1.0f, 0.0f);
        } finally {
            stack.popPose();
        }
    }

    // --- MARKER RENDERING ---
    private void renderMarker(Render3DEvent event, LivingEntity target, float alpha, long now) {
        if (event.consumers == null) return;
        Identifier texture = GLOW_TEXTURE;
        VertexConsumer consumer = event.consumers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(texture));
        PoseStack stack = event.matrices;
        if (stack == null) return;
        Vec3 camera = event.camera != null ? event.camera.position() : Vec3.ZERO;
        Vec3 pos = this.lerped(target, event.tickDelta).subtract(camera);
        float time = (float) (now - this.epoch) / 1000.0F;
        float omega = (float) ((Math.PI * 2) / 2.8F);
        float angle = -(110.0F / omega) * (float) Math.cos(omega * (time + 0.5F)) + 3.0F * (float) Math.sin(time * 0.7);
        float size = 0.55F * (1.0F + 0.06F * (float) Math.sin(time * 1.6F * Math.PI * 2.0)) * (1.0F + 0.25F * this.hurtProgress);
        int[] colors = this.colors(alpha * (0.85F + 0.15F * (float) Math.sin(time * 1.6F * Math.PI * 2.0)));

        stack.pushPose();
        try {
            stack.translate(pos.x, pos.y + target.getBbHeight() * 0.5, pos.z);
            if (mc.gameRenderer != null && mc.gameRenderer.mainCamera() != null) {
                stack.rotate(mc.gameRenderer.mainCamera().rotation());
            }
            stack.rotate(Axis.ZP.rotationDegrees(angle));
            stack.scale(size, size, 1.0F);
            PoseStack.Pose entry = stack.last();

            consumer.addVertex(entry, -1.0F, -1.0F, 0.0F).setColor(colors[1]).setUv(0.0F, 0.0F).setOverlay(0).setLight(0xF000F0).setNormal(entry, 0.0f, 1.0f, 0.0f);
            consumer.addVertex(entry, -1.0F, 1.0F, 0.0F).setColor(colors[0]).setUv(0.0F, 1.0F).setOverlay(0).setLight(0xF000F0).setNormal(entry, 0.0f, 1.0f, 0.0f);
            consumer.addVertex(entry, 1.0F, 1.0F, 0.0F).setColor(colors[1]).setUv(1.0F, 1.0F).setOverlay(0).setLight(0xF000F0).setNormal(entry, 0.0f, 1.0f, 0.0f);
            consumer.addVertex(entry, 1.0F, -1.0F, 0.0F).setColor(colors[0]).setUv(1.0F, 0.0F).setOverlay(0).setLight(0xF000F0).setNormal(entry, 0.0f, 1.0f, 0.0f);
        } finally {
            stack.popPose();
        }
    }

    // --- RING 2D OVERLAY RENDERING ---
    private void renderRingOverlay(GuiGraphicsExtractor context, LivingEntity target, float alpha, float tickDelta, int width, int height) {
        Vec3 center = this.lerped(target, tickDelta);
        float radius = target.getBbWidth();
        int[] palette = this.colors(alpha);

        double wave = 0.5 - 0.5 * Math.cos(this.circleStep);
        this.smoothY = this.smoothY + (wave * target.getBbHeight() - this.smoothY) * 0.12;

        for (int i = 0; i < 48; i++) {
            double angle = i * (Math.PI * 2.0) / 48.0;
            Vec3 world = center.add(Math.cos(angle) * radius, this.smoothY, Math.sin(angle) * radius);
            Vector2f point = RenderUtil3D.worldToScreen(world.x, world.y, world.z, width, height);
            if (point != null) {
                int color = ColorUtil.interpolate(palette[0], palette[1], (float) (0.5 - 0.5 * Math.cos(angle)));
                RenderUtil.drawGlow(context, point.x - 3.5F, point.y - 3.5F, 7.0F, 7.0F, 3.5F, ColorUtil.multiplyAlpha(color, 0.52F), 1.0F, 5.5F);
                RenderUtil.drawCircle(context, point.x - 2.1F, point.y - 2.1F, 4.2F, color);
            }
        }
    }

    // --- BLOOM RENDERING ---
    private void renderBloom(Render3DEvent event, LivingEntity target, float alpha) {
        if (event.consumers == null) return;
        float tickDelta = event.tickDelta;
        Vec3 base = this.lerped(target, tickDelta);
        float height = target.getBbHeight();
        float width = target.getBbWidth();
        int[] palette = this.colors(alpha);
        int theme = ColorUtil.interpolate(palette[0], palette[1], 0.5F);
        int particleColor = ColorUtil.interpolate(theme, ColorUtil.rgb(255, 85, 105), Mth.clamp(this.hurtProgress, 0.0F, 1.0F));
        double time = (System.currentTimeMillis() % 36000000L) / 1000.0 * 1.0;
        int count = MAX_BLOOM_PARTICLES;
        float userSize = 0.2F;
        PoseStack stack = event.matrices;
        if (stack == null) return;
        Vec3 camera = event.camera != null ? event.camera.position() : Vec3.ZERO;
        VertexConsumer consumer = event.consumers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(GLOW_TEXTURE));

        for (int i = 0; i < count; i++) {
            BloomSeed seed = BLOOM_SEEDS[i];
            float rawH = (float) ((seed.heightBase + time * seed.verticalSpeed * 0.22) % 1.15);
            if (rawH < 0.0F) {
                rawH += 1.15F;
            }
            float hAlphaFade = 1.0F;
            if (rawH < 0.12F) {
                hAlphaFade = rawH / 0.12F;
            } else if (rawH > 0.92F) {
                hAlphaFade = Math.max(0.0F, (1.15F - rawH) / 0.23F);
            }
            double curAngle = seed.angleOffset + time * seed.orbitSpeed + Math.sin(time * seed.radialFreq + seed.pulsePhase) * 0.35;
            double baseRadius = width * 0.65F * seed.radiusFactor + Math.sin(time * seed.radialFreq * 1.4 + seed.pulsePhase) * seed.radialAmp;
            double worldX = base.x + Math.cos(curAngle) * baseRadius;
            double worldY = base.y + rawH * height;
            double worldZ = base.z + Math.sin(curAngle) * baseRadius;
            float pulse = 0.78F + 0.32F * (float) Math.sin(time * seed.pulseSpeed + seed.pulsePhase);
            float size = userSize * seed.sizeFactor * pulse * (1.0F + this.hurtProgress * 0.2F);
            float alphaFactor = hAlphaFade * (0.6F + 0.4F * (float) Math.cos(time * seed.pulseSpeed + seed.pulsePhase));
            if (alphaFactor <= 0.01F || size <= 0.01F) {
                continue;
            }
            float rotation = seed.spinSeed + (float) (time * seed.spinSpeed);
            Vec3 pos = new Vec3(worldX, worldY, worldZ);
            this.drawGhostSprite(stack, consumer, pos, camera, size * 1.55F, size * 1.55F, rotation, ColorUtil.multiplyAlpha(particleColor, alphaFactor * 0.43F));
            this.drawGhostSprite(stack, consumer, pos, camera, size, size, rotation, ColorUtil.multiplyAlpha(particleColor, alphaFactor * 0.86F));
        }
    }

    // --- SWORDS RENDERING ---
    private void resetSwords() {
        this.swordTarget = null;
        this.swordPrevHurt = 0;
        this.swordLastUpdate = 0L;
        for (SwordState s : this.swordStates) {
            s.initialized = false;
            s.vx = s.vy = s.vz = 0.0;
            s.roll = 0.0F;
            s.pitch = 0.0F;
        }
    }

    private void ensureSwordTexture() {
        if (this.swordTextureRegistered) return;
        try {
            NativeImage image = new NativeImage(32, 64, false);
            this.paintSwordSprite(image);
            this.swordTexture = new DynamicTexture(() -> "collective_sword", image);
            mc.getTextureManager().register(SWORD_TEXTURE_ID, this.swordTexture);
            this.swordTextureRegistered = true;
        } catch (Exception ignored) {}
    }

    private void paintSwordSprite(NativeImage image) {
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 32; x++) {
                image.setPixel(x, y, 0);
            }
        }
        for (int y = 0; y <= 5; y++) {
            int hw = y * 3 / 5;
            for (int x = 15 - hw; x <= 16 + hw; x++) {
                this.swordPixel(image, x, y, 238, 245, 252, 255);
            }
        }
        for (int y = 6; y <= 42; y++) {
            for (int x = 13; x <= 18; x++) {
                if (x == 15 || x == 16) {
                    this.swordPixel(image, x, y, 238, 245, 252, 255);
                } else if (x == 14 || x == 17) {
                    this.swordPixel(image, x, y, 198, 208, 224, 255);
                } else {
                    this.swordPixel(image, x, y, 138, 148, 166, 255);
                }
            }
        }
        for (int x = 7; x <= 24; x++) {
            for (int y = 43; y <= 46; y++) {
                if (y == 46) {
                    this.swordPixel(image, x, y, 150, 118, 40, 255);
                } else {
                    this.swordPixel(image, x, y, 214, 178, 60, 255);
                }
            }
        }
        for (int y = 47; y <= 57; y++) {
            for (int x = 14; x <= 17; x++) {
                if (y % 3 == 0) {
                    this.swordPixel(image, x, y, 150, 106, 62, 255);
                } else {
                    this.swordPixel(image, x, y, 96, 62, 34, 255);
                }
            }
        }
        for (int y = 58; y <= 61; y++) {
            for (int x = 13; x <= 18; x++) {
                this.swordPixel(image, x, y, 214, 178, 60, 255);
            }
        }
    }

    private void swordPixel(NativeImage image, int x, int y, int r, int g, int b, int a) {
        if (x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight()) {
            image.setPixel(x, y, (a << 24) | (b << 16) | (g << 8) | r);
        }
    }

    private int simulateSwords(LivingEntity target, float tickDelta, long now, float alpha) {
        int count = MAX_SWORDS;
        float radiusSetting = 1.0F;
        float sizeSetting = 1.0F;
        float bobAmp = 0.12F;
        boolean physics = true;
        double speedVal = 1200.0;
        Vec3 base = this.lerped(target, tickDelta);
        double centerY = base.y + target.getBbHeight() * 0.45;
        double actualRadius = (target.getBbWidth() + 0.35) * radiusSetting;

        if (this.swordLastUpdate == 0L) {
            this.swordLastUpdate = now;
        }
        float dt = (now - this.swordLastUpdate) / 1000.0F;
        this.swordLastUpdate = now;
        if (dt > 0.1F) dt = 0.1F;
        float step = Math.min(2.0F, Math.max(0.2F, dt * 60.0F));

        if (this.swordTarget != target) {
            this.swordTarget = target;
            this.swordPrevHurt = target.hurtTime;
            double initOrbit = ((double) (now % (long) speedVal) / speedVal) * SWORDS_TAU;
            for (int i = 0; i < count; i++) {
                SwordState s = this.swordStates[i];
                double angle = initOrbit + i * (SWORDS_TAU / count);
                s.x = base.x + Math.cos(angle) * actualRadius;
                s.y = centerY;
                s.z = base.z + Math.sin(angle) * actualRadius;
                s.vx = s.vy = s.vz = 0.0;
                s.roll = 0.0F;
                s.pitch = 0.0F;
                s.initialized = true;
            }
        }

        double orbitAngle = ((double) (now % (long) speedVal) / speedVal) * SWORDS_TAU;
        int currentHurt = target.hurtTime;
        boolean justDamaged = currentHurt > this.swordPrevHurt && currentHurt >= 9;
        this.swordPrevHurt = currentHurt;
        float hurtPC = currentHurt > 0 ? (float) Math.sin(currentHurt * (Math.PI / 10.0)) : 0.0F;
        float camYaw = mc.gameRenderer != null && mc.gameRenderer.mainCamera() != null ? mc.gameRenderer.mainCamera().yRot() : 0.0f;
        float yawRad = (float) Math.toRadians(camYaw);
        int[] palette = this.colors(alpha);

        for (int i = 0; i < count; i++) {
            SwordState s = this.swordStates[i];
            double swordAngle = orbitAngle + i * (SWORDS_TAU / count);
            double bob = Math.sin(now * 0.0035 + i * (Math.PI / 4.0)) * bobAmp;
            double idealX = base.x + Math.cos(swordAngle) * actualRadius;
            double idealY = centerY + bob;
            double idealZ = base.z + Math.sin(swordAngle) * actualRadius;

            if (!s.initialized) {
                s.x = idealX;
                s.y = idealY;
                s.z = idealZ;
                s.vx = s.vy = s.vz = 0.0;
                s.roll = 0.0F;
                s.pitch = 0.0F;
                s.initialized = true;
            }

            if (justDamaged) {
                double dxFromTarget = s.x - base.x;
                double dzFromTarget = s.z - base.z;
                double len = Math.hypot(dxFromTarget, dzFromTarget);
                if (len > 0.001) {
                    double kick = 0.16 + Math.random() * 0.06;
                    s.vx += dxFromTarget / len * kick;
                    s.vz += dzFromTarget / len * kick;
                    s.vy += 0.07;
                }
                s.roll += (float) ((Math.random() - 0.5) * 28.0);
            }

            if (physics) {
                double spring = 0.22 * step;
                double damping = Math.pow(0.78, step);
                s.vx = (s.vx + (idealX - s.x) * spring) * damping;
                s.vy = (s.vy + (idealY - s.y) * spring) * damping;
                s.vz = (s.vz + (idealZ - s.z) * spring) * damping;
                s.x += s.vx * step;
                s.y += s.vy * step;
                s.z += s.vz * step;
                double camRightVel = -s.vx * Math.cos(yawRad) - s.vz * Math.sin(yawRad);
                float targetRoll = (float) Mth.clamp(camRightVel * 130.0, -42.0, 42.0);
                float targetPitch = (float) Mth.clamp(-s.vy * 85.0, -32.0, 32.0);
                s.roll = Mth.lerp(0.25F * step, s.roll, targetRoll);
                s.pitch = Mth.lerp(0.25F * step, s.pitch, targetPitch);
            } else {
                s.x = idealX;
                s.y = idealY;
                s.z = idealZ;
                s.vx = s.vy = s.vz = 0.0;
                s.roll = 0.0F;
                s.pitch = 0.0F;
            }

            this.swordWorld[i] = new Vec3(s.x, s.y, s.z);
            this.swordRolls[i] = s.roll;
            this.swordPitchs[i] = s.pitch;
            int theme = ColorUtil.interpolate(palette[0], palette[1], 0.5F + 0.5F * (float) Math.sin(i * 0.785F + now / 450.0));
            this.swordColors[i] = ColorUtil.interpolate(theme, ColorUtil.rgb(255, 65, 120), Mth.clamp(hurtPC, 0.0F, 1.0F));
        }

        this.swordHalfW = 0.20F * sizeSetting;
        this.swordHalfH = 0.40F * sizeSetting;
        return count;
    }

    private void renderSwords(Render3DEvent event, LivingEntity target, float alpha) {
        if (event.consumers == null) return;
        ensureSwordTexture();
        long now = System.currentTimeMillis();
        int count = this.simulateSwords(target, event.tickDelta, now, alpha);
        PoseStack stack = event.matrices;
        if (stack == null) return;
        Vec3 camera = event.camera != null ? event.camera.position() : Vec3.ZERO;

        VertexConsumer glowConsumer = event.consumers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(GLOW_TEXTURE));
        for (int i = 0; i < count; i++) {
            int glowCol = ColorUtil.withAlpha(this.swordColors[i], Math.round(115.0F * alpha));
            this.drawSwordSprite(stack, glowConsumer, this.swordWorld[i], camera, this.swordHalfW * 1.35F, this.swordHalfH * 1.35F, this.swordRolls[i], this.swordPitchs[i], glowCol);
        }

        VertexConsumer swordConsumer = event.consumers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(SWORD_TEXTURE_ID));
        for (int i = 0; i < count; i++) {
            this.drawSwordSprite(stack, swordConsumer, this.swordWorld[i], camera, this.swordHalfW, this.swordHalfH, this.swordRolls[i], this.swordPitchs[i], this.swordColors[i]);
        }
    }

    private void drawSwordSprite(PoseStack stack, VertexConsumer consumer, Vec3 position, Vec3 camera, float halfW, float halfH, float roll, float pitch, int color) {
        Vec3 relative = position.subtract(camera);
        stack.pushPose();
        try {
            stack.translate(relative.x, relative.y, relative.z);
            if (mc.gameRenderer != null && mc.gameRenderer.mainCamera() != null) {
                stack.rotate(mc.gameRenderer.mainCamera().rotation());
            }
            stack.rotate(Axis.ZP.rotationDegrees(roll));
            stack.rotate(Axis.XP.rotationDegrees(pitch));
            stack.scale(halfW, halfH, 1.0F);
            PoseStack.Pose entry = stack.last();

            consumer.addVertex(entry, -1.0F, -1.0F, 0.0F).setColor(color).setUv(0.0F, 1.0F).setOverlay(0).setLight(0xF000F0).setNormal(entry, 0.0f, 1.0f, 0.0f);
            consumer.addVertex(entry, -1.0F, 1.0F, 0.0F).setColor(color).setUv(0.0F, 0.0F).setOverlay(0).setLight(0xF000F0).setNormal(entry, 0.0f, 1.0f, 0.0f);
            consumer.addVertex(entry, 1.0F, 1.0F, 0.0F).setColor(color).setUv(1.0F, 0.0F).setOverlay(0).setLight(0xF000F0).setNormal(entry, 0.0f, 1.0f, 0.0f);
            consumer.addVertex(entry, 1.0F, -1.0F, 0.0F).setColor(color).setUv(1.0F, 1.0F).setOverlay(0).setLight(0xF000F0).setNormal(entry, 0.0f, 1.0f, 0.0f);
        } finally {
            stack.popPose();
        }
    }

    // --- THUNDERHACK MODES (порт ESP из Aura ThunderHack) ---

    /** ThunderHack: три спиральные градиентные ленты вокруг цели. */
    private void renderThunderHack(Render3DEvent event, LivingEntity target, float alpha, long now) {
        if (event.consumers == null || event.matrices == null) return;
        VertexConsumer consumer = event.consumers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(GLOW_TEXTURE));
        PoseStack.Pose entry = event.matrices.last();

        Vec3 base = this.lerped(target, event.tickDelta);
        double height = target.getBbHeight();
        float time = (float) (now - this.epoch) / 5.0F;

        for (int strip = 0; strip < 3; strip++) {
            int phase = strip * 120;
            for (int j = 0; j < 360; j++) {
                double a1 = Math.toRadians(j + phase);
                double a2 = Math.toRadians(j + 1 + phase);
                double h1 = height - j * 0.004;
                double h2 = height - (j + 1) * 0.004;
                float fade = Mth.clamp(1.0F - (((float) j + time) % 360.0F) / 60.0F, 0.0F, 1.0F);
                int col = ColorUtil.multiplyAlpha(thBase(j / 20), fade * alpha);
                float x1 = (float) (base.x + Math.cos(a1) * 0.5);
                float z1 = (float) (base.z + Math.sin(a1) * 0.5);
                float x2 = (float) (base.x + Math.cos(a2) * 0.5);
                float z2 = (float) (base.z + Math.sin(a2) * 0.5);
                float y1 = (float) (base.y + h1);
                float y2 = (float) (base.y + h2 + 0.1);
                consumer.addVertex(entry, x1, y1, z1).setColor(col).setUv(0.5F, 0.5F).setOverlay(0).setLight(0xF000F0).setNormal(entry, 0.0F, 1.0F, 0.0F);
                consumer.addVertex(entry, x2, y2, z2).setColor(col).setUv(0.5F, 0.5F).setOverlay(0).setLight(0xF000F0).setNormal(entry, 0.0F, 1.0F, 0.0F);
            }
        }
    }

    /** ThunderHackV2: рой светлячков по орбитам с параметрами длины/тряски. */
    private void renderThunderHackV2(Render3DEvent event, LivingEntity target, float alpha, long now) {
        if (event.consumers == null || event.matrices == null) return;
        VertexConsumer consumer = event.consumers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(FIREFLY_TEXTURE));
        Vec3 camera = event.camera != null ? event.camera.position() : Vec3.ZERO;
        Vec3 base = this.lerped(target, event.tickDelta);

        int length = Math.max(1, this.espLength.getIntValue());
        float factor = Math.max(1.0F, this.espFactor.getFloatValue());
        float shaking = Math.max(1.0F, this.espShaking.getFloatValue());
        float amplitude = this.espAmplitude.getFloatValue();
        float age = target.tickCount + event.tickDelta;
        float w = target.getBbWidth();

        for (int j = 0; j < 3; j++) {
            for (int i = 0; i <= length; i++) {
                double radians = Math.toRadians((((float) i / 1.5F + age) * factor + (j * 120)) % (factor * 360));
                double sinQuad = Math.sin(Math.toRadians(age * 2.5F + i * (j + 1)) * amplitude) / shaking;
                float offset = (float) i / length;
                Vec3 pos = new Vec3(
                    base.x + Math.cos(radians) * w,
                    base.y + 1 + sinQuad,
                    base.z + Math.sin(radians) * w);
                float scale = Math.max(0.24F * offset, 0.2F);
                int col = ColorUtil.multiplyAlpha(thBase((int) (180 * offset)), offset * alpha);
                this.drawGhostSprite(event.matrices, consumer, pos, camera, scale, scale, 0.0F, col);
            }
        }
    }

    /** NurikZapen (CaptureMark): вращающаяся метка-захват на цели. */
    private void renderNurikZapen(Render3DEvent event, LivingEntity target, float alpha) {
        if (event.consumers == null || event.matrices == null) return;
        VertexConsumer consumer = event.consumers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(CAPTURE_TEXTURE));
        Vec3 camera = event.camera != null ? event.camera.position() : Vec3.ZERO;
        Vec3 base = this.lerped(target, event.tickDelta);

        float roll = this.markPrev + (this.markValue - this.markPrev) * event.tickDelta;
        Vec3 pos = new Vec3(base.x, base.y + target.getBbHeight() * 0.5, base.z);
        int col = ColorUtil.multiplyAlpha(thBase((int) roll), alpha);
        this.drawGhostSprite(event.matrices, consumer, pos, camera, 1.5F, 1.5F, roll, col);
    }

    /** CelkaPasta: старое градиентное кольцо, качающееся по высоте. */
    private void renderCelkaPasta(Render3DEvent event, LivingEntity target, float alpha, long now) {
        if (event.consumers == null || event.matrices == null) return;
        VertexConsumer consumer = event.consumers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(GLOW_TEXTURE));
        PoseStack.Pose entry = event.matrices.last();

        float t = (float) (now - this.epoch) / 1000.0F;
        float bob = Math.abs(1.0F + (float) Math.sin(t * 2.0)) / 2.0F;
        Vec3 base = this.lerped(target, event.tickDelta);
        double y = base.y + bob * target.getBbHeight();
        float r = target.getBbWidth() * 0.8F;

        for (int i = 0; i <= 30; i++) {
            double a1 = i * Math.PI * 2 / 30;
            double a2 = (i + 1) * Math.PI * 2 / 30;
            int top = ColorUtil.multiplyAlpha(thBase(i), 0.0F);
            int bottom = ColorUtil.multiplyAlpha(thBase(i), alpha * 0.67F);
            consumer.addVertex(entry, (float) (base.x + Math.cos(a1) * r), (float) y, (float) (base.z + Math.sin(a1) * r)).setColor(top).setUv(0.5F, 0.5F).setOverlay(0).setLight(0xF000F0).setNormal(entry, 0.0F, 1.0F, 0.0F);
            consumer.addVertex(entry, (float) (base.x + Math.cos(a2) * r), (float) (base.y), (float) (base.z + Math.sin(a2) * r)).setColor(bottom).setUv(0.5F, 0.5F).setOverlay(0).setLight(0xF000F0).setNormal(entry, 0.0F, 1.0F, 0.0F);
        }
    }

    // --- CONSTELLATION RENDERING ---
    private Vec3[] constellationPoints(LivingEntity target, float tickDelta, float time, int stars) {
        Vec3[] points = new Vec3[stars];
        Vec3 base = this.lerped(target, tickDelta);
        float tau = (float) (Math.PI * 2.0);
        float spin = time * 1.2F;
        float orbit = 1.2F * target.getBbWidth();
        float halfHeight = target.getBbHeight() * 0.5F;

        for (int s = 0; s < stars; s++) {
            float frac = (float) s / stars;
            float foundation = frac * tau;
            float incl = (float) (Math.PI * 0.35F * Math.sin(frac * (float) Math.PI * 1.5F + 0.5F));
            float ang = spin + foundation;
            float r = orbit * (0.85F + 0.15F * (float) Math.sin(foundation * 2.3F));
            points[s] = new Vec3(
                base.x + Math.cos(ang) * r,
                base.y + halfHeight + Math.sin(ang) * Math.sin(incl) * r * 0.7F + Math.sin(time * 1.2F + frac * tau) * 0.2F,
                base.z + Math.sin(ang) * Math.cos(incl) * r
            );
        }
        return points;
    }

    private int constellationColor(float frac, float time, int[] palette) {
        float tau = (float) (Math.PI * 2.0);
        float wave = 0.5F + 0.5F * (float) Math.sin(frac * tau * 2.0F + time * 1.4F);
        return ColorUtil.interpolate(palette[0], palette[1], wave);
    }

    private void renderConstellation(Render3DEvent event, LivingEntity target, float alpha, long now) {
        if (event.consumers == null) return;
        int stars = 8;
        float time = (float) (now - this.epoch) / 1000.0F;
        Vec3[] points = this.constellationPoints(target, event.tickDelta, time, stars);
        int[] palette = this.colors(alpha);
        PoseStack stack = event.matrices;
        if (stack == null) return;
        Vec3 camera = event.camera != null ? event.camera.position() : Vec3.ZERO;
        VertexConsumer sprites = event.consumers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(STAR_TEXTURE));
        VertexConsumer glowSprites = event.consumers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(GLOW_TEXTURE));

        for (int s = 0; s < stars; s++) {
            float frac = (float) s / stars;
            int color = this.constellationColor(frac, time, palette);
            float pulse = 0.75F + 0.25F * Mth.sin(time * 3.0F + frac * (float) (Math.PI * 2.0) * 1.7F);
            float size = 0.27F * pulse * 1.4F * (1.0F + this.hurtProgress * 0.2F);
            float rotation = time * 40.0F + s * 12.0F;
            this.drawGhostSprite(stack, sprites, points[s], camera, size, size, rotation, color);
            this.drawGhostSprite(stack, glowSprites, points[s], camera, size * 3.2F, size * 3.2F, -rotation * 0.6F, ColorUtil.multiplyAlpha(color, 0.5F * 0.3F));
        }
    }

    // --- TEST RENDERING ---
    private void renderTest(Render3DEvent event, LivingEntity target, float alpha) {
        if (event.consumers == null) return;
        float tickDelta = event.tickDelta;
        Vec3 base = this.lerped(target, tickDelta);
        float entityHeight = target.getBbHeight();
        float radius = 0.725F;
        float userSize = 1.0F;
        float time = (float) (System.currentTimeMillis() - this.epoch) / 1000.0F;
        float rotSpeed = time * 9.0F;
        float vertSpeed = time * 3.25F;
        int[] palette = this.colors(alpha);
        int theme = ColorUtil.interpolate(palette[0], palette[1], 0.5F);
        PoseStack stack = event.matrices;
        if (stack == null) return;
        Vec3 camera = event.camera != null ? event.camera.position() : Vec3.ZERO;
        VertexConsumer consumer = event.consumers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(GLOW_TEXTURE));

        for (int i = 0; i < 3; i++) {
            float angleOffset = i * (SWORDS_TAU / 3.0F);
            for (int j = 0; j < 10; j++) {
                float trailDelay = j * 0.05F;
                float currentAngle = rotSpeed + angleOffset - j * 0.1F;
                double posX = Math.cos(currentAngle) * radius;
                double posZ = Math.sin(currentAngle) * radius;
                double verticalOscillation = (Math.sin(vertSpeed - trailDelay) * 0.5 + 0.5) * (entityHeight * 0.975);
                float size = (0.30F - j * 0.015F) * userSize * alpha;
                if (size < 0.03F) size = 0.03F;
                float tailFade = 1.35F - j / 13.5F;
                int color = ColorUtil.multiplyAlpha(theme, Math.min(1.0F, tailFade) * alpha);
                this.drawGhostSprite(
                    stack, consumer, new Vec3(base.x + posX, base.y + verticalOscillation, base.z + posZ), camera, size, size, 0.0F, color
                );
            }
        }
    }

    // --- LIGHTNING 2D OVERLAY RENDERING ---
    private void resetLightning() {
        this.boltTarget = null;
        this.bolts.clear();
        this.nextBoltAt = 0L;
    }

    private void simulateLightning(LivingEntity target, float tickDelta, long now) {
        int count = 3;
        if (this.boltTarget != target) {
            this.boltTarget = target;
            this.bolts.clear();
            this.nextBoltAt = 0L;
        }
        this.bolts.removeIf(bolt -> now - bolt.born > bolt.life);
        while (this.bolts.size() < count && now >= this.nextBoltAt) {
            this.bolts.add(this.makeBolt(target, tickDelta));
            this.nextBoltAt = now + (long) ((80.0 + Math.random() * 140.0));
        }
        while (this.bolts.size() > count) {
            this.bolts.remove(0);
        }
    }

    private Bolt makeBolt(LivingEntity target, float tickDelta) {
        float size = 1.0F;
        float len = 3.0F;
        Vec3 base = this.lerped(target, tickDelta);
        double topX = base.x + (Math.random() - 0.5) * 2.0 * size;
        double topY = base.y + target.getBbHeight() + len * (0.7 + Math.random() * 0.6);
        double topZ = base.z + (Math.random() - 0.5) * 2.0 * size;
        double botX = base.x + (Math.random() - 0.5) * 0.6 * size;
        double botY = base.y + target.getBbHeight() * 0.45;
        double botZ = base.z + (Math.random() - 0.5) * 0.6 * size;
        int segs = 9;
        Vec3[] pts = new Vec3[segs + 1];
        for (int i = 0; i <= segs; i++) {
            double t = (double) i / segs;
            double ox = i != 0 && i != segs ? (Math.random() - 0.5) * 0.7 * size : 0.0;
            double oz = i != 0 && i != segs ? (Math.random() - 0.5) * 0.7 * size : 0.0;
            pts[i] = new Vec3(Mth.lerp(t, topX, botX) + ox, Mth.lerp(t, topY, botY), Mth.lerp(t, topZ, botZ) + oz);
        }
        Vec3[] branch = null;
        if (Math.random() < 0.7) {
            Vec3 from = pts[3 + (int) (Math.random() * 3)];
            double dirX = Math.random() - 0.5;
            double dirZ = Math.random() - 0.5;
            double dirLen = Math.max(0.2, Math.hypot(dirX, dirZ));
            branch = new Vec3[4];
            branch[0] = from;
            for (int i = 1; i < 4; i++) {
                branch[i] = new Vec3(
                    from.x + dirX / dirLen * 0.35 * size * i,
                    from.y - 0.3 * size * i,
                    from.z + dirZ / dirLen * 0.35 * size * i
                );
            }
        }
        return new Bolt(pts, branch, System.currentTimeMillis(), (long) (120.0 + Math.random() * 140.0));
    }

    private void renderLightningOverlay(GuiGraphicsExtractor context, LivingEntity target, float alpha, float tickDelta, int width, int height) {
        long now = System.currentTimeMillis();
        this.simulateLightning(target, tickDelta, now);
        if (this.bolts.isEmpty()) return;

        int[] palette = this.colors(alpha);
        int theme = ColorUtil.interpolate(palette[0], palette[1], 0.5F);
        int white = ColorUtil.rgb(235, 242, 255);
        Vec3 cameraPos = mc.gameRenderer != null && mc.gameRenderer.mainCamera() != null ? mc.gameRenderer.mainCamera().position() : Vec3.ZERO;

        for (Bolt bolt : this.bolts) {
            float age = Mth.clamp((float) (now - bolt.born) / Math.max(1L, bolt.life), 0.0F, 1.0F);
            float a = (float) Math.sin(age * Math.PI) * alpha;
            if (a <= 0.01F) continue;

            for (int i = 0; i + 1 < bolt.points.length; i++) {
                Vec3 w1 = bolt.points[i];
                Vec3 w2 = bolt.points[i + 1];
                Vector2f p1 = RenderUtil3D.worldToScreen(w1.x, w1.y, w1.z, width, height);
                Vector2f p2 = RenderUtil3D.worldToScreen(w2.x, w2.y, w2.z, width, height);
                if (p1 != null && p2 != null) {
                    RenderUtil.drawCircle(context, p1.x - 1f, p1.y - 1f, 2f, ColorUtil.multiplyAlpha(theme, a));
                    context.fill((int) Math.min(p1.x, p2.x), (int) Math.min(p1.y, p2.y), (int) Math.max(p1.x, p2.x) + 1, (int) Math.max(p1.y, p2.y) + 1, ColorUtil.multiplyAlpha(white, a));
                }
            }

            Vec3 bottom = bolt.points[bolt.points.length - 1];
            Vector2f point = RenderUtil3D.worldToScreen(bottom.x, bottom.y, bottom.z, width, height);
            if (point != null) {
                double dist = Math.max(4.0, Math.sqrt(cameraPos.distanceToSqr(bottom.x, bottom.y, bottom.z)));
                float px = Mth.clamp((float) (0.55F * 320.0F / dist), 3.0F, 22.0F);
                RenderUtil.drawGlow(
                    context, point.x - px * 0.775F, point.y - px * 0.775F, px * 1.55F, px * 1.55F, px * 0.77F,
                    ColorUtil.multiplyAlpha(white, a), 1.0F, px * 1.5F
                );
            }
        }
    }

    // --- UTILITIES ---
    private Vec3 lerp(Vec3 from, Vec3 to, double delta) {
        return new Vec3(Mth.lerp(delta, from.x, to.x), Mth.lerp(delta, from.y, to.y), Mth.lerp(delta, from.z, to.z));
    }

    private boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private void resetGhostMotion() {
        this.ghostAnchor = null;
        this.ghostVelocity = Vec3.ZERO;
        this.ghostHistoryCursor = -1;
        this.ghostHistoryCount = 0;
        this.ghostTrackedTarget = null;
        this.ghostFlightFrom = null;
        this.ghostFlightSide = Vec3.ZERO;
        this.ghostFlightStartedAt = 0L;
        this.ghostFlightDurationMs = 0L;
        this.ghostFlightSideAmount = 0.0F;
        this.ghostFlightArcHeight = 0.0F;
        this.ghostSwitching = false;
        this.ghostFlightStrength = 0.0F;
    }

    private int[] colors(float alpha) {
        int first;
        int second;
        if (customColors.getValue()) {
            first = colorOne.getValue().getRGB();
            second = colorTwo.getValue().getRGB();
        } else {
            first = Colors.INSTANCE != null ? Colors.INSTANCE.getThemeColor().getRGB() : 0xFFBD99FF;
            second = Colors.INSTANCE != null ? Colors.INSTANCE.getEnemyColor().getRGB() : 0xFFFF5555;
        }
        if (this.hurtProgress > 0.0F) {
            first = ColorUtil.interpolate(first, 0xFFFF3333, this.hurtProgress);
            second = ColorUtil.interpolate(second, 0xFFFF3333, this.hurtProgress);
        }

        int a = Mth.clamp((int) (255.0F * alpha), 0, 255);
        return new int[]{ColorUtil.withAlpha(first, a), ColorUtil.withAlpha(second, a)};
    }

    /** Базовый цвет для TH-режимов: радуга или градиент своих цветов. */
    private int thBase(int step) {
        if (customColors.getValue()) {
            float f = (Math.abs(step) % 40) / 40.0F;
            Color a = colorOne.getValue();
            Color b = colorTwo.getValue();
            return ColorUtil.interpolate(a.getRGB(), b.getRGB(), f);
        }
        return ColorUtil.rainbow(step, 0.9F, 1.0F).getRGB();
    }

    private Vec3 lerped(LivingEntity entity, float delta) {
        return entity.getPosition(delta);
    }

    private float easeOutBack(float x) {
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        return 1.0F + c3 * (float) Math.pow(x - 1.0F, 3.0) + c1 * (float) Math.pow(x - 1.0F, 2.0);
    }

    private float smoothStep(float x) {
        return x * x * (3.0F - 2.0F * x);
    }

    private float smootherStep(float x) {
        return x * x * x * (x * (x * 6.0F - 15.0F) + 10.0F);
    }

    private static final class SwordState {
        private double x;
        private double y;
        private double z;
        private double vx;
        private double vy;
        private double vz;
        private float roll;
        private float pitch;
        private boolean initialized;
    }

    private static final class Bolt {
        private final Vec3[] points;
        private final Vec3[] branch;
        private final long born;
        private final long life;

        private Bolt(Vec3[] points, Vec3[] branch, long born, long life) {
            this.points = points;
            this.branch = branch;
            this.born = born;
            this.life = life;
        }
    }

    private static final class BloomSeed {
        private final float heightBase;
        private final float radiusFactor;
        private final float angleOffset;
        private final float orbitSpeed;
        private final float verticalSpeed;
        private final float pulseSpeed;
        private final float pulsePhase;
        private final float spinSpeed;
        private final float spinSeed;
        private final float sizeFactor;
        private final float radialFreq;
        private final float radialAmp;

        private BloomSeed(
            float heightBase,
            float radiusFactor,
            float angleOffset,
            float orbitSpeed,
            float verticalSpeed,
            float pulseSpeed,
            float pulsePhase,
            float spinSpeed,
            float spinSeed,
            float sizeFactor,
            float radialFreq,
            float radialAmp
        ) {
            this.heightBase = heightBase;
            this.radiusFactor = radiusFactor;
            this.angleOffset = angleOffset;
            this.orbitSpeed = orbitSpeed;
            this.verticalSpeed = verticalSpeed;
            this.pulseSpeed = pulseSpeed;
            this.pulsePhase = pulsePhase;
            this.spinSpeed = spinSpeed;
            this.spinSeed = spinSeed;
            this.sizeFactor = sizeFactor;
            this.radialFreq = radialFreq;
            this.radialAmp = radialAmp;
        }
    }
}
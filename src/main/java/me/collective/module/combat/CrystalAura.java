package me.collective.module.combat;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.Render3DEvent;
import me.collective.event.events.TickEvent;
import me.collective.manager.FriendManager;
import me.collective.module.Module;
import me.collective.render.Renderer3D;
import me.collective.render.ShapeMode;
import me.collective.setting.BooleanSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.ExplosionDamageUtil;
import me.collective.util.InventoryUtil;
import me.collective.util.RotationUtils;
import me.collective.util.SwapManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CrystalAura — автоматическая расстановка и подрыв кристаллов Энда.
 *
 * <p>Порядок работы тика: в {@code TickEvent.Pre} модуль ищет выгоднейшее
 * действие и наводится на него, в {@code TickEvent.Post} выполняет действие
 * тем углом, который реально ушёл серверу.
 *
 * <p>Потоки и коллекции:
 * <ul>
 *   <li>{@code visuals} читается и пишется из рендер-треда — только он
 *       потокобезопасен;</li>
 *   <li>{@code damageCache}, {@code placedPositions}, {@code attackedCrystals}
 *       живут исключительно в тик-треде (pre/post), поэтому обычные
 *       {@link HashMap} — это осознанный выбор, а не недосмотр.</li>
 * </ul>
 */
public class CrystalAura extends Module {

    public static CrystalAura INSTANCE;

    private static final long VISUAL_MS        = 300L;
    private static final long ATTACKED_TTL_MS  = 1500L;
    private static final long PLACED_TTL_MS    = 400L;
    private static final long PLACED_BLOCK_MS  = 250L;
    private static final int  SLOT_RETURN_TICKS = 3;
    private static final int  MAX_TARGET_HOLD_TICKS = 8;
    private static final long DEBUG_COOLDOWN_MS     = 2000L;

    /** Приоритет захвата ротации — выше любых боевых модулей, чтобы их не сбивал. */
    private static final int ROTATION_PRIORITY = SwapManager.Priority.PEARL;

    public enum Preset     { Default, Donut }
    public enum RotateMode { ReallyWorld, Smooth, Vanilla, None }

    public enum PlaceResult {
        /** Поставить обсидиан под кристалл. */
        PlaceObsidian,
        /** Поставить кристалл на существующий обсидиан/бедрок. */
        PlaceCrystal,
        /** Подорвать уже существующий кристалл. */
        Explode,
        /** Сломать блок под кристаллом. */
        Break,
        /** Дроп мешает — ждём, действие не выполняем. */
        Awaiting
    }

    private enum Blocking { None, Item, Player }

    // ─────────────────────────────────────────────────────────────
    // Настройки
    // ─────────────────────────────────────────────────────────────

    public final EnumSetting<Preset> preset = add(new EnumSetting<>("Режим", Preset.Default));
    public final EnumSetting<RotateMode> rotate = add(new EnumSetting<>("Ротация", RotateMode.ReallyWorld));

    public final NumberSetting smoothSpeed = add(new NumberSetting(
            "Скорость ротации", 0.5, 0.1, 1.0, 0.05,
            ignored -> this.rotate.getValue() == RotateMode.Smooth));

    public final BooleanSetting autoKrot = add(new BooleanSetting(
            "Делать подкоп", true,
            ignored -> this.preset.getValue() == Preset.Default));

    public final NumberSetting explosionDelay = add(new NumberSetting("Задержка", 0.0, 0.0, 500.0, 10.0));
    public final BooleanSetting render = add(new BooleanSetting("Рендер", true));
    public final BooleanSetting predict = add(new BooleanSetting("Предсказание", false));

    public final NumberSetting targetRange = add(new NumberSetting("Дальность цели", 8.0, 2.0, 16.0, 0.5));
    public final NumberSetting maxSelfDamage = add(new NumberSetting("Макс. самоурон", 30.0, 0.0, 60.0, 1.0));

    /** 0 — брать дальность из атрибута игрока (4.5 в ванилле). */
    public final NumberSetting placeReach = add(new NumberSetting("Дальность блока", 0.0, 0.0, 6.0, 0.1));
    /** 0 — брать дальность из атрибута игрока (3.0 в ванилле). */
    public final NumberSetting hitReach = add(new NumberSetting("Дальность сущности", 0.0, 0.0, 6.0, 0.1));

    /**
     * Коррекция движения при тихой ротации.
     *
     * <p>{@code true} — клиент считает вектор движения по углу, ушедшему
     * серверу, ввод не перемапливается: совпадение с серверным предиктом
     * точное. {@code false} — старое поведение, две коррекции складываются
     * и дают уход до 45° (Strafe на GrimAC).
     */
    public final BooleanSetting syncMovement = add(new BooleanSetting("SyncMovement", true));

    /** Пишет в чат, почему модуль не сработал. Не чаще раза в 2 секунды. */
    public final BooleanSetting debug = add(new BooleanSetting("Отладка", false));

    // ─────────────────────────────────────────────────────────────
    // Состояние
    // ─────────────────────────────────────────────────────────────

    private int ticksToBack;
    private int lastSlot = -1;
    private long lastExplosionAt;

    /** Текущее решение — для внешних потребителей (ESP, другие модули). */
    private PlaceData lastData;

    /**
     * Снимок решения, принятого в pre-тике.
     *
     * <p>Post-тик работает только с ним и немедленно сбрасывает. Поэтому
     * перезапись {@link #lastData} между фазами не может увести действие по
     * устаревшим данным — раньше post читал то же поле, что и pre.
     */
    private PendingAction pending;

    private float[] rotation;

    /** Угол, который реально ушёл в серверные пакеты в этом тике. */
    private float appliedYaw;
    private float appliedPitch;
    private boolean rotationApplied;

    private boolean miningThisTick;
    private boolean acted;

    // Тик-тред. Намеренно обычные коллекции — см. javadoc класса.
    private final Map<DamageKey, Float> damageCache       = new HashMap<>();
    private final Map<BlockPos, Long> placedPositions     = new HashMap<>();
    private final Map<Integer, Long> attackedCrystals     = new HashMap<>();

    // Рендер-тред.
    private final Deque<VisualData> visuals = new ArrayDeque<>();
    private final Map<BlockPos, Boolean> visualSeen = new ConcurrentHashMap<>();

    private int targetHoldTicks;

    private float smoothedYaw;
    private float smoothedPitch;
    private float noiseYaw;
    private float noisePitch;
    private boolean rotationInitialized;

    public CrystalAura() {
        super("CrystalAura", "Автоматически ставит и взрывает кристаллы Энда", Category.COMBAT);
        INSTANCE = this;
    }

    // ─────────────────────────────────────────────────────────────
    // Публичный доступ — по одному геттеру на значение
    // ─────────────────────────────────────────────────────────────

    public boolean isMiningThisTick() {
        return isEnabled() && this.miningThisTick;
    }

    public boolean holdsSlot() {
        return isEnabled() && this.lastSlot != -1;
    }

    public boolean usesAnchors() {
        return this.preset.getValue() == Preset.Donut;
    }

    public PlaceData getLastData() {
        return this.lastData;
    }

    public float[] getRotation() {
        return this.rotation;
    }

    public boolean actedThisTick() {
        return isEnabled() && this.acted;
    }

    private boolean diggingAllowed() {
        return this.preset.getValue() == Preset.Default && this.autoKrot.getValue();
    }

    @Override
    public void onDisable() {
        this.ticksToBack       = 0;
        this.rotation          = null;
        this.lastData          = null;
        this.pending           = null;
        this.rotationInitialized = false;
        this.targetHoldTicks   = 0;
        this.noiseYaw          = 0.0F;
        this.noisePitch        = 0.0F;
        this.miningThisTick    = false;
        this.acted             = false;
        this.rotationApplied   = false;
        this.lastExplosionAt   = 0L;

        this.visuals.clear();
        this.visualSeen.clear();
        this.placedPositions.clear();
        this.attackedCrystals.clear();

        RotationUtils.getInstance().release(this);

        if (this.lastSlot != -1 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(this.lastSlot);
            InventoryManagerBridge.syncSlot(this.lastSlot);
        }
        this.lastSlot = -1;
    }

    // ─────────────────────────────────────────────────────────────
    // Тик
    // ─────────────────────────────────────────────────────────────

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        this.miningThisTick  = false;
        this.acted           = false;
        this.rotationApplied = false;
        this.damageCache.clear();

        ClientPlayerEntity self = mc.player;
        ClientWorld world       = mc.world;

        if (self == null || world == null || mc.interactionManager == null) {
            finishAction();
            return;
        }
        if (self.isUsingItem()) {
            holdPosition();
            return;
        }

        long now = System.currentTimeMillis();
        this.attackedCrystals.entrySet().removeIf(e -> now - e.getValue() > ATTACKED_TTL_MS);
        this.placedPositions.entrySet().removeIf(e -> now - e.getValue() > PLACED_TTL_MS);

        returnSlotIfDue(self);

        PlayerEntity target = findTarget(self, world);
        if (target == null) {
            report("нет цели в " + this.targetRange.getValue() + " блоках");
            finishAction();
            return;
        }

        // Кристаллы нужны только для взрыва и для установки кристалла.
        // Обсидиан ставится без них — иначе на серверах без кристаллов
        // (2b2t, анархия) модуль не делает вообще ничего.
        boolean canExplode = hasCrystals(self);
        boolean canPlaceObsidian = hasObsidian(self);

        if (!canExplode && !canPlaceObsidian) {
            report("в инвентаре нет ни кристаллов, ни обсидиана");
            finishAction();
            return;
        }
        if (!canExplode) report("нет кристаллов — взрыв и установка кристалла отключены");

        List<Entity> entities = collectEntities(self, world);

        PlaceData explosionData = canExplode
                ? findBestExplosion(target, world, self, entities)
                : PlaceData.empty(PlaceResult.Explode);
        PlaceData obsidianData  = canPlaceObsidian
                ? findBestObsidian(target, world, self, entities)
                : PlaceData.empty(PlaceResult.PlaceObsidian);
        PlaceData crystalData  = canExplode
                ? findBestCrystal(target, world, self, entities)
                : PlaceData.empty(PlaceResult.PlaceCrystal);

        PlaceData finalData = explosionData;
        if (crystalData.damage() > finalData.damage())  finalData = crystalData;
        if (obsidianData.damage() > finalData.damage()) finalData = obsidianData;

        if (diggingAllowed()) {
            PlaceData breakData = findBestBreak(target, world, self, entities);
            if (breakData.damage() > finalData.damage()) finalData = breakData;
        }

        // AWAITING — это пауза, а не действие, иdamage у неё нулевой.
        // Раньше она затирала любой вариант безусловно, из-за чего удачный
        // взрыв терялся из-за брошенного дропа. Теперь она побеждает
        // только когда другого варианта вообще нет.
        if (finalData.placeResult() == PlaceResult.Awaiting
                && obsidianData.placeResult() == PlaceResult.Awaiting) {
            finalData = obsidianData;
        }

        boolean keepTarget = shouldKeepTarget(target, world, self, entities);

        if (keepTarget) {
            this.targetHoldTicks++;
        } else {
            this.targetHoldTicks = 0;
            this.lastData = finalData;
        }

        if (this.lastData == null) {
            finishAction();
            return;
        }
        if (this.lastData.placeResult() == PlaceResult.Awaiting) {
            // Дроп в зоне установки — действие не выполняем. Ротацию тоже
            // снимаем: удерживать её без реальной цели бессмысленно, камера
            // всё равно не должна дёргаться.
            holdPosition();
            return;
        }
        if (this.lastData.damage() <= 0.0D) {
            report(String.format("не нашёл выгодной позиции (взрыв %.1f / кристалл %.1f / обсидиан %.1f)",
                    explosionData.damage(), crystalData.damage(), obsidianData.damage()));
            finishAction();
            return;
        }

        this.acted = true;

        Vec3d targetPoint = resolveAimPoint(world, self);
        if (targetPoint == null) {
            report("цель наведения пропала");
            finishAction();
            return;
        }
        applyRotation(calculate(targetPoint, self));

        // Снимок для post-тика — последнее действие pre-фазы.
        this.pending = buildPending(this.lastData, targetPoint);
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        PendingAction action = this.pending;
        this.pending = null;

        if (action == null) return;

        ClientPlayerEntity self = mc.player;
        ClientWorld world       = mc.world;
        if (self == null || world == null || mc.interactionManager == null) return;

        if (action.kind() == PlaceResult.Explode) explodeCrystal(self, action);
        else                                    interactWithBlock(self, world, action);
    }

    // ─────────────────────────────────────────────────────────────
    // Сборка снимка
    // ─────────────────────────────────────────────────────────────

    private PendingAction buildPending(PlaceData data, Vec3d targetPoint) {
        if (data.placeResult() == PlaceResult.Explode) {
            Entity crystal = data.entity();
            if (crystal == null) return null;
            return new PendingAction(PlaceResult.Explode, null, null,
                    crystal.getId(), this.appliedYaw, this.appliedPitch);
        }

        BlockPos pos  = data.blockPos();
        Direction face = data.face();
        if (pos == null || face == null) return null;

        return new PendingAction(data.placeResult(), pos.toImmutable(), face,
                -1, this.appliedYaw, this.appliedPitch);
    }

    private Vec3d resolveAimPoint(ClientWorld world, ClientPlayerEntity self) {
        if (this.lastData.placeResult() == PlaceResult.Explode) {
            Entity crystal = this.lastData.entity();
            if (crystal != null && crystal.isAlive() && !crystal.isRemoved()) {
                BlockPos pos  = this.lastData.blockPos();
                Direction face = this.lastData.face();
                return (pos != null && face != null) ? getFacePoint(pos, face) : nearestPoint(crystal, 0.0D, 0.05D);
            }
            return null;
        }

        BlockPos pos  = this.lastData.blockPos();
        Direction face = this.lastData.face();
        return (pos != null && face != null) ? getFacePoint(pos, face) : null;
    }

    private boolean shouldKeepTarget(PlayerEntity target, ClientWorld world,
                                     ClientPlayerEntity self, List<Entity> entities) {
        if (this.lastData == null || this.targetHoldTicks >= MAX_TARGET_HOLD_TICKS) return false;

        if (this.lastData.placeResult() == PlaceResult.Explode) {
            Entity c = this.lastData.entity();
            if (c instanceof EndCrystalEntity crystal && crystal.isAlive() && !c.isRemoved()
                    && !this.attackedCrystals.containsKey(crystal.getId())
                    && self.getEyePos().squaredDistanceTo(nearestPoint(crystal, 0.0D, 0.05D))
                        <= hitReach() * hitReach()) {
                return true;
            }
        } else if (this.lastData.placeResult() == PlaceResult.PlaceCrystal) {
            BlockPos bp = this.lastData.blockPos();
            if (bp != null
                    && world.getBlockState(bp).isOf(Blocks.OBSIDIAN)
                    && world.getBlockState(bp.up()).isAir()
                    && !blockedForCrystal(entities, bp.up())) {
                return true;
            }
        }

        return false;
    }

    // ─────────────────────────────────────────────────────────────
    // Действия
    // ─────────────────────────────────────────────────────────────

    private void explodeCrystal(ClientPlayerEntity self, PendingAction action) {
        Entity crystal = entityById(action.id());

        // Кристалл не удаляем локально. Раньше здесь стоял discard() сразу
        // после отправки пакета: при потере пакета кристал исчезал у нас,
        // но оставался на сервере, и весь подсчёт ворот вставал на паузу.
        // Сервер пришлёт удаление сам, а повторный удар за это время
        // отсекает attackedCrystals.
        if (!(crystal instanceof EndCrystalEntity end) || !end.isAlive() || end.isRemoved()) {
            this.lastData = null;
            this.targetHoldTicks = 0;
            return;
        }

        boolean lookingAt = isInstantRotation()
                || rayTrace(action.yaw(), action.pitch(), hitReach(), end.getBoundingBox(), self);

        if (!lookingAt) {
            report("луч не попал в кристалл (yaw " + fmt(action.yaw()) + ")");
            return;
        }

        long now = System.currentTimeMillis();
        if (now - this.lastExplosionAt < this.explosionDelay.getValue().longValue()) return;

        if (this.attackedCrystals.put(end.getId(), now) == null && mc.interactionManager != null) {
            BlockPos base = end.getBlockPos().down();
            this.placedPositions.remove(base);
            this.placedPositions.remove(end.getBlockPos());

            mc.interactionManager.attackEntity(self, end);
            self.swingHand(Hand.MAIN_HAND);
            this.lastExplosionAt = now;

            addVisual(end.getBlockPos().up(), 0.2F, 0.8F, 1.0F);

            this.lastData = null;
            this.targetHoldTicks = 0;
        }
    }

    private void interactWithBlock(ClientPlayerEntity self, ClientWorld world, PendingAction action) {
        BlockPos pos  = action.pos();
        Direction face = action.face();
        if (pos == null || face == null) {
            finishAction();
            return;
        }

        double reach = blockReach();

        // Угол берём из снимка — тот, что реально ушёл серверу в этом тике,
        // а не из RotationContext: при плавной ротации там может лежать
        // промежуточное значение, и клиент с сервером разойдутся на
        // полградуса-полтора.
        BlockHitResult hit = raycastBlock(action.yaw(), action.pitch(), pos, reach, self);

        boolean lookingAt;
        if (isInstantRotation()) {
            lookingAt = true;
            if (hit == null) hit = new BlockHitResult(getFacePoint(pos, face), face, pos, false);
        } else {
            lookingAt = hit != null;
        }

        if (!lookingAt || hit == null) {
            report("луч не попал в " + pos.toShortString()
                    + " (yaw " + fmt(action.yaw()) + ", pitch " + fmt(action.pitch()) + ")");
            return;
        }

        if (action.kind() == PlaceResult.Break) breakBlock(self, world, pos, face);
        else                               placeBlock(self, pos, face, hit,
                                                     action.kind() == PlaceResult.PlaceCrystal);

        this.lastData = null;
        this.targetHoldTicks = 0;
    }

    private void breakBlock(ClientPlayerEntity self, ClientWorld world, BlockPos pos, Direction face) {
        BlockState state = world.getBlockState(pos);

        if (state.isAir()) {
            if (mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
            finishAction();
            return;
        }

        int tool = findBestHotbarTool(self, state);
        if (tool == -1) {
            report("нечем ломать " + pos.toShortString());
            finishAction();
            return;
        }
        if (!holdSlot(self, tool)) return;

        this.miningThisTick = true;
        if (mc.interactionManager != null) {
            mc.interactionManager.attackBlock(pos, face);
            self.swingHand(Hand.MAIN_HAND);
            if (!world.getBlockState(pos).isAir()
                    && mc.interactionManager.updateBlockBreakingProgress(pos, face)) {
                self.swingHand(Hand.MAIN_HAND);
            }
        }
        addVisual(pos, 0.8F, 0.1F, 0.1F);
    }

    private void placeBlock(ClientPlayerEntity self, BlockPos pos, Direction face,
                            BlockHitResult hit, boolean crystal) {
        Item item = crystal ? Items.END_CRYSTAL : Items.OBSIDIAN;
        Hand hand = handFor(self, item);

        if (hand == null) {
            report("нет " + item.getName().getString() + " для установки");
            return;
        }

        if (hand == Hand.MAIN_HAND) {
            int slot = InventoryUtil.findInHotbar(item).slot();
            if (slot == -1) return;
            if (!holdSlot(self, slot)) return;
        }

        if (mc.interactionManager != null) {
            mc.interactionManager.interactBlock(self, hand, hit);
            self.swingHand(hand);

            if (crystal) {
                long now = System.currentTimeMillis();
                this.placedPositions.put(pos.toImmutable(), now);
                this.placedPositions.put(pos.up().toImmutable(), now);
            }
            if (this.predict.getValue() && !crystal) {
                // Кристалл — сущность, а не блок: его установку предсказать нечем.
                // Обсидиан можно поставить в мир локально, чтобы рендер не мигал.
                if (mc.world != null) mc.world.setBlockState(pos, Blocks.OBSIDIAN.getDefaultState());
            }
        }

        BlockPos visual = crystal ? pos.up() : pos.offset(face);
        addVisual(visual, 0.2F, 0.8F, 1.0F);
    }

    private Hand handFor(ClientPlayerEntity self, Item item) {
        if (self.getOffHandStack().isOf(item))  return Hand.OFF_HAND;
        if (self.getMainHandStack().isOf(item)) return Hand.MAIN_HAND;
        return InventoryUtil.findInHotbar(item).found() ? Hand.MAIN_HAND : null;
    }

    /**
     * Удержание слота через SwapManager — он же вернёт слот и снимет
     * конфликт с другими модулями. Приоритет CRYSTAL: ниже PLACE и PEARL,
     * но выше MINING, чтобы подкоп не перебивался ставкой кристалла.
     */
    private boolean holdSlot(ClientPlayerEntity self, int slot) {
        if (self.getInventory().getSelectedSlot() != slot) {
            this.lastSlot = self.getInventory().getSelectedSlot();
            this.ticksToBack = SLOT_RETURN_TICKS;
        }
        return SwapManager.getInstance().hold(this, slot, SwapManager.Priority.CRYSTAL, SLOT_RETURN_TICKS);
    }

    private void returnSlotIfDue(ClientPlayerEntity self) {
        if (this.ticksToBack > 0) {
            this.ticksToBack--;
            return;
        }
        if (this.lastSlot == -1) return;

        self.getInventory().setSelectedSlot(this.lastSlot);
        InventoryManagerBridge.syncSlot(this.lastSlot);
        this.lastSlot = -1;
    }

    // ─────────────────────────────────────────────────────────────
    // Ротация
    // ─────────────────────────────────────────────────────────────

    private boolean isInstantRotation() {
        RotateMode mode = this.rotate.getValue();
        return mode == RotateMode.Vanilla || mode == RotateMode.None;
    }

    private void applyRotation(float[] rot) {
        this.rotation = rot;
        if (mc.player == null) return;

        switch (this.rotate.getValue()) {
            case ReallyWorld -> updateSmoothRotation(mc.player, rot[0], rot[1], 0.65F, true);
            case Smooth      -> updateSmoothRotation(mc.player, rot[0], rot[1],
                    this.smoothSpeed.getValue().floatValue(), false);
            case Vanilla     -> pushRotation(rot[0], rot[1]);
            case None        -> RotationUtils.getInstance().release(this);
        }
    }

    private void updateSmoothRotation(ClientPlayerEntity player, float idealYaw, float idealPitch,
                                      float lerpSpeed, boolean addNoise) {
        if (!this.rotationInitialized) {
            this.smoothedYaw   = RotationUtils.getInstance().isRotating()
                    ? RotationUtils.getInstance().getSentYaw() : player.getYaw();
            this.smoothedPitch = RotationUtils.getInstance().isRotating()
                    ? RotationUtils.getInstance().getSentPitch() : player.getPitch();
            this.rotationInitialized = true;
        }

        float deltaYaw   = MathHelper.wrapDegrees(idealYaw - this.smoothedYaw);
        float deltaPitch = idealPitch - this.smoothedPitch;
        float totalDist  = (float) Math.hypot(deltaYaw, deltaPitch);

        if (totalDist > 0.001F) {
            float minStep = 2.5F;
            float maxStep = 25.0F + 35.0F * lerpSpeed;
            float step    = MathHelper.clamp(totalDist * lerpSpeed * 0.8F + minStep, minStep, maxStep);
            step = Math.min(step, totalDist);

            this.smoothedYaw   = MathHelper.wrapDegrees(this.smoothedYaw + (deltaYaw / totalDist) * step);
            this.smoothedPitch = MathHelper.clamp(this.smoothedPitch + (deltaPitch / totalDist) * step, -90.0F, 90.0F);
        }

        if (addNoise) {
            this.noiseYaw   = approach(this.noiseYaw,   (float) Math.random() * 0.70F - 0.35F, 0.15F);
            this.noisePitch = approach(this.noisePitch, (float) Math.random() * 0.50F - 0.25F, 0.15F);
        } else {
            this.noiseYaw = 0.0F;
            this.noisePitch = 0.0F;
        }

        pushRotation(
                this.smoothedYaw + this.noiseYaw,
                MathHelper.clamp(this.smoothedPitch + this.noisePitch, -90.0F, 90.0F));
    }

    private void pushRotation(float yaw, float pitch) {
        RotationUtils rotUtils = RotationUtils.getInstance();
        rotUtils.setRotationSmooth(this, ROTATION_PRIORITY, yaw, pitch, 180.0,
                this.syncMovement.getValue());
        rotUtils.keepWireFresh();

        // Снимок угла для post-тика берём именно оттуда, куда он ушёл в
        // пакет, а не из нашего smoothedYaw: RotationUtils квантует угол по
        // GCD мыши, и между ними остаётся доли градуса.
        this.appliedYaw = rotUtils.getSentYaw();
        this.appliedPitch = rotUtils.getSentPitch();
        this.rotationApplied = true;
    }

    private static float approach(float current, float target, float t) {
        return current + (target - current) * t;
    }

    private void finishAction() {
        this.lastData = null;
        this.rotation = null;
        this.pending = null;
        this.rotationInitialized = false;
        this.targetHoldTicks = 0;
        this.noiseYaw = 0.0F;
        this.noisePitch = 0.0F;
        RotationUtils.getInstance().release(this);
    }

    /** Пауза: действие в этом тике не выполняем, ротацию тоже снимаем. */
    private void holdPosition() {
        finishAction();
    }

    private long lastDebugAt;

    private static String fmt(float v) {
        return String.format("%.2f", v);
    }

    private void report(String reason) {
        if (!this.debug.getValue()) return;

        long now = System.currentTimeMillis();
        if (now - this.lastDebugAt < DEBUG_COOLDOWN_MS) return;

        this.lastDebugAt = now;
        info("CrystalAura: %s", reason);
    }

    // ─────────────────────────────────────────────────────────────
    // Поиск цели
    // ─────────────────────────────────────────────────────────────

    private PlayerEntity findTarget(ClientPlayerEntity self, ClientWorld world) {
        Camera camera = mc.gameRenderer.getCamera();
        double range = this.targetRange.getValue();

        // Друзья выкидываются до цикла — раньше проверка INSTANCE идла
        // на каждом игроке в мире.
        FriendManager friends = FriendManager.getInstance();

        List<TargetCandidate> targets = new ArrayList<>();

        for (PlayerEntity candidate : world.getPlayers()) {
            if (candidate == self || !candidate.isAlive() || candidate.isSpectator()) continue;
            if (candidate.isCreative()) continue;
            if (friends.isFriend(candidate.getName().getString())) continue;

            Vec3d point = nearestTargetPoint(candidate.getBoundingBox(), self.getEyePos());
            if (point == null || self.getEyePos().squaredDistanceTo(point) > range * range) continue;

            float[] targetRotation = calculate(point, self);
            double dot = MathHelper.clamp(
                    RotationUtils.getRotationVector(targetRotation[1], targetRotation[0])
                            .dotProduct(RotationUtils.getRotationVector(camera.getPitch(), camera.getYaw())),
                    -1.0, 1.0);

            targets.add(new TargetCandidate(candidate, Math.toDegrees(Math.acos(dot))));
        }

        targets.sort(Comparator.comparingDouble(TargetCandidate::viewAngle));
        return targets.isEmpty() ? null : targets.get(0).player();
    }

    private Vec3d nearestTargetPoint(Box box, Vec3d eye) {
        return new Vec3d(
                MathHelper.clamp(eye.x, box.minX, box.maxX),
                MathHelper.clamp(eye.y, box.minY, box.maxY),
                MathHelper.clamp(eye.z, box.minZ, box.maxZ));
    }

    private List<Entity> collectEntities(ClientPlayerEntity self, ClientWorld world) {
        List<Entity> out = new ArrayList<>();
        for (Entity entity : world.getEntitiesByClass(Entity.class,
                self.getBoundingBox().expand(16.0D), e -> e.isAlive() && !e.isSpectator())) {
            if (entity instanceof EndCrystalEntity crystal && crystal.isAlive() && !crystal.isRemoved()) {
                this.placedPositions.remove(crystal.getBlockPos().down());
                this.placedPositions.remove(crystal.getBlockPos());
            }
            out.add(entity);
        }
        return out;
    }

    private Entity entityById(int id) {
        if (id < 0 || mc.world == null) return null;
        return mc.world.getEntityById(id);
    }

    // ─────────────────────────────────────────────────────────────
    // Подбор позиций
    // ─────────────────────────────────────────────────────────────

    private PlaceData findBestExplosion(PlayerEntity target, ClientWorld world,
                                        ClientPlayerEntity self, List<Entity> entities) {
        EndCrystalEntity bestCrystal = null;
        BlockPos bestBase = null;
        Direction bestFace = null;
        double bestScore = 0.0D;

        double reach = hitReach();

        for (Entity entity : entities) {
            if (!(entity instanceof EndCrystalEntity crystal)
                    || !crystal.isAlive() || crystal.isRemoved()
                    || this.attackedCrystals.containsKey(crystal.getId())) continue;

            Vec3d hitPoint = nearestPoint(crystal, 0.0D, 0.05D);
            if (self.getEyePos().squaredDistanceTo(hitPoint) > reach * reach) continue;

            // Источник — центр хитбокса кристалла, а не центр блока под ним.
            // Разница на подлёте к границе радиуса даёт пару единиц урона.
            Vec3d source = crystal.getBoundingBox().getCenter();

            double real = crystalDamage(source, target, null);
            if (real < 1.0D) continue;

            double score = real + 30.0D;
            if (score <= bestScore) continue;

            double selfDamage = crystalDamage(source, self, null);
            if (selfDamage > this.maxSelfDamage.getValue()) continue;

            BlockPos pos      = crystal.getBlockPos().down();
            Direction face    = Direction.UP;
            BlockPos hitBase  = null;
            Direction hitFace = null;

            float[] rot  = calculate(getFacePoint(pos, face), self);
            BlockHitResult hit = raycastBlock(rot[0], rot[1], pos, blockReach(), self);

            if (hit != null && hit.getSide() == face
                    && rayTrace(rot[0], rot[1], reach, crystal.getBoundingBox(), self)) {
                hitBase  = pos;
                hitFace = face;
            }

            bestScore = score;
            bestCrystal = crystal;
            bestBase  = hitBase;
            bestFace  = hitFace;
        }

        if (bestCrystal != null) {
            return new PlaceData(bestBase, bestFace, bestScore, PlaceResult.Explode, bestCrystal);
        }
        return PlaceData.empty(PlaceResult.Explode);
    }

    private PlaceData findBestObsidian(PlayerEntity target, ClientWorld world,
                                        ClientPlayerEntity self, List<Entity> entities) {
        BlockPos bestPos = null;
        Direction bestFace = null;
        double bestScore = 0.0D;

        BlockPos base = self.getBlockPos();
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        int targetY = (int) target.getY();

        for (int x = -5; x <= 5; x++) {
            for (int y = -5; y <= 5; y++) {
                for (int z = -5; z <= 5; z++) {
                    cursor.set(base.getX() + x, base.getY() + y, base.getZ() + z);
                    if (!world.getBlockState(cursor).isSolid()) continue;
                    if (cursor.getY() >= targetY) continue;

                    Direction face = bestVisibleFace(world, cursor, self, true);
                    if (face == null) continue;

                    BlockPos placePos  = cursor.offset(face);
                    if (placePos.getY() >= targetY) continue;
                    if (!world.getBlockState(placePos).isAir()) continue;

                    BlockPos crystalPos = placePos.up();
                    if (blockedForObsidian(entities, placePos)  != Blocking.None) continue;
                    if (blockedForObsidian(entities, crystalPos) != Blocking.None) continue;
                    if (!canReachBox(self, reachBox(placePos), 3.8D)) continue;

                    Vec3d source = Vec3d.ofCenter(crystalPos);
                    double real = crystalDamage(source, target, placePos);
                    if (real < 1.5D) continue;

                    double score = real + 13.0D;
                    if (score <= bestScore) continue;

                    double selfDamage = crystalDamage(source, self, placePos);
                    if (selfDamage > this.maxSelfDamage.getValue()) continue;

                    if (blockedForObsidian(entities, crystalPos) == Blocking.Item) {
                        return PlaceData.empty(PlaceResult.Awaiting);
                    }

                    bestScore = score;
                    bestPos   = cursor.toImmutable();
                    bestFace  = face;
                }
            }
        }

        return new PlaceData(bestPos, bestFace, bestScore, PlaceResult.PlaceObsidian, null);
    }

    private PlaceData findBestCrystal(PlayerEntity target, ClientWorld world,
                                      ClientPlayerEntity self, List<Entity> entities) {
        BlockPos bestPos = null;
        Direction bestFace = null;
        double bestScore = 0.0D;

        BlockPos base = self.getBlockPos();
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        int targetY = (int) target.getY();

        for (int x = -5; x <= 5; x++) {
            for (int y = -5; y <= 5; y++) {
                for (int z = -5; z <= 5; z++) {
                    cursor.set(base.getX() + x, base.getY() + y, base.getZ() + z);
                    if (cursor.getY() >= targetY) continue;

                    BlockState state = world.getBlockState(cursor);
                    if (!state.isOf(Blocks.OBSIDIAN) && !state.isOf(Blocks.BEDROCK)) continue;

                    BlockPos placePos = cursor.up();
                    if (blockedForCrystal(entities, placePos)) continue;
                    if (!canReachBox(self, reachBox(placePos), 3.8D)) continue;

                    Direction face = bestVisibleFace(world, cursor, self, false);
                    if (face == null) continue;

                    // Верхняя грань надёжнее боковой — ставим сверху, если видна.
                    float[] topRot  = calculate(getFacePoint(cursor, Direction.UP), self);
                    BlockHitResult topHit = raycastBlock(topRot[0], topRot[1], cursor, blockReach(), self);
                    if (topHit != null && topHit.getSide() == Direction.UP) face = Direction.UP;

                    long now = System.currentTimeMillis();
                    Long placedAt = this.placedPositions.get(cursor);
                    if (placedAt == null) placedAt = this.placedPositions.get(placePos);
                    if (placedAt != null) {
                        if (now - placedAt < PLACED_BLOCK_MS) continue;
                        this.placedPositions.remove(cursor);
                        this.placedPositions.remove(placePos);
                    }

                    Vec3d source = Vec3d.ofCenter(placePos);
                    double real = crystalDamage(source, target, null);
                    if (real < 1.5D) continue;

                    double score = real + 19.0D;
                    if (score <= bestScore) continue;

                    double selfDamage = crystalDamage(source, self, null);
                    if (selfDamage > this.maxSelfDamage.getValue()) continue;

                    bestScore = score;
                    bestPos   = cursor.toImmutable();
                    bestFace  = face;
                }
            }
        }

        return new PlaceData(bestPos, bestFace, bestScore, PlaceResult.PlaceCrystal, null);
    }

    private PlaceData findBestBreak(PlayerEntity target, ClientWorld world,
                                    ClientPlayerEntity self, List<Entity> entities) {
        BlockPos bestPlace = null;
        Direction bestPlaceFace = null;
        double bestPlaceScore = 0.0D;

        BlockPos bestBreak = null;
        Direction bestBreakFace = null;
        double bestBreakScore = 0.0D;

        BlockPos base = self.getBlockPos();
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        int targetY = (int) target.getY();
        double selfLimit = this.maxSelfDamage.getValue();

        for (int x = -5; x <= 5; x++) {
            for (int y = -5; y <= 5; y++) {
                for (int z = -5; z <= 5; z++) {
                    cursor.set(base.getX() + x, base.getY() + y, base.getZ() + z);
                    if (cursor.getY() >= targetY || !world.getBlockState(cursor).isSolid()) continue;

                    BlockPos placePos = cursor.up();
                    if (blockedForBreak(entities, placePos)) continue;
                    if (!canReachBox(self, reachBox(placePos), 3.8D)) continue;

                    Direction face = bestVisibleFace(world, cursor, self, false);
                    if (face == null) continue;

                    float[] rot = calculate(getFacePoint(cursor, face), self);
                    BlockHitResult hit = raycastBlock(rot[0], rot[1], cursor, blockReach(), self);
                    if (hit == null || hit.getSide() != face) continue;

                    BlockState placeState = world.getBlockState(placePos);
                    Vec3d source = Vec3d.ofCenter(placePos);

                    if (!placeState.isAir()) {
                        // Кристалл стоит на блоке — сносим опору под ним.
                        if (findBestHotbarTool(self, placeState) == -1) continue;

                        double real = crystalDamage(source, target, placePos);
                        if (real < 1.5D) continue;

                        double score = real + 7.0D;
                        if (score <= bestBreakScore) continue;

                        double selfDamage = crystalDamage(source, self, placePos);
                        if (selfDamage > selfLimit) continue;

                        bestBreakScore = score;
                        bestBreak      = placePos.toImmutable();
                        bestBreakFace  = face;
                    } else {
                        if (findBestHotbarTool(self, world.getBlockState(cursor)) == -1) continue;

                        double real = crystalDamage(source, target, null);
                        if (real < 1.5D) continue;

                        double score = real + 7.0D;
                        if (score <= bestPlaceScore) continue;

                        double selfDamage = crystalDamage(source, self, null);
                        if (selfDamage > selfLimit) continue;

                        bestPlaceScore = score;
                        bestPlace      = cursor.toImmutable();
                        bestPlaceFace  = face;
                    }
                }
            }
        }

        if (bestBreakScore > bestPlaceScore) {
            return new PlaceData(bestBreak, bestBreakFace, bestBreakScore, PlaceResult.Break, null);
        }
        return new PlaceData(bestPlace, bestPlaceFace, bestPlaceScore, PlaceResult.Break, null);
    }

    /**
     * Грань для установки.
     *
     * @param requireAirSide {@code true} — режим обсидиана: грань должна
     *                       смотреть в воздух, выбираем ту, где нас меньше
     *                       всего приметит собственный взрыв.
     */
    private Direction bestVisibleFace(ClientWorld world, BlockPos pos, ClientPlayerEntity self,
                                      boolean requireAirSide) {
        Direction best = null;
        double bestValue = requireAirSide ? 0.0D : Double.MAX_VALUE;

        for (Direction face : Direction.values()) {
            BlockPos side = pos.offset(face);
            if (requireAirSide && !world.getBlockState(side).isAir()) continue;

            Vec3d point = getFacePoint(pos, face);
            float[] rot = calculate(point, self);
            BlockHitResult hit = raycastBlock(rot[0], rot[1], pos, 4.5D, self);
            if (hit == null) continue;

            if (requireAirSide) {
                double damage = crystalDamage(Vec3d.ofCenter(side.up()), self, side);
                if (damage > bestValue) {
                    bestValue = damage;
                    best = face;
                }
            } else {
                double distance = self.getEyePos().squaredDistanceTo(point);
                if (distance < bestValue) {
                    bestValue = distance;
                    best = face;
                }
            }
        }

        return best;
    }

    // ─────────────────────────────────────────────────────────────
    // Геометрия и проверки
    // ─────────────────────────────────────────────────────────────

    private static Box reachBox(BlockPos pos) {
        return new Box(pos.getX() - 0.5D, pos.getY(), pos.getZ() - 0.5D,
                pos.getX() + 2.0D, pos.getY() + 1.5D, pos.getZ() + 1.5D);
    }

    private static Box crystalBox(BlockPos pos) {
        return new Box(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0D, pos.getY() + 2.0D, pos.getZ() + 1.0D);
    }

    private static boolean intersectsEntityBox(BlockPos pos, Entity entity) {
        return entity.getBoundingBox().intersects(crystalBox(pos));
    }

    private Blocking blockedForObsidian(List<Entity> entities, BlockPos pos) {
        Box blockBox = new Box(pos.getX(), pos.getY() - 0.2D, pos.getZ(),
                pos.getX() + 1.0D, pos.getY() + 2.2D, pos.getZ() + 1.0D);

        for (Entity entity : entities) {
            if (entity instanceof ItemEntity && !entity.isOnGround()
                    && entity.getBoundingBox().intersects(blockBox)) {
                return Blocking.Item;
            }
            if ((entity instanceof PlayerEntity || entity instanceof EndCrystalEntity)
                    && intersectsEntityBox(pos, entity)) {
                return Blocking.Player;
            }
        }
        return Blocking.None;
    }

    private boolean blockedForCrystal(List<Entity> entities, BlockPos pos) {
        for (Entity entity : entities) {
            if (intersectsEntityBox(pos, entity)) return true;
        }
        return false;
    }

    private boolean blockedForBreak(List<Entity> entities, BlockPos pos) {
        for (Entity entity : entities) {
            if (!(entity instanceof ItemEntity) && intersectsEntityBox(pos, entity)) return true;
        }
        return false;
    }

    private boolean hasCrystals(ClientPlayerEntity self) {
        return self.getOffHandStack().isOf(Items.END_CRYSTAL)
                || self.getMainHandStack().isOf(Items.END_CRYSTAL)
                || InventoryUtil.findInHotbar(Items.END_CRYSTAL).found();
    }

    private boolean hasObsidian(ClientPlayerEntity self) {
        return self.getOffHandStack().isOf(Items.OBSIDIAN)
                || self.getMainHandStack().isOf(Items.OBSIDIAN)
                || InventoryUtil.findInHotbar(Items.OBSIDIAN).found();
    }

    private int findBestHotbarTool(ClientPlayerEntity player, BlockState state) {
        int bestSlot = -1;
        float bestSpeed = 1.0F;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot  = slot;
            }
        }
        return bestSlot;
    }

    /** Дальность до блоков: атрибут игрока, при желании ограниченная настройкой. */
    private double blockReach() {
        double attribute = mc.player.getBlockInteractionRange();
        double configured = this.placeReach.getValue();
        return configured > 0.0D ? Math.min(configured, attribute) : attribute;
    }

    /** Дальность до сущностей: атрибут игрока, при желании ограниченная настройкой. */
    private double hitReach() {
        double attribute = mc.player.getEntityInteractionRange();
        double configured = this.hitReach.getValue();
        return configured > 0.0D ? Math.min(configured, attribute) : attribute;
    }

    private static boolean canReachBox(ClientPlayerEntity player, Box box, double maxDistance) {
        Vec3d eye = player.getEyePos();
        return eye.squaredDistanceTo(
                MathHelper.clamp(eye.x, box.minX, box.maxX),
                MathHelper.clamp(eye.y, box.minY, box.maxY),
                MathHelper.clamp(eye.z, box.minZ, box.maxZ)) <= maxDistance * maxDistance;
    }

    private static Vec3d getFacePoint(BlockPos pos, Direction face) {
        return new Vec3d(
                pos.getX() + 0.5D + face.getOffsetX() * 0.5D,
                pos.getY() + 0.5D + face.getOffsetY() * 0.5D,
                pos.getZ() + 0.5D + face.getOffsetZ() * 0.5D);
    }

    private static float[] calculate(Vec3d point, ClientPlayerEntity player) {
        double dx = point.x - player.getX();
        double dy = point.y - player.getEyeY();
        double dz = point.z - player.getZ();
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw   = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, distXZ));
        return new float[]{yaw, pitch};
    }

    private static Vec3d nearestPoint(Entity crystal, double offsetXZ, double offsetY) {
        if (mc.player == null) return crystal.getEntityPos();
        Box box = crystal.getBoundingBox();
        Vec3d eye = mc.player.getEyePos();
        return new Vec3d(
                MathHelper.clamp(eye.x, box.minX + offsetXZ, box.maxX - offsetXZ),
                MathHelper.clamp(eye.y, box.minY + offsetY, box.maxY - offsetY),
                MathHelper.clamp(eye.z, box.minZ + offsetXZ, box.maxZ - offsetXZ));
    }

    // ─────────────────────────────────────────────────────────────
    // Урон (с кэшем на тик)
    // ─────────────────────────────────────────────────────────────

    private float crystalDamage(Vec3d source, LivingEntity target, BlockPos transparentBlock) {
        DamageKey key = new DamageKey(source, target.getId(), transparentBlock);
        Float cached = this.damageCache.get(key);
        if (cached != null) return cached;

        float value = ExplosionDamageUtil.getDamageRaw(mc.world, source,
                ExplosionDamageUtil.CRYSTAL_POWER, target, transparentBlock);

        this.damageCache.put(key, value);
        return value;
    }

    // ─────────────────────────────────────────────────────────────
    // Raycast'ы
    // ─────────────────────────────────────────────────────────────

    private BlockHitResult raycastBlock(float yaw, float pitch, BlockPos targetPos,
                                        double maxDistance, ClientPlayerEntity player) {
        if (player == null || mc.world == null) return null;

        Vec3d eye = player.getEyePos();
        Vec3d end = eye.add(RotationUtils.getRotationVector(pitch, yaw).multiply(maxDistance));

        BlockHitResult hit = mc.world.raycast(new RaycastContext(
                eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));

        if (hit != null && hit.getType() == HitResult.Type.BLOCK
                && hit.getBlockPos().equals(targetPos)) {
            return hit;
        }
        return null;
    }

    /**
     * Пересечение луча с AABB — слэб-метод.
     *
     * <p>У Yarn нет обёртки {@code AABB.clip}, а полагаться на то, что
     * серверный {@code raycast} попадёт именно в сущность, нельзя: кристалл
     * не коллидит с лучом как блок, а рейкаст по миру его не вернёт.
     */
    private boolean rayTrace(float yaw, float pitch, double maxDistance, Box box, ClientPlayerEntity player) {
        if (player == null || box == null) return false;

        Vec3d eye = player.getEyePos();
        Vec3d end = eye.add(RotationUtils.getRotationVector(pitch, yaw).multiply(maxDistance));

        double dirX = end.x - eye.x, dirY = end.y - eye.y, dirZ = end.z - eye.z;
        double tMin = 0.0, tMax = maxDistance;

        double[] origins = {eye.x, eye.y, eye.z};
        double[] deltas  = {dirX, dirY, dirZ};
        double[] mins    = {box.minX, box.minY, box.minZ};
        double[] maxs    = {box.maxX, box.maxY, box.maxZ};

        for (int i = 0; i < 3; i++) {
            if (Math.abs(deltas[i]) < 1.0E-8) {
                if (origins[i] < mins[i] || origins[i] > maxs[i]) return false;
                continue;
            }
            double inv = 1.0 / deltas[i];
            double t1 = (mins[i] - origins[i]) * inv;
            double t2 = (maxs[i] - origins[i]) * inv;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return false;
        }

        return tMax >= 0.0;
    }

    // ─────────────────────────────────────────────────────────────
    // Визуал
    // ─────────────────────────────────────────────────────────────

    private void addVisual(BlockPos pos, float r, float g, float b) {
        BlockPos immutable = pos.toImmutable();
        if (this.visualSeen.putIfAbsent(immutable, Boolean.TRUE) != null) return;
        this.visuals.addLast(new VisualData(immutable, r, g, b));
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!this.render.getValue() || mc.player == null || mc.world == null || this.visuals.isEmpty()) return;

        long now = System.currentTimeMillis();
        while (!this.visuals.isEmpty()) {
            VisualData data = this.visuals.peekFirst();
            if (now - data.startedAt <= VISUAL_MS) break;
            this.visuals.pollFirst();
            this.visualSeen.remove(data.pos);
        }
        if (this.visuals.isEmpty()) return;

        for (VisualData data : this.visuals) {
            float remaining = MathHelper.clamp(1.0F - (float) (now - data.startedAt) / VISUAL_MS, 0.0F, 1.0F);
            float fade = remaining * remaining * remaining;
            if (fade <= 0.01F) continue;

            int alphaFill = (int) (fade * 0.35F * 255.0F);
            int alphaLine = (int) (fade * 0.85F * 255.0F);

            Renderer3D.box(new Box(data.pos),
                    new Color((int) (data.r * 255), (int) (data.g * 255), (int) (data.b * 255), alphaFill),
                    new Color((int) (data.r * 255), (int) (data.g * 255), (int) (data.b * 255), alphaLine),
                    ShapeMode.Both, false);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Модели
    // ─────────────────────────────────────────────────────────────

    /**
     * Снимок pre-тика для post-тика.
     *
     * @param yaw угол, реально ушедший серверу в этом тике
     * @param id  id кристалла (только для {@link PlaceResult#Explode})
     */
    private record PendingAction(PlaceResult kind, BlockPos pos, Direction face,
                                 int id, float yaw, float pitch) {}

    private record TargetCandidate(PlayerEntity player, double viewAngle) {}

    private record DamageKey(Vec3d source, int entityId, BlockPos transparentBlock) {}

    public record PlaceData(BlockPos blockPos, Direction face, double damage,
                            PlaceResult placeResult, Entity entity) {

        public static PlaceData empty(PlaceResult result) {
            return new PlaceData(null, null, 0.0D, result, null);
        }
    }

    private static final class VisualData {
        private final BlockPos pos;
        private final long startedAt = System.currentTimeMillis();
        private final float r;
        private final float g;
        private final float b;

        VisualData(BlockPos pos, float r, float g, float b) {
            this.pos = pos;
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }

    /** Отправка пакета смены слота через общий инвентарный менеджер клиента. */
    private static final class InventoryManagerBridge {
        private InventoryManagerBridge() {}

        static void syncSlot(int slot) {
            me.collective.util.InventoryManager.getInstance().setSlotForced(slot);
        }
    }
}
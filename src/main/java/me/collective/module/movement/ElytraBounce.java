package me.collective.module.movement;

import me.collective.category.Category;
import me.collective.command.ChatHelper;
import me.collective.event.EventHandler;
import me.collective.event.events.PacketEvent;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.module.ModuleManager;
import me.collective.module.combat.KillAura;
import me.collective.module.player.ChestSwap;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.BaritoneHelper;
import me.collective.util.BounceSolver;
import me.collective.util.RotationUtils;
import me.collective.util.Utils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;

public class ElytraBounce extends Module {
    public static ElytraBounce INSTANCE;

    public final BooleanSetting bounce = add(new BooleanSetting("Bounce", true));
    public final BooleanSetting lockPitch = add(new BooleanSetting("LockPitch", true, (v) -> this.bounce.getValue()));
    public final BooleanSetting autoPitch = add(new BooleanSetting("AutoPitch", true, (v) -> this.bounce.getValue() && this.lockPitch.getValue()));
    public final NumberSetting pitch = add(new NumberSetting("Pitch", 90.0, -90.0, 90.0, 1.0, (v) -> this.bounce.getValue() && this.lockPitch.getValue() && !this.autoPitch.getValue(), false));
    public final NumberSetting setbackPause = add(new NumberSetting("SetbackPause", 5, 0, 40, 1, (v) -> this.bounce.getValue(), true));
    public final BooleanSetting lockYaw = add(new BooleanSetting("LockYaw", false, (v) -> this.bounce.getValue()));
    public final BooleanSetting useCustomYaw = add(new BooleanSetting("UseCustomYaw", false, (v) -> this.bounce.getValue()));
    public final NumberSetting yaw = add(new NumberSetting("Yaw", 0.0, 0.0, 359.0, 1.0, (v) -> this.bounce.getValue() && this.useCustomYaw.getValue(), false));

    // Обход препятствий
    public final BooleanSetting highwayObstaclePasser = add(new BooleanSetting("HighwayObstaclePasser", true, (v) -> this.bounce.getValue()));
    public final BooleanSetting awayFromStartPos = add(new BooleanSetting("AwayFromStartPos", true, (v) -> this.bounce.getValue() && this.highwayObstaclePasser.getValue()));
    public final NumberSetting distance = add(new NumberSetting("Distance", 10.0, 1.0, 50.0, 1.0, (v) -> this.bounce.getValue() && this.highwayObstaclePasser.getValue(), false));
    public final BooleanSetting avoidPortalTraps = add(new BooleanSetting("AvoidPortalTraps", false, (v) -> this.bounce.getValue() && this.highwayObstaclePasser.getValue()));
    public final NumberSetting portalAvoidDistance = add(new NumberSetting("PortalAvoidDistance", 20.0, 0.0, 50.0, 1.0, (v) -> this.bounce.getValue() && this.highwayObstaclePasser.getValue() && this.avoidPortalTraps.getValue(), false));
    public final NumberSetting portalScanWidth = add(new NumberSetting("PortalScanWidth", 5, 3, 10, 1, (v) -> this.bounce.getValue() && this.highwayObstaclePasser.getValue() && this.avoidPortalTraps.getValue(), true));
    public final BooleanSetting killAuraWhileWalking = add(new BooleanSetting("KillAuraWhileWalking", false, (v) -> this.bounce.getValue() && this.highwayObstaclePasser.getValue()));

    // Дополнительные настройки
    public final BooleanSetting fakeFly = add(new BooleanSetting("FakeFly", false));
    public final BooleanSetting toggleElytra = add(new BooleanSetting("ToggleElytra", true, (v) -> !this.fakeFly.getValue()));
    public final BooleanSetting lookLikeFalling = add(new BooleanSetting("LookLikeFalling", false));
    public final BooleanSetting showChestplate = add(new BooleanSetting("ShowChestplate", false));
    // Камера не следует за дугой прыжка — видно ровный элитрафлай вперёд.
    // Только визуал клиента: пакеты и траектория на сервере не меняются.
    public final BooleanSetting smoothCamera = add(new BooleanSetting("SmoothCamera", false));

    private boolean startSprinting;
    private boolean jumpKeyDown = false;
    private BlockPos portalTrap = null;
    private boolean paused = false;
    private boolean elytraToggled = false;
    private Vec3 lastUnstuckPos;
    private int stuckTimer = 0;
    private int targetY = 120;
    private BlockPos startPos = new BlockPos(0, 0, 0);
    /** Чанк, прибывший пакетом, для отложенного портального скана (тиковой). */
    private ChunkPos pendingPortalScan;
    private KillAura killAura;
    private Boolean killAuraUserState = null;
    private float solvedPitch = 90.0F;
    private double solvedHeadroom = -1.0;
    private double predictedSpeed = 0.0;
    private int solveCooldown = 0;
    private int setbackTicks = 0;
    private long lastChestSwapAttempt = 0L;
    private BlockPos lastNavGoal = null;
    private BlockPos tempPath = null;
    private boolean waitingForChunksToLoad = false;
    /** Сглаженная высота камеры для SmoothCamera; NaN — ещё не инициализирована. */
    private double cameraSmoothY = Double.NaN;
    private long cameraLastFrameNanos;

    private static final int ROTATION_PRIORITY = 45;

    public ElytraBounce() {
        super("ElytraBounce", "Скоростной баунс-полёт на элитрах с автоматическим углом наклона и таймингами отскока.", Category.MOVEMENT);
        INSTANCE = this;
    }

    public static boolean showChestplate() {
        ElytraBounce module = INSTANCE;
        return module != null && module.isToggled() && module.showChestplate.getValue();
    }

    public static boolean lookLikeFalling() {
        ElytraBounce module = INSTANCE;
        return module != null && module.isToggled() && module.lookLikeFalling.getValue();
    }

    @Override
    public void onWorldChange() {
        this.restoreKillAura();
        this.lastUnstuckPos = null;
        this.tempPath = null;
        this.portalTrap = null;
        this.pendingPortalScan = null;
        this.lastNavGoal = null;
        this.elytraToggled = false;
        this.stuckTimer = 0;
        this.cameraSmoothY = Double.NaN;
        RotationUtils.getInstance().clearRotations(this);
    }

    @Override
    public void onEnable() {
        this.lastUnstuckPos = null;
        if (mc.player != null && !mc.player.getAbilities().allowFlying) {
            this.startSprinting = mc.player.isSprinting();
            this.jumpKeyDown = false;
            this.tempPath = null;
            this.portalTrap = null;
            this.paused = false;
            this.waitingForChunksToLoad = false;
            this.elytraToggled = false;
            this.lastUnstuckPos = playerPos();
            this.stuckTimer = 0;
            this.solvedPitch = this.pitch.getValue().floatValue();
            this.solvedHeadroom = -1.0;
            this.predictedSpeed = 0.0;
            this.solveCooldown = 0;
            this.setbackTicks = 0;
            this.cameraSmoothY = Double.NaN;
            if (this.bounce.getValue() && playerPos().multiply(1.0, 0.0, 1.0).length() >= 100.0) {
                if (!BaritoneHelper.hasElytraDestination()) {
                    BaritoneHelper.clearGoal();
                }

                if (this.highwayObstaclePasser.getValue()) {
                    this.startPos = mc.player.getBlockPos();
                    this.targetY = mc.player.getBlockY();
                } else {
                    this.startPos = new BlockPos(0, 0, 0);
                }

                if (!this.useCustomYaw.getValue()) {
                    if (!(mc.player.getBlockPos().getSquaredDistance(this.startPos) < 10000.0) && this.highwayObstaclePasser.getValue()) {
                        BlockPos directionVec = mc.player.getBlockPos().subtract(this.startPos);
                        double angle = Math.toDegrees(Math.atan2(-directionVec.getX(), directionVec.getZ()));
                        double angleNormalized = Utils.angleOnAxis(angle);
                        if (!this.awayFromStartPos.getValue()) {
                            angleNormalized += 180.0;
                        }

                        this.yaw.setValue(angleNormalized);
                    } else {
                        double playerAngleNormalized = Utils.angleOnAxis(mc.player.getYaw());
                        this.yaw.setValue(playerAngleNormalized);
                    }
                }
            }
        }
    }

    @Override
    public void onDisable() {
        this.restoreKillAura();
        RotationUtils.getInstance().clearRotations(this);
        this.lastNavGoal = null;
        if (mc.player != null) {
            if (this.bounce.getValue() && !BaritoneHelper.hasElytraDestination()) {
                BaritoneHelper.clearGoal();
            }

            mc.player.setSprinting(this.startSprinting);
            if (this.toggleElytra.getValue()
                && !this.fakeFly.getValue()
                && !mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem().toString().contains("chestplate")) {
                ChestSwap cs = ModuleManager.INSTANCE.getModule(ChestSwap.class);
                if (cs != null) {
                    cs.swap();
                }
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player != null && !mc.player.getAbilities().allowFlying) {
            if (this.lastUnstuckPos == null) onEnable();
            if (this.setbackTicks > 0) {
                this.setbackTicks--;
            }

            this.updateAutoPitch();
            this.updateKillAura();
            this.updateJumpKey();
            this.scanPendingPortalChunk();

            if (this.toggleElytra.getValue() && !this.fakeFly.getValue() && !this.elytraToggled) {
                ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
                if (!chest.isOf(Items.ELYTRA) && !chest.contains(DataComponentTypes.GLIDER)) {
                    // Не чаще раза в секунду: спам свапами не применяется сервером,
                    // а следующий glide-start палится как BadPacketsJ
                    long now = System.currentTimeMillis();
                    if (now - this.lastChestSwapAttempt >= 1000) {
                        this.lastChestSwapAttempt = now;
                        ChestSwap cs = ModuleManager.INSTANCE.getModule(ChestSwap.class);
                        if (cs != null) {
                            cs.swap();
                        }
                    }
                } else {
                    this.elytraToggled = true;
                }
            }

            if (this.enabled() && hasEnoughFood()) {
                mc.player.setSprinting(true);
            }

            if (this.bounce.getValue()) {
                if (this.tempPath != null && mc.player.getBlockPos().getSquaredDistance(this.tempPath) < 500.0) {
                    this.tempPath = null;
                    this.lastNavGoal = null;
                    BaritoneHelper.clearGoal();
                } else if (this.tempPath != null) {
                    if (!this.tempPath.equals(this.lastNavGoal)) {
                        this.lastNavGoal = this.tempPath;
                        BaritoneHelper.setGoal(this.tempPath);
                    }
                    RotationUtils.getInstance().clearRotations(this);
                    return;
                }

                if (this.highwayObstaclePasser.getValue() && BaritoneHelper.hasGoal()) {
                    RotationUtils.getInstance().clearRotations(this);
                    return;
                }

                if (mc.player.squaredDistanceTo(this.lastUnstuckPos) < 25.0) {
                    this.stuckTimer++;
                } else {
                    this.stuckTimer = 0;
                    this.lastUnstuckPos = playerPos();
                }

                if (this.highwayObstaclePasser.getValue()
                    && playerPos().length() > 100.0
                    && (
                        mc.player.getY() < this.targetY
                            || mc.player.getY() > this.targetY + 2
                            || mc.player.horizontalCollision
                            || (this.portalTrap != null
                                && this.portalTrap.getSquaredDistance(mc.player.getBlockPos())
                                    < this.portalAvoidDistance.getValue() * this.portalAvoidDistance.getValue())
                            || this.waitingForChunksToLoad
                            || this.stuckTimer > 50
                    )) {
                    this.waitingForChunksToLoad = false;
                    this.paused = true;
                    RotationUtils.getInstance().clearRotations(this);
                    BlockPos goal = mc.player.getBlockPos();
                    double currDistance = this.distance.getValue();
                    if (this.portalTrap != null) {
                        currDistance += playerPos().distanceTo(this.portalTrap.toCenterPos());
                        this.portalTrap = null;
                        ChatHelper.sendMessage("Путь в обход портала.");
                    }

                    do {
                        if (currDistance > 80.0) {
                            this.tempPath = goal;
                            if (!goal.equals(this.lastNavGoal)) {
                                this.lastNavGoal = goal;
                                BaritoneHelper.setGoal(goal);
                            }
                            return;
                        }

                        Vec3 unitYawVec = Utils.yawToDirection(this.yaw.getValue());
                        Vec3 travelVec = playerPos().subtract(this.startPos.toCenterPos());
                        double parallelCurrPosDot = travelVec.multiply(new Vec3(1.0, 0.0, 1.0)).dotProduct(unitYawVec);
                        Vec3 parallelCurrPosComponent = unitYawVec.multiply(parallelCurrPosDot);
                        Vec3 pos = this.startPos.toCenterPos().add(parallelCurrPosComponent);
                        pos = Utils.positionInDirection(pos, this.yaw.getValue(), currDistance);
                        goal = new BlockPos((int) Math.floor(pos.getX()), this.targetY, (int) Math.floor(pos.getZ()));
                        currDistance++;
                        if (mc.world.getBlockState(goal).getBlock() == Blocks.VOID_AIR) {
                            this.waitingForChunksToLoad = true;
                            return;
                        }
                    } while (
                        !mc.world.getBlockState(goal.down()).isSolidBlock(mc.world, goal.down())
                            || mc.world.getBlockState(goal).getBlock() == Blocks.NETHER_PORTAL
                            || !mc.world.getBlockState(goal).isAir()
                    );

                    if (!goal.equals(this.lastNavGoal)) {
                        this.lastNavGoal = goal;
                        BaritoneHelper.setGoal(goal);
                    }
                } else {
                    this.paused = false;
                    if (!this.enabled()) {
                        RotationUtils.getInstance().clearRotations(this);
                        return;
                    }

                    if (this.lockYaw.getValue()) {
                        mc.player.setYaw(this.yaw.getValue().floatValue());
                    }

                    RotationUtils rotation = RotationUtils.getInstance();
                    if (this.lockPitch.getValue()) {
                        rotation.setRotationFullInstant(
                            this,
                            ROTATION_PRIORITY,
                            this.lockYaw.getValue() ? this.yaw.getValue().floatValue() : mc.player.getYaw(),
                            this.effectivePitch()
                        );
                    } else {
                        rotation.clearRotations(this);
                    }
                }
            }

            if (this.enabled() && this.fakeFly.getValue()) {
                this.doGrimEflyStuff();
            }
        }
    }

    private void updateKillAura() {
        if (this.killAura == null) {
            this.killAura = ModuleManager.INSTANCE.getModule(KillAura.class);
        }

        if (this.killAura != null) {
            if (this.isToggled() && this.killAuraWhileWalking.getValue() && this.bounce.getValue() && this.highwayObstaclePasser.getValue()) {
                if (this.killAuraUserState == null) {
                    this.killAuraUserState = this.killAura.isToggled();
                }

                boolean walking = this.paused;
                if (walking != this.killAura.isToggled()) {
                    this.killAura.toggle();
                }
            } else {
                this.restoreKillAura();
            }
        }
    }

    private void updateAutoPitch() {
        if (this.autoPitch.getValue() && this.bounce.getValue() && this.lockPitch.getValue() && mc.world != null) {
            if (this.solveCooldown > 0) {
                this.solveCooldown--;
            } else if (mc.player.isOnGround()) {
                this.solveCooldown = 10;
                double headroom = BounceSolver.measureHeadroom(mc.player, mc.world);
                if (!(this.solvedHeadroom >= 0.0) || Math.abs(headroom - this.solvedHeadroom) >= 0.05) {
                    this.solvedHeadroom = headroom;
                    this.solvedPitch = BounceSolver.solvePitch(headroom);
                    this.predictedSpeed = BounceSolver.simulateSpeed(this.solvedPitch, headroom);
                }
            }
        }
    }

    private float effectivePitch() {
        return this.autoPitch.getValue() ? this.solvedPitch : this.pitch.getValue().floatValue();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (this.bounce.getValue() && this.setbackPause.getValue().intValue() > 0) {
            if (event.getPacket() instanceof PlayerPositionLookS2CPacket) {
                this.setbackTicks = this.setbackPause.getValue().intValue();
            }
        }

        if (this.avoidPortalTraps.getValue() && this.highwayObstaclePasser.getValue()) {
            // Receive стреляет на нетти-треде: чанк ещё не интегрирован в мир
            // (это делает ванилла позже, на основном треде). Запоминаем чанк и
            // сканируем его в тике — иначе getChunk всегда null и детект мёртв.
            if (event.getPacket() instanceof ChunkDataS2CPacket chunkPacket) {
                this.pendingPortalScan = new ChunkPos(chunkPacket.getChunkX(), chunkPacket.getChunkZ());
            }
        }
    }

    /** Сканирует отложенный чанк на порталы-ловушки; вызывается только из тика. */
    private void scanPendingPortalChunk() {
        ChunkPos pos = this.pendingPortalScan;
        this.pendingPortalScan = null;
        if (pos == null || mc.world == null || mc.player == null) return;

        Vec3 moveDir = Utils.yawToDirection(this.yaw.getValue());
        if (Utils.distancePointToDirection(Vec3.of(pos.getCenterAtY(this.targetY)), moveDir, playerPos()) > 21.0) {
            return;
        }

        WorldChunk chunk = mc.world.getChunk(pos.x, pos.z);
        if (chunk == null) return;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = this.targetY; y < this.targetY + 3; y++) {
                    BlockPos position = new BlockPos(pos.getStartX() + x, y, pos.getStartZ() + z);
                    if ((Utils.distancePointToDirection(Vec3.of(position), moveDir, playerPos())
                            <= this.portalScanWidth.getValue().intValue())
                        && mc.world.getBlockState(position).getBlock().equals(Blocks.NETHER_PORTAL)) {
                        BlockPos posBehind = new BlockPos(
                            (int) Math.floor(position.getX() + moveDir.getX()),
                            position.getY(),
                            (int) Math.floor(position.getZ() + moveDir.getZ())
                        );
                        if ((
                                mc.world.getBlockState(posBehind).isSolidBlock(mc.world, posBehind)
                                    || mc.world.getBlockState(posBehind).getBlock() == Blocks.NETHER_PORTAL
                            )
                            && (
                                this.portalTrap == null
                                    || this.portalTrap.getSquaredDistance(posBehind) > 100.0
                                        && mc.player.getBlockPos().getSquaredDistance(posBehind)
                                            < mc.player.getBlockPos().getSquaredDistance(this.portalTrap)
                            )) {
                            this.portalTrap = posBehind;
                        }
                    }
                }
            }
        }
    }

    private void restoreKillAura() {
        if (this.killAuraUserState != null) {
            if (this.killAura != null && (this.killAura.isToggled() != this.killAuraUserState)) {
                this.killAura.toggle();
            }

            this.killAuraUserState = null;
        }
    }

    public boolean enabled() {
        return this.isToggled()
            && !this.paused
            && this.setbackTicks == 0
            && mc.player != null
            && (this.fakeFly.getValue()
                || mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)
                || mc.player.getEquippedStack(EquipmentSlot.CHEST).contains(DataComponentTypes.GLIDER));
    }

    public boolean shouldAutoJump() {
        return this.bounce.getValue() && !this.fakeFly.getValue();
    }

    private void updateJumpKey() {
        boolean prev = this.jumpKeyDown;
        if (!this.enabled() || !this.shouldAutoJump()) {
            this.jumpKeyDown = false;
        } else if (mc.player.isOnGround()) {
            this.jumpKeyDown = true;
        } else if (mc.player.isGliding()) {
            this.jumpKeyDown = false;
        } else {
            this.jumpKeyDown = !prev;
        }
    }

    public boolean isJumpKeyForcedDown() {
        return this.jumpKeyDown;
    }

    /**
     * Срезка дуги прыжка для камеры и модели (SmoothCamera) до нуля:
     * в воздухе камера и модель вообще не тянутся вверх за дугой прыжка —
     * от клиента не видно, что персонаж прыгает, взгляд идёт ровным глайдом.
     *
     * <p>Зазоры выравниваются на земле: при касании грунта сглаженная высота
     * быстро (за 2-3 кадра) догоняет реальную, поэтому смена уровня дороги,
     * ступени и сетбеки не впечатываются в глаз одним рывком. На ровной трассе
     * между прыжками дельта нулевая и камера стоит неподвижно. Траектория на
     * сервере не меняется — чисто клиентский визуал.
     *
     * @param realY   реальная интерполированная высота игрока (ступни)
     * @param advance true — фильтр делает шаг этого кадра (камера обновляется
     *                в кадре первой); false — переиспользовать шаг кадра
     *                (состояние рендера модели в F5 читает готовую дельту)
     * @return сколько визуально «срезали» по вертикали: realY − сглаженная
     */
    public double smoothVisualDeltaY(double realY, boolean advance) {
        if (!this.smoothCamera.getValue() || mc.player == null || !this.enabled()) {
            this.cameraSmoothY = realY;
            return 0.0;
        }

        long now = System.nanoTime();
        if (Double.isNaN(this.cameraSmoothY) || realY < this.cameraSmoothY - 16.0) {
            this.cameraSmoothY = realY;
            this.cameraLastFrameNanos = now;
            return 0.0;
        }

        if (advance) {
            // Нормализация шага по времени кадра: на 144+ fps скорость не падает.
            double dt = MathHelper.clamp((now - this.cameraLastFrameNanos) / 50_000_000.0, 0.25, 3.0);
            this.cameraLastFrameNanos = now;

            double gap = realY - this.cameraSmoothY;
            if (mc.player.isOnGround()) {
                // на земле — быстрый плавный прилип к реальной высоте
                // (≈75% на кадр 20 мс): ступени и смена уровня дороги
                // склеиваются за 2-3 кадра, без рывка
                this.cameraSmoothY += gap * (1.0 - Math.pow(0.25, dt));
            } else if (gap > 2.0) {
                // страховка: реальная высота уехала слишком далеко вверх
                // (климб, сетбек) — плавно догоняем, модель не отстаёт абсурдно
                this.cameraSmoothY += (gap - 2.0) * (1.0 - Math.pow(0.9, dt));
            } else if (gap < 0.0) {
                // спуск: догоняем мягко (≈13% на кадр 20 мс); в конце дуги
                // прыжка высоты и так сходятся в ноль
                this.cameraSmoothY += gap * (1.0 - Math.pow(0.875, dt));
            }
            // иначе (подъём дуги прыжка в пределах 2 блоков): камера и модель
            // не тянутся вверх вообще — прыжок полностью невидим для клиента
        }

        return realY - this.cameraSmoothY;
    }

    private void doGrimEflyStuff() {
        if (this.bounce.getValue() && mc.player.isOnGround()) {
            mc.player.jump();
        }
    }

    public static boolean shouldCancelSound(Identifier soundId) {
        ElytraBounce module = INSTANCE;
        if (module == null || !module.isToggled() || !module.fakeFly.getValue()) return false;

        for (Identifier id : List.of(
            Identifier.of("minecraft:item.armor.equip_generic"),
            Identifier.of("minecraft:item.armor.equip_netherite"),
            Identifier.of("minecraft:item.armor.equip_elytra"),
            Identifier.of("minecraft:item.armor.equip_diamond"),
            Identifier.of("minecraft:item.armor.equip_gold"),
            Identifier.of("minecraft:item.armor.equip_iron"),
            Identifier.of("minecraft:item.armor.equip_chain"),
            Identifier.of("minecraft:item.armor.equip_leather"),
            Identifier.of("minecraft:item.elytra.flying")
        )) {
            if (id.equals(soundId)) {
                return true;
            }
        }
        return false;
    }

    private Vec3 playerPos() {
        return new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());
    }

    private boolean hasEnoughFood() {
        return mc.player.getHungerManager().getFoodLevel() >= 20;
    }
}
package me.collective.module.player;
import me.collective.render.ShapeMode;

import me.collective.setting.Setting;
import me.collective.setting.SettingGroup;
import me.collective.setting.BoolSetting;
import me.collective.setting.DoubleSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.IntSetting;
import me.collective.setting.BlockListSetting;
import me.collective.setting.SettingColorSetting;

import me.collective.util.SwapManager;
import me.collective.util.RotationUtils;
import me.collective.util.printer.AirPlaceExecutor;
import me.collective.util.printer.PlacementSolver;
import me.collective.util.printer.PrinterRegion;
import me.collective.util.printer.SchematicAccess;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import me.collective.event.events.Render3DEvent;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.category.Category;
import me.collective.util.FindItemResult;
import me.collective.util.InvUtils;
import me.collective.util.SettingColor;
import me.collective.event.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * LitematicaPrinter (порт из BepHax): автоматическая сборка выбранной схемы Litematica.
 *
 * Фазовая машина SCAN -> ALIGN -> HOLD -> DELAY: сканируем кандидатов в радиусе,
 * подбираем решение установки (PlacementSolver), удерживаем нужный слот через
 * SwapManager, крутим серверную ротацию через RotationUtils и шлём клик
 * (AirPlaceExecutor). При отсутствии решения/материала цель уходит в кулдаун.
 *
 * 1.21.11 (Yarn): isAutoSpinAttack -> isUsingRiptide, isPassenger -> hasVehicle,
 * keyJump.isDown -> options.jumpKey.isPressed(), BlockState.contains/get,
 * CollisionView.canPlace(state, pos, ShapeContext), MultifaceBlock.getProperty(Direction).
 */
public class LitematicaPrinter extends Module {
    private static final boolean LITEMATICA = FabricLoader.getInstance().isModLoaded("litematica");

    private final SettingGroup sgGeneral;
    private final SettingGroup sgTiming;
    private final SettingGroup sgRender;

    private final Setting<Mode> mode;
    private final Setting<OnMove> onMove;
    private final Setting<Double> range;
    private final Setting<Boolean> onlySelected;
    private final Setting<AirPlaceExecutor.Method> airPlaceMethod;
    private final Setting<Boolean> pauseOnUse;
    private final Setting<List<Block>> ignoredBlocks;
    private final Setting<Boolean> debug;
    private final Setting<Integer> placeDelay;
    private final Setting<Integer> rotationHold;
    private final Setting<Integer> replaceCooldown;
    private final Setting<Double> turnSpeed;
    private final Setting<Boolean> snapRotation;
    private final Setting<Double> alignTolerance;
    private final Setting<Boolean> render;
    private final Setting<ShapeMode> shapeMode;
    private final Setting<SettingColor> sideColor;
    private final Setting<SettingColor> lineColor;
    private final Setting<SettingColor> targetColor;

    private static final int ALIGN_TIMEOUT = 40;
    private static final int WATCHDOG_TICKS = 200;
    private static final int T_COOLED = 0;
    private static final int T_OBSTRUCTED = 1;
    private static final int T_GONE = 2;

    /** Свойства-«счётчики» (слои, свечи и т.п.), по которым блок можно досыпать в уже стоящий. */
    private static final IntProperty[] COUNT_PROPERTIES = new IntProperty[]{
        Properties.LAYERS,
        Properties.CANDLES,
        Properties.PICKLES,
        Properties.EGGS,
        Properties.FLOWER_AMOUNT,
        Properties.SEGMENT_AMOUNT
    };

    private Phase phase;
    private BlockPos target;
    private BlockState targetState;
    private PlacementSolver.Solution solution;
    private int timer;
    private int alignTicks;
    private long tick;
    private BlockPos lastAbandoned;
    private int repeatAbandons;
    private long lastMissingWarnTick;
    private String lastDebugMsg;
    private long lastDebugTick;
    private long lastIdleLogTick;
    private long lastProgressTick;
    private boolean sawWork;
    private int consecutiveResets;
    private final Map<BlockPos, Long> cooldown;
    private final List<BlockPos> preview;
    private List<PrinterRegion> regions;

    public LitematicaPrinter() {
        super("LitematicaPrinter", "Автоматически строит выбранную схему Litematica: безопасная для Grim установка блоков.", Category.PLAYER);

        sgGeneral = settings.getDefaultGroup();
        sgTiming = settings.createGroup("Тайминги");
        sgRender = settings.createGroup("Рендер");

        mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("режим")
            .description("Авто: реальная грань, если тривиально, иначе air-place. Эир-плейс: всегда через воздух. Легит: только по реальным граням.")
            .defaultValue(Mode.Auto)
            .build()
        );
        onMove = sgGeneral.add(new EnumSetting.Builder<OnMove>()
            .name("при-движении")
            .description("Свободно: печатаем пока вы движетесь — камера и движение полностью ваши, серверный взгляд зажимается в 45-градусный оффсет от вашего yaw, что сохраняет точность движения в симуляции Grim. Пауза: прекращать установку при движении. Синхрон: продолжать установку, но серверный взгляд ведёт ваше движение.")
            .defaultValue(OnMove.Free)
            .build()
        );
        range = sgGeneral.add(new DoubleSetting.Builder()
            .name("дистанция")
            .description("Максимальная дистанция установки от глаз. Держите на 4.5 или ниже, чтобы проходить reach-проверку Grim.")
            .defaultValue(4.5)
            .min(1.0)
            .max(6.0)
            .sliderMax(6.0)
            .build()
        );
        onlySelected = sgGeneral.add(new BoolSetting.Builder()
            .name("только-выбранная-схема")
            .description("Строить только выбранную схему в Litematica. Выкл.: строить все включённые схемы в мире схем.")
            .defaultValue(true)
            .build()
        );
        airPlaceMethod = sgGeneral.add(new EnumSetting.Builder<AirPlaceExecutor.Method>()
            .name("способ-эир-плейса")
            .description("По умолчанию: обычный ванильный интерэкт, который принимает 2b2t. Грим: оборачиваем в подмену оффхенда, чтобы античит не видел, каким предметом поставлен блок — нужно только на серверах, отменяющих air-place.")
            .defaultValue(AirPlaceExecutor.Method.Default)
            .build()
        );
        pauseOnUse = sgGeneral.add(new BoolSetting.Builder()
            .name("пауза-при-использовании")
            .description("Пауза печати, пока вы используете предмет (едите, натягиваете лук и т.п.).")
            .defaultValue(true)
            .build()
        );
        ignoredBlocks = sgGeneral.add(new BlockListSetting.Builder()
            .name("игнорируемые-блоки")
            .description("Блоки схемы, которые принтер никогда не ставит. По умолчанию — только-креативные и недоступные в выживании (рамки портала Края, спавнеры, заражённые блоки, ...).")
            .defaultValue(
                Blocks.END_PORTAL_FRAME,
                Blocks.END_PORTAL,
                Blocks.NETHER_PORTAL,
                Blocks.END_GATEWAY,
                Blocks.BEDROCK,
                Blocks.BARRIER,
                Blocks.LIGHT,
                Blocks.COMMAND_BLOCK,
                Blocks.CHAIN_COMMAND_BLOCK,
                Blocks.REPEATING_COMMAND_BLOCK,
                Blocks.STRUCTURE_BLOCK,
                Blocks.STRUCTURE_VOID,
                Blocks.JIGSAW,
                Blocks.TEST_BLOCK,
                Blocks.TEST_INSTANCE_BLOCK,
                Blocks.SPAWNER,
                Blocks.TRIAL_SPAWNER,
                Blocks.VAULT,
                Blocks.REINFORCED_DEEPSLATE,
                Blocks.BUDDING_AMETHYST,
                Blocks.SUSPICIOUS_SAND,
                Blocks.SUSPICIOUS_GRAVEL,
                Blocks.INFESTED_STONE,
                Blocks.INFESTED_COBBLESTONE,
                Blocks.INFESTED_STONE_BRICKS,
                Blocks.INFESTED_MOSSY_STONE_BRICKS,
                Blocks.INFESTED_CRACKED_STONE_BRICKS,
                Blocks.INFESTED_CHISELED_STONE_BRICKS,
                Blocks.INFESTED_DEEPSLATE,
                Blocks.PETRIFIED_OAK_SLAB,
                Blocks.FARMLAND,
                Blocks.DIRT_PATH,
                Blocks.FROSTED_ICE,
                Blocks.CHORUS_PLANT
            )
            .build()
        );
        debug = sgGeneral.add(new BoolSetting.Builder()
            .name("отладка")
            .description("Логировать решения в чат: выбранные цели, каждый отказ с причиной и почему принтер простаивает.")
            .defaultValue(false)
            .build()
        );

        placeDelay = sgTiming.add(new IntSetting.Builder()
            .name("задержка-установки")
            .description("Дополнительные тики простоя между установками. 0 ставит каждый тик — как при зажатом ПКМ.")
            .defaultValue(0)
            .min(0)
            .max(20)
            .build()
        );
        rotationHold = sgTiming.add(new IntSetting.Builder()
            .name("удержание-ротации")
            .description("Тики, в течение которых держим прицел на блоке после установки, чтобы пост-полётная проверка прямой видимости Grim прошла. Поднимите, если блоки не ставятся или ставятся не туда на высокой скорости.")
            .defaultValue(2)
            .min(0)
            .max(10)
            .build()
        );
        replaceCooldown = sgTiming.add(new IntSetting.Builder()
            .name("кулдаун-замены")
            .description("Тики до повторной проверки позиции. Поднимите на серверах с высоким пингом (2b2t), чтобы блок не ставился дважды до подтверждения сервером.")
            .defaultValue(20)
            .min(1)
            .max(100)
            .build()
        );
        turnSpeed = sgTiming.add(new DoubleSetting.Builder()
            .name("скорость-поворота")
            .description("Градусов за тик, на которые может двигаться серверный прицел. Меньше — человечнее, но медленнее установка.")
            .defaultValue(180.0)
            .min(20.0)
            .max(360.0)
            .sliderMax(360.0)
            .build()
        );
        snapRotation = sgTiming.add(new BoolSetting.Builder()
            .name("мгновенная-ротация")
            .description("Поворачивать к блоку за один тик (по-прежнему ограничено скоростью поворота) вместо плавного прихода за три-пять. Плавная кривая существует только против Grim DuplicateRotPlace — это эксперимент, на 2b2t не применяется.")
            .defaultValue(true)
            .build()
        );
        alignTolerance = sgTiming.add(new DoubleSetting.Builder()
            .name("допуск-прицела")
            .description("Насколько точно прицел должен совпасть с целью перед установкой (в градусах).")
            .defaultValue(3.0)
            .min(0.1)
            .max(15.0)
            .sliderMax(15.0)
            .build()
        );

        render = sgRender.add(new BoolSetting.Builder()
            .name("рендер")
            .description("Отрисовывать блоки, поставленные в очередь на установку.")
            .defaultValue(true)
            .build()
        );
        shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("форма-отрисовки")
            .description("Как отрисовываются фигуры.")
            .defaultValue(ShapeMode.Both)
            .visible(() -> render.get())
            .build()
        );
        sideColor = sgRender.add(new SettingColorSetting("цвет-граней", new SettingColor(45, 225, 150, 40)));
        lineColor = sgRender.add(new SettingColorSetting("цвет-линий", new SettingColor(45, 225, 150, 255)));
        targetColor = sgRender.add(new SettingColorSetting("цвет-цели", new SettingColor(255, 200, 0, 255)));

        phase = Phase.SCAN;
        lastMissingWarnTick = -1000L;
        cooldown = new HashMap<>();
        preview = new ArrayList<>();
    }

    @Override
    public void onActivate() {
        if (!LITEMATICA) {
            this.error("Litematica не установлена - Litematica Printer требует Litematica + malilib.");
            this.toggle();
            return;
        }
        this.reset();
    }

    @Override
    public void onDeactivate() {
        this.reset();
        RotationUtils.getInstance().clearRotations(this);
    }

    private void reset() {
        this.phase = Phase.SCAN;
        this.target = null;
        this.targetState = null;
        this.solution = null;
        this.timer = 0;
        this.alignTicks = 0;
        this.cooldown.clear();
        this.preview.clear();
        this.lastAbandoned = null;
        this.repeatAbandons = 0;
        this.regions = null;
        this.lastDebugMsg = null;
        this.lastProgressTick = this.tick;
        this.sawWork = false;
        this.consecutiveResets = 0;
    }

    private void debug(String msg) {
        if (!this.debug.get()) {
            return;
        }
        if (msg.equals(this.lastDebugMsg) && this.tick - this.lastDebugTick < 20L) {
            return;
        }
        this.lastDebugMsg = msg;
        this.lastDebugTick = this.tick;
        this.info("(highlight)[debug](default) " + msg);
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (this.mc.player == null || this.mc.world == null) {
            return;
        }
        if (!LITEMATICA) {
            this.toggle();
            return;
        }
        this.tick++;
        this.cooldown.values().removeIf(exp -> exp <= this.tick);
        if (!SchematicAccess.hasActivePlacement()) {
            this.preview.clear();
            this.regions = null;
            this.abandonTarget("no active placement");
            this.debug("idle: no schematic placement selected/enabled in Litematica");
            return;
        }
        this.regions = this.onlySelected.get() ? SchematicAccess.getSelectedRegions() : null;
        if (!(this.phase != Phase.ALIGN && this.phase != Phase.HOLD || this.solution != null && this.target != null)) {
            this.phase = Phase.SCAN;
        }
        if (this.sawWork && this.tick - this.lastProgressTick >= WATCHDOG_TICKS) {
            this.abandonTarget("watchdog reset");
            this.cooldown.clear();
            RotationUtils.getInstance().clearRotations(this);
            SwapManager.getInstance().releaseNow(this);
            this.phase = Phase.SCAN;
            this.lastAbandoned = null;
            this.repeatAbandons = 0;
            this.lastProgressTick = this.tick;
            this.sawWork = false;
            String msg = "no placement for 10s despite pending work - printer state reset";
            if (this.consecutiveResets++ == 0) {
                this.info(msg);
            } else {
                this.debug(msg);
            }
        }
        RotationUtils rotation = RotationUtils.getInstance();
        if (this.solution == null && rotation.isOwner(this) && rotation.isOffsetRotation()) {
            rotation.clearRotations(this);
        }
        switch (this.phase) {
            case DELAY:
                if (--this.timer > 0) {
                    break;
                }
                this.phase = Phase.SCAN;
                if (!this.holdFire()) {
                    this.acquireTarget();
                } else {
                    this.debug("paused: " + this.holdFireReason());
                }
                break;
            case SCAN:
                if (!this.holdFire()) {
                    this.acquireTarget();
                } else {
                    this.debug("paused: " + this.holdFireReason());
                }
                break;
            case ALIGN:
                if (this.holdFire()) {
                    this.abandonTarget(this.holdFireReason());
                    return;
                }
                if (++this.alignTicks > ALIGN_TIMEOUT) {
                    this.abandonTarget(20, "align timeout (aim or slot held by another module?)");
                    return;
                }
                if (!this.inBuildSet(this.target)) {
                    this.abandonTarget("left the build set");
                    return;
                }
                if (!this.solution.anyRotation() && this.rotationUnsafe()) {
                    this.abandonTarget("rotation unsafe (gliding/swimming/riding/sprint-jump)");
                    return;
                }
                if (this.onMove.get() == OnMove.Free && !this.solution.anyRotation() && this.solution.latticeK() == null && this.hasMovementInput()) {
                    this.abandonTarget("movement started, re-solving on lattice");
                    return;
                }
                ItemStack required = SchematicAccess.getRequiredItem(this.targetState, this.target);
                if (required == null || required.isEmpty() || !InvUtils.findInHotbar(required.getItem()).found()) {
                    this.warnMissing(required);
                    this.abandonTarget(20, "material no longer in hotbar");
                    return;
                }
                if (!this.selectItem(required)) {
                    this.debug("align: swap hold refused - retrying");
                    return;
                }
                PlacementSolver.Solution s = PlacementSolver.revalidate(
                    this.solution, this.target, this.targetState, required, this.range.get(), this.mc.player.isSprinting()
                );
                if (s == null) {
                    this.abandonTarget(10, "revalidate failed (drifted out of reach/occluded)");
                    return;
                }
                this.solution = s;
                this.assertAim();
                break;
            case HOLD:
                if (!this.solution.anyRotation() && this.solution.latticeK() == null) {
                    float[] rot = RotationUtils.getRotationsTo(this.mc.player.getEyePos(), this.solution.hit().getPos());
                    this.solution = new PlacementSolver.Solution(
                        this.solution.hit(), this.solution.hand(), rot[0], rot[1], this.solution.predicted(), null, false
                    );
                }
                this.assertAim();
                break;
        }
    }

    private boolean holdFire() {
        if (this.pauseOnUse.get() && this.mc.player.isUsingItem()) {
            return true;
        }
        return this.onMove.get() == OnMove.Pause && this.hasMovementInput();
    }

    private String holdFireReason() {
        if (this.pauseOnUse.get() && this.mc.player.isUsingItem()) {
            return "using an item (pause-on-use)";
        }
        return "moving (Pause mode)";
    }

    private boolean rotationUnsafe() {
        if (this.mc.player.isGliding() || this.mc.player.isSwimming() || this.mc.player.isUsingRiptide() || this.mc.player.hasVehicle()) {
            return true;
        }
        // Спрыг-прыжок: ротацию в этот момент ставить опасно.
        if (this.mc.player.isSprinting() && this.mc.player.isOnGround() && this.mc.options.jumpKey.isPressed()) {
            return true;
        }
        return this.onMove.get() == OnMove.Pause && this.hasMovementInput();
    }

    private boolean hasMovementInput() {
        if (this.mc.options.forwardKey.isPressed()
            || this.mc.options.backKey.isPressed()
            || this.mc.options.leftKey.isPressed()
            || this.mc.options.rightKey.isPressed()) {
            return true;
        }
        // 1.21.11: player.input теперь Input; оверрайд движения идёт через InputAccessor (модульный миксин).
        if (this.mc.player != null && this.mc.player.input != null) {
            net.minecraft.util.math.Vec2f move = this.mc.player.input.getMovementInput();
            return Math.abs(move.x) > 1.0E-4f || Math.abs(move.y) > 1.0E-4f;
        }
        return false;
    }

    private void assertAim() {
        if (this.solution.anyRotation()) {
            return;
        }
        RotationUtils rot = RotationUtils.getInstance();
        boolean ok;
        if (this.solution.latticeK() != null) {
            ok = rot.setRotationOffset(this, SwapManager.Priority.PLACE, this.solution.latticeK(), this.solution.pitch());
        } else {
            boolean sync = this.onMove.get() == OnMove.Sync || this.phase == Phase.HOLD && this.hasMovementInput();
            ok = sync
                ? rot.setRotationFull(this, SwapManager.Priority.PLACE, this.solution.yaw(), this.solution.pitch(), this.turnSpeed.get())
                : this.snapRotation.get()
                    ? rot.setRotationSilentDirect(this, SwapManager.Priority.PLACE, this.solution.yaw(), this.solution.pitch(), this.turnSpeed.get())
                    : rot.setRotationSilent(this, SwapManager.Priority.PLACE, this.solution.yaw(), this.solution.pitch(), this.turnSpeed.get());
        }
        if (!ok) {
            this.debug("aim claim refused (rotation owned by a higher-priority module)");
        }
    }

    private void abandonTarget(String reason) {
        this.abandonTarget(0, reason);
    }

    private void abandonTarget(int cooldownTicks, String reason) {
        if (this.target != null) {
            int cd = cooldownTicks;
            if (this.target.equals(this.lastAbandoned)) {
                if (++this.repeatAbandons >= 3) {
                    cd = Math.max(cd, 20);
                }
            } else {
                this.lastAbandoned = this.target.toImmutable();
                this.repeatAbandons = 1;
            }
            if (cd > 0) {
                this.cooldown.put(this.target.toImmutable(), this.tick + cd);
            }
            this.debug("abandon " + this.target.toShortString() + ": " + reason + (cd > 0 ? " (retry in " + cd + "t)" : ""));
        }
        if (this.solution != null) {
            RotationUtils.getInstance().clearRotations(this);
        }
        this.solution = null;
        this.target = null;
        this.targetState = null;
        this.alignTicks = 0;
        if (this.phase == Phase.ALIGN || this.phase == Phase.HOLD) {
            this.phase = Phase.SCAN;
        }
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (this.mc.player == null || this.mc.world == null || this.solution == null || this.target == null) {
            return;
        }
        if (this.phase == Phase.ALIGN) {
            if (this.holdFire()) {
                this.abandonTarget(this.holdFireReason());
                return;
            }
            if (!this.inBuildSet(this.target)) {
                this.abandonTarget("left the build set");
                return;
            }
            if (!this.solution.anyRotation() && this.rotationUnsafe()) {
                this.abandonTarget("rotation unsafe (gliding/swimming/riding/sprint-jump)");
                return;
            }
            if (this.solution.anyRotation()) {
                AirPlaceExecutor.airPlace(this.solution.hit(), this.solution.predicted(), this.airPlaceMethod.get());
            } else {
                RotationUtils rot = RotationUtils.getInstance();
                if (!rot.isRotating() || !rot.isAlignedFor(this, this.alignTolerance.get())) {
                    this.debug("waiting for aim (rotating=" + rot.isRotating() + ", owned=" + rot.isOwner(this) + ")");
                    return;
                }
                if (this.solution.latticeK() != null && this.mc.player.isSprinting() && Math.abs(this.solution.latticeK()) > 1) {
                    this.abandonTarget("sprint started outside lattice window");
                    return;
                }
                ItemStack required = SchematicAccess.getRequiredItem(this.targetState, this.target);
                if (required == null || required.isEmpty()) {
                    this.abandonTarget(20, "no required item resolved");
                    return;
                }
                if (!PlacementSolver.confirmSent(this.solution, this.target, this.targetState, required)) {
                    this.debug("sent-rotation simulation mismatch - deferring click");
                    return;
                }
                if (this.solution.latticeK() != null) {
                    AirPlaceExecutor.airPlace(this.solution.hit(), this.solution.predicted(), this.airPlaceMethod.get());
                } else {
                    AirPlaceExecutor.place(this.solution.hit(), this.target, this.solution.hand(), this.solution.predicted(), this.airPlaceMethod.get());
                }
            }
            int hold = this.solution.anyRotation() || this.solution.latticeK() != null ? 0 : this.rotationHold.get();
            this.debug("placed " + this.target.toShortString() + (hold > 0 ? " (holding aim " + hold + "t)" : ""));
            if (hold > 0) {
                this.preview.remove(this.target);
                this.phase = Phase.HOLD;
                this.timer = hold;
                return;
            }
            this.completePlacement();
        } else if (this.phase == Phase.HOLD) {
            if (this.timer-- > 0) {
                return;
            }
            this.completePlacement();
        }
    }

    private void completePlacement() {
        this.lastAbandoned = null;
        this.repeatAbandons = 0;
        this.lastProgressTick = this.tick;
        this.consecutiveResets = 0;
        this.cooldown.put(this.target.toImmutable(), this.tick + this.replaceCooldown.get());
        this.preview.remove(this.target);
        this.finishTarget();
    }

    private void acquireTarget() {
        boolean allowFace = this.mode.get() != Mode.AirPlace;
        boolean allowAir = this.mode.get() != Mode.Legit;
        int[] tally = new int[3];
        int unsolvable = 0;
        int noMaterial = 0;
        List<BlockPos> candidates = this.scan();
        this.sawWork = !candidates.isEmpty();
        for (BlockPos c : candidates) {
            BlockState ts = this.candidateState(c, tally);
            if (ts == null) {
                continue;
            }
            ItemStack required = SchematicAccess.getRequiredItem(ts, c);
            if (!this.hasMaterial(c, required)) {
                noMaterial++;
                continue;
            }
            PlacementSolver.Solution s = allowAir ? PlacementSolver.solveAnyRotation(c, ts, required, this.range.get()) : null;
            if (s == null && !this.rotationUnsafe()) {
                boolean lattice = this.onMove.get() == OnMove.Free && this.hasMovementInput();
                s = lattice
                    ? PlacementSolver.solveMoving(c, ts, required, this.range.get(), allowFace, allowAir, this.mc.player.isSprinting())
                    : PlacementSolver.solve(c, ts, required, this.range.get(), allowFace, allowAir);
            }
            if (s == null) {
                this.cooldown.put(c, this.tick + 20L);
                unsolvable++;
                continue;
            }
            if (!this.selectItem(required)) {
                this.debug("swap hold refused (conflict tick or higher-priority session) - retrying next tick");
                break;
            }
            this.target = c;
            this.targetState = ts;
            this.solution = s;
            this.phase = Phase.ALIGN;
            this.alignTicks = 0;
            this.assertAim();
            this.debug("target " + c.toShortString() + " -> " + LitematicaPrinter.describeSolution(s));
            return;
        }
        this.target = null;
        if (this.tick - this.lastIdleLogTick >= 20L) {
            this.lastIdleLogTick = this.tick;
            if (candidates.isEmpty()) {
                this.debug(
                    this.regions != null && this.regions.isEmpty()
                        ? "idle: selected placement has no enabled sub-regions"
                        : "idle: no mismatched blocks in range (finished, out of reach, or hidden by render layers)"
                );
            } else {
                this.debug("idle: " + candidates.size() + " candidates (" + tally[T_COOLED] + " cooling down, "
                    + tally[T_OBSTRUCTED] + " blocked by an entity, " + tally[T_GONE] + " gone/occupied, "
                    + unsolvable + " unsolvable from here, " + noMaterial + " missing material)");
            }
        }
    }

    private static String describeSolution(PlacementSolver.Solution s) {
        if (s.anyRotation()) {
            return "any-rotation";
        }
        if (s.latticeK() != null) {
            return "lattice k=" + s.latticeK();
        }
        return String.format("aim %.1f/%.1f", s.yaw(), s.pitch());
    }

    private BlockState candidateState(BlockPos c, int[] tally) {
        if (this.cooldown.containsKey(c)) {
            tally[T_COOLED]++;
            return null;
        }
        BlockState ts = SchematicAccess.getTargetState(c);
        if (ts == null || ts.isAir()) {
            this.cooldown.put(c, this.tick + 10L);
            tally[T_GONE]++;
            return null;
        }
        if (!this.mc.world.canPlace(ts, c, ShapeContext.absent())) {
            this.cooldown.put(c, this.tick + 10L);
            tally[T_OBSTRUCTED]++;
            return null;
        }
        BlockState ws = this.mc.world.getBlockState(c);
        if (!ws.isReplaceable() && !LitematicaPrinter.isMergeCandidate(ws, ts)) {
            this.cooldown.put(c, this.tick + 10L);
            tally[T_GONE]++;
            return null;
        }
        if (PlacementSolver.statesMatch(ts, ws)) {
            this.cooldown.put(c, this.tick + 10L);
            tally[T_GONE]++;
            return null;
        }
        return ts;
    }

    private boolean hasMaterial(BlockPos c, ItemStack required) {
        if (required == null || required.isEmpty() || !InvUtils.findInHotbar(required.getItem()).found()) {
            this.cooldown.put(c, this.tick + 20L);
            this.warnMissing(required);
            return false;
        }
        return true;
    }

    private void finishTarget() {
        this.phase = this.placeDelay.get() > 0 ? Phase.DELAY : Phase.SCAN;
        this.timer = this.placeDelay.get();
        this.alignTicks = 0;
        this.solution = null;
        this.target = null;
        this.targetState = null;
    }

    private boolean selectItem(ItemStack required) {
        if (required == null || required.isEmpty()) {
            return false;
        }
        FindItemResult res = InvUtils.findInHotbar(required.getItem());
        if (!res.found()) {
            return false;
        }
        return SwapManager.getInstance().hold(this, res.slot(), SwapManager.Priority.PLACE, 4);
    }

    private void warnMissing(ItemStack required) {
        if (this.tick - this.lastMissingWarnTick < 100L) {
            return;
        }
        if (required != null && !required.isEmpty() && InvUtils.findInHotbar(required.getItem()).found()) {
            return;
        }
        this.info("Missing material in hotbar: " + (required == null || required.isEmpty() ? "unknown item" : required.getName().getString()));
        this.lastMissingWarnTick = this.tick;
    }

    private List<BlockPos> scan() {
        this.preview.clear();
        List<BlockPos> out = new ArrayList<>();
        Vec3 eye = this.mc.player.getEyePos();
        BlockPos origin = BlockPos.ofFloored(eye);
        int r = (int) Math.ceil(this.range.get()) + 1;
        double reachSq = this.range.get() * this.range.get();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        HashSet<Block> ignored = new HashSet<>(this.ignoredBlocks.get());
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockState ws;
                    BlockState ts;
                    pos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!this.inBuildSet(pos)
                        || (ts = SchematicAccess.getTargetState(pos)) == null
                        || ts.isAir()
                        || ignored.contains(ts.getBlock())
                        || !(ws = this.mc.world.getBlockState(pos)).isReplaceable() && !LitematicaPrinter.isMergeCandidate(ws, ts)
                        || PlacementSolver.statesMatch(ts, ws)
                        || eye.squaredDistanceTo(Vec3.ofCenter(pos)) > reachSq) {
                        continue;
                    }
                    out.add(pos.toImmutable());
                }
            }
        }
        out.sort(Comparator.comparingDouble(p -> eye.squaredDistanceTo(Vec3.ofCenter(p))));
        if (this.render.get()) {
            this.preview.addAll(out);
        }
        return out;
    }

    private boolean inBuildSet(BlockPos pos) {
        if (!SchematicAccess.isWithinRenderLayers(pos)) {
            return false;
        }
        if (this.regions == null) {
            return true;
        }
        for (PrinterRegion r : this.regions) {
            if (r.contains(pos.getX(), pos.getY(), pos.getZ())) {
                return true;
            }
        }
        return false;
    }

    /** Можно ли «досыпать» целевое состояние в уже стоящее (слои, свечи, мультифейс). */
    private static boolean isMergeCandidate(BlockState ws, BlockState ts) {
        if (ws.getBlock() != ts.getBlock()) {
            return false;
        }
        if (ws.contains(Properties.SLAB_TYPE)) {
            return ts.get(Properties.SLAB_TYPE) == SlabType.DOUBLE && ws.get(Properties.SLAB_TYPE) != SlabType.DOUBLE;
        }
        for (IntProperty p : COUNT_PROPERTIES) {
            if (ws.contains(p)) {
                return ts.get(p) > ws.get(p);
            }
        }
        if (PlacementSolver.faceDefinedByPlacement(ts)) {
            boolean missing = false;
            for (Direction d : Direction.values()) {
                BooleanProperty p = MultifaceBlock.getProperty(d);
                if (p == null || !ws.contains(p) || !ts.contains(p)) {
                    continue;
                }
                boolean have = ws.get(p);
                boolean want = ts.get(p);
                if (have && !want) {
                    return false;
                }
                if (!have && want) {
                    missing = true;
                }
            }
            return missing;
        }
        return false;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!this.render.get()) {
            return;
        }
        for (BlockPos p : this.preview) {
            event.renderer.box(p, this.sideColor.get(), this.lineColor.get(), this.shapeMode.get(), 0);
        }
        if (this.target != null) {
            event.renderer.box(this.target, this.targetColor.get(), this.targetColor.get(), this.shapeMode.get(), 0);
        }
    }

    public enum Mode {
        Auto,
        AirPlace,
        Legit
    }

    public enum OnMove {
        Free,
        Pause,
        Sync
    }

    private enum Phase {
        SCAN,
        ALIGN,
        HOLD,
        DELAY
    }
}
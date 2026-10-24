package me.collective.module.player;

import me.collective.setting.Setting;
import me.collective.setting.SettingGroup;
import me.collective.setting.BoolSetting;
import me.collective.setting.DoubleSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.IntSetting;
import me.collective.setting.StringSetting;
import me.collective.setting.SettingColorSetting;

import me.collective.util.sort.ChestNavigator;
import me.collective.util.sort.ContainerGeometry;
import me.collective.util.sort.SortGroupKey;
import me.collective.util.sort.SortPlanner;
import me.collective.util.sort.SortZone;
import me.collective.util.sort.SorterWorldStore;
import me.collective.util.tracker.ChestTrackerDataManager;
import me.collective.util.tracker.ChestTrackerDataV2;
import me.collective.util.tracker.TrackedContainer;
import me.collective.event.events.PacketEvent;
import me.collective.event.events.Render3DEvent;
import me.collective.event.events.TickEvent;
import me.collective.manager.FriendManager;
import me.collective.render.ShapeMode;
import me.collective.module.Module;
import me.collective.category.Category;
import me.collective.util.SettingColor;
import me.collective.event.EventHandler;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Автосортировка стеша (порт из BepHax, там — StashSorter).
 *
 * <p>Как работает: сортировщик не перекладывает вещи из сундука в сундук
 * напрямую (так нельзя — открыт только один контейнер), а возит их в
 * инвентаре: забрал нужное из источника → положил в получателя. Планировщик
 * заранее решает, где должен лежать каждый тип вещей, а машина состояний
 * обходит сундуки и перекладывает.
 *
 * <p>Данные берутся из индекса контейнеров ({@link ContainerIndex}), поэтому
 * перед сортировкой нужно один раз открыть сундуки стеша. Зона задаётся
 * командой {@code .сорт зона}, метки — {@code .сорт метка}.
 */
public class StashSorter extends Module {
    private static final Logger LOG = LoggerFactory.getLogger("B2XY/StashSorter");

    /** Сколько раз пробуем открыть сундук, прежде чем бросить его. */
    private static final int MAX_RETRIES = 3;
    /** Сколько полных проходов по стешу: один проход не всё разбирает. */
    private static final int MAX_PASSES = 4;
    private static final int OPEN_ALIGN_TIMEOUT = 40;
    /** На сколько градусов доводим взгляд, чтобы попасть в сундук. */
    private static final float AIM_EPSILON = 3.0F;
    private static final int AIM_HOLD_TICKS = 1;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPathing = settings.createGroup("Путь");
    private final SettingGroup sgOverlay = settings.createGroup("Оверлей");
    private final SettingGroup sgTiming = settings.createGroup("Тайминги");
    private final SettingGroup sgSafety = settings.createGroup("Безопасность");

    private final Setting<Boolean> moveShulkers = sgGeneral.add(new BoolSetting.Builder()
        .name("перемещать-шалкеры")
        .description("Сортировать и шалкеры, и рассыпанные предметы.")
        .defaultValue(true)
        .build());

    private final Setting<LooseItems> looseItems = sgGeneral.add(new EnumSetting.Builder<LooseItems>()
        .name("рассыпанные-предметы")
        .description("Что делать с предметами, которые лежат россыпью, а не в шалкере.")
        .defaultValue(LooseItems.MIXED_CHEST)
        .build());

    private final Setting<Integer> minShulkersForOwnChest = sgGeneral.add(new IntSetting.Builder()
        .name("мин-шалкеров-для-своего")
        .description("Сколько шалкеров одной категории нужно, чтобы выделить им отдельный сундук. Меньше — в общий.")
        .defaultValue(4)
        .min(1)
        .max(64)
        .sliderRange(1, 32)
        .build());

    private final Setting<Integer> minItemsForOwnChest = sgGeneral.add(new IntSetting.Builder()
        .name("мин-предметов-для-своего")
        .description("Сколько предметов одной категории нужно, чтобы выделить им отдельный сундук.")
        .defaultValue(32)
        .min(1)
        .max(2048)
        .sliderRange(1, 256)
        .build());

    private final Setting<Integer> reservedHotbarSlots = sgGeneral.add(new IntSetting.Builder()
        .name("занято-хотбара")
        .description("Сколько первых слотов хотбара не трогать. Туда кладут то, что нужно оставить при себе.")
        .defaultValue(1)
        .min(0)
        .max(8)
        .build());

    private final Setting<Boolean> depositInventoryFirst = sgGeneral.add(new BoolSetting.Builder()
        .name("сначала-выложить-инвентарь")
        .description("Сначала разложить то, что уже лежит в инвентаре, и только потом начать сбор.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> verifyEveryChest = sgGeneral.add(new BoolSetting.Builder()
        .name("проверять-каждый-сундук")
        .description("Первый проход открывает все сундуки зоны подряд, даже если план их не тронул. Надёжнее, но дольше.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> safePathing = sgPathing.add(new BoolSetting.Builder()
        .name("не-ломать-блоки")
        .description("Запретить Baritone ломать и ставить блоки, пока идёт сортировка.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> navTimeout = sgTiming.add(new IntSetting.Builder()
        .name("таймаут-пути")
        .description("Сколько тиков ждать, пока дойдёт до сундука, потом бросить задание.")
        .defaultValue(200)
        .min(20)
        .max(2000)
        .build());

    private final Setting<Integer> stuckTimeout = sgTiming.add(new IntSetting.Builder()
        .name("таймаут-застревания")
        .description("Сколько тиков ждать прогресса при перекладывании, потом бросить сундук.")
        .defaultValue(60)
        .min(10)
        .max(600)
        .build());

    private final Setting<Integer> settleDelay = sgTiming.add(new IntSetting.Builder()
        .name("задержка-успокоения")
        .description("Сколько тиков постоять неподвижно перед кликом по сундуку.")
        .defaultValue(2)
        .min(0)
        .max(20)
        .build());

    private final Setting<Integer> moveDelay = sgTiming.add(new IntSetting.Builder()
        .name("пауза-перед-ходьбой")
        .description("Задержка перед началом очередного перехода.")
        .defaultValue(2)
        .min(0)
        .max(40)
        .build());

    private final Setting<Integer> openDelay = sgTiming.add(new IntSetting.Builder()
        .name("пауза-после-клика")
        .description("Задержка после клика по сундуку, пока не придёт содержимое.")
        .defaultValue(4)
        .min(0)
        .max(40)
        .build());

    private final Setting<Integer> clicksPerTick = sgTiming.add(new IntSetting.Builder()
        .name("кликов-за-тик")
        .description("Сколько слотов перекладывать за один тик. Больше — быстрее и заметнее.")
        .defaultValue(6)
        .min(1)
        .max(27)
        .build());

    private final Setting<Integer> turnSpeed = sgTiming.add(new IntSetting.Builder()
        .name("скорость-поворота")
        .description("На сколько градусов доворачивается взгляд за тик. 0 — мгновенно.")
        .defaultValue(45)
        .min(0)
        .max(180)
        .build());

    private final Setting<Verbosity> verbosity = sgGeneral.add(new EnumSetting.Builder<Verbosity>()
        .name("подробность")
        .description("Сколько писать в чат. «Только итог» молчит на каждом сундуке "
            + "и в конце показывает сводку пропусков — иначе на большом стеше "
            + "уходит сотня строк.")
        .defaultValue(Verbosity.ТОЛЬКО_ИТОГ)
        .build());

    private final Setting<Boolean> showZones = sgOverlay.add(new BoolSetting.Builder()
        .name("зоны")
        .description("Рисовать рамку зоны сортировки.")
        .defaultValue(true)
        .build());

    private final Setting<SettingColor> zoneColor = sgOverlay.add(new SettingColorSetting("цвет-зоны", new SettingColor(0, 220, 255, 220)));

    private final Setting<Boolean> showLabels = sgOverlay.add(new BoolSetting.Builder()
        .name("подписи")
        .description("Подписывать сундуки: что внутри и сколько свободно.")
        .defaultValue(true)
        .build());

    private final Setting<SettingColor> labelColor = sgOverlay.add(new SettingColorSetting("цвет-подписи", new SettingColor(255, 255, 255, 230)));

    private final Setting<SettingColor> pinnedColor = sgOverlay.add(new SettingColorSetting("цвет-метки", new SettingColor(255, 200, 60, 230)));

    private final Setting<Integer> overlayRange = sgOverlay.add(new IntSetting.Builder()
        .name("дальность-оверлея")
        .description("Дальше этого расстояния подписи и рамки не рисуются.")
        .defaultValue(24)
        .min(4)
        .max(64)
        .build());

    private final Setting<Boolean> pauseOnStrangers = sgSafety.add(new BoolSetting.Builder()
        .name("пауза-при-чужих")
        .description("Остановиться, если рядом появился игрок, которого нет в друзьях.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> pauseOnLag = sgSafety.add(new BoolSetting.Builder()
        .name("пауза-при-лагах")
        .description("Остановиться, если клиент подвисает — при лагах сортировщик кликает не туда.")
        .defaultValue(true)
        .build());

    // --- состояние прогона ---
    private long lastPacketTime = System.currentTimeMillis();

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        lastPacketTime = System.currentTimeMillis();
    }

    private SortState state = SortState.IDLE;
    private SortState lastState = SortState.IDLE;
    private int stateTicks;
    private int timer;
    private int openRetries;
    private boolean menuSettled;
    private int settleTicks;
    private int alignTicks;

    private long startTime;
    private int pass = 1;
    private int movedAtPassStart;
    private int sweptAtPassStart;
    private int chestsSwept;
    private int stacksMoved;
    private int shulkersMoved;
    private int jobIdx;
    private int depositLastCount = -1;
    private boolean keepSlotsCaptured;
    private PauseReason pauseReason = PauseReason.MANUAL;

    private List<SortPlanner.SweepJob> jobs = new ArrayList<>();
    @Nullable private SortPlanner.SweepJob currentJob;
    @Nullable private BlockPos currentDest;
    @Nullable private String currentRouteKey;

    private Map<String, List<BlockPos>> sigTargets = Map.of();
    private Map<String, String> keyCollapse = Map.of();
    private final Set<BlockPos> markedFull = new HashSet<>();
    private final Set<Integer> keepSlots = new HashSet<>();
    private final Set<String> deadRoutes = new HashSet<>();
    private final Map<String, BlockPos> overflowChests = new HashMap<>();
    private final Map<String, String> overflowRoutes = new HashMap<>();
    private final Map<BlockPos, Integer> knownFree = new HashMap<>();
    private final Set<BlockPos> emptyClaims = new HashSet<>();
    private final List<BlockPos> spareEmpties = new ArrayList<>();

    /** Причина пропуска -> сколько раз встретилась. Печатается сводкой в конце. */
    private final Map<String, Integer> pendingNotes = new LinkedHashMap<>();
    private final Map<String, String> pendingNoteDetail = new LinkedHashMap<>();

    private List<BlockPos> unknownCache = List.of();
    private long unknownCacheTime;
    private String unknownCacheDim = "";

    @Nullable private ChestNavigator nav;
    @Nullable private SorterWorldStore worldStore;
    @Nullable private BlockPos selectionCorner1;

    private final List<OverlayEntry> overlayCache = new ArrayList<>();

    public StashSorter() {
        super("StashSorter", "Автоматическая сортировка сундуков стеша.", Category.PLAYER);
    }

    // ------------------------------------------------------------------
    //  Жизненный цикл
    // ------------------------------------------------------------------

    @Override
    public void onActivate() {
        worldStore = new SorterWorldStore();
        worldStore.load();
        ChestTrackerDataManager.onModuleActivate();
        resetRun();
        state = SortState.IDLE;
        lastState = SortState.IDLE;

        List<SortZone> zones = sourceZones();
        if (zones.isEmpty()) {
            info("Зона сортировки не задана. Посмотри на первый угол и выполни: §7.сорт зона§f, потом на второй — ещё раз.");
        }
        else {
            info("Зон: §f" + zones.size() + "§f, сундуков в индексе: §f" + ChestTrackerDataManager.getData().getAllContainers(dimension()).size());
            startSorting();
        }
    }

    @Override
    public void onDeactivate() {
        ChestTrackerDataManager.saveData();
        ChestTrackerDataManager.onModuleDeactivate();
        if (nav != null) {
            nav.restorePathing();
            nav.stop();
        }
        state = SortState.IDLE;
        overlayCache.clear();
    }

    private void resetRun() {
        markedFull.clear();
        deadRoutes.clear();
        overflowChests.clear();
        overflowRoutes.clear();
        emptyClaims.clear();
        spareEmpties.clear();
        keepSlots.clear();
        keepSlotsCaptured = false;
        jobs = new ArrayList<>();
        sigTargets = Map.of();
        keyCollapse = Map.of();
        currentJob = null;
        currentDest = null;
        currentRouteKey = null;
        jobIdx = 0;
        pass = 1;
        movedAtPassStart = 0;
        sweptAtPassStart = 0;
        chestsSwept = 0;
        stacksMoved = 0;
        shulkersMoved = 0;
        openRetries = 0;
        depositLastCount = -1;
        menuSettled = false;
        timer = 0;
        pendingNotes.clear();
        pendingNoteDetail.clear();
        startTime = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------
    //  Тик: один шаг машины состояний за раз
    // ------------------------------------------------------------------

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        ChestTrackerDataManager.tickAutosave();
        if (nav != null) nav.setSafePathing(safePathing.get());

        if (mc.player.isDead()) {
            warning("Игрок умер — сортировка остановлена.");
            toggle();
            return;
        }

        // При лагах кликаем не туда: лучше постоять
        if (pauseOnLag.get() && System.currentTimeMillis() - lastPacketTime > 1500L) return;

        if (state != lastState) {
            lastState = state;
            stateTicks = 0;
            settleTicks = 0;
            alignTicks = 0;
        } else {
            stateTicks++;
        }

        if (pauseOnStrangers.get() && state != SortState.PAUSED && state != SortState.IDLE && strangerNearby()) {
            enterPause(PauseReason.STRANGER, "Рядом чужой игрок");
            return;
        }

        if (state == SortState.PAUSED) {
            if (pauseReason == PauseReason.STRANGER && (!pauseOnStrangers.get() || !strangerNearby())) {
                info("Чужой ушёл — продолжаю.");
                resumeSorting();
            }
            return;
        }

        if (state == SortState.IDLE) return;

        // Вещи, которые уже разложены, сами собой исчезают из слотов переноски
        keepSlots.removeIf(i -> mc.player.getInventory().getStack(i).isEmpty());

        if (timer > 0) {
            timer--;
            return;
        }

        switch (state) {
            case PLAN -> handlePlan();
            case SELECT_JOB -> handleSelectJob();
            case NAV_SOURCE -> handleNavSource();
            case OPEN_SOURCE -> handleOpenSource();
            case VERIFY_SOURCE -> handleVerifySource();
            case WITHDRAW -> handleWithdraw();
            case CLOSE_SOURCE -> handleCloseSource();
            case SELECT_DEST -> handleSelectDest();
            case NAV_DEST -> handleNavDest();
            case OPEN_DEST -> handleOpenDest();
            case VERIFY_DEST -> handleVerifyDest();
            case DEPOSIT -> handleDeposit();
            case CLOSE_DEST -> handleCloseDest();
            case DONE -> handleDone();
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------
    //  Шаги
    // ------------------------------------------------------------------

    private void handlePlan() {
        String dim = dimension();
        markedFull.clear();
        deadRoutes.clear();
        overflowChests.clear();
        overflowRoutes.clear();

        // Слоты, которые игрок занял до сортировки, не трогаем
        if (!keepSlotsCaptured) {
            keepSlotsCaptured = true;
            if (!depositInventoryFirst.get()) {
                for (int i = reservedHotbarSlots.get(); i < 36; i++) {
                    if (!mc.player.getInventory().getStack(i).isEmpty()) keepSlots.add(i);
                }
            }
        }

        Map<String, List<BlockPos>> pins = buildPinMap(dim);
        List<SortPlanner.ContainerSnapshot> snapshots = buildSnapshots(dim, pins.keySet());
        BlockPos origin = mc.player.getBlockPos();
        List<BlockPos> unknown = unknownContainers();

        if (snapshots.isEmpty()) {
            if (unknown.isEmpty()) {
                warning("В зоне нет сундуков (ни в индексе, ни в чанках). Включи «ContainerIndex» или открой сундуки.");
                toggle();
                return;
            }
            info("В индексе сундуков нет, но найдено " + unknown.size() + " сундуков в зоне. Провожу сканирование...");
            jobs = new ArrayList<>();
            for (BlockPos p : unknown) {
                jobs.add(new SortPlanner.SweepJob(p, null));
            }
            jobIdx = 0;
            sigTargets = new LinkedHashMap<>();
            emptyClaims.clear();
            spareEmpties.clear();
            knownFree.clear();
            for (BlockPos p : unknown) knownFree.put(p, slotCountOf(p, containerTypeAt(p), 0));
            state = SortState.SELECT_JOB;
            return;
        }

        knownFree.clear();
        for (SortPlanner.ContainerSnapshot c : snapshots) {
            knownFree.put(c.pos(), Math.max(0, c.estSlots() - c.usedSlots()));
        }
        for (BlockPos p : unknown) knownFree.put(p, slotCountOf(p, containerTypeAt(p), 0));

        SortPlanner.Plan plan = SortPlanner.planConsolidation(
            withProbeSnapshots(snapshots, unknown), 1, origin, this::maxStackOfSig, pins,
            minItemsForOwnChest.get(), unknown);

        for (String w : plan.warnings()) {
            if (verbosity.get() == Verbosity.ПОДРОБНО) warning(w);
            else note("предупреждение плана", w);
        }

        jobs = new ArrayList<>(plan.jobs());
        if (verifyEveryChest.get() && pass == 1) {
            // Первый проход — осмотр всего: так расхождения индекса всплывают сразу
            jobs = new ArrayList<>();
            List<SortPlanner.ContainerSnapshot> ordered = new ArrayList<>(snapshots);
            ordered.sort(Comparator.<SortPlanner.ContainerSnapshot>comparingDouble(c -> origin.getSquaredDistance(c.pos()))
                .thenComparing(SortPlanner.ContainerSnapshot::pos));
            for (SortPlanner.ContainerSnapshot c : ordered) {
                if (c.usedSlots() > 0) jobs.add(new SortPlanner.SweepJob(c.pos(), null));
            }
        }
        // Если есть сундуки без записи, добавляем их для обязательного осмотра и индексации
        for (BlockPos p : unknown) {
            boolean already = false;
            for (SortPlanner.SweepJob j : jobs) {
                if (j.pos().equals(p)) { already = true; break; }
            }
            if (!already) jobs.add(new SortPlanner.SweepJob(p, null));
        }

        jobIdx = 0;

        sigTargets = new LinkedHashMap<>();
        for (Map.Entry<String, List<BlockPos>> e : plan.sigTargets().entrySet()) {
            sigTargets.put(e.getKey(), new ArrayList<>(e.getValue()));
        }

        emptyClaims.clear();
        emptyClaims.addAll(plan.claimedEmpties());

        Set<BlockPos> pinnedPos = new HashSet<>();
        for (List<BlockPos> ps : pins.values()) pinnedPos.addAll(ps);

        int emptyIndexed = 0;
        for (SortPlanner.ContainerSnapshot c : snapshots) {
            if (c.usedSlots() == 0 && !pinnedPos.contains(c.pos())) {
                emptyIndexed++;
                if (!plan.claimedEmpties().contains(c.pos())) spareEmpties.add(c.pos());
            }
        }
        spareEmpties.sort(Comparator.comparingDouble((BlockPos p) -> origin.getSquaredDistance(p)).thenComparing(p -> p));

        info("План готов" + (pass > 1 ? " (проход " + pass + ")" : "") + ": сундуков в зоне "
            + snapshots.size() + " (пустых " + emptyIndexed
            + (unknown.isEmpty() ? "" : ", без записи " + unknown.size()) + "), к разбору " + jobs.size()
            + (plan.claimedEmpties().isEmpty() ? "" : ", занято новых сундуков " + plan.claimedEmpties().size()) + ".");

        state = depositInventoryFirst.get() ? SortState.SELECT_DEST : SortState.SELECT_JOB;
    }

    private void handleSelectJob() {
        if (!cargoScan().isEmpty()) {
            state = SortState.SELECT_DEST;
        } else if (jobIdx >= jobs.size()) {
            state = SortState.DONE;
        } else {
            currentJob = jobs.get(jobIdx);
            openRetries = 0;
            state = SortState.NAV_SOURCE;
            timer = moveDelay.get();
        }
    }

    private void handleNavSource() {
        if (currentJob == null) {
            state = SortState.SELECT_JOB;
            return;
        }
        if (containerGone(currentJob.pos())) {
            skipJob("сундука больше нет");
            return;
        }

        switch (nav().travelTo(currentJob.pos())) {
            case ARRIVED -> {
                state = SortState.OPEN_SOURCE;
                timer = moveDelay.get();
            }
            case UNREACHABLE -> skipJob(nav().failure());
            case TRAVELLING -> {
                if (timedOut(navTimeout.get())) skipJob("не дошёл за отведённое время");
            }
        }
    }

    private void handleOpenSource() {
        if (currentJob == null) return;
        switch (stepOpen(currentJob.pos())) {
            case WAITING -> {
                if (timedOut(OPEN_ALIGN_TIMEOUT)) reapproachSource("не смог встать и навестись на сундук");
            }
            case CLICKED -> {
                menuSettled = false;
                state = SortState.VERIFY_SOURCE;
                timer = openDelay.get();
            }
            case NO_ANGLE -> reapproachSource("нет прямого клика по сундуку");
        }
    }

    private void reapproachSource(String reason) {
        if (++openRetries >= MAX_RETRIES) skipJob(reason);
        else state = SortState.NAV_SOURCE;
    }

    private void handleVerifySource() {
        if (currentJob == null) return;
        GenericContainerScreenHandler menu = openContainerMenu();
        if (menu == null) {
            if (wrongMenuOpen()) {
                closeOpenContainer();
                skipJob("открылось не то");
            } else if (timedOut(OPEN_ALIGN_TIMEOUT)) {
                if (++openRetries >= MAX_RETRIES) skipJob("не открылся за " + MAX_RETRIES + " попытки");
                else {
                    closeOpenContainer();
                    state = SortState.OPEN_SOURCE;
                    timer = openDelay.get();
                }
            }
            return;
        }

        // Ждём, пока сервер досласт содержимое
        if (!menuSettled) {
            menuSettled = true;
            timer = openDelay.get();
            return;
        }

        openRetries = 0;
        List<Integer> slots = computeWithdrawSlots(menu);
        if (slots.isEmpty()) {
            reindexOpenContainer(currentJob.pos());
            noteIfEmptied(menu, currentJob.pos());
            jobIdx++;
            chestsSwept++;
            state = SortState.CLOSE_SOURCE;
        } else {
            state = SortState.WITHDRAW;
        }
    }

    private void handleWithdraw() {
        if (currentJob == null) return;
        if (timedOut(stuckTimeout.get())) {
            skipJob("застрял при выдаче (нет прогресса)");
            return;
        }

        GenericContainerScreenHandler menu = openContainerMenu();
        if (menu == null) {
            if (++openRetries >= MAX_RETRIES) skipJob("сундук закрывается в середине выдачи");
            else {
                note("сундук закрылся при выдаче", "открываю заново (" + openRetries + "/" + MAX_RETRIES + ")");
                state = SortState.OPEN_SOURCE;
                timer = openDelay.get();
            }
            return;
        }

        int free = ferryFreeSlots();
        List<Integer> slots = computeWithdrawSlots(menu);
        if (slots.isEmpty() || free <= 0) {
            if (slots.isEmpty()) {
                jobIdx++;
                chestsSwept++;
                noteIfEmptied(menu, currentJob.pos());
            } else if (cargoScan().isEmpty()) {
                // Переносить некуда: в инвентаре лежит то, что сортировщик не трогает
                enterPause(PauseReason.INVENTORY_FULL,
                    "Нет свободных слотов для переноски — инвентарь заняты тем, что сортировщик не двигает");
                return;
            }
            reindexOpenContainer(currentJob.pos());
            state = SortState.CLOSE_SOURCE;
            return;
        }

        int clicks = 0;
        for (int slotId : slots) {
            if (clicks >= clicksPerTick.get() || clicks >= free) break;
            ItemStack stack = menu.slots.get(slotId).getStack();
            if (ContainerGeometry.isShulkerItem(stack)) shulkersMoved++;
            else stacksMoved++;
            mc.interactionManager.clickSlot(menu.syncId, slotId, 0, SlotActionType.QUICK_MOVE, mc.player);
            clicks++;
        }
    }

    private void handleCloseSource() {
        closeOpenContainer();
        timer = openDelay.get();
        state = cargoScan().isEmpty() ? SortState.SELECT_JOB : SortState.SELECT_DEST;
    }

    private void handleSelectDest() {
        Map<String, List<Integer>> cargo = cargoScan();
        if (cargo.isEmpty()) {
            state = SortState.SELECT_JOB;
            timer = moveDelay.get();
            return;
        }

        BlockPos me = mc.player.getBlockPos();
        String bestKey = null;
        BlockPos bestDest = null;
        double bestDist = Double.MAX_VALUE;

        // Вещём сначала то, до чего ближе всего дойти
        for (String key : cargo.keySet()) {
            BlockPos d = destFor(key);
            if (d == null) continue;
            double dist = me.getSquaredDistance(d);
            if (dist < bestDist) {
                bestDist = dist;
                bestDest = d;
                bestKey = key;
            }
        }

        currentRouteKey = bestKey != null ? bestKey : cargo.keySet().iterator().next();
        currentDest = bestDest != null ? bestDest : huntEmptyChest(currentRouteKey);

        // Ничего подходящего нет — пробуем общую «переполненную» категорию
        if (currentDest == null) {
            String overflow = SortGroupKey.overflowKey(currentRouteKey);
            if (!overflow.equals(currentRouteKey)) {
                overflowRoutes.put(currentRouteKey, overflow);
                currentRouteKey = overflow;
                currentDest = destFor(overflow);
                if (currentDest == null) currentDest = huntEmptyChest(overflow);
            }
            if (currentDest == null) currentDest = assignOverflowChest(currentRouteKey);
        }

        if (currentDest == null) {
            deadRoutes.add(currentRouteKey);
            note("нет сундука для «" + SortGroupKey.friendlyName(currentRouteKey) + "»", "вещи останутся в инвентаре");
            state = SortState.SELECT_DEST;
            return;
        }

        openRetries = 0;
        depositLastCount = -1;
        state = SortState.NAV_DEST;
    }

    private void handleNavDest() {
        if (currentDest == null) {
            state = SortState.SELECT_DEST;
            return;
        }
        if (containerGone(currentDest)) {
            skipDest("сундука больше нет");
            return;
        }

        switch (nav().travelTo(currentDest)) {
            case ARRIVED -> {
                state = SortState.OPEN_DEST;
                timer = moveDelay.get();
            }
            case UNREACHABLE -> skipDest(nav().failure());
            case TRAVELLING -> {
                if (timedOut(navTimeout.get())) skipDest("не дошёл за отведённое время");
            }
        }
    }

    private void handleOpenDest() {
        if (currentDest == null) return;
        switch (stepOpen(currentDest)) {
            case WAITING -> {
                if (timedOut(OPEN_ALIGN_TIMEOUT)) reapproachDest("не смог встать и навестись на сундук");
            }
            case CLICKED -> {
                menuSettled = false;
                state = SortState.VERIFY_DEST;
                timer = openDelay.get();
            }
            case NO_ANGLE -> reapproachDest("нет прямого клика по сундуку");
        }
    }

    private void reapproachDest(String reason) {
        if (++openRetries >= MAX_RETRIES) skipDest(reason);
        else state = SortState.NAV_DEST;
    }

    private void handleVerifyDest() {
        if (currentDest == null || currentRouteKey == null) return;
        GenericContainerScreenHandler menu = openContainerMenu();
        if (menu == null) {
            if (wrongMenuOpen()) {
                closeOpenContainer();
                markFull(currentDest);
                note("получатель — не сундук", currentDest == null ? "?" : currentDest.toShortString());
                state = SortState.SELECT_DEST;
            } else if (timedOut(OPEN_ALIGN_TIMEOUT)) {
                if (++openRetries >= MAX_RETRIES) {
                    markFull(currentDest);
                    note("получатель не открывается", currentDest.toShortString());
                    state = SortState.SELECT_DEST;
                } else {
                    closeOpenContainer();
                    state = SortState.OPEN_DEST;
                    timer = openDelay.get();
                }
            }
            return;
        }

        if (!menuSettled) {
            menuSettled = true;
            timer = openDelay.get();
            return;
        }

        openRetries = 0;
        int containerSlots = menu.getRows() * 9;
        int empty = freeSlotsIn(menu);
        knownFree.put(currentDest, empty);

        // Сундук брали как пустой под новую категорию, а он оказался занятым
        if (emptyClaims.contains(currentDest)) {
            for (int i = 0; i < containerSlots; i++) {
                ItemStack s = menu.slots.get(i).getStack();
                if (s.isEmpty() || matchesRoute(SortGroupKey.of(s), currentRouteKey)) continue;

                reindexOpenContainer(currentDest);
                for (List<BlockPos> ts : sigTargets.values()) ts.remove(currentDest);
                if (empty == 0) markFull(currentDest);
                note("получатель не пустой", currentDest.toShortString() + " — записал в индекс");
                state = SortState.CLOSE_DEST;
                return;
            }

            List<BlockPos> targets = sigTargets.get(currentRouteKey);
            if (targets != null && !targets.contains(currentDest)) targets.add(currentDest);
        }

        if (empty == 0) {
            markFull(currentDest);
            reindexOpenContainer(currentDest);
            state = SortState.CLOSE_DEST;
        } else {
            depositLastCount = -1;
            state = SortState.DEPOSIT;
        }
    }

    private void handleDeposit() {
        if (currentDest == null) return;
        if (timedOut(stuckTimeout.get())) {
            markFull(currentDest);
            note("застрял при выдаче", currentDest.toShortString());
            state = SortState.CLOSE_DEST;
            return;
        }

        GenericContainerScreenHandler menu = openContainerMenu();
        if (menu == null) {
            if (++openRetries >= MAX_RETRIES) {
                markFull(currentDest);
                note("получатель закрылся при выдаче", currentDest.toShortString());
                state = SortState.SELECT_DEST;
            } else {
                note("сундук закрылся при выдаче", "открываю заново (" + openRetries + "/" + MAX_RETRIES + ")");
                state = SortState.OPEN_DEST;
                timer = openDelay.get();
            }
            return;
        }

        List<Integer> slots = depositSlots();
        if (slots.isEmpty()) {
            reindexOpenContainer(currentDest);
            state = SortState.CLOSE_DEST;
            return;
        }

        // Количество не меняется при кликах — сундук не принимает, не долбимся
        int count = 0;
        for (int i : slots) count += mc.player.getInventory().getStack(i).getCount();
        if (count == depositLastCount) {
            markFull(currentDest);
            reindexOpenContainer(currentDest);
            state = SortState.CLOSE_DEST;
            return;
        }
        depositLastCount = count;

        int clicks = 0;
        for (int invSlot : slots) {
            if (clicks >= clicksPerTick.get()) break;
            mc.interactionManager.clickSlot(menu.syncId, invIndexToMenuId(menu, invSlot), 0,
                SlotActionType.QUICK_MOVE, mc.player);
            clicks++;
        }
    }

    private void handleCloseDest() {
        closeOpenContainer();
        timer = openDelay.get();
        state = SortState.SELECT_DEST;
    }

    private void handleDone() {
        int moved = stacksMoved + shulkersMoved;
        // Один проход не разбирает всё: то, что переехало или просканировалось, меняет раскладку
        if (pass < MAX_PASSES && (moved > movedAtPassStart || chestsSwept > sweptAtPassStart)) {
            movedAtPassStart = moved;
            sweptAtPassStart = chestsSwept;
            pass++;
            info("Проход " + pass + ": пересчитываю то, что сортировщик переписал в индексе.");
            state = SortState.PLAN;
            return;
        }

        flushNotes();

        long seconds = Math.max(1L, (System.currentTimeMillis() - startTime) / 1000L);
        info(String.format("Готово: сундуков %d, переложено стопок %d и шалкеров %d за %d:%02d%s.",
            chestsSwept, stacksMoved, shulkersMoved, seconds / 60L, seconds % 60L,
            pass > 1 ? " (" + pass + " проходов)" : ""));

        for (int i : cargoSlots()) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) continue;
            String key = routeKeyOf(s, true);
            if (key != null && deadRoutes.contains(key)) {
                warning("Часть вещей не нашла дом и осталась в инвентаре.");
                break;
            }
        }

        toggle();
    }

    private void skipJob(String reason) {
        closeOpenContainer();
        nav().stop();
        note("пропущен сундук", (currentJob == null ? "?" : currentJob.pos().toShortString()) + " — " + reason);
        jobIdx++;
        state = SortState.SELECT_JOB;
        timer = moveDelay.get();
    }

    private void skipDest(String reason) {
        closeOpenContainer();
        nav().stop();
        markFull(currentDest);
        note("проблемный получатель", (currentDest == null ? "?" : currentDest.toShortString()) + " — " + reason);
        state = SortState.SELECT_DEST;
    }

    /**
     * Сообщение о походовой неудаче. На большом стеше таких сотни, поэтому в
     * режиме «только итог» текст не пишется — вместо него копится счётчик,
     * и всё накопленное показывается одной строкой в конце прогона.
     */
    private void note(String reason, String detail) {
        if (verbosity.get() == Verbosity.ПОДРОБНО) {
            warning("Пропуск: " + reason + " — " + detail + ".");
            return;
        }
        pendingNotes.merge(reason, 1, Integer::sum);
        if (verbosity.get() == Verbosity.КРАТКО && !pendingNoteDetail.containsKey(reason)) {
            pendingNoteDetail.put(reason, detail);
        }
    }

    /** Накопленная сводка пропусков. Одна строка в конце прогона. */
    private void flushNotes() {
        if (pendingNotes.isEmpty()) return;

        StringBuilder sb = new StringBuilder("Пропусков: ");
        boolean first = true;
        for (Map.Entry<String, Integer> e : pendingNotes.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(e.getKey()).append(" ×").append(e.getValue());
        }
        warning(sb + ".");

        if (verbosity.get() == Verbosity.КРАТКО) {
            for (Map.Entry<String, Integer> e : pendingNotes.entrySet()) {
                String detail = pendingNoteDetail.get(e.getKey());
                if (detail != null) warning("  " + e.getKey() + ": " + detail);
            }
        }

        pendingNotes.clear();
        pendingNoteDetail.clear();
    }

    private void enterPause(PauseReason reason, String message) {
        closeOpenContainer();
        nav().stop();
        pauseReason = reason;
        state = SortState.PAUSED;
        warning(message + " — на паузе. Продолжить: §7.сорт пауза§f.");
    }

    // ------------------------------------------------------------------
    //  Что и куда
    // ------------------------------------------------------------------

    private List<Integer> computeWithdrawSlots(GenericContainerScreenHandler menu) {
        int containerSlots = menu.getRows() * 9;
        List<Integer> out = new ArrayList<>();

        for (int i = 0; i < containerSlots; i++) {
            ItemStack s = menu.slots.get(i).getStack();
            if (s.isEmpty()) continue;

            String key = routeKeyOf(s, false);
            if (key == null || deadRoutes.contains(key)) continue;

            List<BlockPos> targets = sigTargets.get(key);
            if (targets == null) continue;
            // Из домашнего сундука ничего не берём — он и так свой
            if (targets.contains(currentJob == null ? null : currentJob.pos())) continue;
            if (destFor(key) == null && currentJob != null
                && currentJob.pos().equals(overflowChests.get(SortGroupKey.overflowKey(key)))) continue;

            out.add(i);
        }

        return out;
    }

    /** Что игрок сейчас несёт, сгруппированное по маршрутам. */
    private Map<String, List<Integer>> cargoScan() {
        Map<String, List<Integer>> out = new TreeMap<>();
        if (mc.player == null) return out;

        for (int i : cargoSlots()) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) continue;
            String key = routeKeyOf(s, true);
            if (key != null && !deadRoutes.contains(key)) out.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }
        return out;
    }

    /**
     * Куда едет эта стопка. Мелкие категории сводим в общую, если так настроено,
     * а если дома нет — в «переполненное», иначе вещи останутся в инвентаре.
     */
    @Nullable
    private String routeKeyOf(ItemStack s, boolean allowOverflow) {
        boolean shulker = ContainerGeometry.isShulkerItem(s);
        if (shulker && !moveShulkers.get()) return null;
        if (!shulker && looseItems.get() == LooseItems.IGNORE) return null;

        String key = SortGroupKey.of(s);
        if (sigTargets.containsKey(key)) return allowOverflow ? overflowRoutes.getOrDefault(key, key) : key;

        String fallback = groupOf(key);
        if (!fallback.equals(key) && sigTargets.containsKey(fallback)) {
            return allowOverflow ? overflowRoutes.getOrDefault(fallback, fallback) : fallback;
        }
        return allowOverflow ? SortGroupKey.overflowKey(key) : null;
    }

    private String groupOf(String key) {
        String mapped = keyCollapse.get(key);
        if (mapped != null) return mapped;
        return looseItems.get() == LooseItems.MIXED_CHEST && !key.isEmpty() && !SortGroupKey.isShulkerGroup(key)
            ? SortGroupKey.MIXED_ITEMS
            : SortGroupKey.fallbackKey(key);
    }

    private boolean matchesRoute(String key, String routeKey) {
        if (key.equals(routeKey)) return true;
        if (SortGroupKey.OVERFLOW_SHULKERS.equals(routeKey)) return SortGroupKey.isShulkerGroup(key);
        if (SortGroupKey.MIXED_ITEMS.equals(routeKey) || SortGroupKey.OVERFLOW_ITEMS.equals(routeKey)) {
            return !key.isEmpty() && !SortGroupKey.isShulkerGroup(key);
        }
        return routeKey.equals(groupOf(key));
    }

    /** Слоты инвентаря, которые можно использовать как переноску. */
    private List<Integer> cargoSlots() {
        List<Integer> out = new ArrayList<>(36);
        for (int i = reservedHotbarSlots.get(); i < 36; i++) {
            if (!keepSlots.contains(i)) out.add(i);
        }
        return out;
    }

    private int ferryFreeSlots() {
        int free = 0;
        for (int i : cargoSlots()) if (mc.player.getInventory().getStack(i).isEmpty()) free++;
        return free;
    }

    private List<Integer> depositSlots() {
        List<Integer> out = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> e : cargoScan().entrySet()) {
            List<BlockPos> targets = sigTargets.get(e.getKey());
            if (e.getKey().equals(currentRouteKey) || (targets != null && targets.contains(currentDest))) {
                out.addAll(e.getValue());
            }
        }
        return out;
    }

    private static int freeSlotsIn(GenericContainerScreenHandler menu) {
        int free = 0;
        int containerSlots = menu.getRows() * 9;
        for (int i = 0; i < containerSlots; i++) if (menu.slots.get(i).getStack().isEmpty()) free++;
        return free;
    }

    private void noteIfEmptied(GenericContainerScreenHandler menu, BlockPos pos) {
        int containerSlots = menu.getRows() * 9;
        for (int i = 0; i < containerSlots; i++) {
            if (!menu.slots.get(i).getStack().isEmpty()) return;
        }

        knownFree.put(pos, containerSlots);
        if (markedFull.contains(pos) || emptyClaims.contains(pos) || spareEmpties.contains(pos)) return;
        for (List<BlockPos> targets : sigTargets.values()) if (targets.contains(pos)) return;

        spareEmpties.add(pos);
    }

    private void markFull(@Nullable BlockPos pos) {
        if (pos == null || mc.world == null) return;
        markedFull.add(pos);
        BlockPos other = ContainerGeometry.otherHalf(mc.world, pos);
        if (other != null) markedFull.add(other);
    }

    private void claimEmpty(@Nullable BlockPos pos) {
        if (pos == null || mc.world == null) return;
        emptyClaims.add(pos);
        BlockPos other = ContainerGeometry.otherHalf(mc.world, pos);
        if (other != null) emptyClaims.add(other);
    }

    @Nullable
    private BlockPos destFor(String routeKey) {
        List<BlockPos> targets = sigTargets.get(routeKey);
        if (targets == null) return null;
        for (BlockPos t : targets) if (!markedFull.contains(t)) return t;
        return null;
    }

    @Nullable
    private BlockPos huntEmptyChest(String routeKey) {
        for (BlockPos p : unknownContainers()) {
            if (!markedFull.contains(p) && !emptyClaims.contains(p)) {
                claimEmpty(p);
                return p;
            }
        }
        for (BlockPos p : spareEmpties) {
            if (!markedFull.contains(p) && !emptyClaims.contains(p)) {
                claimEmpty(p);
                return p;
            }
        }
        return null;
    }

    /**
     * Аварийный дом: под остаток, которому не нашлось места, берём любой
     * свободный сундук, который ещё не закреплён за другой категорией.
     */
    @Nullable
    private BlockPos assignOverflowChest(String overflowKey) {
        BlockPos existing = overflowChests.get(overflowKey);
        if (existing != null && !markedFull.contains(existing)) return existing;

        Set<BlockPos> owned = new HashSet<>();
        for (List<BlockPos> ts : sigTargets.values()) owned.addAll(ts);

        Set<BlockPos> queued = new HashSet<>();
        for (int i = jobIdx; i < jobs.size(); i++) queued.add(jobs.get(i).pos());

        Set<BlockPos> pinned = new HashSet<>();
        for (var pin : store().pinsFor(dimension())) pinned.add(ContainerGeometry.canonical(mc.world, pin.pos()));

        BlockPos best = pickOverflowChest(owned, queued, pinned, true);
        if (best == null) best = pickOverflowChest(owned, queued, pinned, false);
        if (best == null) return null;

        overflowChests.put(overflowKey, best);
        sigTargets.computeIfAbsent(overflowKey, k -> new ArrayList<>()).add(best);
        note("аварийный дом для «" + SortGroupKey.friendlyName(overflowKey) + "»", best.toShortString());
        return best;
    }

    @Nullable
    private BlockPos pickOverflowChest(Set<BlockPos> owned, Set<BlockPos> queued, Set<BlockPos> pinned, boolean skipOwned) {
        BlockPos me = mc.player.getBlockPos();
        BlockPos best = null;
        int bestFree = 0;
        double bestDist = Double.MAX_VALUE;

        for (Map.Entry<BlockPos, Integer> e : knownFree.entrySet()) {
            BlockPos pos = e.getKey();
            int free = e.getValue();
            if (free <= 0 || markedFull.contains(pos) || pinned.contains(pos)
                || queued.contains(pos) || emptyClaims.contains(pos)) continue;
            if (skipOwned && owned.contains(pos)) continue;

            double dist = me.getSquaredDistance(pos);
            // Сначала самый пустой, при равенстве — ближайший
            if (free > bestFree || (free == bestFree && (dist < bestDist || (dist == bestDist && best != null && pos.compareTo(best) < 0)))) {
                best = pos;
                bestFree = free;
                bestDist = dist;
            }
        }
        return best;
    }

    /** Сундуки в зоне, которых нет в индексе: содержимое неизвестно. */
    private List<BlockPos> unknownContainers() {
        long now = System.currentTimeMillis();
        String dim = dimension();
        if (now - unknownCacheTime < 5000L && dim.equals(unknownCacheDim)) return unknownCache;

        unknownCacheTime = now;
        unknownCacheDim = dim;

        ChestTrackerDataV2 data = ChestTrackerDataManager.getData();
        List<SortZone> areas = sourceZones();
        if (areas.isEmpty()) {
            unknownCache = List.of();
            return unknownCache;
        }

        BlockPos me = mc.player.getBlockPos();
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> out = new ArrayList<>();

        // Смотрим только загруженные чанки вокруг игрока: полный обход мира
        // на 2b2t — это минуты ожидания на каждый план
        int meChunkX = me.getX() >> 4;
        int meChunkZ = me.getZ() >> 4;
        int chunkRadius = Math.max(1, overlayRange.get() / 16);

        for (int cx = meChunkX - chunkRadius; cx <= meChunkX + chunkRadius; cx++) {
            for (int cz = meChunkZ - chunkRadius; cz <= meChunkZ + chunkRadius; cz++) {
                if (!mc.world.getChunkManager().isChunkLoaded(cx, cz)) continue;
                var chunk = mc.world.getChunkManager().getChunk(cx, cz, net.minecraft.world.chunk.ChunkStatus.FULL, false);
                if (chunk == null) continue;

                for (var blockEntity : chunk.getBlockEntities().values()) {
                    if (!ContainerGeometry.isContainerBlock(blockEntity.getCachedState())) continue;

                    BlockPos raw = blockEntity.getPos();
                    BlockPos canonical = ContainerGeometry.canonical(mc.world, raw);
                    if (!seen.add(canonical) || !inArea(areas, raw, canonical)) continue;

                    if (data.getContainer(canonical, dim) != null) continue;
                    BlockPos other = ContainerGeometry.otherHalf(mc.world, canonical);
                    if (other != null && data.getContainer(other, dim) != null) continue;

                    out.add(canonical.toImmutable());
                }
            }
        }

        out.sort(Comparator.comparingDouble((BlockPos p) -> me.getSquaredDistance(p)).thenComparing(p -> p));
        unknownCache = out;
        return unknownCache;
    }

    private int maxStackOfSig(String sig) {
        if (SortGroupKey.isShulkerKey(sig) || SortGroupKey.MIXED_ITEMS.equals(sig)) return 1;
        String id = SortGroupKey.looseItemId(sig);
        Identifier ident = Identifier.tryParse(id);
        return ident != null && Registries.ITEM.containsId(ident)
            ? new ItemStack((ItemConvertible) Registries.ITEM.get(ident)).getMaxCount()
            : 64;
    }

    private boolean inArea(List<SortZone> areas, BlockPos raw, BlockPos canonical) {
        if (containsAny(areas, raw) || containsAny(areas, canonical)) return true;
        BlockPos other = ContainerGeometry.otherHalf(mc.world, canonical);
        return other != null && containsAny(areas, other);
    }

    private static boolean containsAny(List<SortZone> areas, BlockPos pos) {
        for (SortZone z : areas) if (z.contains(pos)) return true;
        return false;
    }

    public boolean isInSortArea(BlockPos canonical) {
        List<SortZone> areas = sourceZones();
        return !areas.isEmpty() && inArea(areas, canonical, canonical);
    }

    // ------------------------------------------------------------------
    //  Снимки индекса для планировщика
    // ------------------------------------------------------------------

    private Map<String, List<BlockPos>> buildPinMap(String dim) {
        Map<String, List<BlockPos>> out = new LinkedHashMap<>();
        Set<BlockPos> seen = new HashSet<>();
        for (var p : store().pinsFor(dim)) {
            BlockPos canonical = ContainerGeometry.canonical(mc.world, p.pos());
            if (seen.add(canonical)) out.computeIfAbsent(p.groupKey, k -> new ArrayList<>()).add(canonical);
        }
        return out;
    }

    private List<SortPlanner.ContainerSnapshot> buildSnapshots(String dim, Set<String> pinnedKeys) {
        ChestTrackerDataV2 data = ChestTrackerDataManager.getData();
        List<SortZone> areas = sourceZones();
        if (areas.isEmpty()) return List.of();

        Map<BlockPos, SortPlanner.ContainerSnapshot> byCanonical = new HashMap<>();
        Map<BlockPos, Long> newest = new HashMap<>();

        for (TrackedContainer tc : data.getAllContainers(dim)) {
            String type = tc.getContainerType();
            if (!"chest".equals(type) && !"copper_chest".equals(type) && !"barrel".equals(type)) continue;

            BlockPos raw = tc.getPosition();
            BlockPos canonical = ContainerGeometry.canonical(mc.world, raw);
            if (!inArea(areas, raw, canonical)) continue;

            Long prev = newest.get(canonical);
            if (prev != null && prev >= tc.getLastUpdated()) continue;
            newest.put(canonical, tc.getLastUpdated());

            Map<String, Integer> sigs = new HashMap<>();
            for (Map.Entry<String, Integer> e : tc.getTopLevelSignatureCounts().entrySet()) {
                boolean shulker = SortGroupKey.isShulkerGroup(e.getKey());
                if (shulker && !moveShulkers.get()) continue;
                if (!shulker && looseItems.get() == LooseItems.IGNORE) continue;
                sigs.merge(e.getKey(), e.getValue(), Integer::sum);
            }

            int used = tc.getUsedStacks();
            if (used == 0 && !tc.getTopLevelSignatureCounts().isEmpty()) used = 1;

            byCanonical.put(canonical, new SortPlanner.ContainerSnapshot(
                canonical, type, slotCountOf(canonical, type, used), used, sigs));
        }

        // Сводим мелкие категории в крупные — планировщик считает уже свёрнутые
        Map<String, Integer> totals = new HashMap<>();
        for (SortPlanner.ContainerSnapshot c : byCanonical.values()) {
            for (Map.Entry<String, Integer> e : c.sigCounts().entrySet()) {
                totals.merge(e.getKey(), e.getValue(), Integer::sum);
            }
        }

        Map<String, String> collapse = new HashMap<>();
        for (String key : totals.keySet()) collapse.put(key, collapseKey(key, totals, pinnedKeys));
        keyCollapse = collapse;

        List<SortPlanner.ContainerSnapshot> out = new ArrayList<>(byCanonical.size());
        for (SortPlanner.ContainerSnapshot c : byCanonical.values()) {
            Map<String, Integer> sigs = new HashMap<>();
            for (Map.Entry<String, Integer> e : c.sigCounts().entrySet()) {
                String group = collapse.get(e.getKey());
                sigs.merge(group, groupAmount(e.getKey(), group, e.getValue()), Integer::sum);
            }
            out.add(new SortPlanner.ContainerSnapshot(c.pos(), c.type(), c.estSlots(), c.usedSlots(), sigs));
        }

        return out;
    }

    /** В смешанном сундуке считаем стопки, а не предметы — иначе 64 блока «съедят» целый сундук. */
    private int groupAmount(String key, String group, int count) {
        if (!SortGroupKey.MIXED_ITEMS.equals(group)) return count;
        int maxStack = Math.max(1, maxStackOfSig(key));
        return Math.max(1, (count + maxStack - 1) / maxStack);
    }

    private String collapseKey(String key, Map<String, Integer> totals, Set<String> pinnedKeys) {
        if (pinnedKeys.contains(key)) return key;

        if (SortGroupKey.isShulkerKey(key)) {
            boolean bucket = SortGroupKey.EMPTY_SHULKERS.equals(key) || SortGroupKey.KIT_SHULKERS.equals(key)
                || SortGroupKey.MISC_SHULKERS.equals(key);
            if (bucket) return key;
            return totals.getOrDefault(key, 0) < minShulkersForOwnChest.get() ? SortGroupKey.fallbackKey(key) : key;
        }
        if (SortGroupKey.isShulkerGroup(key)) return key;
        return looseItems.get() == LooseItems.MIXED_CHEST ? SortGroupKey.MIXED_ITEMS : key;
    }

    private List<SortPlanner.ContainerSnapshot> withProbeSnapshots(List<SortPlanner.ContainerSnapshot> indexed, List<BlockPos> unknown) {
        if (unknown.isEmpty()) return indexed;

        List<SortPlanner.ContainerSnapshot> out = new ArrayList<>(indexed.size() + unknown.size());
        out.addAll(indexed);
        for (BlockPos p : unknown) {
            String type = containerTypeAt(p);
            out.add(new SortPlanner.ContainerSnapshot(p, type, slotCountOf(p, type, 0), 0, Map.of()));
        }
        return out;
    }

    private int slotCountOf(BlockPos canonical, String type, int used) {
        return ContainerGeometry.slotCount(mc.world, canonical, type, used);
    }

    // ------------------------------------------------------------------
    //  Открытие сундука
    // ------------------------------------------------------------------

    private OpenStep stepOpen(BlockPos canonicalPos) {
        BlockHitResult hit = nav().openHit(canonicalPos);
        if (hit == null) return OpenStep.NO_ANGLE;

        if (!movementSettled()) {
            alignTicks = 0;
            return OpenStep.WAITING;
        }

        if (!turnTowards(hit.getPos())) {
            alignTicks = 0;
            return OpenStep.WAITING;
        }

        if (alignTicks++ < AIM_HOLD_TICKS) return OpenStep.WAITING;

        mc.interactionManager.interactBlock(mc.player, InteractionHand.MAIN_HAND, hit);
        mc.player.swingHand(InteractionHand.MAIN_HAND);
        return OpenStep.CLICKED;
    }

    /** Кликать можно только когда Baritone стоит и клюши отпущены. */
    private boolean movementSettled() {
        releaseMovement();
        if (settleTicks++ < settleDelay.get()) return false;
        return true;
    }

    private void releaseMovement() {
        nav().stop();
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
    }

    /** Доворачивает взгляд на точку и говорит, попал ли. */
    private boolean turnTowards(Vec3 point) {
        Vec3 eye = mc.player.getEyePos();
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));

        float maxStep = turnSpeed.get();
        if (maxStep <= 0) {
            mc.player.setYaw(targetYaw);
            mc.player.setHeadYaw(targetYaw);
            mc.player.setPitch(MathHelper.clamp(targetPitch, -90.0F, 90.0F));
            return true;
        }

        float dYaw = MathHelper.wrapDegrees(targetYaw - mc.player.getYaw());
        float dPitch = targetPitch - mc.player.getPitch();

        float yaw = mc.player.getYaw() + MathHelper.clamp(dYaw, -maxStep, maxStep);
        mc.player.setYaw(yaw);
        mc.player.setHeadYaw(yaw);
        mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + MathHelper.clamp(dPitch, -maxStep, maxStep), -90.0F, 90.0F));

        return Math.abs(dYaw) <= AIM_EPSILON && Math.abs(dPitch) <= AIM_EPSILON;
    }

    @Nullable
    private GenericContainerScreenHandler openContainerMenu() {
        if (!(mc.currentScreen instanceof HandledScreen)) return null;
        return mc.player.currentScreenHandler instanceof GenericContainerScreenHandler menu ? menu : null;
    }

    private boolean wrongMenuOpen() {
        return mc.currentScreen instanceof HandledScreen
            && mc.player.currentScreenHandler != mc.player.playerScreenHandler
            && !(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler);
    }

    private boolean containerGone(BlockPos pos) {
        return mc.world.isPosLoaded(pos) && !ContainerGeometry.isContainerBlock(mc.world.getBlockState(pos));
    }

    /** Индекс слота инвентаря в номере слотов экрана: у контейнера сверху, у игрока снизу. */
    private static int invIndexToMenuId(GenericContainerScreenHandler menu, int invIndex) {
        int rows = menu.getRows();
        return rows * 9 + (invIndex < 9 ? invIndex + 27 : invIndex - 9);
    }

    /**
     * Закрыть контейнер. В 1.21.11 {@code closeHandledScreen()} у игрока
     * protected, поэтому закрываем экран — он сам отправит пакет закрытия.
     */
    private void closeOpenContainer() {
        if (mc.player == null || mc.currentScreen == null) return;
        boolean serverContainer = mc.player.currentScreenHandler != mc.player.playerScreenHandler;
        boolean containerScreen = mc.currentScreen instanceof HandledScreen && !(mc.currentScreen instanceof InventoryScreen);
        if (serverContainer || containerScreen) mc.currentScreen.close();
    }

    /** Перезаписывает запись индекса тем, что прямо сейчас в сундуке. */
    private void reindexOpenContainer(@Nullable BlockPos pos) {
        if (pos == null || mc.player == null || mc.world == null) return;

        ChestTrackerDataV2 data = ChestTrackerDataManager.getData();
        ScreenHandler h = mc.player.currentScreenHandler;
        if (h == null || h.slots.size() < 37) return;

        int cont = h.slots.size() - 36;
        List<ItemStack> contents = new ArrayList<>();
        for (int i = 0; i < cont; i++) {
            ItemStack s = h.slots.get(i).getStack();
            if (!s.isEmpty()) contents.add(s.copy());
        }

        knownFree.put(ContainerGeometry.canonical(mc.world, pos), cont - contents.size());

        String dim = dimension();
        BlockPos key = pos;
        if (data.getContainer(pos, dim) == null) {
            BlockPos other = ContainerGeometry.otherHalf(mc.world, pos);
            if (other != null && data.getContainer(other, dim) != null) key = other;
        }
        data.trackContainer(key, dim, containerTypeAt(key), contents);
    }

    private String containerTypeAt(BlockPos pos) {
        return mc.world.getBlockState(pos).getBlock() instanceof BarrelBlock ? "barrel" : "chest";
    }

    private String dimension() {
        return mc.world == null ? "unknown" : mc.world.getRegistryKey().getValue().toString();
    }

    private ChestNavigator nav() {
        if (nav == null) nav = new ChestNavigator();
        return nav;
    }

    private SorterWorldStore store() {
        if (worldStore == null) {
            worldStore = new SorterWorldStore();
            worldStore.load();
        }
        return worldStore;
    }

    private List<SortZone> sourceZones() {
        return store().sourceZones(dimension());
    }

    private boolean timedOut(int ticks) {
        return stateTicks > ticks;
    }

    private boolean strangerNearby() {
        if (mc.player == null || mc.world == null) return false;
        for (Player player : mc.world.getPlayers()) {
            if (player == mc.player || player.isSpectator()) continue;
            if (FriendManager.INSTANCE.isFriend(player.getNameForScoreboard())) continue;
            if (player.squaredDistanceTo(mc.player) < 64) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    //  Зона и метки — настраиваются командой
    // ------------------------------------------------------------------

    /** Ставит первый угол зоны по блоку, на который смотрит игрок. */
    public boolean setSelectionCorner() {
        BlockPos looked = lookedAtBlock();
        if (looked == null) {
            info("Посмотри на угол сундука и повтори.");
            return false;
        }
        if (selectionCorner1 == null) {
            selectionCorner1 = looked;
            info("Первый угол: §f" + looked.toShortString() + "§f. Посмотри на второй и повтори.");
            return true;
        }

        BlockPos c1 = selectionCorner1;
        selectionCorner1 = null;
        store().addZone(new SortZone("stash", SortZone.ZoneRole.SOURCE, dimension(), c1, looked));
        info("Зона задана: §f" + store().sourceZones(dimension()).get(store().sourceZones(dimension()).size() - 1).footprint()
            + "§f, углы §f" + c1.toShortString() + "§f — §f" + looked.toShortString() + "§f.");
        return true;
    }

    public int clearZones() {
        int removed = store().clearSourceZones(dimension());
        selectionCorner1 = null;
        return removed;
    }

    /** Помечает сундук как дом для того, чего в нём больше всего. */
    public boolean pinLookedChest() {
        BlockPos looked = lookedAtBlock();
        if (looked == null || mc.world == null) {
            info("Посмотри на сундук и повтори.");
            return false;
        }

        BlockPos canonical = ContainerGeometry.canonical(mc.world, looked);
        ChestTrackerDataV2 data = ChestTrackerDataManager.getData();
        TrackedContainer tc = data.getContainer(canonical, dimension());
        if (tc == null) {
            BlockPos other = ContainerGeometry.otherHalf(mc.world, canonical);
            if (other != null) tc = data.getContainer(other, dimension());
        }
        if (tc == null) {
            info("Этот сундук не открывали — индекса нет. Открой его один раз.");
            return false;
        }

        String group = groupOf(dominantKey(tc));
        store().setPin(canonical, dimension(), group);
        info("Метка: §f" + canonical.toShortString() + "§f — дом для «§f" + SortGroupKey.friendlyName(group) + "§f».");
        return true;
    }

    public boolean clearLookedPin() {
        BlockPos looked = lookedAtBlock();
        if (looked == null || mc.world == null) return false;
        BlockPos canonical = ContainerGeometry.canonical(mc.world, looked);
        if (!store().clearPin(canonical, dimension())) {
            info("Метки на этом сундуке нет.");
            return false;
        }
        info("Метка снята: §f" + canonical.toShortString());
        return true;
    }

    private String dominantKey(TrackedContainer tc) {
        String best = SortGroupKey.MIXED_ITEMS;
        int bestCount = -1;
        for (Map.Entry<String, Integer> e : tc.getTopLevelSignatureCounts().entrySet()) {
            if (e.getValue() > bestCount) {
                best = e.getKey();
                bestCount = e.getValue();
            }
        }
        return best;
    }

    @Nullable
    public BlockPos lookedAtBlock() {
        if (mc.player == null || mc.crosshairTarget == null) return null;
        if (mc.crosshairTarget.getType() != HitResult.Type.BLOCK) return null;
        return ((BlockHitResult) mc.crosshairTarget).getBlockPos();
    }

    // ------------------------------------------------------------------
    //  Управление прогоном
    // ------------------------------------------------------------------

    public void startSorting() {
        if (sourceZones().isEmpty()) {
            info("Сначала задай зону: §7.сорт зона§f.");
            return;
        }
        if (!isActive()) toggle();
        resetRun();
        state = SortState.PLAN;
    }

    public void stopSorting() {
        closeOpenContainer();
        if (nav != null) {
            nav.restorePathing();
            nav.stop();
        }
        state = SortState.IDLE;
        if (isActive()) toggle();
    }

    public void pauseSorting() {
        if (state == SortState.IDLE || state == SortState.PAUSED) return;
        enterPause(PauseReason.MANUAL, "Остановлено вручную");
    }

    public void resumeSorting() {
        if (state != SortState.PAUSED) return;
        closeOpenContainer();
        if (nav != null) nav.stop();
        state = cargoScan().isEmpty() ? SortState.SELECT_JOB : SortState.SELECT_DEST;
        stateTicks = 0;
        lastState = state;
    }

    public boolean isPaused() {
        return state == SortState.PAUSED;
    }

    public boolean isSorting() {
        return isActive() && state != SortState.IDLE;
    }

    /** Сколько зон задано в текущем измерении. */
    public int zoneCount() {
        return sourceZones().size();
    }

    public String statusLine() {
        return switch (state) {
            case IDLE -> "ждёт команды";
            case PLAN -> "планирует";
            case SELECT_JOB -> "выбирает сундук";
            case NAV_SOURCE -> "идёт к источнику";
            case OPEN_SOURCE -> "открывает источник";
            case VERIFY_SOURCE -> "проверяет источник";
            case WITHDRAW -> "забирает";
            case CLOSE_SOURCE -> "закрывает источник";
            case SELECT_DEST -> "выбирает получателя";
            case NAV_DEST -> "идёт к получателю";
            case OPEN_DEST -> "открывает получателя";
            case VERIFY_DEST -> "проверяет получателя";
            case DEPOSIT -> "выкладывает";
            case CLOSE_DEST -> "закрывает получателя";
            case DONE -> "завершает";
            case PAUSED -> "на паузе";
        };
    }

    // ------------------------------------------------------------------
    //  Оверлей
    // ------------------------------------------------------------------

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (showZones.get()) {
            SettingColor c = zoneColor.get();
            if (c.a > 5) {
                for (SortZone z : store().sourceZones(dimension())) {
                    // Высоты берём из самой зоны: у Level в 1.21.11 их больше не отдать
                    AABB box = z.box(Math.min(z.y1, z.y2), Math.max(z.y1, z.y2));
                    event.renderer.box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                        new SettingColor(c.r, c.g, c.b, 0), c, ShapeMode.Both, 0);
                }
            }
        }

        if (!showLabels.get()) return;
        if (labelColor.get().a <= 5 && pinnedColor.get().a <= 5) return;

        double rangeSq = (double) overlayRange.get() * overlayRange.get();
        updateOverlayCache();

        for (OverlayEntry entry : overlayCache) {
            double dist = entry.pos().getSquaredDistance(mc.player.getEntityPos());
            if (dist > rangeSq) continue;

            SettingColor color = entry.pinned() ? pinnedColor.get() : labelColor.get();
            if (color.a <= 5) continue;

            event.renderer.box(entry.pos().getX() + 0.1, entry.pos().getY() + 0.1, entry.pos().getZ() + 0.1,
                entry.pos().getX() + 0.9, entry.pos().getY() + 0.9, entry.pos().getZ() + 0.9,
                new SettingColor(color.r, color.g, color.b, Math.min(60, color.a)), color, ShapeMode.Both, 0);
        }
    }

    private long overlayCacheTime;

    private void updateOverlayCache() {
        long now = System.currentTimeMillis();
        String dim = dimension();
        if (now - overlayCacheTime < 1000L && dim.equals(overlayCacheDim)) return;
        overlayCacheTime = now;
        overlayCacheDim = dim;

        ChestTrackerDataV2 data = ChestTrackerDataManager.getData();
        List<SortZone> areas = sourceZones();
        overlayCache.clear();
        if (areas.isEmpty()) return;

        for (TrackedContainer tc : data.getAllContainers(dim)) {
            BlockPos canonical = ContainerGeometry.canonical(mc.world, tc.getPosition());
            String pinKey = store().pinFor(canonical, dim);
            boolean inArea = inArea(areas, tc.getPosition(), canonical);

            int free = Math.max(0, slotCountOf(canonical, tc.getContainerType(), tc.getUsedStacks()) - tc.getUsedStacks());
            String name = pinKey != null
                ? SortGroupKey.friendlyName(pinKey)
                : (tc.isEmpty() ? "пусто" : SortGroupKey.friendlyName(dominantKey(tc)));
            String text = name + "  " + free + "/" + slotCountOf(canonical, tc.getContainerType(), tc.getUsedStacks()) + " свободно";

            overlayCache.add(new OverlayEntry(canonical, text, pinKey != null, inArea));
        }
    }

    private String overlayCacheDim = "";

    private record OverlayEntry(BlockPos pos, String text, boolean pinned, boolean inArea) {
    }

    // ------------------------------------------------------------------

    public enum Verbosity {
        ТОЛЬКО_ИТОГ("Только итог"),
        КРАТКО("Кратко"),
        ПОДРОБНО("Подробно");

        private final String title;

        Verbosity(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    public enum LooseItems {
        MIXED_CHEST("Смешанный сундук"),
        OWN_CHESTS("Свои сундуки"),
        IGNORE("Не трогать");

        private final String title;

        LooseItems(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private enum OpenStep {
        WAITING,
        CLICKED,
        NO_ANGLE
    }

    public enum PauseReason {
        STRANGER,
        MANUAL,
        INVENTORY_FULL
    }

    private enum SortState {
        IDLE,
        PLAN,
        SELECT_JOB,
        NAV_SOURCE,
        OPEN_SOURCE,
        VERIFY_SOURCE,
        WITHDRAW,
        CLOSE_SOURCE,
        SELECT_DEST,
        NAV_DEST,
        OPEN_DEST,
        VERIFY_DEST,
        DEPOSIT,
        CLOSE_DEST,
        DONE,
        PAUSED
    }

    static {
        LOG.debug("StashSorter загружен");
    }
}
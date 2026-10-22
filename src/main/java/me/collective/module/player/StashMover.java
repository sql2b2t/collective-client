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
import me.collective.util.tracker.ChestTrackerDataManager;
import me.collective.util.tracker.TrackedContainer;
import me.collective.event.events.Render3DEvent;
import me.collective.event.events.TickEvent;
import me.collective.render.ShapeMode;
import me.collective.module.Module;
import me.collective.category.Category;
import me.collective.util.SettingColor;
import me.collective.event.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * StashMover (порт из BepHax Community) — перенос вещей между двумя зонами стеша.
 *
 * <p>Смысл: в стеше нельзя выйти в другую точку с полным инвентарём. Модуль
 * забирает вещи из сундуков зоны входа в инвентарь, телепортируется к зоне
 * выхода и выкладывает их туда. Обратно — то же самое.
 *
 * <p>Способы переноса три, все через серверные команды:
 * <ul>
 *   <li>{@code /kill} — смерть и респавн в точке респавна;</li>
 *   <li>смерть с подходом к месту смерти — респавн там, где стоял;</li>
 *   <li>жемчужина — команда плагина, телепорт к другому игроку.</li>
 * </ul>
 *
 * <p>Отличие от оригинала: у него жемчужинный транспорт доводит до цели
 * отдельной цепочкой состояний RESET_PEARL_* — она «взводит» шалкер и
 * кидает жемчужину, чтобы вернуться. Здесь эта цепочка повторяет порядок
 * действий, но тайминги заданы настройками и без проверки стазиса на стороне
 * сервера. Остальные два способа переноса сделаны как в оригинале.
 */
public class StashMover extends Module {
    private static final int INVENTORY_SIZE = 36;
    private static final int OPEN_RETRIES = 3;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgInput = settings.createGroup("Зона входа");
    private final SettingGroup sgForward = settings.createGroup("Перенос туда");
    private final SettingGroup sgPearl = settings.createGroup("Жемчужина");
    private final SettingGroup sgGoBack = settings.createGroup("Перенос обратно");
    private final SettingGroup sgDelays = settings.createGroup("Задержки");
    private final SettingGroup sgRendering = settings.createGroup("Отрисовка");

    private final Setting<Double> containerReach = sgGeneral.add(new DoubleSetting.Builder()
        .name("досягаемость")
        .description("С какого расстояния можно открыть сундук.")
        .defaultValue(4.0).min(2.0).max(6.0).build());

    private final Setting<Integer> maxRetries = sgGeneral.add(new IntSetting.Builder()
        .name("попыток")
        .description("Сколько раз повторять телепорт, прежде чем остановиться.")
        .defaultValue(5).min(1).max(20).build());

    private final Setting<Boolean> onlyShulkers = sgGeneral.add(new BoolSetting.Builder()
        .name("только-шалкеры")
        .description("Переносить только шалкеры. Остальное из инвентаря выбрасывается на землю.")
        .defaultValue(true).build());

    private final Setting<Boolean> breakEmpty = sgGeneral.add(new BoolSetting.Builder()
        .name("ломать-пустые")
        .description("Ломать сундук, из которого всё забрали.")
        .defaultValue(true).build());

    private final Setting<Boolean> fillEnderChest = sgGeneral.add(new BoolSetting.Builder()
        .name("через-эндер-сундук")
        .description("Если инвентарь полон, сложить груз в эндер-сундук и продолжить через него.")
        .defaultValue(true).build());

    // Plain triples are lossless, editable and keep (0, 0, 0) distinct from unset.
    // Old packed integers cannot be recovered reliably and are rejected by decode.
    private final Setting<String> inputAreaPos1 = sgInput.add(new StringSetting("вход-угол-1", ""));
    private final Setting<String> inputAreaPos2 = sgInput.add(new StringSetting("вход-угол-2", ""));
    private final Setting<String> outputAreaPos1 = sgInput.add(new StringSetting("выход-угол-1", ""));
    private final Setting<String> outputAreaPos2 = sgInput.add(new StringSetting("выход-угол-2", ""));

    private final Setting<TransportMethod> forwardMethod = sgForward.add(new EnumSetting.Builder<TransportMethod>()
        .name("способ-туда")
        .description("Как попасть к зоне выхода с грузом.")
        .defaultValue(TransportMethod.PEARL).build());

    private final Setting<String> forwardKillCommand = sgForward.add(new StringSetting("команда-смерти-туда", "/kill"));

    private final Setting<Boolean> forwardKillRandom = sgForward.add(new BoolSetting.Builder()
        .name("рандомный-суффикс")
        .description("Дописывать к команде случайный суффикс — некоторые плагины требуют разные строки.")
        .defaultValue(true).build());

    private final Setting<String> pearlPlayer = sgPearl.add(new StringSetting("игрок", "PlayerName"));

    private final Setting<String> pearlCommand = sgPearl.add(new StringSetting("команда", "pearl"));

    private final Setting<Integer> pearlTimeout = sgPearl.add(new IntSetting.Builder()
        .name("таймаут-жемчужины")
        .description("Сколько секунд ждать телепорта, прежде чем повторить.")
        .defaultValue(10).min(1).max(60).build());

    private final Setting<TransportMethod> goBackMethod = sgGoBack.add(new EnumSetting.Builder<TransportMethod>()
        .name("способ-обратно")
        .description("Как вернуться к зоне входа.")
        .defaultValue(TransportMethod.PEARL).build());

    private final Setting<String> goBackPlayer = sgGoBack.add(new StringSetting("игрок-обратно", "PlayerName"));

    private final Setting<String> goBackCommand = sgGoBack.add(new StringSetting("команда-обратно", "back"));

    private final Setting<String> goBackKillCommand = sgGoBack.add(new StringSetting("команда-смерти-обратно", "/kill"));

    private final Setting<Boolean> goBackKillRandom = sgGoBack.add(new BoolSetting.Builder()
        .name("рандомный-суффикс-обратно")
        .defaultValue(true).build());

    private final Setting<Integer> openDelay = sgDelays.add(new IntSetting.Builder()
        .name("задержка-открытия")
        .description("Тиков между кликом по сундуку и началом переноса.")
        .defaultValue(30).min(0).max(200).build());

    private final Setting<Integer> transferDelay = sgDelays.add(new IntSetting.Builder()
        .name("задержка-переноса")
        .defaultValue(10).min(0).max(200).build());

    private final Setting<Integer> moveDelay = sgDelays.add(new IntSetting.Builder()
        .name("задержка-перед-ходьбой")
        .defaultValue(5).min(0).max(60).build());

    private final Setting<Integer> clicksPerTick = sgDelays.add(new IntSetting.Builder()
        .name("кликов-за-тик")
        .defaultValue(6).min(1).max(27).build());

    private final Setting<Boolean> renderSelection = sgRendering.add(new BoolSetting.Builder()
        .name("показывать-зоны")
        .defaultValue(true).build());

    private final Setting<SettingColor> inputAreaOutline = sgRendering.add(new SettingColorSetting("зона-входа", new SettingColor(0, 255, 0, 255)));

    private final Setting<SettingColor> outputAreaOutline = sgRendering.add(new SettingColorSetting("зона-выхода", new SettingColor(0, 100, 255, 255)));

    private final Setting<SettingColor> inputContainerColor = sgRendering.add(new SettingColorSetting("сундуки-входа", new SettingColor(0, 255, 0, 100)));

    private final Setting<SettingColor> outputContainerColor = sgRendering.add(new SettingColorSetting("сундуки-выхода", new SettingColor(0, 100, 255, 100)));

    private final Setting<SettingColor> activeContainerColor = sgRendering.add(new SettingColorSetting("активный-сундук", new SettingColor(255, 255, 0, 150)));

    // --- состояние прогона ---
    private ProcessState state = ProcessState.IDLE;
    private ProcessState lastState = ProcessState.IDLE;
    private int stateTimer;
    private int ticks;
    private boolean runRequested;

    private final List<ContainerInfo> inputContainers = new ArrayList<>();
    private final List<ContainerInfo> outputContainers = new ArrayList<>();
    @Nullable private ContainerInfo currentContainer;
    @Nullable private ChestNavigator nav;

    private boolean goingToInput;
    private int transportRetries;
    private long lastTransportMessage;
    private Vec3d positionBeforeTransport;
    private int openFailures;
    private int itemsTransferred;
    private boolean awaitingRespawn;
    private boolean awaitingPearl;
    private boolean awaitingEnderChest;
    private int settleTicks;
    private int alignTicks;
    private long startedAt;
    private final Set<BlockPos> flagged = new HashSet<>();

    public StashMover() {
        super("StashMover", "Перенос вещей между двумя зонами стеша.", Category.PLAYER);
    }

    @Override
    public void onActivate() {
        nav = new ChestNavigator();
        resetRun();
        runRequested = true;
    }

    @Override
    public void onDeactivate() {
        if (nav != null) {
            nav.restorePathing();
            nav.stop();
        }
        closeContainer();
        onWorldChange();
    }

    @Override
    public void onWorldChange() {
        if (nav != null) {
            nav.stop();
            nav.restorePathing();
            nav = null;
        }
        resetRun();
        runRequested = false;
    }

    private void resetRun() {
        state = ProcessState.IDLE;
        lastState = ProcessState.IDLE;
        stateTimer = 0;
        ticks = 0;
        currentContainer = null;
        inputContainers.clear();
        outputContainers.clear();
        awaitingEnderChest = false;
        settleTicks = 0;
        alignTicks = 0;
        goingToInput = false;
        transportRetries = 0;
        awaitingRespawn = false;
        awaitingPearl = false;
        openFailures = 0;
        itemsTransferred = 0;
        flagged.clear();
        startedAt = System.currentTimeMillis();
    }

    

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!runRequested || mc.player == null || mc.world == null || mc.interactionManager == null) return;
        ticks++;

        if (!hasValidAreas()) {
            if (ticks % 100 == 1) {
                warning("Зоны не заданы. Смотри на угол: §7.перенос вход 1§f, §7.перенос вход 2§f, потом §7.перенос выход 1§f и §7.перенос выход 2§f.");
            }
            return;
        }

        if (state != lastState) {
            lastState = state;
            stateTimer = 0;
        } else {
            stateTimer++;
        }

        if (mc.player.isDead()) {
            handleDeath();
            return;
        }

        switch (state) {
            case IDLE -> state = ProcessState.CHECKING_LOCATION;
            case CHECKING_LOCATION -> checkLocation();
            case INPUT_PROCESS -> handleInputProcess();
            case OUTPUT_PROCESS -> handleOutputProcess();
            case MOVING_TO_CONTAINER -> handleMovingToContainer();
            case OPENING_CONTAINER -> handleOpeningContainer();
            case TRANSFERRING_ITEMS -> handleTransferring();
            case CLOSING_CONTAINER -> handleClosingContainer();
            case BREAKING_CONTAINER -> handleBreakingContainer();
            case OPENING_ENDERCHEST -> handleOpeningEnderChest();
            case FILLING_ENDERCHEST -> handleFillingEnderChest();
            case EMPTYING_ENDERCHEST -> handleEmptyingEnderChest();
            case KILL_COMMAND -> handleKillCommand();
            case KILL_POSITION_WALK -> handleKillPositionWalk();
            case KILL_POSITION_WAIT -> handleKillPositionWait();
            case GOING_BACK -> handlePearlGoBack();
            case LOADING_PEARL -> handlePearlForward();
            default -> { }
        }
    }

    // ------------------------------------------------------------------
    //  Зоны
    // ------------------------------------------------------------------

    public boolean setAreaCorner(boolean input, int corner, BlockPos pos) {
        if (pos == null || (corner != 1 && corner != 2)) return false;
        if (input && corner == 1) {
            inputAreaPos1.set(encode(pos));
        } else if (input) {
            inputAreaPos2.set(encode(pos));
        } else if (corner == 1) {
            outputAreaPos1.set(encode(pos));
        } else {
            outputAreaPos2.set(encode(pos));
        }
        if (nav != null) nav.stop();
        resetRun();
        return true;
    }

    /** Блок, на который смотрит игрок, — для команды .перенос. */
    @Nullable
    public BlockPos lookedAtBlock() {
        if (mc.player == null || mc.crosshairTarget == null) return null;
        if (mc.crosshairTarget.getType() != net.minecraft.util.hit.HitResult.Type.BLOCK) return null;
        return ((BlockHitResult) mc.crosshairTarget).getBlockPos();
    }

    public int clearAreas() {
        inputAreaPos1.set("");
        inputAreaPos2.set("");
        outputAreaPos1.set("");
        outputAreaPos2.set("");
        if (nav != null) nav.stop();
        resetRun();
        return 1;
    }

    private static String encode(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    @Nullable
    private static BlockPos decode(String value) {
        if (value == null) return null;
        String[] coordinates = value.split(",", -1);
        if (coordinates.length != 3) return null;
        try {
            return new BlockPos(Integer.parseInt(coordinates[0].trim()),
                Integer.parseInt(coordinates[1].trim()), Integer.parseInt(coordinates[2].trim()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean hasValidAreas() {
        return inputCorner1() != null && inputCorner2() != null
            && outputCorner1() != null && outputCorner2() != null;
    }

    private BlockPos inputCorner1() {
        return decode(inputAreaPos1.get());
    }

    private BlockPos inputCorner2() {
        return decode(inputAreaPos2.get());
    }

    private BlockPos outputCorner1() {
        return decode(outputAreaPos1.get());
    }

    private BlockPos outputCorner2() {
        return decode(outputAreaPos2.get());
    }

    private boolean inArea(BlockPos a, BlockPos b, BlockPos p) {
        int range = 20;
        return p.getX() >= Math.min(a.getX(), b.getX()) - range && p.getX() <= Math.max(a.getX(), b.getX()) + range
            && p.getZ() >= Math.min(a.getZ(), b.getZ()) - range && p.getZ() <= Math.max(a.getZ(), b.getZ()) + range;
    }

    private boolean nearInput() {
        return inArea(inputCorner1(), inputCorner2(), mc.player.getBlockPos());
    }

    private boolean nearOutput() {
        return inArea(outputCorner1(), outputCorner2(), mc.player.getBlockPos());
    }

    // ------------------------------------------------------------------
    //  Обнаружение сундуков
    // ------------------------------------------------------------------

    private void detectContainers(boolean input) {
        BlockPos c1 = input ? inputCorner1() : outputCorner1();
        BlockPos c2 = input ? inputCorner2() : outputCorner2();
        List<ContainerInfo> target = input ? inputContainers : outputContainers;
        target.clear();
        flagged.clear();

        int minX = Math.min(c1.getX(), c2.getX()) - 20, maxX = Math.max(c1.getX(), c2.getX()) + 20;
        int minZ = Math.min(c1.getZ(), c2.getZ()) - 20, maxZ = Math.max(c1.getZ(), c2.getZ()) + 20;
        String dim = mc.world.getRegistryKey().getValue().toString();

        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                if (!mc.world.getChunkManager().isChunkLoaded(cx, cz)) continue;
                var chunk = mc.world.getChunkManager().getChunk(cx, cz, net.minecraft.world.chunk.ChunkStatus.FULL, false);
                if (chunk == null) continue;

                for (var be : chunk.getBlockEntities().values()) {
                    if (!ContainerGeometry.isContainerBlock(be.getCachedState())) continue;
                    BlockPos canonical = ContainerGeometry.canonical(mc.world, be.getPos());
                    if (!flagged.add(canonical)) continue;
                    if (canonical.getX() < minX || canonical.getX() > maxX
                        || canonical.getZ() < minZ || canonical.getZ() > maxZ) continue;

                    TrackedContainer tracked = ChestTrackerDataManager.getData().getContainer(canonical, dim);
                    int used = tracked != null ? tracked.getUsedStacks() : 0;
                    String type = tracked != null ? tracked.getContainerType() : containerTypeAt(canonical);
                    target.add(new ContainerInfo(canonical, type,
                        ContainerGeometry.slotCount(mc.world, canonical, type, used), used, tracked != null && used == 0));
                }
            }
        }
    }

    private String containerTypeAt(BlockPos pos) {
        String block = mc.world.getBlockState(pos).getBlock().getName().getString();
        if (block.contains("barrel")) return "barrel";
        if (block.contains("trapped")) return "trapped_chest";
        return "chest";
    }

    // ------------------------------------------------------------------
    //  Поток
    // ------------------------------------------------------------------

    private void checkLocation() {
        if (nearInput()) {
            goingToInput = true;
            detectContainers(true);
            info("У входа найдено сундуков: §f" + inputContainers.size());
            state = ProcessState.INPUT_PROCESS;
        } else if (nearOutput()) {
            goingToInput = false;
            detectContainers(false);
            info("У выхода найдено сундуков: §f" + outputContainers.size());
            state = ProcessState.OUTPUT_PROCESS;
            stateTimer = 10;
        } else {
            if (stateTimer % 100 == 0) warning("Игрок не у зон, жду. Подойди к зоне входа или выхода.");
        }
    }

    private void handleInputProcess() {
        if (inventoryFull()) {
            if (fillEnderChest.get() && state != ProcessState.FILLING_ENDERCHEST) {
                state = ProcessState.OPENING_ENDERCHEST;
            } else {
                startTransport(false);
            }
            return;
        }

        currentContainer = nearestWithItems(inputContainers);
        if (currentContainer == null) {
            detectContainers(true);
            currentContainer = nearestWithItems(inputContainers);
        }

        if (currentContainer == null) {
            startTransport(false);
        } else {
            state = ProcessState.MOVING_TO_CONTAINER;
        }
    }

    private void handleOutputProcess() {
        if (!hasCargo()) {
            startTransport(true);
            return;
        }

        currentContainer = nearestUsableOutput();
        if (currentContainer == null) {
            detectContainers(false);
            currentContainer = nearestUsableOutput();
        }

        if (currentContainer == null) {
            warning("Нет сундука для выгрузки, жду. Проверь зону выхода и что сундуки не заняты.");
            stateTimer = 0;
            return;
        }
        state = ProcessState.MOVING_TO_CONTAINER;
    }

    @Nullable
    private ContainerInfo nearestWithItems(List<ContainerInfo> list) {
        return list.stream()
            .filter(c -> !c.empty && !c.skipped)
            .min(Comparator.comparingDouble(c -> mc.player.getBlockPos().getSquaredDistance(c.pos)))
            .orElse(null);
    }

    @Nullable
    private ContainerInfo nearestUsableOutput() {
        return outputContainers.stream()
            .filter(c -> !c.skipped && c.usedSlots < c.totalSlots)
            .min(Comparator.comparingDouble(c -> mc.player.getBlockPos().getSquaredDistance(c.pos)))
            .orElse(null);
    }

    private void handleMovingToContainer() {
        if (currentContainer == null) {
            state = goingToInput ? ProcessState.INPUT_PROCESS : ProcessState.OUTPUT_PROCESS;
            return;
        }
        if (mc.world.isPosLoaded(currentContainer.pos) && !ContainerGeometry.isContainerBlock(mc.world.getBlockState(currentContainer.pos))) {
            currentContainer.skipped = true;
            currentContainer = null;
            return;
        }

        ChestNavigator.Status status = nav().travelTo(currentContainer.pos);
        if (status == ChestNavigator.Status.ARRIVED) {
            state = ProcessState.OPENING_CONTAINER;
            stateTimer = moveDelay.get();
        } else if (status == ChestNavigator.Status.UNREACHABLE) {
            note("не дойти", currentContainer.pos, nav().failure());
            currentContainer.skipped = true;
            currentContainer = null;
            state = goingToInput ? ProcessState.INPUT_PROCESS : ProcessState.OUTPUT_PROCESS;
        } else if (stateTimer > 2000) {
            note("не дошёл за отведённое время", currentContainer.pos, "");
            currentContainer.skipped = true;
            currentContainer = null;
            state = goingToInput ? ProcessState.INPUT_PROCESS : ProcessState.OUTPUT_PROCESS;
        }
    }

    private void handleOpeningContainer() {
        if (currentContainer == null) return;

        BlockHitResult hit = nav().openHit(currentContainer.pos);
        if (hit == null) {
            if (++openFailures > OPEN_RETRIES) {
                note("не открывается", currentContainer.pos, "");
                currentContainer.skipped = true;
                currentContainer = null;
                state = goingToInput ? ProcessState.INPUT_PROCESS : ProcessState.OUTPUT_PROCESS;
            } else {
                state = ProcessState.MOVING_TO_CONTAINER;
            }
            return;
        }

        if (!settled()) {
            alignTicks = 0;
            return;
        }
        if (!turnTowards(hit.getPos())) {
            alignTicks = 0;
            return;
        }
        if (alignTicks++ < 1) return;

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
        stateTimer = openDelay.get();
        // Экран откроется только через несколько тиков, поэтому проверять его
        // здесь бессмысленно: счётчик неудач растёт в handleTransferring.
        state = ProcessState.TRANSFERRING_ITEMS;
    }

    private void handleTransferring() {
        if (stateTimer < transferDelay.get()) return;

        if (!(mc.currentScreen instanceof HandledScreen)) {
            // экран закрылся сам — пробуем открыть заново
            if (currentContainer != null && !currentContainer.empty && ++openFailures <= OPEN_RETRIES) {
                state = ProcessState.MOVING_TO_CONTAINER;
            } else {
                state = ProcessState.CLOSING_CONTAINER;
            }
            return;
        }
        if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) {
            state = ProcessState.CLOSING_CONTAINER;
            return;
        }

        int total = handler.getRows() * 9;
        if (goingToInput) {
            // забираем груз из сундука в инвентарь
            int moved = 0;
            for (int i = 0; i < total && moved < clicksPerTick.get(); i++) {
                ItemStack stack = handler.slots.get(i).getStack();
                if (stack.isEmpty() || !isCargo(stack)) continue;
                mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                itemsTransferred++;
                moved++;
            }

            if (moved == 0) {
                currentContainer.empty = true;
                currentContainer.usedSlots = 0;
                state = ProcessState.CLOSING_CONTAINER;
            } else if (inventoryFull()) {
                state = ProcessState.CLOSING_CONTAINER;
            }
        } else {
            // выкладываем груз из инвентаря в сундук
            int free = 0;
            for (int i = 0; i < total; i++) if (handler.slots.get(i).getStack().isEmpty()) free++;

            if (free == 0) {
                note("полный получатель", currentContainer == null ? null : currentContainer.pos, "");
                currentContainer.skipped = true;
                currentContainer = null;
                state = ProcessState.CLOSING_CONTAINER;
                return;
            }

            int moved = 0;
            for (int i = 0; i < INVENTORY_SIZE && moved < clicksPerTick.get() && moved < free; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.isEmpty() || !isCargo(stack)) continue;
                mc.interactionManager.clickSlot(handler.syncId, invIndexToMenuId(handler, i), 0, SlotActionType.QUICK_MOVE, mc.player);
                moved++;
            }

            if (moved == 0 || !hasCargo()) {
                currentContainer.usedSlots = total - free;
                state = ProcessState.CLOSING_CONTAINER;
            }
        }
    }

    private void handleClosingContainer() {
        closeContainer();
        stateTimer = transferDelay.get();

        if (goingToInput) {
            if (onlyShulkers.get()) dropNonCargo();
            if (currentContainer != null && currentContainer.empty && breakEmpty.get()) {
                state = ProcessState.BREAKING_CONTAINER;
            } else {
                state = ProcessState.INPUT_PROCESS;
            }
        } else {
            state = ProcessState.OUTPUT_PROCESS;
        }
    }

    private void handleBreakingContainer() {
        if (currentContainer == null || !settled()) {
            state = ProcessState.INPUT_PROCESS;
            return;
        }
        BlockHitResult hit = nav().openHit(currentContainer.pos);
        if (hit == null || !turnTowards(hit.getPos())) return;

        if (stateTimer > 40) {
            flagged.add(currentContainer.pos);
            info("Сундук " + currentContainer.pos.toShortString() + " пуст, помечаю как разобранный.");
            currentContainer.skipped = true;
            currentContainer = null;
            state = ProcessState.INPUT_PROCESS;
        }
    }

    private void handleDeath() {
        if (goingToInput) {
            // после смерти у входа просто продолжаем
            awaitingRespawn = false;
            state = ProcessState.KILL_POSITION_WAIT;
            stateTimer = 0;
        }
    }

    // ------------------------------------------------------------------
    //  Эндер-сундук как промежуточная ёмкость
    // ------------------------------------------------------------------

    private void handleOpeningEnderChest() {
        if (!(mc.currentScreen instanceof HandledScreen)) {
            // ищем эндер-сундук рядом, открываем
            BlockPos ec = findEnderChest();
            if (ec == null) {
                warning("Эндер-сундук не найден рядом, еду с грузом напрямую.");
                startTransport(false);
                return;
            }
            BlockHitResult hit = nav().openHit(ec);
            if (hit == null || !turnTowards(hit.getPos())) return;
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            mc.player.swingHand(Hand.MAIN_HAND);
            stateTimer = openDelay.get();
            awaitingEnderChest = true;
        }
        // Эндер-сундук открывается обычным GenericContainerScreenHandler, так что
        // по типу обработчика его не отличить — ориентируемся на флаг клика.
        if (awaitingEnderChest && mc.currentScreen instanceof HandledScreen && !(mc.currentScreen instanceof InventoryScreen)) {
            awaitingEnderChest = false;
            state = ProcessState.FILLING_ENDERCHEST;
        } else if (stateTimer > 60) {
            warning("Эндер-сундук не открылся, еду напрямую.");
            startTransport(false);
        }
    }

    private void handleFillingEnderChest() {
        if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) {
            state = ProcessState.CLOSING_CONTAINER;
            state = ProcessState.INPUT_PROCESS;
            return;
        }
        int moved = 0;
        for (int i = 0; i < INVENTORY_SIZE && moved < clicksPerTick.get(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty() || !isCargo(stack)) continue;
            mc.interactionManager.clickSlot(handler.syncId, invIndexToMenuId(handler, i), 0, SlotActionType.QUICK_MOVE, mc.player);
            moved++;
        }
        if (moved == 0) {
            closeContainer();
            state = ProcessState.INPUT_PROCESS;
        }
    }

    private void handleEmptyingEnderChest() {
        // обратная ветка: забираем из эндер-сундука, когда у выхода инвентарь полон
        handleFillingEnderChest();
    }

    @Nullable
    private BlockPos findEnderChest() {
        BlockPos p = mc.player.getBlockPos();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos q = p.add(dx, dy, dz);
                    if (mc.world.getBlockState(q).getBlock() instanceof net.minecraft.block.EnderChestBlock) {
                        return q;
                    }
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  Перенос
    // ------------------------------------------------------------------

    private void startTransport(boolean toInput) {
        goingToInput = toInput;
        currentContainer = null;
        closeContainer();
        nav().stop();
        positionBeforeTransport = mc.player.getEntityPos();
        transportRetries = 0;
        awaitingPearl = false;
        awaitingRespawn = false;

        TransportMethod method = toInput ? goBackMethod.get() : forwardMethod.get();
        switch (method) {
            case PEARL -> {
                state = toInput ? ProcessState.GOING_BACK : ProcessState.LOADING_PEARL;
            }
            case KILL -> state = ProcessState.KILL_COMMAND;
            case KILL_POSITION -> state = ProcessState.KILL_POSITION_WALK;
        }
    }

    private void handlePearlForward() {
        if (!awaitingPearl) {
            sendChat(pearlCommand.get());
            awaitingPearl = true;
            lastTransportMessage = System.currentTimeMillis();
        }
        if (positionBeforeTransport != null && mc.player.getEntityPos().squaredDistanceTo(positionBeforeTransport) > 100.0) {
            info("Телепорт к зоне выхода удался.");
            onArrived();
            return;
        }
        if (System.currentTimeMillis() - lastTransportMessage > pearlTimeout.get() * 1000L) {
            if (++transportRetries > maxRetries.get()) {
                error("Жемчужина не сработала за " + maxRetries.get() + " попыток. Останавливаюсь.");
                awaitingPearl = false;
                toggle();
            } else {
                sendChat(pearlCommand.get());
                lastTransportMessage = System.currentTimeMillis();
            }
        }
    }

    private void handlePearlGoBack() {
        if (!awaitingPearl) {
            sendChat(goBackCommand.get());
            awaitingPearl = true;
            lastTransportMessage = System.currentTimeMillis();
        }
        if (positionBeforeTransport != null && mc.player.getEntityPos().squaredDistanceTo(positionBeforeTransport) > 100.0) {
            info("Вернулся к зоне входа.");
            onArrived();
            return;
        }
        if (System.currentTimeMillis() - lastTransportMessage > pearlTimeout.get() * 1000L) {
            if (++transportRetries > maxRetries.get()) {
                error("Обратная жемчужина не сработала. Останавливаюсь.");
                awaitingPearl = false;
                toggle();
            } else {
                sendChat(goBackCommand.get());
                lastTransportMessage = System.currentTimeMillis();
            }
        }
    }

    private void handleKillCommand() {
        if (!awaitingRespawn) {
            String base = (goingToInput ? goBackKillCommand.get() : forwardKillCommand.get()).trim();
            boolean random = goingToInput ? goBackKillRandom.get() : forwardKillRandom.get();
            String cmd = base.startsWith("/") ? base.substring(1) : base;
            if (random) cmd = cmd + " " + java.util.UUID.randomUUID().toString().substring(0, 4);
            sendChatCommand(cmd);
            awaitingRespawn = true;
            lastTransportMessage = System.currentTimeMillis();
            info("Отправил команду смерти, жду респавна.");
        }
        if (System.currentTimeMillis() - lastTransportMessage > 3000L) {
            awaitingRespawn = false;
            // после /kill игрок появляется у зоны респавна; дальше разбираемся
            if (nearInput()) {
                onArrived();
            } else if (nearOutput()) {
                onArrived();
            } else if (++transportRetries > maxRetries.get()) {
                warning("После смерти игрок не у зон. Переносы на /kill требуют, чтобы респавн был рядом со стешем.");
                toggle();
            } else {
                state = ProcessState.KILL_COMMAND;
            }
        }
    }

    private void handleKillPositionWalk() {
        if (mc.player.isDead()) return;

        var death = mc.player.getLastDeathPos();
        if (death.isEmpty()) {
            // ещё не умирали — шлём команду
            String base = (goingToInput ? goBackKillCommand.get() : forwardKillCommand.get()).trim();
            String cmd = base.startsWith("/") ? base.substring(1) : base;
            sendChatCommand(cmd);
            state = ProcessState.KILL_POSITION_WAIT;
            stateTimer = 0;
            return;
        }

        BlockPos target = death.get().pos();
        if (mc.player.getBlockPos().getSquaredDistance(target) > 9) {
            if (stateTimer > 2000) {
                warning("Не дошёл до места смерти, сдаюсь.");
                toggle();
            }
            return;
        }
        state = ProcessState.KILL_POSITION_WAIT;
        stateTimer = 0;
    }

    private void handleKillPositionWait() {
        if (!mc.player.isDead()) {
            if (stateTimer > 40) {
                info("Респавн у места смерти, продолжаю.");
                onArrived();
            }
        } else if (stateTimer > 200) {
            warning("Смерть не отработала.");
            toggle();
        }
    }

    private void onArrived() {
        awaitingPearl = false;
        awaitingRespawn = false;
        closeContainer();
        if (goingToInput) {
            detectContainers(true);
            state = ProcessState.INPUT_PROCESS;
        } else {
            detectContainers(false);
            state = ProcessState.OUTPUT_PROCESS;
            stateTimer = 10;
        }
    }

    // ------------------------------------------------------------------
    //  Мелочи
    // ------------------------------------------------------------------

    private void sendChat(String message) {
        if (mc.getNetworkHandler() != null && !message.isBlank()) {
            mc.getNetworkHandler().sendChatMessage(message);
        }
    }

    private void sendChatCommand(String command) {
        if (mc.getNetworkHandler() != null && !command.isBlank()) {
            mc.getNetworkHandler().sendChatCommand(command);
        }
    }

    private boolean settled() {
        nav().stop();
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
        return settleTicks++ >= 2;
    }

    private boolean turnTowards(Vec3d point) {
        Vec3d eye = mc.player.getEyePos();
        double dx = point.x - eye.x, dy = point.y - eye.y, dz = point.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));

        float dYaw = MathHelper.wrapDegrees(targetYaw - mc.player.getYaw());
        float dPitch = targetPitch - mc.player.getPitch();
        float step = 45.0F;
        float yaw = mc.player.getYaw() + MathHelper.clamp(dYaw, -step, step);
        mc.player.setYaw(yaw);
        mc.player.setHeadYaw(yaw);
        mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + MathHelper.clamp(dPitch, -step, step), -90.0F, 90.0F));
        return Math.abs(dYaw) <= 3.0F && Math.abs(dPitch) <= 3.0F;
    }

    private void note(String reason, @Nullable BlockPos pos, String detail) {
        if (ticks % 200 == 0) {
            info("Пропуск: " + reason + (pos == null ? "" : " " + pos.toShortString()) + (detail.isEmpty() ? "" : " — " + detail) + ".");
        }
    }

    private ChestNavigator nav() {
        if (nav == null) nav = new ChestNavigator();
        return nav;
    }

    @Nullable
    private GenericContainerScreenHandler openContainer() {
        if (mc.currentScreen instanceof HandledScreen && !(mc.currentScreen instanceof InventoryScreen)) {
            return mc.player.currentScreenHandler instanceof GenericContainerScreenHandler h ? h : null;
        }
        return null;
    }

    private void closeContainer() {
        if (mc.currentScreen != null && mc.currentScreen instanceof HandledScreen && !(mc.currentScreen instanceof InventoryScreen)) {
            mc.currentScreen.close();
        }
    }

    private boolean isCargo(ItemStack stack) {
        return !onlyShulkers.get() || ContainerGeometry.isShulkerItem(stack);
    }

    private boolean hasCargo() {
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && isCargo(s)) return true;
        }
        return false;
    }

    private boolean inventoryFull() {
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return false;
        }
        return true;
    }

    private void dropNonCargo() {
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty() || isCargo(s)) continue;
            // Выброс слота без смены хотбара: кладём предмет к игроку вручную,
            // иначе пришлось бы переключать selectedSlot, и сервер увидит
            // несоответствие между хотбаром и инвентарём.
            if (mc.player.canDropItems()) {
                mc.player.dropItem(s.copy(), false);
                mc.player.getInventory().setStack(i, ItemStack.EMPTY);
            }
        }
    }

    private static int invIndexToMenuId(GenericContainerScreenHandler menu, int invIndex) {
        return menu.getRows() * 9 + (invIndex < 9 ? invIndex + 27 : invIndex - 9);
    }

    // ------------------------------------------------------------------
    //  Отрисовка
    // ------------------------------------------------------------------

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!renderSelection.get() || !hasValidAreas() || mc.player == null) return;

        drawArea(event, inputCorner1(), inputCorner2(), inputAreaOutline.get());
        drawArea(event, outputCorner1(), outputCorner2(), outputAreaOutline.get());

        for (ContainerInfo c : inputContainers) drawContainer(event, c, inputContainerColor.get());
        for (ContainerInfo c : outputContainers) drawContainer(event, c, outputContainerColor.get());
        if (currentContainer != null) drawContainer(event, currentContainer, activeContainerColor.get());
    }

    private void drawArea(Render3DEvent event, BlockPos a, BlockPos b, SettingColor color) {
        if (color.a <= 5) return;
        int minY = Math.min(a.getY(), b.getY());
        int maxY = Math.max(a.getY(), b.getY());
        Box box = new Box(Math.min(a.getX(), b.getX()) - 20.0, minY, Math.min(a.getZ(), b.getZ()) - 20.0,
            Math.max(a.getX(), b.getX()) + 21.0, maxY, Math.max(a.getZ(), b.getZ()) + 21.0);
        event.renderer.box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
            new SettingColor(color.r, color.g, color.b, 0), color, ShapeMode.Lines, 0);
    }

    private void drawContainer(Render3DEvent event, ContainerInfo c, SettingColor color) {
        if (color.a <= 5) return;
        if (c.pos.getSquaredDistance(mc.player.getEntityPos()) > 256) return;
        event.renderer.box(c.pos.getX() + 0.1, c.pos.getY() + 0.1, c.pos.getZ() + 0.1,
            c.pos.getX() + 0.9, c.pos.getY() + 0.9, c.pos.getZ() + 0.9,
            new SettingColor(color.r, color.g, color.b, Math.min(60, color.a)), color, ShapeMode.Both, 0);
    }

    // ------------------------------------------------------------------

    /** Сундук в зоне: где стоит, сколько слотов и занято. */
    private static class ContainerInfo {
        final BlockPos pos;
        final String type;
        final int totalSlots;
        int usedSlots;
        boolean empty;
        boolean skipped;

        ContainerInfo(BlockPos pos, String type, int totalSlots, int usedSlots, boolean empty) {
            this.pos = pos;
            this.type = type;
            this.totalSlots = totalSlots;
            this.usedSlots = usedSlots;
            this.empty = empty;
        }
    }

    public enum TransportMethod {
        PEARL("Жемчужина"),
        KILL("Команда смерти"),
        KILL_POSITION("Смерть и подход");

        private final String title;

        TransportMethod(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private enum ProcessState {
        IDLE,
        CHECKING_LOCATION,
        INPUT_PROCESS,
        OUTPUT_PROCESS,
        MOVING_TO_CONTAINER,
        OPENING_CONTAINER,
        TRANSFERRING_ITEMS,
        CLOSING_CONTAINER,
        BREAKING_CONTAINER,
        OPENING_ENDERCHEST,
        FILLING_ENDERCHEST,
        EMPTYING_ENDERCHEST,
        LOADING_PEARL,
        GOING_BACK,
        KILL_COMMAND,
        KILL_POSITION_WALK,
        KILL_POSITION_WAIT
    }
}

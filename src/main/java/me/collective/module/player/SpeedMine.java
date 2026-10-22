package me.collective.module.player;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.PacketEvent;
import me.collective.event.events.Render3DEvent;
import me.collective.event.events.StartBreakingBlockEvent;
import me.collective.event.events.TickEvent;
import me.collective.manager.FriendManager;
import me.collective.module.Module;
import me.collective.render.Renderer3D;
import me.collective.render.ShapeMode;
import me.collective.setting.*;
import me.collective.util.FadeAnimator;
import me.collective.util.GrimUtils;
import me.collective.util.RotationUtils;
import me.collective.util.SettingColor;
import me.collective.util.SwapManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * SpeedMine — быстрый packet-mine с обходом GrimAC.
 *
 * Порт из BepHax (NEW-SRC, Mojmap) на Yarn 1.21.11.
 * Основные адаптации 1.21.11 описаны по коду: BlockStatePredictionHandler удалён
 * (см. sendInstaBreak), пакет ACK последовательности блоков удалён (см. onPacketReceive).
 */
public class SpeedMine extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgAutoMine = this.settings.createGroup("Авто-майнинг");
    private final SettingGroup sgRender = this.settings.createGroup("Отображение");

    private final Setting<SpeedmineMode> modeConfig = this.sgGeneral.add(new EnumSetting.Builder<SpeedmineMode>()
        .name("режим")
        .description("Режим майнинга.")
        .defaultValue(SpeedmineMode.PACKET)
        .build());
    private final Setting<Boolean> multitaskConfig = this.sgGeneral.add(new BoolSetting.Builder()
        .name("мультизадачность")
        .description("Продолжать копать во время использования предметов. Флагует проверку MultiActions Grim на обновлённых серверах — для 2b2t оставь выключенным.")
        .defaultValue(false)
        .visible(() -> this.modeConfig.get() == SpeedmineMode.PACKET)
        .build());
    public final Setting<Boolean> doubleBreakConfig = this.sgGeneral.add(new BoolSetting.Builder()
        .name("двойное-разрушение")
        .description("Копать два блока одновременно: клик по второму блоку передаёт первый собственному таймеру разрушения сервера (он завершается сам по расписанию), пока новый блок копается параллельно. Очередь кликов продолжает кормить второй слот, так что полная очередь копает два блока за раз. Быстрые блоки передаются мгновенно; блокам класса обсидиана нужно ~половина прогресса, иначе античит съест передачу — для лучшего перекрытия кликай сначала медленный блок.")
        .defaultValue(true)
        .visible(() -> this.modeConfig.get() == SpeedmineMode.PACKET)
        .build());
    private final Setting<Boolean> clickQueueConfig = this.sgGeneral.add(new BoolSetting.Builder()
        .name("очередь-кликов")
        .description("Клики по блокам во время майнинга ставят их в очередь (копаются по порядку; при включённом двойном-разрушении очередь копает два блока за раз) вместо замены текущего блока.")
        .defaultValue(true)
        .visible(() -> this.modeConfig.get() == SpeedmineMode.PACKET)
        .build());
    private final Setting<Integer> queueLimitConfig = this.sgGeneral.add(new IntSetting.Builder()
        .name("лимит-очереди")
        .description("Максимум блоков в очереди кликов; лишние клики игнорируются.")
        .defaultValue(10)
        .min(1)
        .max(200)
        .sliderRange(1, 20)
        .visible(() -> this.modeConfig.get() == SpeedmineMode.PACKET && this.clickQueueConfig.get())
        .build());
    private final Setting<Keybind> clearQueueKey = this.sgGeneral.add(new KeybindSetting.Builder()
        .name("клавиша-очистки-очереди")
        .description("Отменяет текущий майнинг(и) и очищает очередь кликов при нажатии.")
        .defaultValue(Keybind.none())
        .visible(() -> this.modeConfig.get() == SpeedmineMode.PACKET)
        .build());
    private final Setting<Double> rangeConfig = this.sgGeneral.add(new DoubleSetting.Builder()
        .name("дальность")
        .description("Дальность копания блоков, измеряется до ближайшей точки блока, как у Grim. Выше ~4.5 (выживальческая дальность взаимодействия) обновлённый Grim FarBreak отменяет копание.")
        .defaultValue(6.0)
        .min(0.1)
        .sliderRange(0.1, 6.0)
        .visible(() -> this.modeConfig.get() == SpeedmineMode.PACKET)
        .build());
    private final Setting<Double> speedConfig = this.sgGeneral.add(new DoubleSetting.Builder()
        .name("скорость")
        .description("Прогресс разрушения, на котором отправляется пакет разрушения. 0.7 — собственная линия мгновенного разрушения сервера (быстрее блока легально не сломать), требует grim-bypass. 1.0 = точная ванильная скорость.")
        .defaultValue(0.7)
        .min(0.01)
        .max(2.0)
        .sliderRange(0.1, 1.5)
        .build());
    private final Setting<Integer> breakDelayConfig = this.sgGeneral.add(new IntSetting.Builder()
        .name("задержка-разрушения")
        .description("Минимум тиков между завершением разрушения и началом следующего блока. 0 — темп задаёт зеркалированный бюджет интервалов старта Grim (всплески, затем стабилизация около 275 мс); повышай только чтобы специально замедлиться.")
        .defaultValue(0)
        .min(0)
        .max(10)
        .sliderRange(0, 10)
        .visible(() -> this.modeConfig.get() == SpeedmineMode.PACKET)
        .build());
    private final Setting<Integer> breakDelayRandomConfig = this.sgGeneral.add(new IntSetting.Builder()
        .name("разброс-задержки")
        .description("Дополнительные случайные тики (0..это) к задержке разрушения на блок, чтобы блоки из очереди не ломались в постоянном ритме. Только добавляет задержку — никогда не опускается ниже задержки-разрушения. 0 = выкл.")
        .defaultValue(0)
        .min(0)
        .max(10)
        .sliderRange(0, 10)
        .visible(() -> this.modeConfig.get() == SpeedmineMode.PACKET)
        .build());
    private final Setting<GrimBypass> grimBypassConfig = this.sgGeneral.add(new EnumSetting.Builder<GrimBypass>()
        .name("grim-обход")
        .description("Отправляет холостое копание выше предела постройки (сервер его отбрасывает, Grim читает как воздух), из-за чего предсказанное время разрушения у Grim схлопывается и разрушение под ним перестаёт накапливаться. AUTO тратит один такой только когда тайминг иначе был бы отменён — цена: один флаг AirLiquidBreak за обойдённый блок. Нужен для скорости ниже 1.0 и для двойного-разрушения по блокам класса обсидиана.")
        .defaultValue(GrimBypass.ALWAYS)
        .visible(() -> this.modeConfig.get() == SpeedmineMode.PACKET)
        .build());
    private final Setting<SwingMode> swingConfig = this.sgGeneral.add(new EnumSetting.Builder<SwingMode>()
        .name("взмах")
        .description("Взмах рукой при майнинге: FULL — видимый взмах, PACKET — только серверу (без анимации), NONE — без взмахов (флагует проверку NoSwingBreak у обновлённого Grim; тогда тик-пакет майнинга несёт сэмплирование скорости разрушения Grim).")
        .defaultValue(SwingMode.PACKET)
        .visible(() -> this.modeConfig.get() == SpeedmineMode.PACKET)
        .build());
    private final Setting<Boolean> instantConfig = this.sgGeneral.add(new BoolSetting.Builder()
        .name("мгновенно")
        .description("Пере-ломает последний добытый блок, когда его ставят заново (city block). Мгновенно, пока собственная бухгалтерия античита делает пере-разрушение бесплатным (позиция виделась воздухом, что схлопывает предсказанное время разрушения); иначе сначала выжидает реальный тайминг, а блок, который так и не сломался видимо, заново ставится в очередь как свежий майнинг вместо спама преждевременными разрушениями.")
        .defaultValue(true)
        .build());
    private final Setting<Integer> remineDelayConfig = this.sgGeneral.add(new IntSetting.Builder()
        .name("задержка-перекопа")
        .description("Тики ожидания после появления добытого блока, прежде чем пере-ломать его. 0 = сменить слот и отправить пере-разрушение в момент возврата (мгновенный перекоп эндер-сундука).")
        .defaultValue(0)
        .min(0)
        .max(20)
        .sliderRange(0, 20)
        .visible(() -> this.modeConfig.get() == SpeedmineMode.PACKET && this.instantConfig.get())
        .build());
    private final Setting<Keybind> instantToggleKey = this.sgGeneral.add(new KeybindSetting.Builder()
        .name("клавиша-мгновенного")
        .description("Клавиша переключения опции мгновенного майнинга.")
        .defaultValue(Keybind.none())
        .build());
    private final Setting<Boolean> persistentConfig = this.sgGeneral.add(new BoolSetting.Builder()
        .name("постоянный")
        .description("При выключении модуля не отправлять ABORT активному майну (сервер прервёт копание сам — удобно для полураскопанных эндер-сундуков). По умолчанию модуль выключается чисто: ABORT уходит, всё состояние сбрасывается.")
        .defaultValue(false)
        .visible(() -> this.modeConfig.get() == SpeedmineMode.PACKET)
        .build());
    private final Setting<Swap> swapConfig = this.sgGeneral.add(new EnumSetting.Builder<Swap>()
        .name("авто-смена")
        .description("Сменять слот на лучший инструмент после завершения майнинга.")
        .defaultValue(Swap.SILENT)
        .visible(() -> this.modeConfig.get() == SpeedmineMode.PACKET)
        .build());
    private final Setting<Double> swapProgressConfig = this.sgGeneral.add(new DoubleSetting.Builder()
        .name("смена-на-прогрессе")
        .description("Прогресс разрушения, на котором происходит тихая смена инструмента (стиль авто-инструмента: взять инструмент прямо перед завершением блока). 0.85 = смена на 85% разрушения.")
        .defaultValue(0.5)
        .min(0.01)
        .max(0.95)
        .sliderRange(0.5, 0.95)
        .visible(() -> this.modeConfig.get() == SpeedmineMode.PACKET && this.swapConfig.get() != Swap.OFF)
        .build());
    private final Setting<Double> swapProgressRandomConfig = this.sgGeneral.add(new DoubleSetting.Builder()
        .name("разброс-смены")
        .description("Случайный джиттер точки смены на блок (+/- столько прогресса), чтобы тайминг смены не образовывал фиксированный паттерн. 0 = выкл.")
        .defaultValue(0.0)
        .min(0.0)
        .max(0.6)
        .sliderRange(0.0, 0.2)
        .visible(() -> this.modeConfig.get() == SpeedmineMode.PACKET && this.swapConfig.get() != Swap.OFF)
        .build());
    private final Setting<Integer> swapDelayConfig = this.sgGeneral.add(new IntSetting.Builder()
        .name("мин-удержание-смены")
        .description("Минимум тиков, что инструмент должен быть удержан и использован до пакета разрушения — страховочный минимум для быстрых блоков, где смена по прогрессу попадает на последний тик.")
        .defaultValue(1)
        .min(1)
        .max(10)
        .sliderRange(1, 10)
        .visible(() -> this.modeConfig.get() == SpeedmineMode.PACKET && this.swapConfig.get() != Swap.OFF)
        .build());
    private final Setting<Integer> swapReleaseDelayConfig = this.sgGeneral.add(new IntSetting.Builder()
        .name("задержка-снятия-смены")
        .description("Тики, что инструмент остаётся удержанным после пакета разрушения, прежде чем сменить обратно (сервер перепроверяет удерживаемый предмет, пока обрабатывает разрушение).")
        .defaultValue(3)
        .min(1)
        .max(20)
        .sliderRange(1, 20)
        .visible(() -> this.modeConfig.get() == SpeedmineMode.PACKET && this.swapConfig.get() != Swap.OFF)
        .build());
    private final Setting<Boolean> rotateConfig = this.sgGeneral.add(new BoolSetting.Builder()
        .name("поворот")
        .description("Тихо поворачиваться на блок во время майнинга (Grim RotationBreak).")
        .defaultValue(false)
        .visible(() -> this.modeConfig.get() == SpeedmineMode.PACKET)
        .build());

    private final Setting<Keybind> autoMineKey = this.sgAutoMine.add(new KeybindSetting.Builder()
        .name("клавиша-авто-майна")
        .description("Клавиша переключения авто-майнинга врагов.")
        .defaultValue(Keybind.none())
        .build());
    private final Setting<Boolean> autoMine = this.sgAutoMine.add(new BoolSetting.Builder()
        .name("авто-майн")
        .description("Автоматически копать блоки вокруг ближайших врагов.")
        .defaultValue(false)
        .build());
    private final Setting<Double> enemyRange = this.sgAutoMine.add(new DoubleSetting.Builder()
        .name("дальность-врагов")
        .description("Дальность поиска врагов-игроков.")
        .defaultValue(6.0)
        .min(1.0)
        .sliderRange(1.0, 10.0)
        .visible(this.autoMine::get)
        .build());
    private final Setting<Boolean> targetHead = this.sgAutoMine.add(new BoolSetting.Builder()
        .name("цель-голова")
        .description("Также целиться в блоки на уровне головы (Y+1).")
        .defaultValue(true)
        .visible(this.autoMine::get)
        .build());
    private final Setting<Boolean> autoRotate = this.sgAutoMine.add(new BoolSetting.Builder()
        .name("авто-поворот")
        .description("Поворачиваться на вражеские блоки (использует тихие ротации).")
        .defaultValue(false)
        .visible(this.autoMine::get)
        .build());
    private final Setting<Boolean> antiCrawl = this.sgAutoMine.add(new BoolSetting.Builder()
        .name("анти-краул")
        .description("Автоматически копать блок над головой при ползании, чтобы встать.")
        .defaultValue(false)
        .visible(this.autoMine::get)
        .build());

    private final Setting<Boolean> render = this.sgRender.add(new BoolSetting.Builder()
        .name("отрисовка")
        .description("Отрисовывать ли блок, который копается.")
        .defaultValue(true)
        .build());
    private final Setting<ShapeMode> shapeMode = this.sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("режим-фигуры")
        .description("Как отрисовываются фигуры.")
        .defaultValue(ShapeMode.Both)
        .build());
    private final Setting<SettingColor> colorConfig = this.sgRender.add(new ColorSetting.Builder()
        .name("цвет-майна")
        .description("Цвет отрисовки майнинга.")
        .defaultValue(new SettingColor(Color.BLUE))
        .visible(() -> this.modeConfig.get() == SpeedmineMode.PACKET)
        .build());
    private final Setting<SettingColor> colorDoneConfig = this.sgRender.add(new ColorSetting.Builder()
        .name("цвет-готово")
        .description("Цвет отрисовки завершённого блока.")
        .defaultValue(new SettingColor(Color.CYAN))
        .visible(() -> this.modeConfig.get() == SpeedmineMode.PACKET)
        .build());
    private final Setting<Integer> fadeTimeConfig = this.sgRender.add(new IntSetting.Builder()
        .name("время-затухания")
        .description("Время затухания.")
        .defaultValue(250)
        .min(0)
        .sliderRange(0, 1000)
        .visible(() -> false)
        .build());

    private final Map<BlockPos, FadeEntry> fadeList = new HashMap<>();
    private final SettingColor renderBoxColor = new SettingColor();
    private final SettingColor renderLineColor = new SettingColor();

    private static final int ALIGN_TIMEOUT_TICKS = 10;
    private static final int MAX_DIG_PACKETS_PER_SECOND = 300;
    private static final int SECONDARY_GRACE_TICKS = 20;
    private static final int REBREAK_EATEN_TICKS = 20;
    private static final int REBREAK_AIM_PARK_TICKS = 60;
    private static final double GRIM_FLAG_MS = 1000.0;
    private static final double BREAK_BUDGET_MS = 800.0;
    private static final double DELAY_BUDGET_MS = 700.0;
    private static final long GRIM_START_GAP_MS = 275L;
    private static final long GRIM_START_FULL_MS = 300L;
    private static final double GRIM_BREAK_SLACK_MS = 25.0;
    private static final int BYPASS_Y_OFFSET = 955;
    private static final int BYPASS_Y_MAX = 2000;
    private static final int MAX_BYPASS_DIGS_PER_SECOND = 40;

    private MiningData activeMine;
    private MiningData secondaryMine;
    private final ArrayDeque<MiningData> pending = new ArrayDeque<>();
    private int destroyDelayTicks;
    private BlockPos lastStartPos;
    private long lastFinishMs;
    private long grimStartMs;
    private boolean grimSampleIsAir;
    private double delayBalanceMirror;
    private double breakBalanceMirror;
    private boolean effMirrorInit;
    private int grimAttrEffLevel;
    private int pendingEffLevel = -1;
    private long effChangeMs;
    private BlockPos instaGracePos;
    private int instaGraceTicks;
    private BlockPos rebreakPos;
    private Direction rebreakDir;
    private int rebreakSeenTicks;
    private volatile boolean rebreakAirSeen;
    private boolean rebreakReplaceSeen;
    private int rebreakStaleTicks;
    private static final int REBREAK_STALE_TICKS = 2;
    private int rebreakSolidTicks;
    private int rebreakAimParkTicks;
    private volatile boolean rebreakAirPending;
    private volatile boolean rebreakReplacePending;
    private int lastDigSequence;
    private int rebreakAckSequence;
    private volatile int lastAckedSequence;
    private volatile int timePacketCount;
    private int rebreakTimeMark;
    private Block rebreakBlock;
    private float rebreakGrimDelta;
    private boolean digSentThisTick;
    private boolean instantTogglePressed = false;
    private boolean autoMineTogglePressed = false;
    private boolean clearQueuePressed = false;
    private PlayerEntity currentTarget = null;
    private long lastAutoMineTime = 0L;
    private long lastAntiCrawlTime = 0L;
    private static final long AUTO_MINE_DELAY_MS = 250L;
    private static final long ANTI_CRAWL_DELAY_MS = 100L;
    private final ArrayDeque<Long> digPacketTimes = new ArrayDeque<>();
    private final ArrayDeque<Long> bypassPacketTimes = new ArrayDeque<>();

    public static SpeedMine INSTANCE;

    public SpeedMine() {
        super("SpeedMine", "Быстрое разрушение блоков (packet-mine) с обходом GrimAC.", Category.PLAYER);
        INSTANCE = this;
    }

    public Setting<Double> getSpeedConfig() {
        return this.speedConfig;
    }

    public Setting<SpeedmineMode> getModeConfig() {
        return this.modeConfig;
    }

    public float getEffectiveThreshold() {
        return this.speedConfig.get().floatValue();
    }

    private float rollSwapPoint() {
        double p = this.swapProgressConfig.get();
        double r = this.swapProgressRandomConfig.get();
        if (r > 0.0) {
            p += (Math.random() * 2.0 - 1.0) * r;
        }

        return (float) MathHelper.clamp(p, 0.1, 0.95);
    }

    private int rollBreakDelay() {
        int jitter = this.breakDelayRandomConfig.get();
        return this.breakDelayConfig.get() + (jitter > 0 ? (int) (Math.random() * (jitter + 1)) : 0);
    }

    @Override
    public void onActivate() {
        this.resetState();
    }

    @Override
    public void onDeactivate() {
        // Выключение всегда чистое: ABORT активному майну, полный сброс состояния.
        // «Постоянный» означает лишь отказ от ABORT-пакета (сервер прервёт копание сам,
        // например, чтобы полураскопанный эндер-сундук продолжил жить), а не удержание модуля.
        if (!this.persistentConfig.get() && this.activeMine != null && this.activeMine.started
                && this.mc.player != null && this.mc.getNetworkHandler() != null) {
            this.sendAbort(this.activeMine.pos);
        }

        this.resetState();
        SwapManager.getInstance().releaseNow(this);
        RotationUtils.getInstance().release(this);
    }

    // No packets here: the old operation must never be aborted in the new world.
    @Override
    public void onWorldChange() {
        this.resetState();
        this.resetGrimMirrors();
        this.digPacketTimes.clear();
        this.digSentThisTick = false;
        this.timePacketCount = 0;
        this.instantTogglePressed = false;
        this.autoMineTogglePressed = false;
        this.clearQueuePressed = false;
        RotationUtils.getInstance().release(this);
    }

    private void resetState() {
        this.activeMine = null;
        this.secondaryMine = null;
        this.pending.clear();
        this.fadeList.clear();
        this.destroyDelayTicks = 0;
        this.instaGracePos = null;
        this.instaGraceTicks = 0;
        this.clearRebreak();
        this.lastAutoMineTime = 0L;
        this.lastAntiCrawlTime = 0L;
        this.currentTarget = null;
    }

    private void resetGrimMirrors() {
        this.lastStartPos = null;
        this.lastFinishMs = 0L;
        this.grimStartMs = 0L;
        this.grimSampleIsAir = false;
        this.lastDigSequence = 0;
        this.rebreakAckSequence = 0;
        this.lastAckedSequence = 0;
        this.delayBalanceMirror = 0.0;
        this.breakBalanceMirror = 0.0;
        this.bypassPacketTimes.clear();
        this.effMirrorInit = false;
        this.grimAttrEffLevel = 0;
        this.pendingEffLevel = -1;
    }

    @EventHandler
    public void onPlayerTick(TickEvent.Pre event) {
        if (this.mc.player != null && this.mc.world != null) {
            if (!this.mc.player.isCreative() && !this.mc.player.isSpectator()) {
                if (this.destroyDelayTicks > 0) {
                    this.destroyDelayTicks--;
                }

                if (this.instaGraceTicks > 0 && --this.instaGraceTicks == 0) {
                    this.instaGracePos = null;
                }

                this.updateGrimEffMirror();
                this.handleKeybinds();
                if (this.modeConfig.get() != SpeedmineMode.DAMAGE) {
                    if (!this.mc.player.isUsingItem() || this.multitaskConfig.get()) {
                        this.updateAutoMine();
                        this.tickRebreak();
                        this.tickSecondary();
                        this.tickActiveMine();
                    }
                }
            }
        }
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        this.digSentThisTick = false;
    }

    private boolean actionConflictTick() {
        return SwapManager.getInstance().isActionConflictTick();
    }

    private void handleKeybinds() {
        if (!this.autoMineKey.get().isPressed() || this.mc.currentScreen != null) {
            this.autoMineTogglePressed = false;
        } else if (!this.autoMineTogglePressed) {
            this.autoMineTogglePressed = true;
            this.autoMine.set(!this.autoMine.get());
            String status = this.autoMine.get() ? "§aвключён" : "§cвыключен";
            this.mc.player.sendMessage(Text.literal("§7[§bFast Mine§7] §fАвто-майн " + status), false);
        }

        if (!this.instantToggleKey.get().isPressed() || this.mc.currentScreen != null) {
            this.instantTogglePressed = false;
        } else if (!this.instantTogglePressed) {
            this.instantTogglePressed = true;
            this.instantConfig.set(!this.instantConfig.get());
            if (!this.instantConfig.get()) {
                this.clearRebreak();
            }

            String status = this.instantConfig.get() ? "§aвключён" : "§cвыключен";
            this.mc.player.sendMessage(Text.literal("§7[§bFast Mine§7] §fМгновенный майнинг " + status), false);
        }

        if (!this.clearQueueKey.get().isPressed() || this.mc.currentScreen != null) {
            this.clearQueuePressed = false;
        } else if (!this.clearQueuePressed) {
            this.clearQueuePressed = true;
            int cleared = this.pending.size() + (this.activeMine != null ? 1 : 0) + (this.secondaryMine != null ? 1 : 0);
            if (cleared > 0) {
                if (this.activeMine != null && this.activeMine.started) {
                    this.sendAbort(this.activeMine.pos);
                }

                this.activeMine = null;
                this.clearSecondary();
                this.pending.clear();
                this.mc.player.sendMessage(Text.literal("§7[§bFast Mine§7] §fОчередь очищена §7(" + cleared + ")"), false);
            }
        }
    }

    private void updateAutoMine() {
        if (this.autoMine.get() && this.modeConfig.get() == SpeedmineMode.PACKET) {
            if (this.activeMine == null && this.pending.isEmpty()) {
                long currentTime = System.currentTimeMillis();
                if (this.antiCrawl.get() && this.mc.player.getPose() == EntityPose.SWIMMING && currentTime - this.lastAntiCrawlTime >= 100L) {
                    BlockPos crawlBlock = this.getAntiCrawlBlock();
                    if (crawlBlock != null && !this.isMiningBlock(crawlBlock)) {
                        this.pending.add(new MiningData(crawlBlock, Direction.DOWN, true, false));
                        this.lastAntiCrawlTime = currentTime;
                        return;
                    }
                }

                this.currentTarget = this.getClosestEnemy();
                if (this.currentTarget != null && currentTime - this.lastAutoMineTime >= 250L) {
                    BlockPos targetBlock = this.findBestEnemyBlock(this.currentTarget);
                    if (targetBlock != null && !this.isMiningBlock(targetBlock)) {
                        Direction direction = this.getInteractDirection(targetBlock);
                        this.pending.add(new MiningData(targetBlock, direction, true, false));
                        this.lastAutoMineTime = currentTime;
                    }
                }
            }
        }
    }

    private void tickRebreak() {
        if (this.instantConfig.get() && this.rebreakPos != null) {
            if (this.lastStartPos == null || !this.lastStartPos.equals(this.rebreakPos)) {
                this.clearRebreak();
            } else if (this.activeMine == null || !this.activeMine.pos.equals(this.rebreakPos)) {
                if (this.rebreakAirPending) {
                    this.rebreakAirPending = false;
                    this.noteRebreakAir();
                }

                if (this.rebreakReplacePending) {
                    this.rebreakReplacePending = false;
                    this.rebreakReplaceSeen = true;
                }

                BlockState state = this.mc.world.getBlockState(this.rebreakPos);
                if (state.isAir()) {
                    this.noteRebreakAir();
                    this.rebreakReplaceSeen = true;
                    this.rebreakStaleTicks = 0;
                    this.rebreakSeenTicks = 0;
                    if (this.rotateConfig.get() && this.canSpoofAim() && ++this.rebreakAimParkTicks <= 60) {
                        this.assertMiningAim(this.rebreakPos);
                    }
                } else if (state.getHardness(this.mc.world, this.rebreakPos) != -1.0F && this.inRange(this.rebreakPos)) {
                    if (state.getBlock() != this.rebreakBlock && state.getFluidState().isEmpty()) {
                        if (!this.rebreakAirSeen) {
                            this.noteRebreakAir();
                        }

                        this.rebreakReplaceSeen = true;
                        this.rebreakBlock = state.getBlock();
                    }

                    if (!this.rebreakAirSeen) {
                        for (Entity drop : this.mc.world.getOtherEntities(this.mc.player, new Box(this.rebreakPos), e -> e instanceof ItemEntity)) {
                            if (drop.age <= 3) {
                                this.noteRebreakAir();
                                this.rebreakReplaceSeen = true;
                                break;
                            }
                        }
                    }

                    if (!this.rebreakAirSeen) {
                        // В 1.21.11 пакет ACK последовательности блоков удалён, поэтому lastAckedSequence
                        // всегда 0, а rebreakAckSequence >= 0 — условие lastAckedSequence - rebreakAckSequence >= 0
                        // всегда истинно. Пере-разрушение фактически опирается только на время.
                        if (this.lastAckedSequence - this.rebreakAckSequence >= 0 || this.timePacketCount - this.rebreakTimeMark >= 2) {
                            if (++this.rebreakSolidTicks > 20 && (this.secondaryMine == null || !this.secondaryMine.pos.equals(this.rebreakPos))) {
                                BlockPos pos = this.rebreakPos;
                                Direction dir = this.rebreakDir;
                                this.clearRebreak();
                                if (this.isValidTarget(pos) && !this.isMiningBlock(pos)) {
                                    this.pending.addFirst(new MiningData(pos, dir, false, false));
                                }
                            }
                        }
                    } else {
                        if (!this.rebreakReplaceSeen) {
                            if (++this.rebreakStaleTicks < 2) {
                                return;
                            }

                            this.rebreakReplaceSeen = true;
                        }

                        this.rebreakSeenTicks++;
                        int delay = this.remineDelayConfig.get();
                        if (this.rebreakSeenTicks <= delay) {
                            if (this.rebreakSeenTicks == delay && !this.actionConflictTick() && this.ensureToolHeld(this.bestToolSlot(state))) {
                                this.sendSwing();
                            }
                        } else {
                            double predictedMs = this.grimPredictedFromDelta(this.rebreakGrimDelta);
                            if (this.finishBudgetOk(predictedMs)) {
                                if (!this.actionConflictTick() && !this.digSentThisTick && this.digBudgetAvailable()) {
                                    if (this.rotateConfig.get() && this.canSpoofAim()) {
                                        this.assertMiningAim(this.rebreakPos);
                                        if (!this.sentAimHitsBlock(this.rebreakPos) && this.rebreakSeenTicks <= delay + 10) {
                                            return;
                                        }
                                    }

                                    if (this.holdOrPulseDigSlot(state)) {
                                        this.sendSwing();
                                        this.sendDig(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, this.rebreakPos, this.getInteractDirection(this.rebreakPos));
                                        this.noteFinishSent(predictedMs);
                                        this.destroyDelayTicks = this.rollBreakDelay();
                                        this.rebreakAirSeen = false;
                                        this.rebreakAirPending = false;
                                        this.rebreakReplaceSeen = false;
                                        this.rebreakReplacePending = false;
                                        this.rebreakStaleTicks = 0;
                                        this.rebreakSolidTicks = 0;
                                        this.rebreakSeenTicks = 0;
                                        this.rebreakAckSequence = this.lastDigSequence;
                                        this.rebreakTimeMark = this.timePacketCount;
                                        this.rebreakAimParkTicks = 0;
                                        this.rebreakBlock = state.getBlock();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void noteRebreakAir() {
        this.rebreakAirSeen = true;
        this.grimSampleIsAir = true;
        this.rebreakSolidTicks = 0;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        // Ветка ClientboundBlockChangedAckPacket удалена: в 1.21.11 этот пакет не существует,
        // lastAckedSequence не обновляется, и условие перекопа в tickRebreak вырождается во временное.
        if (event.packet instanceof WorldTimeUpdateS2CPacket) {
            this.timePacketCount++;
        } else {
            BlockPos pos = this.rebreakPos;
            if (pos != null && this.modeConfig.get() == SpeedmineMode.PACKET) {
                if (this.activeMine == null || !this.activeMine.pos.equals(pos)) {
                    if (event.packet instanceof BlockUpdateS2CPacket packet) {
                        if (packet.getPos().equals(pos)) {
                            this.noteRebreakUpdate(packet.getState());
                        }
                    } else if (event.packet instanceof ChunkDeltaUpdateS2CPacket packet) {
                        packet.visitUpdates((updatePos, state) -> {
                            if (updatePos.equals(pos)) {
                                this.noteRebreakUpdate(state);
                            }
                        });
                    }
                }
            }
        }
    }

    private void noteRebreakUpdate(BlockState state) {
        if (state.isAir()) {
            this.rebreakAirPending = true;
        } else if (this.rebreakAirPending || this.rebreakAirSeen) {
            this.rebreakReplacePending = true;
        }
    }

    private void clearRebreak() {
        this.rebreakPos = null;
        this.rebreakDir = null;
        this.rebreakBlock = null;
        this.rebreakSeenTicks = 0;
        this.rebreakAirSeen = false;
        this.rebreakAirPending = false;
        this.rebreakReplaceSeen = false;
        this.rebreakReplacePending = false;
        this.rebreakStaleTicks = 0;
        this.rebreakSolidTicks = 0;
        this.rebreakAimParkTicks = 0;
    }

    private void clearSecondary() {
        this.secondaryMine = null;
    }

    private void tickSecondary() {
        if (this.secondaryMine != null) {
            MiningData data = this.secondaryMine;
            BlockState state = this.mc.world.getBlockState(data.pos);
            if (!state.isAir() && (data.minedBlock == null || state.getBlock() == data.minedBlock)) {
                data.ticksMined++;
                float serverDelta = this.swapConfig.get() == Swap.OFF
                    ? this.serverHeldBreakingDelta(state, data.pos)
                    : this.calcBlockBreakingDelta(state, data.pos);
                float serverProgress = (data.ticksMined + 1) * serverDelta;
                data.lastDamage = data.damage;
                data.damage = Math.min(this.getEffectiveThreshold(), serverProgress * this.getEffectiveThreshold());
                if (!(serverProgress < 1.0F)) {
                    if (!this.actionConflictTick()) {
                        this.holdOrPulseDigSlot(state);
                    }

                    if (++data.graceTicks > 20) {
                        if (this.isValidTarget(data.pos)) {
                            this.pending.addFirst(new MiningData(data.pos, data.direction, data.autoTarget, false));
                        }

                        this.clearSecondary();
                    }
                } else {
                    int toolSlot = this.bestToolSlot(state);
                    if (this.swapConfig.get() != Swap.OFF && toolSlot != -1 && !this.actionConflictTick() && !this.toolStarved()) {
                        SwapManager swap = SwapManager.getInstance();
                        boolean primaryWantsOther = this.activeMine != null
                            && this.activeMine.started
                            && this.bestToolSlot(this.mc.world.getBlockState(this.activeMine.pos)) != toolSlot;
                        if (!primaryWantsOther) {
                            swap.hold(this, toolSlot, 10, this.swapConfig.get() == Swap.SILENT, 2);
                        }
                    }
                }
            } else {
                this.clearSecondary();
            }
        }
    }

    private void tickActiveMine() {
        this.tryPromote();
        if (this.activeMine == null && !this.pending.isEmpty()) {
            MiningData next;
            while ((next = this.pending.poll()) != null && !this.isValidTarget(next.pos)) {
            }

            this.activeMine = next;
        }

        if (this.activeMine != null) {
            MiningData data = this.activeMine;
            BlockState state = this.mc.world.getBlockState(data.pos);
            if (state.isAir()) {
                if (data.started) {
                    this.sendAbort(data.pos);
                }

                this.activeMine = null;
            } else if (this.inRange(data.pos) && state.getHardness(this.mc.world, data.pos) != -1.0F) {
                if (data.started && state.getBlock() != data.minedBlock) {
                    this.sendAbort(data.pos);
                    data.started = false;
                    data.damage = 0.0F;
                    data.lastDamage = 0.0F;
                    data.heldTicks = 0;
                    data.ticksMined = 0;
                    data.startMs = 0L;
                    data.grimMaxDelta = 0.0F;
                    data.swapPoint = this.rollSwapPoint();
                }

                boolean rotate = data.autoTarget ? this.autoRotate.get() : this.rotateConfig.get();
                if (rotate && this.canSpoofAim()) {
                    this.assertMiningAim(data.pos);
                }

                if (!data.started) {
                    this.tryStartActive(state, rotate);
                } else {
                    float delta = this.calcBlockBreakingDelta(state, data.pos);
                    data.lastDamage = data.damage;
                    data.damage += delta;
                    data.ticksMined++;
                    data.grimMaxDelta = Math.max(data.grimMaxDelta, this.grimSampledBreakingDelta(state, data.pos));
                    float threshold = this.getEffectiveThreshold();
                    int toolSlot = this.bestToolSlot(state);
                    boolean wantsTool = this.swapConfig.get() != Swap.OFF && toolSlot != -1;
                    if (wantsTool
                        && (data.damage >= data.swapPoint * threshold || this.promotionWaiting())
                        && !this.actionConflictTick()
                        && this.ensureToolHeld(toolSlot)) {
                        data.heldTicks++;
                    }

                    float finishAt = this.secondaryMine != null ? Math.max(threshold, 0.72F) : threshold;
                    double predictedMs = this.grimPredictedMs(data);
                    boolean needsBypass = this.shouldBypass(predictedMs);
                    if (this.grimBypassConfig.get() == GrimBypass.ALWAYS
                        && data.damage + delta < finishAt
                        && this.sinceFinish() >= 275L
                        && this.delayBalanceMirror > 400.0
                        && this.bypassBudgetAvailable()
                        && this.canSendDigNow()) {
                        this.sendBypassStart(data.pos);
                        this.sendSwing();
                    } else if (needsBypass && data.damage + delta >= finishAt && this.canSendDigNow()) {
                        this.sendBypassStart(data.pos);
                        this.sendSwing();
                    } else if (data.damage < finishAt) {
                        this.sendSwing();
                    } else if (wantsTool && !this.toolStarved() && data.heldTicks < this.swapDelayConfig.get()) {
                        this.sendSwing();
                    } else if (rotate && this.canSpoofAim() && !this.sentAimHitsBlock(data.pos) && data.alignTicks++ < 10) {
                        this.sendSwing();
                    } else if (!this.canSendDigNow()) {
                        this.sendSwing();
                    } else if (needsBypass) {
                        this.sendBypassStart(data.pos);
                        this.sendSwing();
                    } else if (!this.finishBudgetOk(predictedMs)) {
                        this.sendSwing();
                    } else if (!this.holdOrPulseDigSlot(state)) {
                        this.sendSwing();
                    } else {
                        this.sendSwing();
                        data.direction = this.getInteractDirection(data.pos);
                        this.sendDig(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, data.pos, data.direction);
                        this.noteFinishSent(predictedMs);
                        if (data.damage + delta < 0.7F && this.secondaryMine == null) {
                            data.graceTicks = 0;
                            this.secondaryMine = data;
                        }

                        this.destroyDelayTicks = this.rollBreakDelay();
                        this.activeMine = null;
                        if (this.instantConfig.get()) {
                            this.rebreakSeenTicks = 0;
                            this.rebreakAirSeen = false;
                            this.rebreakAirPending = false;
                            this.rebreakReplaceSeen = false;
                            this.rebreakReplacePending = false;
                            this.rebreakStaleTicks = 0;
                            this.rebreakSolidTicks = 0;
                            this.rebreakAimParkTicks = 0;
                            this.rebreakGrimDelta = data.grimMaxDelta;
                            this.rebreakBlock = data.minedBlock;
                            this.rebreakAckSequence = this.lastDigSequence;
                            this.rebreakTimeMark = this.timePacketCount;
                            this.rebreakDir = data.direction;
                            this.rebreakPos = data.pos;
                        }

                        SwapManager.getInstance().scheduleRelease(this, this.swapReleaseDelayConfig.get());
                    }
                }
            } else {
                if (data.started) {
                    this.sendAbort(data.pos);
                }

                this.activeMine = null;
            }
        }
    }

    private boolean promotionWaiting() {
        return this.doubleBreakConfig.get() && this.secondaryMine == null && !this.pending.isEmpty();
    }

    private void tryPromote() {
        if (this.doubleBreakConfig.get() && !this.pending.isEmpty()) {
            if (this.activeMine != null && this.activeMine.started) {
                if (this.secondaryMine == null) {
                    MiningData primary = this.activeMine;
                    BlockState state = this.mc.world.getBlockState(primary.pos);
                    if (!state.isAir()) {
                        float delta = this.calcBlockBreakingDelta(state, primary.pos);
                        if (!(delta <= 0.0F)) {
                            if (!(delta >= 1.0F) && !(primary.damage >= this.getEffectiveThreshold())) {
                                MiningData next;
                                while ((next = this.pending.peek()) != null && !this.isValidTarget(next.pos)) {
                                    this.pending.poll();
                                }

                                if (next != null) {
                                    if (this.canSendDigNow()) {
                                        int toolSlot = this.bestToolSlot(state);
                                        boolean wantsTool = this.swapConfig.get() != Swap.OFF && toolSlot != -1;
                                        if (!wantsTool
                                            || this.toolStarved()
                                            || primary.heldTicks >= this.swapDelayConfig.get() && this.ensureToolHeld(toolSlot)) {
                                            boolean rotate = primary.autoTarget ? this.autoRotate.get() : this.rotateConfig.get();
                                            if (!rotate || !this.canSpoofAim() || this.sentAimHitsBlock(primary.pos)) {
                                                double predictedMs = this.grimPredictedMs(primary);
                                                if (this.shouldBypass(predictedMs)) {
                                                    this.sendBypassStart(primary.pos);
                                                    this.sendSwing();
                                                } else if (this.finishBudgetOk(predictedMs)) {
                                                    this.sendSwing();
                                                    this.sendDig(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, primary.pos, this.getInteractDirection(primary.pos));
                                                    this.noteFinishSent(predictedMs);
                                                    this.destroyDelayTicks = this.rollBreakDelay();
                                                    int holdTicks = Math.min(
                                                        100, (int) Math.ceil((1.0F - Math.min(1.0F, primary.damage)) / Math.min(1.0F, delta)) + 2
                                                    );
                                                    SwapManager.getInstance().scheduleRelease(this, holdTicks);
                                                    primary.graceTicks = 0;
                                                    this.secondaryMine = primary;
                                                    this.pending.poll();
                                                    this.activeMine = next;
                                                    this.activeMine.fastStart = true;
                                                    boolean rotateNext = next.autoTarget ? this.autoRotate.get() : this.rotateConfig.get();
                                                    if (rotateNext && this.canSpoofAim()) {
                                                        this.assertMiningAim(next.pos);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private long sinceFinish() {
        return this.lastFinishMs == 0L ? Long.MAX_VALUE : System.currentTimeMillis() - this.lastFinishMs;
    }

    private void noteStartSent(boolean airSample) {
        long since = this.sinceFinish();
        if (since >= 275L) {
            this.delayBalanceMirror *= 0.9;
        } else {
            this.delayBalanceMirror = Math.min(1000.0, this.delayBalanceMirror + (300L - since));
        }

        this.grimStartMs = System.currentTimeMillis();
        this.grimSampleIsAir = airSample;
    }

    private void noteFinishSent(double predictedMs) {
        long now = System.currentTimeMillis();
        double predicted = this.grimSampleIsAir ? 0.0 : predictedMs;
        double real = this.grimStartMs == 0L ? 0.0 : now - this.grimStartMs;
        this.breakBalanceMirror = MathHelper.clamp(this.breakBalanceMirror, -1000.0, 1000.0);
        double diff = predicted - real;
        if (diff < 25.0) {
            this.breakBalanceMirror *= 0.9;
        } else {
            this.breakBalanceMirror += diff;
        }

        this.lastFinishMs = now;
        this.grimStartMs = now;
    }

    private boolean startBudgetOk() {
        long since = this.sinceFinish();
        return since >= 275L ? true : this.delayBalanceMirror + (300L - since) <= 700.0;
    }

    private boolean finishBudgetOk(double predictedMs) {
        if (this.grimSampleIsAir) {
            return true;
        }

        double diff = predictedMs - (this.grimStartMs == 0L ? 0L : System.currentTimeMillis() - this.grimStartMs);
        return diff < 25.0 ? true : this.breakBalanceMirror + diff <= 800.0;
    }

    private double grimPredictedMs(MiningData data) {
        return this.grimPredictedFromDelta(data.grimMaxDelta);
    }

    private double grimPredictedFromDelta(float delta) {
        return delta <= 0.0F ? 1000.0 : Math.ceil(1.0 / Math.min(1.0F, delta)) * 50.0;
    }

    private void sendBypassStart(BlockPos pos) {
        int y = Math.min(2000, pos.getY() + 955);
        this.bypassPacketTimes.addLast(System.currentTimeMillis());
        this.sendDig(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, new BlockPos(pos.getX(), y, pos.getZ()), Direction.DOWN);
        this.noteStartSent(true);
    }

    private boolean bypassBudgetAvailable() {
        long now = System.currentTimeMillis();

        while (!this.bypassPacketTimes.isEmpty() && now - this.bypassPacketTimes.peekFirst() > 1000L) {
            this.bypassPacketTimes.pollFirst();
        }

        return this.bypassPacketTimes.size() < 40;
    }

    private boolean shouldBypass(double predictedMs) {
        if (this.grimBypassConfig.get() != GrimBypass.OFF && !this.grimSampleIsAir) {
            if (this.sinceFinish() < 275L) {
                return false;
            } else {
                return !this.bypassBudgetAvailable() ? false : this.grimBypassConfig.get() == GrimBypass.ALWAYS || !this.finishBudgetOk(predictedMs);
            }
        } else {
            return false;
        }
    }

    private boolean canSendDigNow() {
        return !this.actionConflictTick() && !this.digSentThisTick && this.digBudgetAvailable();
    }

    private void tryStartActive(BlockState state, boolean rotate) {
        MiningData data = this.activeMine;
        if (this.canSendDigNow()) {
            float delta = this.calcBlockBreakingDelta(state, data.pos);
            boolean insta = delta >= 1.0F;
            if (this.startBudgetOk()) {
                if (this.destroyDelayTicks <= 0 || insta || data.fastStart) {
                    if (!rotate || !this.canSpoofAim() || this.sentAimHitsBlock(data.pos) || data.alignTicks++ >= 10) {
                        if (!data.manualFace) {
                            data.direction = this.getInteractDirection(data.pos);
                        }

                        if (!(delta >= 1.0F) || this.swapConfig.get() == Swap.OFF || this.holdOrPulseDigSlot(state)) {
                            data.minedBlock = state.getBlock();
                            data.started = true;
                            data.damage = 0.0F;
                            data.lastDamage = 0.0F;
                            data.ticksMined = 0;
                            data.startMs = System.currentTimeMillis();
                            data.grimMaxDelta = this.grimSampledBreakingDelta(state, data.pos);
                            data.alignTicks = 0;
                            this.noteStart(data.pos);
                            if (delta >= 1.0F) {
                                this.sendInstaBreak(data.pos, data.direction);
                            } else {
                                this.sendDig(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, data.pos, data.direction);
                            }

                            this.sendSwing();
                            if (delta >= 1.0F) {
                                this.instaGracePos = data.pos;
                                this.instaGraceTicks = 8;
                                this.activeMine = null;
                                if (rotate && this.canSpoofAim()) {
                                    for (MiningData next : this.pending) {
                                        if (this.isValidTarget(next.pos)) {
                                            this.assertMiningAim(next.pos);
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onAttackBlock(StartBreakingBlockEvent event) {
        if (this.mc.player != null && this.mc.world != null) {
            if (!this.mc.player.isCreative() && !this.mc.player.isSpectator() && this.modeConfig.get() == SpeedmineMode.PACKET) {
                event.cancel();
                BlockPos pos = event.blockPos;
                if (!this.isMiningBlock(pos)) {
                    BlockState blockState = this.mc.world.getBlockState(pos);
                    if (blockState.getHardness(this.mc.world, pos) != -1.0F && !blockState.isAir() && this.inRange(pos)) {
                        if (this.activeMine != null) {
                            if (this.clickQueueConfig.get() || this.doubleBreakConfig.get()) {
                                int limit = this.clickQueueConfig.get() ? this.queueLimitConfig.get() : 1;
                                if (this.pending.size() < limit) {
                                    this.pending.add(new MiningData(pos.toImmutable(), event.direction, false, true));
                                    if (this.doubleBreakConfig.get()) {
                                        this.tryPromote();
                                    }
                                }

                                return;
                            }

                            if (this.activeMine.started) {
                                this.sendAbort(this.activeMine.pos);
                            }
                        }

                        this.activeMine = new MiningData(pos, event.direction, false, true);
                        if (this.rotateConfig.get() && this.canSpoofAim()) {
                            this.assertMiningAim(pos);
                        }

                        this.tryStartActive(blockState, this.rotateConfig.get());
                    }
                }
            }
        }
    }

    @EventHandler
    public void onRenderWorld(Render3DEvent event) {
        if (this.mc.player != null && this.mc.world != null) {
            if (!this.mc.player.isCreative() && this.modeConfig.get() == SpeedmineMode.PACKET && this.render.get()) {
                this.trackFade(this.activeMine);
                this.trackFade(this.secondaryMine);

                for (MiningData data : this.pending) {
                    this.trackFade(data);
                }

                float total = this.getEffectiveThreshold();
                double fadeSeconds = this.fadeTimeConfig.get().intValue() / 1000.0;
                SettingColor mineColor = this.colorConfig.get();
                SettingColor doneColor = this.colorDoneConfig.get();
                Iterator<FadeEntry> it = this.fadeList.values().iterator();

                while (it.hasNext()) {
                    FadeEntry entry = it.next();
                    MiningData data = entry.data;
                    BlockState state = data.getState();
                    boolean air = state.isAir();
                    boolean primary = data == this.activeMine || data == this.secondaryMine;
                    boolean queued = !primary && this.pending.contains(data);
                    boolean visible = (primary || queued) && !air;
                    entry.fade.update(visible, fadeSeconds, true);
                    if (!visible && !entry.fade.rendering()) {
                        it.remove();
                    } else {
                        float factor = entry.fade.alpha();
                        int boxAlpha = (int) ((queued ? 20 : 40) * factor);
                        int lineAlpha = (int) ((queued ? 60 : 100) * factor);
                        boolean done = !queued && (data.damage >= total || air);
                        SettingColor base = done ? doneColor : mineColor;
                        this.renderBoxColor.set(base.r, base.g, base.b, boxAlpha);
                        this.renderLineColor.set(base.r, base.g, base.b, lineAlpha);
                        BlockPos mining = data.pos;
                        VoxelShape outlineShape = state.getOutlineShape(this.mc.world, mining);
                        Box bounds = (outlineShape.isEmpty() ? VoxelShapes.fullCube() : outlineShape).getBoundingBox();
                        float scale = !queued && !air
                            ? MathHelper.clamp((data.damage + (data.damage - data.lastDamage) * event.tickDelta) / total, 0.0F, 1.0F)
                            : 1.0F;
                        double cx = mining.getX() + (bounds.minX + bounds.maxX) / 2.0;
                        double cy = mining.getY() + (bounds.minY + bounds.maxY) / 2.0;
                        double cz = mining.getZ() + (bounds.minZ + bounds.maxZ) / 2.0;
                        double hx = (bounds.maxX - bounds.minX) / 2.0 * scale;
                        double hy = (bounds.maxY - bounds.minY) / 2.0 * scale;
                        double hz = (bounds.maxZ - bounds.minZ) / 2.0 * scale;
                        event.renderer.box(cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz, this.renderBoxColor, this.renderLineColor, this.shapeMode.get(), 0);
                    }
                }
            }
        }
    }

    private void trackFade(MiningData data) {
        if (data != null && !data.getState().isAir()) {
            FadeEntry existing = this.fadeList.get(data.pos);
            if (existing == null) {
                this.fadeList.put(data.pos, new FadeEntry(data));
            } else {
                existing.data = data;
            }
        }
    }

    public boolean canDelegateMining() {
        return this.isActive() && this.modeConfig.get() == SpeedmineMode.PACKET;
    }

    public void mineBlock(BlockPos pos, Direction direction) {
        if (this.canDelegateMining() && this.mc.player != null && this.mc.world != null) {
            if (!this.isMiningBlock(pos)) {
                if (this.isValidTarget(pos)) {
                    if (this.pending.size() < this.queueLimitConfig.get()) {
                        this.pending.add(new MiningData(pos.toImmutable(), direction, false, false));
                    }
                }
            }
        }
    }

    public boolean isMiningBlock(BlockPos pos) {
        if (this.activeMine != null && this.activeMine.pos.equals(pos)) {
            return true;
        }

        if (this.secondaryMine != null && this.secondaryMine.pos.equals(pos)) {
            return true;
        }

        if (this.rebreakPos != null && this.rebreakPos.equals(pos)) {
            return true;
        }

        if (this.instaGraceTicks > 0 && pos.equals(this.instaGracePos)) {
            return true;
        }

        for (MiningData data : this.pending) {
            if (data.pos.equals(pos)) {
                return true;
            }
        }

        return false;
    }

    private boolean isValidTarget(BlockPos pos) {
        BlockState state = this.mc.world.getBlockState(pos);
        return !state.isAir() && state.getHardness(this.mc.world, pos) != -1.0F && this.inRange(pos);
    }

    private boolean inRange(BlockPos pos) {
        double r = this.rangeConfig.get();
        return GrimUtils.closestEyeDistanceSqTo(this.mc.player, new Box(pos)) <= r * r;
    }

    private void noteStart(BlockPos pos) {
        this.noteStartSent(false);
        this.lastStartPos = pos;
        if (this.rebreakPos != null && !this.rebreakPos.equals(pos)) {
            this.clearRebreak();
        }
    }

    private void sendAbort(BlockPos pos) {
        if (this.rebreakPos != null && !this.rebreakPos.equals(pos)) {
            this.clearRebreak();
        }

        this.sendDig(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, pos, Direction.DOWN);
    }

    public boolean needsMiningTickPacket() {
        if (!this.isActive() || this.modeConfig.get() != SpeedmineMode.PACKET) {
            return false;
        } else if (this.instantConfig.get() && this.rebreakPos != null) {
            return true;
        } else {
            return this.swingConfig.get() != SwingMode.NONE ? false : this.activeMine != null && this.activeMine.started;
        }
    }

    private void sendSwing() {
        switch (this.swingConfig.get()) {
            case FULL:
                this.mc.player.swingHand(Hand.MAIN_HAND);
                break;
            case PACKET:
                this.mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            case NONE:
        }
    }

    private void sendDig(PlayerActionC2SPacket.Action action, BlockPos pos, Direction direction) {
        this.digPacketTimes.addLast(System.currentTimeMillis());
        if (action == PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK) {
            this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(action, pos, direction));
        } else {
            this.digSentThisTick = true;
            this.lastDigSequence = GrimUtils.nextSequence();
            this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(action, pos, direction, this.lastDigSequence));
        }
    }

    private void sendInstaBreak(BlockPos pos, Direction direction) {
        this.digPacketTimes.addLast(System.currentTimeMillis());
        this.digSentThisTick = true;
        // В 1.21.11 BlockStatePredictionHandler удалён: используем собственный счётчик
        // последовательности GrimUtils и напрямую ломаем блок через interactionManager.
        int seq = GrimUtils.nextSequence();
        this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, direction, seq));
        this.mc.interactionManager.breakBlock(pos);
    }

    private boolean digBudgetAvailable() {
        long now = System.currentTimeMillis();

        while (!this.digPacketTimes.isEmpty() && now - this.digPacketTimes.peekFirst() > 1000L) {
            this.digPacketTimes.pollFirst();
        }

        return this.digPacketTimes.size() < 300;
    }

    private boolean ensureToolHeld(int slot) {
        return this.swapConfig.get() != Swap.OFF && slot != -1
            ? SwapManager.getInstance().hold(this, slot, 10, this.swapConfig.get() == Swap.SILENT, this.swapReleaseDelayConfig.get())
            : true;
    }

    private boolean toolStarved() {
        return SwapManager.getInstance().isForeignSession(this, 10);
    }

    private boolean holdOrPulseDigSlot(BlockState state) {
        if (this.swapConfig.get() == Swap.OFF) {
            return true;
        }

        int slot = this.bestToolSlot(state);
        SwapManager swap = SwapManager.getInstance();
        if (this.toolStarved()) {
            int target = slot != -1 ? slot : swap.referenceSlot();
            return swap.pulse(this, target, 10);
        }

        if (slot == -1) {
            slot = swap.referenceSlot();
        }

        return swap.hold(this, slot, 10, this.swapConfig.get() == Swap.SILENT, this.swapReleaseDelayConfig.get());
    }

    public float calcBlockBreakingDelta(BlockState state, BlockPos pos) {
        if (state.isAir()) {
            return 0.0F;
        }

        if (this.swapConfig.get() == Swap.OFF) {
            return state.calcBlockBreakingDelta(this.mc.player, this.mc.world, pos);
        }

        float f = state.getHardness(this.mc.world, pos);
        if (f == -1.0F) {
            return 0.0F;
        }

        int i = this.canHarvest(state) ? 30 : 100;
        return this.getBlockBreakingSpeed(state) / f / i;
    }

    private float serverHeldBreakingDelta(BlockState state, BlockPos pos) {
        float hardness = state.getHardness(this.mc.world, pos);
        if (hardness == -1.0F) {
            return 0.0F;
        }

        ItemStack stack = this.mc.player.getInventory().getStack(SwapManager.getInstance().getServerSlot());
        int i = this.canStackHarvest(stack, state) ? 30 : 100;
        return this.stackBreakingSpeed(stack, state, this.effLevel(stack)) / hardness / i;
    }

    private float grimSampledBreakingDelta(BlockState state, BlockPos pos) {
        float hardness = state.getHardness(this.mc.world, pos);
        if (hardness == -1.0F) {
            return 0.0F;
        }

        ItemStack stack = this.mc.player.getInventory().getStack(SwapManager.getInstance().getServerSlot());
        int i = this.canStackHarvest(stack, state) ? 30 : 100;
        return this.stackBreakingSpeed(stack, state, this.grimAttrEffLevel) / hardness / i;
    }

    private void updateGrimEffMirror() {
        int level = this.effLevel(this.mc.player.getInventory().getStack(SwapManager.getInstance().getServerSlot()));
        long now = System.currentTimeMillis();
        if (!this.effMirrorInit) {
            this.effMirrorInit = true;
            this.grimAttrEffLevel = level;
            this.pendingEffLevel = -1;
        } else {
            if (this.pendingEffLevel == -1 ? level != this.grimAttrEffLevel : level != this.pendingEffLevel) {
                this.pendingEffLevel = level;
                this.effChangeMs = now;
            }

            if (this.pendingEffLevel != -1 && now - this.effChangeMs >= this.attrSyncMs()) {
                this.grimAttrEffLevel = this.pendingEffLevel;
                this.pendingEffLevel = -1;
            }
        }
    }

    private long attrSyncMs() {
        int latency = 200;
        if (this.mc.getNetworkHandler() != null) {
            PlayerListEntry info = this.mc.getNetworkHandler().getPlayerListEntry(this.mc.player.getUuid());
            if (info != null && info.getLatency() > 0) {
                latency = info.getLatency();
            }
        }

        return latency + 150L;
    }

    private int effLevel(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }

        ItemEnchantmentsComponent enchants = stack.getEnchantments();
        for (RegistryEntry<Enchantment> entry : enchants.getEnchantments()) {
            if (entry.matchesKey(Enchantments.EFFICIENCY)) {
                return enchants.getLevel(entry);
            }
        }

        return 0;
    }

    private float getBlockBreakingSpeed(BlockState block) {
        int tool = this.getBestTool(block);
        return this.stackBreakingSpeed(this.mc.player.getInventory().getStack(tool), block);
    }

    private float stackBreakingSpeed(ItemStack stack, BlockState block) {
        return this.stackBreakingSpeed(stack, block, this.effLevel(stack));
    }

    private float stackBreakingSpeed(ItemStack stack, BlockState block, int efficiency) {
        float f = stack.getMiningSpeedMultiplier(block);
        if (f > 1.0F && efficiency > 0 && !stack.isEmpty()) {
            f += efficiency * efficiency + 1;
        }

        if (StatusEffectUtil.hasHaste(this.mc.player)) {
            f *= 1.0F + (StatusEffectUtil.getHasteAmplifier(this.mc.player) + 1) * 0.2F;
        }

        if (this.mc.player.hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
            float g = switch (this.mc.player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier()) {
                case 0 -> 0.3F;
                case 1 -> 0.09F;
                case 2 -> 0.0027F;
                default -> 8.1E-4F;
            };
            f *= g;
        }

        f *= (float) this.mc.player.getAttributeValue(EntityAttributes.BLOCK_BREAK_SPEED);
        if (this.mc.player.isSubmergedIn(FluidTags.WATER)) {
            f *= (float) this.mc.player.getAttributeValue(EntityAttributes.SUBMERGED_MINING_SPEED);
        }

        if (!this.mc.player.isOnGround()) {
            f /= 5.0F;
        }

        return f;
    }

    private boolean canHarvest(BlockState state) {
        if (state.isToolRequired()) {
            int tool = this.getBestTool(state);
            return this.mc.player.getInventory().getStack(tool).isSuitableFor(state);
        } else {
            return true;
        }
    }

    private int referenceSlot() {
        return SwapManager.getInstance().referenceSlot();
    }

    private int getBestTool(BlockState state) {
        int bestSlot = this.bestToolSlot(state);
        return bestSlot == -1 ? this.referenceSlot() : bestSlot;
    }

    private int bestToolSlot(BlockState state) {
        ItemStack held = this.mc.player.getInventory().getStack(this.referenceSlot());
        float bestSpeed = held.getMiningSpeedMultiplier(state);
        boolean bestHarvest = this.canStackHarvest(held, state);
        int bestSlot = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = this.mc.player.getInventory().getStack(i);
            float speed = stack.getMiningSpeedMultiplier(state);
            boolean harvest = this.canStackHarvest(stack, state);
            if (harvest && !bestHarvest || harvest == bestHarvest && speed > bestSpeed) {
                bestHarvest = harvest;
                bestSpeed = speed;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    private boolean canStackHarvest(ItemStack stack, BlockState state) {
        return !state.isToolRequired() || stack.isSuitableFor(state);
    }

    private PlayerEntity getClosestEnemy() {
        if (this.mc.world != null && this.mc.player != null) {
            PlayerEntity closest = null;
            double closestDist = this.enemyRange.get() * this.enemyRange.get();

            for (PlayerEntity player : this.mc.world.getPlayers()) {
                if (player != this.mc.player && !player.isSpectator() && !player.isDead() && !FriendManager.getInstance().isFriend(player)) {
                    double dist = this.mc.player.squaredDistanceTo(player);
                    if (dist < closestDist) {
                        closestDist = dist;
                        closest = player;
                    }
                }
            }

            return closest;
        } else {
            return null;
        }
    }

    private BlockPos findBestEnemyBlock(PlayerEntity enemy) {
        if (enemy == null) {
            return null;
        }

        BlockPos enemyPos = enemy.getBlockPos();
        BlockState feetState = this.mc.world.getBlockState(enemyPos);
        if (!feetState.isAir()
            && feetState.getHardness(this.mc.world, enemyPos) != -1.0F
            && this.inRange(enemyPos)
            && !this.isMiningBlock(enemyPos)
            && this.isResistantBlock(feetState)
            && !this.isOwnSurroundBlock(enemyPos)) {
            return enemyPos;
        }

        List<BlockPos> surroundBlocks = new ArrayList<>();
        surroundBlocks.add(enemyPos.north());
        surroundBlocks.add(enemyPos.south());
        surroundBlocks.add(enemyPos.east());
        surroundBlocks.add(enemyPos.west());
        BlockPos bestSurround = this.findBestBlock(surroundBlocks);
        if (bestSurround != null) {
            return bestSurround;
        }

        if (this.targetHead.get()) {
            BlockPos aboveHead = enemyPos.up(2);
            BlockState aboveState = this.mc.world.getBlockState(aboveHead);
            if (!aboveState.isAir()
                && aboveState.getHardness(this.mc.world, aboveHead) != -1.0F
                && this.inRange(aboveHead)
                && !this.isMiningBlock(aboveHead)
                && this.isResistantBlock(aboveState)
                && !this.isOwnSurroundBlock(aboveHead)) {
                return aboveHead;
            }
        }

        return null;
    }

    private BlockPos findBestBlock(List<BlockPos> positions) {
        BlockPos bestBlock = null;
        double bestDist = Double.MAX_VALUE;

        for (BlockPos pos : positions) {
            if (this.inRange(pos)) {
                BlockState state = this.mc.world.getBlockState(pos);
                if (!state.isAir()
                    && state.getHardness(this.mc.world, pos) != -1.0F
                    && !this.isMiningBlock(pos)
                    && !this.isOwnSurroundBlock(pos)
                    && this.isResistantBlock(state)) {
                    double dist = GrimUtils.closestEyeDistanceSqTo(this.mc.player, new Box(pos));
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestBlock = pos;
                    }
                }
            }
        }

        return bestBlock;
    }

    private BlockPos getAntiCrawlBlock() {
        if (this.mc.player != null && this.mc.world != null) {
            BlockPos playerPos = this.mc.player.getBlockPos();
            BlockPos blockAbove = playerPos.up();
            BlockState state = this.mc.world.getBlockState(blockAbove);
            return !state.isAir()
                    && state.getHardness(this.mc.world, blockAbove) != -1.0F
                    && !state.isOf(Blocks.BEDROCK)
                    && !state.isOf(Blocks.REINFORCED_DEEPSLATE)
                    && !state.isOf(Blocks.BARRIER)
                    && this.inRange(blockAbove)
                ? blockAbove
                : null;
        } else {
            return null;
        }
    }

    private boolean isOwnSurroundBlock(BlockPos pos) {
        BlockPos playerPos = this.mc.player.getBlockPos();
        return !pos.equals(playerPos.north())
                && !pos.equals(playerPos.south())
                && !pos.equals(playerPos.east())
                && !pos.equals(playerPos.west())
            ? pos.equals(playerPos.up()) || pos.equals(playerPos.up(2))
            : true;
    }

    private boolean isResistantBlock(BlockState state) {
        return !state.isOf(Blocks.BEDROCK)
                && !state.isOf(Blocks.REINFORCED_DEEPSLATE)
                && !state.isOf(Blocks.BARRIER)
                && !state.isOf(Blocks.COMMAND_BLOCK)
                && !state.isOf(Blocks.STRUCTURE_BLOCK)
            ? state.isOf(Blocks.OBSIDIAN)
                || state.isOf(Blocks.CRYING_OBSIDIAN)
                || state.isOf(Blocks.ENDER_CHEST)
                || state.isOf(Blocks.ANCIENT_DEBRIS)
                || state.isOf(Blocks.RESPAWN_ANCHOR)
            : false;
    }

    private boolean sentAimHitsBlock(BlockPos pos) {
        RotationUtils rot = RotationUtils.getInstance();
        Vec3d dir = RotationUtils.getRotationVector(rot.getServerPitch(), rot.getServerYaw());
        Box box = new Box(pos);
        double reach = this.mc.player.getBlockInteractionRange();
        Vec3d base = new Vec3d(this.mc.player.getX(), this.mc.player.getY(), this.mc.player.getZ());

        for (double h : GrimUtils.getPossibleEyeHeights(this.mc.player)) {
            Vec3d eye = base.add(0.0, h, 0.0);
            if (box.contains(eye) || box.raycast(eye, eye.add(dir.multiply(reach))).isPresent()) {
                return true;
            }
        }

        return false;
    }

    private Vec3d aimPointIn(BlockPos pos) {
        Vec3d eye = this.mc.player.getEyePos();
        Vec3d camDir = RotationUtils.getRotationVector(this.mc.player.getPitch(), this.mc.player.getYaw());
        Box box = new Box(pos).contract(0.15);
        double t = MathHelper.clamp(box.getCenter().subtract(eye).dotProduct(camDir), 0.0, this.mc.player.getBlockInteractionRange());
        Vec3d p = eye.add(camDir.multiply(t));
        return new Vec3d(
            MathHelper.clamp(p.x, box.minX, box.maxX),
            MathHelper.clamp(p.y, box.minY, box.maxY),
            MathHelper.clamp(p.z, box.minZ, box.maxZ)
        );
    }

    private void assertMiningAim(BlockPos pos) {
        float[] rotations = RotationUtils.getRotationsTo(this.mc.player.getEyePos(), this.aimPointIn(pos));
        RotationUtils.getInstance().setRotationSilent(this, 10, rotations[0], rotations[1]);
    }

    private boolean canSpoofAim() {
        return !this.mc.player.isGliding() && !this.mc.player.isSwimming() && !this.mc.player.hasVehicle();
    }

    private Direction getInteractDirection(BlockPos pos) {
        Vec3d eyePos = this.mc.player.getEyePos();
        Vec3d posVec = Vec3d.ofCenter(pos);
        Direction bestDir = null;
        double bestDot = -1.0;

        for (Direction dir : Direction.values()) {
            Vec3d dirVec = Vec3d.of(dir.getVector());
            double dot = eyePos.subtract(posVec).normalize().dotProduct(dirVec);
            if (dot > bestDot) {
                bestDot = dot;
                bestDir = dir;
            }
        }

        return bestDir;
    }

    private static final class FadeEntry {
        private MiningData data;
        private final FadeAnimator fade = new FadeAnimator();

        private FadeEntry(MiningData data) {
            this.data = data;
        }
    }

    public enum GrimBypass {
        AUTO,
        ALWAYS,
        OFF;
    }

    public class MiningData {
        private final BlockPos pos;
        private Direction direction;
        private final boolean autoTarget;
        private final boolean manualFace;
        private Block minedBlock;
        private float lastDamage;
        private float damage;
        private boolean started;
        private int alignTicks;
        private int heldTicks;
        private int ticksMined;
        private long startMs;
        private float grimMaxDelta;
        private boolean fastStart;
        private int graceTicks;
        private float swapPoint;

        public MiningData(BlockPos pos, Direction direction, boolean autoTarget, boolean manualFace) {
            this.pos = pos;
            this.direction = direction;
            this.autoTarget = autoTarget;
            this.manualFace = manualFace;
            this.swapPoint = SpeedMine.this.rollSwapPoint();
        }

        public BlockPos getPos() {
            return this.pos;
        }

        public Direction getDirection() {
            return this.direction;
        }

        public BlockState getState() {
            return SpeedMine.this.mc.world.getBlockState(this.pos);
        }

        public float getBlockDamage() {
            return this.damage;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            } else if (o != null && this.getClass() == o.getClass()) {
                MiningData that = (MiningData) o;
                return this.pos.equals(that.pos);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return this.pos.hashCode();
        }
    }

    public enum SpeedmineMode {
        PACKET,
        DAMAGE;
    }

    public enum Swap {
        NORMAL,
        SILENT,
        OFF;
    }

    public enum SwingMode {
        FULL,
        PACKET,
        NONE;
    }
}

package me.collective.module.player;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.Render3DEvent;
import me.collective.event.events.StartBreakingBlockEvent;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.module.ModuleManager;
import me.collective.render.ShapeMode;
import me.collective.setting.*;
import me.collective.util.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.core.Holder;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

/**
 * EchestRebreak — автоматическая установка и моментальное разрушение эндер-сундуков
 * с использованием эксплойта rebreak (из логики SpeedMine).
 *
 * <p>Предназначен для быстрого и безопасного фарма обсидиана на серверах с GrimAC:
 * <ul>
 *   <li>Интеграция с движком {@link SpeedMine} (FastMine) для идеального обхода баланса Grim FastBreak;</li>
 *   <li>Установка строго на опорную грань блока с подтверждением рейкаста (обход AirLiquidPlace и RotationPlace);</li>
 *   <li>Фазовое разделение тиков (Pre/Post) и сброс спринта перед установкой;</li>
 *   <li>Автоматический добор эндер-сундуков из инвентаря в хотбар;</li>
 *   <li>Приоритет кирки без Шёлкового касания (Silk Touch) для выпадения 8 единиц обсидиана.</li>
 * </ul>
 */
public class EchestRebreak extends Module {
    private static final int ROTATION_PRIORITY = 38;
    private static final double REACH = 4.2;
    private static final double REACH_SQ = REACH * REACH;
    private static final double ALIGN_EPSILON = 2.5;
    private static final int MAX_ALIGN_TICKS = 4;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDelays = settings.createGroup("Задержки");
    private final SettingGroup sgAutomation = settings.createGroup("Автоматизация");
    private final SettingGroup sgRender = settings.createGroup("Рендер");

    // General
    private final Setting<Boolean> useSpeedMine = sgGeneral.add(new BoolSetting.Builder()
        .name("использовать-fastmine")
        .description("Делегировать майнинг модулю SpeedMine для обхода предсказания баланса Grim.")
        .defaultValue(true)
        .build());

    private final Setting<PosMode> posMode = sgGeneral.add(new EnumSetting.Builder<PosMode>()
        .name("режим-позиции")
        .description("Автоматически выбрать блок перед собой или вручную кликнуть по блоку.")
        .defaultValue(PosMode.Auto)
        .build());

    private final Setting<Boolean> preferNoSilk = sgGeneral.add(new BoolSetting.Builder()
        .name("предпочитать-удачу")
        .description("Использовать кирку без Шёлкового касания, чтобы дропался обсидиан.")
        .defaultValue(true)
        .build());

    private final Setting<Double> turnSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("скорость-поворота")
        .description("Скорость тихого наведения взгляда.")
        .defaultValue(180.0)
        .min(30.0)
        .max(180.0)
        .sliderMax(180.0)
        .build());

    // Delays
    private final Setting<Integer> placeDelay = sgDelays.add(new IntSetting.Builder()
        .name("задержка-установки")
        .description("Задержка в тиках после установки эндер-сундука.")
        .defaultValue(1)
        .min(0)
        .sliderMax(5)
        .build());

    private final Setting<Integer> breakDelay = sgDelays.add(new IntSetting.Builder()
        .name("задержка-ломания")
        .description("Задержка в тиках перед отправкой пакета rebreak в автономном режиме.")
        .defaultValue(1)
        .min(0)
        .sliderMax(5)
        .visible(() -> !this.useSpeedMine.get())
        .build());

    // Automation
    private final Setting<Boolean> replenish = sgAutomation.add(new BoolSetting.Builder()
        .name("пополнять-хотбар")
        .description("Автоматически перемещать эндер-сундуки из инвентаря в хотбар при окончании.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> obsidianLimit = sgAutomation.add(new IntSetting.Builder()
        .name("лимит-стаков")
        .description("Автоматически отключить модуль после сбора стольки стаков обсидиана (0 — без лимита).")
        .defaultValue(0)
        .min(0)
        .sliderMax(36)
        .build());

    // Render
    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("отображение")
        .description("Подсвечивать позицию фарма сундука.")
        .defaultValue(true)
        .build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("режим-отображения")
        .description("Режим отрисовки рамки.")
        .defaultValue(ShapeMode.Both)
        .visible(this.render::get)
        .build());

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("цвет-граней")
        .description("Цвет заливки граней.")
        .defaultValue(new SettingColor(128, 0, 255, 40))
        .visible(this.render::get)
        .build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("цвет-линий")
        .description("Цвет контура блока.")
        .defaultValue(new SettingColor(180, 50, 255, 255))
        .visible(this.render::get)
        .build());

    public enum PosMode {
        Auto,
        Click
    }

    private BlockPos targetPos;
    private int actionTimer;
    private int placedWaitTimer;

    // Phased placement state
    private BlockHitResult pendingPlaceHit;
    private int alignTicks;

    // Standalone fallback state
    private boolean rebreakPrimed;
    private float breakingProgress;
    private boolean initialMiningStarted;
    private int staleTicks;

    public EchestRebreak() {
        super("EchestRebreak", "Автоматически ставит эндер-сундуки и ломает их через эксплойт rebreak для фарма обсидиана.", Category.PLAYER);
    }

    @Override
    public void onActivate() {
        this.actionTimer = 0;
        this.placedWaitTimer = 0;
        this.pendingPlaceHit = null;
        this.alignTicks = 0;
        this.rebreakPrimed = false;
        this.breakingProgress = 0.0f;
        this.initialMiningStarted = false;
        this.staleTicks = 0;

        if (this.posMode.get() == PosMode.Auto && this.mc.player != null) {
            this.targetPos = determineAutoPosition();
        } else {
            this.targetPos = null;
            info("Ударь по блоку, где нужно фармить эндер-сундуки.");
        }
    }

    @Override
    public void onDeactivate() {
        RotationUtils.getInstance().clearRotations(this);
        this.targetPos = null;
        this.placedWaitTimer = 0;
        this.pendingPlaceHit = null;
        this.alignTicks = 0;
        this.rebreakPrimed = false;
        this.initialMiningStarted = false;
    }

    @Override
    public void onWorldChange() {
        // Drop the old coordinates/priming without sending digs to a new world.
        this.onDeactivate();
        this.actionTimer = 0;
        this.breakingProgress = 0.0f;
        this.staleTicks = 0;
    }

    @EventHandler
    private void onStartBreakingBlock(StartBreakingBlockEvent event) {
        if (this.posMode.get() != PosMode.Click) return;

        this.targetPos = event.blockPos;
        this.rebreakPrimed = false;
        this.initialMiningStarted = false;
        this.breakingProgress = 0.0f;
        this.staleTicks = 0;
        this.placedWaitTimer = 0;
        this.pendingPlaceHit = null;
        this.alignTicks = 0;
        event.cancel();
        info("Позиция для фарма выбрана: %s", this.targetPos.toShortString());
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (this.mc.player == null || this.mc.level == null || this.mc.getConnection() == null) return;
        // Player-inventory indices are only valid in the player's own handler.
        if (this.mc.player.containerMenu != this.mc.player.inventoryMenu
            || !this.mc.player.containerMenu.getCarried().isEmpty()) {
            this.pendingPlaceHit = null;
            RotationUtils.getInstance().clearRotations(this);
            return;
        }

        if (this.placedWaitTimer > 0) {
            this.placedWaitTimer--;
        }

        // Проверка лимита обсидиана
        if (this.obsidianLimit.get() > 0) {
            int obsidianCount = InvUtils.find(Items.OBSIDIAN).count();
            if (obsidianCount >= this.obsidianLimit.get() * 64) {
                info("Лимит обсидиана достигнут (%d стаков). Модуль выключен.", this.obsidianLimit.get());
                toggle();
                return;
            }
        }

        if (this.targetPos == null) {
            if (this.posMode.get() == PosMode.Auto) {
                this.targetPos = determineAutoPosition();
            }
            if (this.targetPos == null) return;
        }

        // Проверка досягаемости
        if (GrimUtils.closestEyeDistanceSqTo(this.mc.player, Vec3.atCenterOf(this.targetPos)) > REACH_SQ) {
            RotationUtils.getInstance().clearRotations(this);
            this.pendingPlaceHit = null;
            return;
        }

        if (this.actionTimer > 0) {
            this.actionTimer--;
            if (this.pendingPlaceHit != null) {
                RotationUtils.getInstance().keepWireFresh();
            }
            return;
        }

        BlockState state = this.mc.level.getBlockState(this.targetPos);

        if (state.isAir() || state.canBeReplaced()) {
            // Сундука нет — если только что поставили, ждём подтверждения от сервера
            if (this.placedWaitTimer > 0) {
                return;
            }
            this.staleTicks = 0;
            this.initialMiningStarted = false;
            this.breakingProgress = 0.0f;
            preparePlaceEchest();
        } else if (state.is(Blocks.ENDER_CHEST)) {
            // Эндер-сундук на месте — ломаем
            this.placedWaitTimer = 0;
            this.pendingPlaceHit = null;
            handleBreakEchest(state);
        } else {
            // Позиция занята посторонним блоком
            this.placedWaitTimer = 0;
            this.pendingPlaceHit = null;
            if (this.posMode.get() == PosMode.Auto) {
                this.targetPos = determineAutoPosition();
            }
        }
    }

    private void preparePlaceEchest() {
        FindItemResult echest = findEnderChest();
        if (!echest.found()) {
            error("Закончились эндер-сундуки!");
            toggle();
            return;
        }

        BlockHitResult hit = findBestSupportHit(this.targetPos);
        if (hit == null) return;

        this.pendingPlaceHit = hit;

        Vec3 eye = this.mc.player.getEyePosition();
        float[] rot = RotationUtils.getRotationsTo(eye, hit.getLocation());

        RotationUtils rotUtils = RotationUtils.getInstance();
        rotUtils.setRotationFull(this, ROTATION_PRIORITY, rot[0], rot[1], this.turnSpeed.get());
        rotUtils.keepWireFresh();

        boolean aligned = !rotUtils.isRotating() || rotUtils.isAlignedFor(this, ALIGN_EPSILON);
        BlockHitResult confirmed = confirmSentRaycast(hit);

        if (aligned && confirmed != null && !SwapManager.getInstance().isActionConflictTick()) {
            if (this.mc.player.isSprinting()) {
                this.mc.player.setSprinting(false);
            }
            executePlaceEchest(confirmed);
        }
    }

    private void executePlaceEchest(BlockHitResult hit) {
        FindItemResult echest = findEnderChest();
        if (!echest.found()) {
            this.pendingPlaceHit = null;
            return;
        }

        if (!SwapManager.getInstance().hold(this, echest.slot(), SwapManager.Priority.PLACE, 2)) {
            return;
        }

        int seq = GrimUtils.nextSequence();
        this.mc.getConnection().send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hit, seq));
        this.mc.getConnection().send(ServerboundPunchPacket.INSTANCE);

        this.placedWaitTimer = 10;
        this.actionTimer = this.placeDelay.get();
        this.pendingPlaceHit = null;
        this.alignTicks = 0;
    }

    private void handleBreakEchest(BlockState state) {
        if (this.useSpeedMine.get()) {
            SpeedMine speedMine = ModuleManager.INSTANCE.getModule(SpeedMine.class);
            if (speedMine != null) {
                if (!speedMine.isActive()) speedMine.toggle();
                if (speedMine.canDelegateMining()) {
                    if (!speedMine.isMiningBlock(this.targetPos)) {
                        speedMine.mineBlock(this.targetPos, Direction.UP);
                    }
                    return;
                }
            }
        }

        // Автономный режим (если SpeedMine выключен)
        FindItemResult pickaxe = findPickaxe();
        if (!pickaxe.found()) {
            error("В хотбаре нет кирки!");
            toggle();
            return;
        }

        Vec3 center = Vec3.atCenterOf(this.targetPos);
        Vec3 eye = this.mc.player.getEyePosition();
        float[] rot = RotationUtils.getRotationsTo(eye, center);
        RotationUtils.getInstance().setRotationSilentDirect(this, ROTATION_PRIORITY, rot[0], rot[1], this.turnSpeed.get());

        if (!SwapManager.getInstance().hold(this, pickaxe.slot(), SwapManager.Priority.MINING, 2)) return;

        if (this.rebreakPrimed) {
            // Rebreak через STOP_DESTROY_BLOCK
            sendSwing();
            int seq = GrimUtils.nextSequence();
            this.mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, this.targetPos, Direction.UP, seq));

            this.staleTicks++;
            if (this.staleTicks > 10) {
                this.rebreakPrimed = false;
                this.staleTicks = 0;
                this.initialMiningStarted = false;
                this.breakingProgress = 0.0f;
            }

            this.actionTimer = Math.max(1, this.breakDelay.get());
        } else {
            // Первое легитное разрушение (Priming)
            if (!this.initialMiningStarted) {
                int seq = GrimUtils.nextSequence();
                this.mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, this.targetPos, Direction.UP, seq));
                sendSwing();
                this.initialMiningStarted = true;
                this.breakingProgress = 0.0f;
            } else {
                sendSwing();
                float delta = state.getDestroyProgress(this.mc.player, this.mc.level, this.targetPos);
                this.breakingProgress += delta;

                if (this.breakingProgress >= 1.0f) {
                    int seq = GrimUtils.nextSequence();
                    this.mc.getConnection().send(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, this.targetPos, Direction.UP, seq));
                    this.rebreakPrimed = true;
                    this.initialMiningStarted = false;
                    this.breakingProgress = 0.0f;
                    this.actionTimer = Math.max(1, this.breakDelay.get());
                }
            }
        }
    }

    private BlockHitResult findBestSupportHit(BlockPos placePos) {
        if (this.mc.player == null || this.mc.level == null) return null;

        Vec3 eye = this.mc.player.getEyePosition();
        BlockHitResult best = null;
        double bestDist = Double.MAX_VALUE;

        for (Direction dir : Direction.values()) {
            BlockPos neighbor = placePos.relative(dir);
            BlockState state = this.mc.level.getBlockState(neighbor);

            if (state.canBeReplaced() || state.getCollisionShape(this.mc.level, neighbor).isEmpty()) {
                continue;
            }

            Direction clickFace = dir.getOpposite();
            if (!isFaceFacingEye(eye, neighbor, clickFace)) {
                continue;
            }

            Vec3 sample = getFaceSamplePoint(neighbor, clickFace, eye);
            Vec3 dirVec = sample.subtract(eye);
            if (dirVec.lengthSqr() < 1.0E-6) continue;

            Vec3 end = eye.add(dirVec.normalize().scale(REACH + 0.1));
            BlockHitResult clip = this.mc.level.clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.mc.player
            ));

            if (clip.getType() == HitResult.Type.BLOCK
                && clip.getBlockPos().equals(neighbor)
                && clip.getDirection() == clickFace) {
                double d = eye.distanceToSqr(clip.getLocation());
                if (d <= REACH_SQ && d < bestDist) {
                    bestDist = d;
                    best = clip;
                }
            }
        }

        return best;
    }

    private BlockHitResult confirmSentRaycast(BlockHitResult expected) {
        if (this.mc.player == null || this.mc.level == null) return null;

        RotationUtils rotUtils = RotationUtils.getInstance();
        Vec3 eye = this.mc.player.getEyePosition();
        float yaw = rotUtils.getSentYaw();
        float pitch = rotUtils.getSentPitch();

        Vec3 look = RotationUtils.getRotationVector(pitch, yaw);
        Vec3 end = eye.add(look.scale(REACH + 0.1));
        BlockHitResult clip = this.mc.level.clip(new ClipContext(
            eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.mc.player
        ));

        if (clip.getType() == HitResult.Type.BLOCK
            && clip.getBlockPos().equals(expected.getBlockPos())
            && eye.distanceToSqr(clip.getLocation()) <= REACH_SQ) {
            return clip;
        }

        return null;
    }

    private boolean isFaceFacingEye(Vec3 eye, BlockPos pos, Direction face) {
        return switch (face) {
            case DOWN -> eye.y < pos.getY();
            case UP -> eye.y > pos.getY() + 1.0;
            case NORTH -> eye.z < pos.getZ();
            case SOUTH -> eye.z > pos.getZ() + 1.0;
            case WEST -> eye.x < pos.getX();
            case EAST -> eye.x > pos.getX() + 1.0;
        };
    }

    private Vec3 getFaceSamplePoint(BlockPos pos, Direction face, Vec3 eye) {
        double minX = pos.getX();
        double minY = pos.getY();
        double minZ = pos.getZ();

        return switch (face) {
            case DOWN -> new Vec3(minX + 0.5, minY, minZ + 0.5);
            case UP -> new Vec3(minX + 0.5, minY + 1.0, minZ + 0.5);
            case NORTH -> new Vec3(minX + 0.5, minY + 0.5, minZ);
            case SOUTH -> new Vec3(minX + 0.5, minY + 0.5, minZ + 1.0);
            case WEST -> new Vec3(minX, minY + 0.5, minZ + 0.5);
            case EAST -> new Vec3(minX + 1.0, minY + 0.5, minZ + 0.5);
        };
    }

    private void sendSwing() {
        if (this.mc.getConnection() != null) {
            this.mc.getConnection().send(ServerboundPunchPacket.INSTANCE);
        }
    }

    private FindItemResult findEnderChest() {
        FindItemResult hotbar = InvUtils.find(stack -> stack.is(Items.ENDER_CHEST), 0, 8);
        if (hotbar.found()) return hotbar;

        if (this.replenish.get()
            && this.mc.player.containerMenu == this.mc.player.inventoryMenu
            && this.mc.player.containerMenu.getCarried().isEmpty()) {
            FindItemResult inv = InvUtils.find(Items.ENDER_CHEST);
            if (inv.found() && inv.slot() >= 9 && inv.slot() < 36) {
                InvUtils.move().from(inv.slot()).toHotbar(this.mc.player.getInventory().getSelectedSlot());
                return InvUtils.find(stack -> stack.is(Items.ENDER_CHEST), 0, 8);
            }
        }

        return hotbar;
    }

    private FindItemResult findPickaxe() {
        if (this.preferNoSilk.get()) {
            FindItemResult noSilk = InvUtils.find(stack -> stack.is(ItemTags.PICKAXES) && !hasSilkTouch(stack), 0, 8);
            if (noSilk.found()) return noSilk;
        }

        return InvUtils.find(stack -> stack.is(ItemTags.PICKAXES), 0, 8);
    }

    private boolean hasSilkTouch(ItemStack stack) {
        ItemEnchantments enchants = stack.getEnchantments();
        for (net.minecraft.core.Holder<Enchantment> entry : enchants.keySet()) {
            if (entry.is(Enchantments.SILK_TOUCH)) {
                return true;
            }
        }
        return false;
    }

    private BlockPos determineAutoPosition() {
        if (this.mc.player == null || this.mc.level == null) return null;

        Direction facing = this.mc.player.getDirection();
        BlockPos front = this.mc.player.blockPosition().relative(facing);

        if (this.mc.level.getBlockState(front).is(Blocks.ENDER_CHEST) || this.mc.level.getBlockState(front).canBeReplaced()) {
            return front;
        }

        BlockPos down = this.mc.player.blockPosition().below();
        if (this.mc.level.getBlockState(down).is(Blocks.ENDER_CHEST) || this.mc.level.getBlockState(down).canBeReplaced()) {
            return down;
        }

        return front;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!this.render.get() || this.targetPos == null) return;

        event.renderer.box(
            this.targetPos,
            this.sideColor.get(),
            this.lineColor.get(),
            this.shapeMode.get(),
            0
        );
    }
}
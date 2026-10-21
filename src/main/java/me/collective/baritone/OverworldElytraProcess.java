package me.collective.baritone;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.event.events.RenderEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.Rotation;
import baritone.api.utils.input.Input;
import baritone.utils.PathRenderer;
import me.collective.command.ChatHelper;
import me.collective.util.BaritoneHelper;
import me.collective.module.ModuleManager;
import me.collective.module.movement.Pitch40;
import me.collective.util.InventoryUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Высокоэффективный процесс полёта на элитрах в Оверворлде (и любых измерениях) для Baritone.
 * Реализует режим Pitch-40 (синусоидальный волновой полёт) для минимального расхода фейерверков.
 */
public class OverworldElytraProcess implements AbstractGameEventListener, IBaritoneProcess {

    public enum State {
        INACTIVE("Неактивен"),
        TAKEOFF("Взлёт"),
        CLIMBING("Набор высоты"),
        CRUISING_PITCH40("Pitch-40"),
        APPROACHING("Приближение"),
        LANDING("Посадка");

        public final String description;

        State(String description) {
            this.description = description;
        }
    }

    private static OverworldElytraProcess INSTANCE;

    private final IBaritone baritone;
    private final IPlayerContext ctx;

    private State state = State.INACTIVE;
    private BlockPos destination = null;
    private final List<BetterBlockPos> visualPath = new ArrayList<>();

    // Параметры Pitch-40
    private double cruiseTargetY = 360.0;
    private double cruiseLowerY = 320.0;
    private double cruiseUpperY = 380.0;
    private float divePitch = 40.0f;
    private float climbPitch = -35.0f;
    private double minSpeedThreshold = 18.0;
    // Эконом-режим: ниже пороги помощи фейерверками и длиннее паузы между ними.
    // Волна Pitch-40 сама восстанавливает скорость в пикировании, ракеты — только страховка.
    private boolean ecoMode = true;

    // Таймеры и логика полёта
    private boolean isPitchDiving = true;
    private int fireworkCooldown = 0;
    private int takeoffTicks = 0;
    private Vec3d lastTickPos = Vec3d.ZERO;

    public OverworldElytraProcess(IBaritone baritone) {
        this.baritone = baritone;
        this.ctx = baritone.getPlayerContext();
        INSTANCE = this;
        baritone.getGameEventHandler().registerEventListener(this);
        baritone.getPathingControlManager().registerProcess(this);
    }

    public static OverworldElytraProcess getInstance() {
        return INSTANCE;
    }

    public static void init() {
        // Без установленного Baritone любые упоминания baritone.api уронят загрузку класса.
        if (!BaritoneHelper.isAvailable()) return;
        if (INSTANCE == null && BaritoneAPI.getProvider() != null) {
            IBaritone primary = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (primary != null) {
                new OverworldElytraProcess(primary);
            }
        }
    }

    public void pathTo(BlockPos dest) {
        this.destination = dest;
        this.visualPath.clear();
        this.takeoffTicks = 0;
        this.fireworkCooldown = 0;
        syncBoundsFromModule();

        if (ctx.player() != null && ctx.player().isGliding()) {
            this.state = State.CRUISING_PITCH40;
            ChatHelper.sendMessage("§b[Elytra] §fЦель: " + dest.toShortString() + ". Режим Pitch-40 активен!");
        } else {
            this.state = State.TAKEOFF;
            ChatHelper.sendMessage("§b[Elytra] §fЦель: " + dest.toShortString() + ". Начинаю взлёт...");
        }
        recalculateVisualPath();
    }

    public void pathTo(Goal goal) {
        if (goal instanceof GoalBlock gb) {
            pathTo(new BlockPos(gb.x, gb.y, gb.z));
        } else if (goal instanceof GoalXZ gxz) {
            int y = ctx.player() != null ? (int) ctx.player().getY() : 64;
            pathTo(new BlockPos(gxz.getX(), y, gxz.getZ()));
        } else {
            throw new IllegalArgumentException("Цель должна быть GoalBlock или GoalXZ");
        }
    }

    public void cancel() {
        onLostControl();
    }

    @Override
    public boolean isActive() {
        return this.state != State.INACTIVE && this.destination != null;
    }

    @Override
    public void onLostControl() {
        this.state = State.INACTIVE;
        this.destination = null;
        this.visualPath.clear();
        this.takeoffTicks = 0;
        if (baritone != null && baritone.getInputOverrideHandler() != null) {
            baritone.getInputOverrideHandler().clearAllKeys();
        }
    }

    @Override
    public double priority() {
        return 3.0;
    }

    @Override
    public boolean isTemporary() {
        return false;
    }

    @Override
    public String displayName0() {
        return "Overworld Elytra - " + this.state.description;
    }

    public BlockPos getDestination() {
        return this.destination;
    }

    public State getState() {
        return this.state;
    }

    private void syncBoundsFromModule() {
        try {
            Pitch40 pitch40 = ModuleManager.INSTANCE.getModule(Pitch40.class);
            if (pitch40 != null) {
                this.cruiseLowerY = pitch40.pitch40LowerBounds.getValue();
                this.cruiseUpperY = pitch40.pitch40UpperBounds.getValue();
                this.cruiseTargetY = (cruiseLowerY + cruiseUpperY) / 2.0;
                this.minSpeedThreshold = pitch40.minSpeed.getValue();
                this.ecoMode = pitch40.ecoMode.getValue();
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        if (!isActive() || ctx.player() == null || ctx.world() == null) {
            return new PathingCommand(null, PathingCommandType.DEFER);
        }

        if (this.fireworkCooldown > 0) {
            this.fireworkCooldown--;
        }

        Vec3d playerPos = ctx.player().getEntityPos();
        double distSqToDest = playerPos.squaredDistanceTo(destination.getX() + 0.5, playerPos.y, destination.getZ() + 0.5);

        // Прибыли в пункт назначения
        if (distSqToDest < 16.0 && Math.abs(playerPos.y - destination.getY()) < 10.0) {
            ChatHelper.sendMessage("§a[Elytra] Прибыли в пункт назначения!");
            onLostControl();
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        // Авто-приземление при приближении к координатам
        if (distSqToDest < 64.0 * 64.0 && this.state != State.LANDING && this.state != State.TAKEOFF) {
            this.state = State.LANDING;
            ChatHelper.sendMessage("§b[Elytra] §fСнижаюсь к целевой точке для посадки...");
        }

        switch (this.state) {
            case TAKEOFF -> tickTakeoff();
            case CLIMBING -> tickClimbing();
            case CRUISING_PITCH40 -> tickPitch40Cruise();
            case LANDING -> tickLanding();
            default -> {}
        }

        this.lastTickPos = playerPos;
        return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
    }

    private void tickTakeoff() {
        if (ctx.player().isGliding()) {
            this.takeoffTicks = 0;
            if (ctx.player().getY() < this.cruiseLowerY) {
                this.state = State.CLIMBING;
            } else {
                this.state = State.CRUISING_PITCH40;
            }
            return;
        }

        if (!hasElytraEquipped()) {
            ChatHelper.sendMessage("§c[Elytra] Элитры не надеты! Отмена полёта.");
            onLostControl();
            return;
        }

        this.takeoffTicks++;
        baritone.getInputOverrideHandler().clearAllKeys();

        // 1. Прыжок с земли
        if (ctx.player().isOnGround()) {
            baritone.getInputOverrideHandler().setInputForceState(Input.JUMP, true);
        } else if (this.takeoffTicks > 2) {
            // 2. Отправка пакета раскрытия элитр
            MinecraftClient mc = ctx.minecraft();
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(ctx.player(), ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }
            // 3. Запуск фейерверка вверх для разгона
            if (this.takeoffTicks > 4 && this.fireworkCooldown <= 0) {
                lookTowards(new Vec3d(destination.getX() + 0.5, ctx.player().getY() + 50, destination.getZ() + 0.5), -70.0f);
                if (useFirework()) {
                    this.fireworkCooldown = 15;
                    this.state = State.CLIMBING;
                } else {
                    ChatHelper.sendMessage("§c[Elytra] Нет фейерверков для взлёта!");
                    onLostControl();
                }
            }
        }

        if (this.takeoffTicks > 40) {
            this.takeoffTicks = 0;
        }
    }

    private void tickClimbing() {
        if (!ctx.player().isGliding()) {
            this.state = State.TAKEOFF;
            return;
        }

        double currentY = ctx.player().getY();
        if (currentY >= this.cruiseLowerY) {
            this.state = State.CRUISING_PITCH40;
            ChatHelper.sendMessage("§a[Elytra] Крейсерская высота достигнута (Y=" + (int) currentY + "). Включаю Pitch-40!");
            return;
        }

        lookTowards(new Vec3d(destination.getX() + 0.5, this.cruiseTargetY, destination.getZ() + 0.5), -55.0f);

        double speed = getSpeedBPS();
        if (speed < (ecoMode ? 9.0 : 12.0) && this.fireworkCooldown <= 0) {
            if (useFirework()) {
                this.fireworkCooldown = ecoMode ? 50 : 25;
            }
        }
    }

    private void tickPitch40Cruise() {
        if (!ctx.player().isGliding()) {
            ChatHelper.sendMessage("§e[Elytra] Полёт прерван! Возобновляю взлёт...");
            this.state = State.TAKEOFF;
            return;
        }

        double currentY = ctx.player().getY();
        double speed = getSpeedBPS();

        // Синусоидальная механика Pitch 40:
        // Пикирование (+40°) разгоняет до 60-70 блоков/сек
        // Кабрирование (-35°) за счёт набранной кинетической энергии поднимает обратно
        if (this.isPitchDiving) {
            if (currentY <= this.cruiseLowerY || (speed > 45.0 && currentY <= this.cruiseTargetY)) {
                this.isPitchDiving = false;
            }
        } else {
            if (currentY >= this.cruiseUpperY || speed < this.minSpeedThreshold) {
                // Если импульса не хватило подняться — аккуратно подталкиваем 1 фейерверком.
                // В эконом-режиме ждём дольше и терпим меньшую скорость: волна сама доберёт в пике.
                if (speed < (ecoMode ? 8.0 : 10.0) && this.fireworkCooldown <= 0) {
                    if (useFirework()) {
                        this.fireworkCooldown = ecoMode ? 60 : 30;
                    }
                }
                this.isPitchDiving = true;
            }
        }

        float desiredPitch = this.isPitchDiving ? this.divePitch : this.climbPitch;
        lookTowards(new Vec3d(destination.getX() + 0.5, this.destination.getY(), destination.getZ() + 0.5), desiredPitch);

        // Экстренный фейерверк при критическом падении скорости
        if (speed < (ecoMode ? 5.0 : 6.0) && this.fireworkCooldown <= 0) {
            if (useFirework()) {
                this.fireworkCooldown = ecoMode ? 50 : 25;
            }
        }

        if (ctx.player().age % 40 == 0) {
            recalculateVisualPath();
        }
    }

    private void tickLanding() {
        if (!ctx.player().isGliding()) {
            if (ctx.player().isOnGround()) {
                ChatHelper.sendMessage("§a[Elytra] Успешная посадка в цель!");
                onLostControl();
            }
            return;
        }

        Vec3d destVec = new Vec3d(destination.getX() + 0.5, destination.getY() + 1.0, destination.getZ() + 0.5);
        double distXZ = Math.sqrt(ctx.player().squaredDistanceTo(destVec.x, ctx.player().getY(), destVec.z));

        if (distXZ > 15.0) {
            lookTowards(destVec, 15.0f);
        } else {
            lookTowards(destVec, 45.0f);
            if (ctx.player().getY() <= destination.getY() + 3.0) {
                baritone.getInputOverrideHandler().setInputForceState(Input.SNEAK, true);
            }
        }
    }

    private void lookTowards(Vec3d target, float targetPitch) {
        Vec3d from = ctx.playerFeetAsVec().add(0, ctx.player().getStandingEyeHeight(), 0);
        double dx = target.x - from.x;
        double dz = target.z - from.z;
        float yaw = (float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        baritone.getLookBehavior().updateTarget(new Rotation(yaw, targetPitch), true);
    }

    private double getSpeedBPS() {
        Vec3d v = ctx.player().getVelocity();
        return Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z) * 20.0;
    }

    private boolean hasElytraEquipped() {
        ItemStack chest = ctx.player().getEquippedStack(EquipmentSlot.CHEST);
        return chest.isOf(Items.ELYTRA) && (chest.getMaxDamage() - chest.getDamage()) > 5;
    }

    private boolean useFirework() {
        MinecraftClient mc = ctx.minecraft();
        if (mc.player == null) return false;

        int fwSlot = -1;
        if (isFirework(mc.player.getMainHandStack())) {
            fwSlot = mc.player.getInventory().selectedSlot;
        } else if (isFirework(mc.player.getOffHandStack())) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            return true;
        } else {
            for (int i = 0; i < 9; i++) {
                if (isFirework(mc.player.getInventory().getStack(i))) {
                    fwSlot = i;
                    break;
                }
            }
        }

        if (fwSlot != -1) {
            InventoryUtil.swap(fwSlot, true);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            InventoryUtil.swapBack();
            return true;
        }

        for (int i = 9; i < 36; i++) {
            if (isFirework(mc.player.getInventory().getStack(i))) {
                InventoryUtil.swap(i, true);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                InventoryUtil.swapBack();
                return true;
            }
        }

        return false;
    }

    private boolean isFirework(ItemStack stack) {
        if (!stack.isOf(Items.FIREWORK_ROCKET)) return false;
        FireworksComponent fc = stack.get(DataComponentTypes.FIREWORKS);
        return fc != null && fc.explosions().isEmpty();
    }

    private void recalculateVisualPath() {
        this.visualPath.clear();
        if (destination == null || ctx.player() == null) return;

        Vec3d start = ctx.player().getEntityPos();
        Vec3d end = new Vec3d(destination.getX() + 0.5, destination.getY() + 0.5, destination.getZ() + 0.5);

        this.visualPath.add(new BetterBlockPos(start.x, start.y, start.z));
        this.visualPath.add(new BetterBlockPos(start.x, this.cruiseTargetY, start.z));

        int steps = 15;
        for (int i = 1; i <= steps; i++) {
            double frac = (double) i / steps;
            double x = MathHelper.lerp(frac, start.x, end.x);
            double z = MathHelper.lerp(frac, start.z, end.z);
            double y = (i == steps) ? end.y : this.cruiseTargetY;
            this.visualPath.add(new BetterBlockPos(x, y, z));
        }
        this.visualPath.add(new BetterBlockPos(end.x, end.y, end.z));
    }

    @Override
    public void onRenderPass(RenderEvent event) {
        if (isActive() && !this.visualPath.isEmpty()) {
            PathRenderer.a(event.getModelViewStack(), this.visualPath, 0, new Color(0, 220, 255, 200), false, 0, 0, 0.0);
        }
    }
}

package me.collective.module.highway;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.PacketEvent;
import me.collective.event.events.Render3DEvent;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.module.ModuleManager;
import me.collective.module.movement.Speed;
import me.collective.module.player.AutoEat;
import me.collective.render.Renderer3D;
import me.collective.render.ShapeMode;
import me.collective.setting.BlockListSetting;
import me.collective.setting.BooleanSetting;
import me.collective.setting.ColorSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.ItemListSetting;
import me.collective.setting.NumberSetting;
import me.collective.setting.SettingGroup;
import me.collective.util.FindItemResult;
import me.collective.util.InventoryUtil;
import me.collective.render.RenderUtil3D;
import me.collective.util.RotationUtils;
import me.collective.font.FontManager;
import me.collective.util.sort.ShulkerDataParser;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Порт HighwayBuilderHIG: автоматическая стройка и копка хайвеев.
 *
 * <p>Отличия от оригинала (вынужденные): движение идёт через хук PlayerInput
 * (флаги ниже), а не заменой объекта input; TextHUD-статистика печатается
 * в чат; GameLeft заменён уходом в suspend при потере мира; проверки
 * KillAura/AutoGap/InstantRebreak/NoGhostBlocks/SpeedMine пропущены
 * (нет API); restock читает контейнеры через слоты хендлера.
 */
public class HighwayBuilderHIG extends Module {
    public static HighwayBuilderHIG INSTANCE;
    public enum Floor {
        Replace,
        PlaceMissing
    }

    public enum Rotation {
        None(false, false),
        Mine(true, false),
        Place(false, true),
        Both(true, true);

        public final boolean mine;
        public final boolean place;

        Rotation(boolean mine, boolean place) {
            this.mine = mine;
            this.place = place;
        }
    }

    public enum BlockadeType {
        Full(6),
        Partial(4),
        Shulker(3);

        public final int columns;

        BlockadeType(int columns) {
            this.columns = columns;
        }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDigging = settings.createGroup("Digging");
    private final SettingGroup sgPaving = settings.createGroup("Paving");
    private final SettingGroup sgInventory = settings.createGroup("Inventory");
    private final SettingGroup sgRenderDigging = settings.createGroup("RenderDigging");
    private final SettingGroup sgRenderPaving = settings.createGroup("RenderPaving");
    private final SettingGroup sgStatistics = settings.createGroup("Statistics");

    public final NumberSetting width = add(sgGeneral.add(new NumberSetting("Width", 4, 1, 6, 1)));
    public final NumberSetting height = add(sgGeneral.add(new NumberSetting("Height", 3, 2, 5, 1)));
    public final EnumSetting<Floor> floor = add(sgGeneral.add(new EnumSetting<>("Floor", Floor.Replace)));
    public final BooleanSetting railings = add(sgGeneral.add(new BooleanSetting("Railings", true)));
    public final BooleanSetting cornerBlock = add(sgGeneral.add(new BooleanSetting("CornerBlock", true)));
    public final BooleanSetting mineAboveRailings = add(sgGeneral.add(new BooleanSetting("MineAboveRailings", true)));
    public final EnumSetting<Rotation> rotation = add(sgGeneral.add(new EnumSetting<>("Rotation", Rotation.Both)));
    public final BooleanSetting disconnectOnToggle = add(sgGeneral.add(new BooleanSetting("DisconnectOnToggle", false)));
    public final BooleanSetting pauseOnLag = add(sgGeneral.add(new BooleanSetting("PauseOnLag", true)));
    public final NumberSetting resumeTps = add(sgGeneral.add(new NumberSetting("ResumeTps", 16, 1, 19, 1)));
    public final BooleanSetting destroyCrystalTraps = add(sgGeneral.add(new BooleanSetting("DestroyCrystalTraps", true)));

    public final BooleanSetting ignoreSigns = add(sgDigging.add(new BooleanSetting("IgnoreSigns", true)));
    public final BooleanSetting doubleMine = add(sgDigging.add(new BooleanSetting("DoubleMine", true)));
    public final BooleanSetting fastBreak = add(sgDigging.add(new BooleanSetting("FastBreak", true)));
    public final BooleanSetting dontBreakTools = add(sgDigging.add(new BooleanSetting("DontBreakTools", false)));
    public final NumberSetting breakDurability = add(sgDigging.add(new NumberSetting("DurabilityPct", 2, 1, 100, 1)));
    public final NumberSetting savePickaxes = add(sgDigging.add(new NumberSetting("SavePickaxes", 1, 0, 36, 1)));
    public final NumberSetting breakDelay = add(sgDigging.add(new NumberSetting("BreakDelay", 0, 0, 100, 1)));
    public final NumberSetting blocksPerTick = add(sgDigging.add(new NumberSetting("BlocksPerTick", 1, 1, 100, 1)));

    public final BlockListSetting blocksToPlace = add(sgPaving.add(new BlockListSetting("BlocksToPlace", List.of(Blocks.OBSIDIAN))));
    public final NumberSetting placeRange = add(sgPaving.add(new NumberSetting("PlaceRange", 4.5, 0.0, 5.5, 0.1)));
    public final NumberSetting placeDelay = add(sgPaving.add(new NumberSetting("PlaceDelay", 0, 0, 100, 1)));
    public final NumberSetting placementsPerTick = add(sgPaving.add(new NumberSetting("PlacementsPerTick", 1, 1, 100, 1)));

    public final ItemListSetting trashItems = add(sgInventory.add(new ItemListSetting("TrashItems", List.of(
        Items.NETHERRACK, Items.QUARTZ, Items.GOLD_NUGGET, Items.GOLDEN_SWORD, Items.GLOWSTONE_DUST,
        Items.GLOWSTONE, Items.BLACKSTONE, Items.BASALT, Items.GHAST_TEAR, Items.SOUL_SAND, Items.SOUL_SOIL,
        Items.ROTTEN_FLESH, Items.MAGMA_BLOCK))));
    public final NumberSetting inventoryDelay = add(sgInventory.add(new NumberSetting("InventoryDelay", 3, 0, 100, 1)));
    public final BooleanSetting ejectUselessShulkers = add(sgInventory.add(new BooleanSetting("EjectUselessShulkers", true)));
    public final BooleanSetting searchEnderChest = add(sgInventory.add(new BooleanSetting("SearchEnderChest", false)));
    public final BooleanSetting searchShulkers = add(sgInventory.add(new BooleanSetting("SearchShulkers", true)));
    public final NumberSetting minEmpty = add(sgInventory.add(new NumberSetting("MinEmpty", 3, 0, 9, 1)));
    public final BooleanSetting mineEnderChests = add(sgInventory.add(new BooleanSetting("MineEnderChests", true)));
    public final EnumSetting<BlockadeType> blockadeType = add(sgInventory.add(new EnumSetting<>("BlockadeType", BlockadeType.Full)));
    public final NumberSetting saveEchests = add(sgInventory.add(new NumberSetting("SaveEchests", 2, 0, 64, 1)));
    public final BooleanSetting rebreakEchests = add(sgInventory.add(new BooleanSetting("RebreakEchests", false)));
    public final NumberSetting rebreakTimer = add(sgInventory.add(new NumberSetting("RebreakTimer", 0, 0, 20, 1)));

    public final BooleanSetting renderMine = add(sgRenderDigging.add(new BooleanSetting("RenderMine", true)));
    public final EnumSetting<ShapeMode> renderMineShape = add(sgRenderDigging.add(new EnumSetting<>("RenderMineShape", ShapeMode.Both)));
    public final ColorSetting renderMineSideColor = add(sgRenderDigging.add(new ColorSetting("RenderMineSide", new Color(225, 25, 25, 25))));
    public final ColorSetting renderMineLineColor = add(sgRenderDigging.add(new ColorSetting("RenderMineLine", new Color(225, 25, 25, 255))));

    public final BooleanSetting renderPlace = add(sgRenderPaving.add(new BooleanSetting("RenderPlace", true)));
    public final EnumSetting<ShapeMode> renderPlaceShape = add(sgRenderPaving.add(new EnumSetting<>("RenderPlaceShape", ShapeMode.Both)));
    public final ColorSetting renderPlaceSideColor = add(sgRenderPaving.add(new ColorSetting("RenderPlaceSide", new Color(25, 25, 225, 25))));
    public final ColorSetting renderPlaceLineColor = add(sgRenderPaving.add(new ColorSetting("RenderPlaceLine", new Color(25, 25, 225, 255))));

    public final BooleanSetting printStatistics = add(sgStatistics.add(new BooleanSetting("PrintStatistics", true)));

    // Движение через хук PlayerInput (читает MixinClientPlayerEntity)
    public boolean inFwd;
    public boolean inBack;
    public boolean inLeft;
    public boolean inRight;
    public boolean inJump;
    public boolean inSneak;

    public void stopInput() {
        inFwd = inBack = inLeft = inRight = inJump = inSneak = false;
    }

    public boolean isDrivingInput() {
        return inFwd || inBack || inLeft || inRight || inJump || inSneak;
    }

    private HorizontalDirection dir;
    private HorizontalDirection leftDir;
    private HorizontalDirection rightDir;

    private State state;
    private State lastState;
    private IBlockPosProvider blockPosProvider;

    public Vec3 start;
    public int blocksBroken;
    public int blocksPlaced;
    private final MBlockPos lastBreakingPos = new MBlockPos();
    private boolean displayInfo;
    private boolean warned;
    private boolean sentLagMessage;
    private boolean suspended = true;
    private boolean inventory = true;
    private int placeTimer;
    private int breakTimer;
    private int count;
    private int containerId;
    private final RestockTask restockTask = new RestockTask(this);
    private final ArrayList<EndCrystalEntity> ignoreCrystals = new ArrayList<>();
    public boolean drawingBow;
    public DoubleMineBlock normalMining;
    public DoubleMineBlock packetMining;

    private final MBlockPos posRender2 = new MBlockPos();
    private final MBlockPos posRender3 = new MBlockPos();
    private final Map<BlockPos, Long> placedFx = new HashMap<>();

    private long lastTickMs = 0L;
    private float tickRate = 20f;

    public HighwayBuilderHIG() {
        super("HighwayBuilderHIG", "Автоматически строит дорожное полотно хайвеев.", Category.HIGHWAY);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.world == null) return;

        updateVariables();
        stopInput();

        dir = HorizontalDirection.get(mc.player.getYaw());
        leftDir = dir.rotateLeftSkipOne();
        rightDir = leftDir.opposite();

        blockPosProvider = dir.diagonal ? new DiagonalBlockPosProvider() : new StraightBlockPosProvider();
        state = State.Forward;
        setState(State.Center);
        lastBreakingPos.set(0, 0, 0);

        start = mc.player.getEntityPos();
        blocksBroken = blocksPlaced = 0;
        displayInfo = true;
        sentLagMessage = false;
        suspended = false;
        lastTickMs = 0L;
        tickRate = 20f;

        restockTask.complete();

        if (blocksPerTick.getIntValue() > 1 && rotation.getValue().mine)
            warning("With rotations enabled, you can break at most 1 block per tick.");
        if (placementsPerTick.getIntValue() > 1 && rotation.getValue().place)
            warning("With rotations enabled, you can place at most 1 block per tick.");

        if (ModuleManager.INSTANCE.getModule(Speed.class).isToggled() && dir.diagonal)
            warning("It's recommended to disable the Speed module to avoid misalignment on diagonals.");

    }

    @Override
    public void onDisable() {
        if (mc.player == null || mc.world == null) return;

        stopInput();
        mc.options.useKey.setPressed(false);

        if (displayInfo && printStatistics.getValue()) {
            info("Distance: %.0f", mc.player.getEntityPos().distanceTo(start));
            info("Blocks broken: %d", blocksBroken);
            info("Blocks placed: %d", blocksPlaced);
        }
    }

    @Override
    public void error(String msg) {
        super.error(msg);
        setToggled(false);
        if (disconnectOnToggle.getValue()) {
            disconnect(msg);
        }
    }

    private void errorEarly(String msg) {
        displayInfo = false;
        error(msg);
    }

    private void disconnect(String msg) {
        if (mc.getNetworkHandler() == null) return;
        mc.getNetworkHandler().getConnection().disconnect(
            Component.literal("§7[§9HighwayBuilderHIG§7] §c" + msg));
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isToggled()) return;
        if (mc.player == null || mc.world == null) {
            suspended = true;
            inventory = false;
            return;
        }

        if (dir == null) {
            onEnable();
            return;
        }

        if (suspended) {
            if (inventory) {
                updateVariables();
                suspended = false;
            } else return;
        }

        if (width.getIntValue() < 3 && dir.diagonal) {
            errorEarly("Diagonal highways less than 3 blocks wide are not supported, please change the width setting.");
            return;
        }

        AutoEat autoEat = ModuleManager.INSTANCE.getModule(AutoEat.class);
        OffhandManager offhand = ModuleManager.INSTANCE.getModule(OffhandManager.class);
        if ((autoEat != null && autoEat.isEating()) || (offhand != null && offhand.isEating())) {
            stopInput();
            return;
        }

        if (pauseOnLag.getValue() && timeSinceLastTick() > 1.4f) {
            if (!sentLagMessage) {
                error("Server isn't responding, pausing.");
                stopInput();
                sentLagMessage = true;
                return;
            }

            if (sentLagMessage && tickRate <= resumeTps.getIntValue()) return;
            sentLagMessage = false;
        }

        count = 0;

        if (mc.player.getY() < start.y - 0.5) setState(State.ReLevel);
        tickDoubleMine();
        state.tick(this);

        if (breakTimer > 0) breakTimer--;
        if (placeTimer > 0) placeTimer--;
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (event.getPacket() instanceof WorldTimeUpdateS2CPacket) {
            long now = System.currentTimeMillis();
            if (lastTickMs > 0) {
                float delta = Math.max(1, now - lastTickMs);
                tickRate = Math.max(0f, Math.min(20f, tickRate * 0.9f + (1000f / delta) * 0.1f));
            }
            lastTickMs = now;
            return;
        }
        if (event.getPacket() instanceof InventoryS2CPacket p) {
            if (p.syncId() == 0 && suspended) inventory = true;
            else this.containerId = p.syncId();
        }
    }

    @EventHandler
    public void onRender2D(net.minecraft.client.gui.GuiGraphics context, float delta) {
        if (!isToggled() || suspended || !renderMine.getValue()) return;
        if (normalMining != null) normalMining.renderLetter(context);
        if (packetMining != null) packetMining.renderLetter(context);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!isToggled() || mc.player == null || mc.world == null) return;
        if (suspended || blockPosProvider == null) return;

        if (renderMine.getValue()) {
            render(blockPosProvider.getFront(), pos -> canMine(pos, true), true);
            if (floor.getValue() == Floor.Replace)
                render(blockPosProvider.getFloor(), pos -> canMine(pos, false), true);
            if (railings.getValue())
                render(blockPosProvider.getRailings(0), pos -> canMine(pos, false), true);
            if (mineAboveRailings.getValue())
                render(blockPosProvider.getRailings(1), pos -> canMine(pos, true), true);
            if (state == State.MineEChestBlockade)
                render(blockPosProvider.getBlockade(true, blockadeType.getValue()), pos -> canMine(pos, true), true);
        }

        if (renderPlace.getValue()) {
            render(blockPosProvider.getLiquids(), pos -> canPlace(pos, true), false);

            if (railings.getValue()) {
                render(blockPosProvider.getRailings(0), pos -> canPlace(pos, false), false);

                if (cornerBlock.getValue()) {
                    render(blockPosProvider.getRailings(-1), mBlockPos -> {
                        boolean valid = false;
                        for (MBlockPos pos : blockPosProvider.getRailings(0)) {
                            if (!blocksToPlace.getValue().contains(pos.getState().getBlock())
                                && pos.add(0, -1, 0).equals(mBlockPos)) {
                                valid = true;
                                break;
                            }
                        }
                        return valid && canPlace(mBlockPos, false);
                    }, false);
                }
            }

            render(blockPosProvider.getFloor(), pos -> canPlace(pos, false), false);
            if (state == State.PlaceEChestBlockade)
                render(blockPosProvider.getBlockade(false, blockadeType.getValue()), pos -> canPlace(pos, false), false);
        }

        long now = System.currentTimeMillis();
        placedFx.entrySet().removeIf(e -> now - e.getValue() > 4000L);
        Color sideBase = renderPlaceSideColor.getValue();
        Color lineBase = renderPlaceLineColor.getValue();
        for (Map.Entry<BlockPos, Long> e : new ArrayList<>(placedFx.entrySet())) {
            float age = Math.max(0f, Math.min(1f, (now - e.getValue()) / 4000f));
            int alpha = (int) (lineBase.getAlpha() * (1f - age));
            if (alpha <= 4) continue;
            BlockPos pos = e.getKey();
            Renderer3D.box(new AABB(pos),
                new Color(sideBase.getRed(), sideBase.getGreen(), sideBase.getBlue(), (int) (sideBase.getAlpha() * (1f - age))),
                new Color(lineBase.getRed(), lineBase.getGreen(), lineBase.getBlue(), alpha),
                renderPlaceShape.getValue(), true);
        }
    }

    private void render(MBPIterator it, Predicate<MBlockPos> predicate, boolean mine) {
        Color sideColor = mine ? renderMineSideColor.getValue() : renderPlaceSideColor.getValue();
        Color lineColor = mine ? renderMineLineColor.getValue() : renderPlaceLineColor.getValue();
        ShapeMode shapeMode = mine ? renderMineShape.getValue() : renderPlaceShape.getValue();

        for (MBlockPos pos : it) {
            posRender2.set(pos);
            if (predicate.test(posRender2)) {
                Renderer3D.box(posRender2.getBlockPos(), sideColor, lineColor, shapeMode, 0);
            }
        }
    }

    private void updateVariables() {
        placeTimer = breakTimer = count = containerId = 0;
        ignoreCrystals.clear();
        normalMining = null;
        packetMining = null;
    }

    private void setState(State state) {
        setState(state, this.state);
    }

    private void setState(State state, State lastState) {
        this.lastState = lastState;
        this.state = state;
        stopInput();
        state.start(this);
    }

    private int getWidthLeft() {
        return switch (width.getIntValue()) {
            case 6 -> 3;
            case 5, 4 -> 2;
            case 3, 2 -> 1;
            default -> 0;
        };
    }

    private int getWidthRight() {
        return switch (width.getIntValue()) {
            case 6, 5 -> 2;
            case 4, 3 -> 1;
            default -> 0;
        };
    }

    private boolean canMine(MBlockPos pos, boolean mineBlocksToPlace) {
        BlockState state = pos.getState();
        return canBreak(pos.getBlockPos(), state)
            && (mineBlocksToPlace || !blocksToPlace.getValue().contains(state.getBlock()));
    }

    private boolean canBreak(BlockPos pos, BlockState state) {
        if (mc.world == null) return false;
        if (state.isAir() || !state.getFluidState().isEmpty()) return false;
        return state.getHardness(mc.world, pos) >= 0;
    }

    private boolean canInstaBreak(BlockPos pos) {
        if (mc.player == null || mc.world == null) return false;
        return mc.world.getBlockState(pos).calcBlockBreakingDelta(mc.player, mc.world, pos) >= 1.0F;
    }

    private boolean canPlace(MBlockPos pos, boolean liquids) {
        if (mc.player == null) return false;
        BlockPos bp = pos.getBlockPos();
        double dx = bp.getX() + 0.5 - mc.player.getEyePos().x;
        double dy = bp.getY() + 0.5 - mc.player.getEyePos().y;
        double dz = bp.getZ() + 0.5 - mc.player.getEyePos().z;
        if (dx * dx + dy * dy + dz * dz > placeRange.getValue() * placeRange.getValue()) return false;
        BlockState state = pos.getState();
        return liquids ? !state.getFluidState().isEmpty() : state.isReplaceable();
    }

    private float timeSinceLastTick() {
        if (lastTickMs == 0) return 0f;
        return (System.currentTimeMillis() - lastTickMs) / 1000f;
    }

    private float[] lookAt(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        return new float[]{
            (float) Math.toDegrees(Math.atan2(-dx, dz)),
            (float) -Math.toDegrees(Math.atan2(dy, distXZ))
        };
    }

    private float[] lookAt(Entity e) {
        return lookAt(mc.player.getEyePos(), new Vec3(e.getX(), e.getY() + 0.5, e.getZ()));
    }

    private float[] lookAt(BlockPos bp) {
        return lookAt(mc.player.getEyePos(),
            new Vec3(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5));
    }

    private void rotateTo(float yaw, float pitch) {
        RotationUtils.getInstance().setRotationSilentInstant(this, 50, yaw, pitch);
    }

    private void rotateTo(BlockPos bp) {
        float[] yp = lookAt(bp);
        rotateTo(yp[0], yp[1]);
    }

    private void rotateTo(Entity e) {
        float[] yp = lookAt(e);
        rotateTo(yp[0], yp[1]);
    }

    public static Direction getInteractDirection(BlockPos pos) {
        if (mc.player == null) return Direction.UP;
        Vec3 eyePos = mc.player.getEyePos();
        Vec3 posVec = Vec3.ofCenter(pos);
        Direction bestDir = Direction.UP;
        double bestDot = -Double.MAX_VALUE;

        for (Direction dir : Direction.values()) {
            Vec3 dirVec = Vec3.of(dir.getVector());
            double dot = eyePos.subtract(posVec).normalize().dotProduct(dirVec);
            if (dot > bestDot) {
                bestDot = dot;
                bestDir = dir;
            }
        }
        return bestDir;
    }

    /** Ставит блок с опорой на соседний цельный блок. */
    private boolean placeBlock(BlockPos pos, int hotbarSlot) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return false;
        for (Direction dir : new Direction[]{Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            BlockPos neighbor = pos.offset(dir);
            BlockState ns = mc.world.getBlockState(neighbor);
            if (ns.isReplaceable() || !ns.isSolidBlock(mc.world, neighbor)) continue;
            if (!mc.world.getOtherEntities(mc.player, new AABB(pos)).stream()
                .filter(e -> !(e instanceof net.minecraft.entity.ItemEntity)).toList().isEmpty()) {
                return false;
            }
            Direction side = dir.getOpposite();
            Vec3 hitVec = new Vec3(neighbor.getX() + 0.5 + side.getOffsetX() * 0.45,
                neighbor.getY() + 0.5 + side.getOffsetY() * 0.45,
                neighbor.getZ() + 0.5 + side.getOffsetZ() * 0.45);

            if (me.collective.util.GrimUtils.closestEyeDistanceSqTo(mc.player, hitVec) > 18.0) continue;

            InventoryUtil.swap(hotbarSlot, true);
            if (rotation.getValue().place) {
                float[] yp = lookAt(mc.player.getEyePos(), hitVec);
                rotateTo(yp[0], yp[1]);
            }
            mc.interactionManager.interactBlock(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hitVec, side, neighbor, false));
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(InteractionHand.MAIN_HAND));
            }
            InventoryUtil.swapBack();
            return true;
        }
        return false;
    }

    private boolean breakBlockAt(BlockPos bp) {
        if (mc.player == null || mc.interactionManager == null) return false;
        Direction dir = getInteractDirection(bp);
        if (rotation.getValue().mine) {
            float[] yp = lookAt(bp);
            rotateTo(yp[0], yp[1]);
        }
        boolean started = mc.interactionManager.attackBlock(bp, dir);
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(InteractionHand.MAIN_HAND));
        }
        return started;
    }

    private void sendAction(PlayerActionC2SPacket.Action action, BlockPos bp) {
        if (mc.getNetworkHandler() != null) {
            Direction dir = getInteractDirection(bp);
            int seq = me.collective.util.GrimUtils.nextSequence();
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(action, bp, dir, seq));
        }
    }

    private void tickDoubleMine() {
        if (normalMining != null) {
            if (normalMining.shouldRemove()) {
                sendAction(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, normalMining.blockPos);
                normalMining = null;
            } else if (mc.world.getBlockState(normalMining.blockPos).getBlock() != normalMining.block) {
                normalMining = null;
            } else if (normalMining.isReady()) {
                normalMining.stopDestroying();
            }
        }

        if (packetMining != null) {
            if (packetMining.shouldRemove()) {
                packetMining = null;
            } else if (mc.world.getBlockState(packetMining.blockPos).getBlock() != packetMining.block) {
                packetMining = null;
            }
        }
    }

    private void mine(HighwayBuilderHIG b, MBPIterator it, boolean mineBlocksToPlace, State nextState, State lastState) {
        boolean breaking = false;
        boolean finishedBreaking = false;

        if (b.doubleMine.getValue()) {
            ArrayDeque<BlockPos> toDoubleMine = new ArrayDeque<>();

            it.save();
            it.forEach(pos -> {
                if (b.canBreak(pos.getBlockPos(), pos.getState())
                    && (mineBlocksToPlace || !b.blocksToPlace.getValue().contains(pos.getState().getBlock()))
                    && !b.canInstaBreak(pos.getBlockPos())
                    && (b.normalMining == null || !pos.getBlockPos().equals(b.normalMining.blockPos))
                    && (b.packetMining == null || !pos.getBlockPos().equals(b.packetMining.blockPos))) {
                    toDoubleMine.add(pos.getBlockPos());
                }
            });

            it.restore();

            if (!toDoubleMine.isEmpty()) {
                int slot = b.findAndMoveBestToolToHotbar(b.mc.world.getBlockState(toDoubleMine.peek()), false);
                if (slot == -1) return;

                InventoryUtil.swap(slot);
                doubleMine(b, toDoubleMine);
            }

            if (b.normalMining != null || b.packetMining != null) {
                int slot = b.findAndMoveBestToolToHotbar(
                    b.normalMining != null ? b.normalMining.blockState : b.packetMining.blockState, false);
                if (slot == -1) return;

                InventoryUtil.swap(slot);
                return;
            }
        }

        for (MBlockPos pos : it) {
            if (b.count >= b.blocksPerTick.getIntValue()) return;
            if (b.breakTimer > 0) return;

            BlockState state = pos.getState();
            if (state.isAir() || (b.ignoreSigns.getValue() && state.getBlock() instanceof SignBlock
                && pos.getBlockPos().getY() >= (int) b.mc.player.getY())
                || (!mineBlocksToPlace && b.blocksToPlace.getValue().contains(state.getBlock()))) continue;

            int slot = b.findAndMoveBestToolToHotbar(state, false);
            if (slot == -1) return;

            InventoryUtil.swap(slot);

            BlockPos mcPos = pos.getBlockPos();
            boolean multiBreak = b.blocksPerTick.getIntValue() > 1 && b.canInstaBreak(mcPos) && !b.rotation.getValue().mine;
            if (b.canBreak(mcPos, state)) {
                if (b.rotation.getValue().mine) {
                    float[] yp = b.lookAt(mcPos);
                    b.rotateTo(yp[0], yp[1]);
                }
                b.breakBlockAt(mcPos);
                breaking = true;

                b.breakTimer = b.breakDelay.getIntValue();

                if (!b.lastBreakingPos.equals(pos)) {
                    b.lastBreakingPos.set(pos);
                    b.blocksBroken++;
                }

                b.count++;

                if (!multiBreak) break;
            }

            if (!it.hasNext() && b.canInstaBreak(mcPos)) finishedBreaking = true;
        }

        if (finishedBreaking || !breaking) {
            b.setState(nextState, lastState);
        }
    }

    private void doubleMine(HighwayBuilderHIG b, ArrayDeque<BlockPos> blocks) {
        if (b.breakTimer > 0) return;

        if (b.normalMining == null) {
            DoubleMineBlock block = new DoubleMineBlock(b, blocks.pop());
            b.normalMining = block.startDestroying();

            b.breakTimer = b.breakDelay.getIntValue();
            if (b.breakTimer > 0) return;
        }

        if (DoubleMineBlock.rateLimited) return;

        if (b.packetMining == null && !blocks.isEmpty()) {
            DoubleMineBlock block = new DoubleMineBlock(b, blocks.pop());

            if (block != null) {
                b.packetMining = b.normalMining.packetMine();
                b.normalMining = block.startDestroying();

                b.breakTimer = b.breakDelay.getIntValue();
            }
        }
    }

    private void place(HighwayBuilderHIG b, MBPIterator it, int slot, State nextState) {
        boolean placed = false;
        boolean finishedPlacing = false;

        for (MBlockPos pos : it) {
            if (b.count >= it.placementsPerTick(b)) return;
            if (b.placeTimer > 0) return;

            if (distToCenterSq(pos.getBlockPos(), b.mc.player.getEyePos()) > b.placeRange.getValue() * b.placeRange.getValue())
                continue;

            if (b.placeBlock(pos.getBlockPos(), slot)) {
                placed = true;
                b.blocksPlaced++;
                b.placedFx.put(pos.getBlockPos(), System.currentTimeMillis());
                b.placeTimer = b.placeDelay.getIntValue();

                b.count++;
                if (b.placementsPerTick.getIntValue() == 1) break;
            }

            if (!it.hasNext()) finishedPlacing = true;
        }

        if (finishedPlacing || !placed) b.setState(nextState);
    }

    private int findSlot(Predicate<ItemStack> predicate, boolean hotbar) {
        for (int i = hotbar ? 0 : 9; i < (hotbar ? 9 : 36); i++) {
            if (predicate.test(mc.player.getInventory().getStack(i))) return i;
        }

        return -1;
    }

    private int findHotbarSlot(boolean replaceTools) {
        int thrashSlot = -1;
        int slotsWithBlocks = 0;
        int slotWithLeastBlocks = -1;
        int slotWithLeastBlocksCount = Integer.MAX_VALUE;

        for (int i = 0; i < 9; i++) {
            ItemStack itemStack = mc.player.getInventory().getStack(i);

            if (itemStack.isEmpty()) return i;

            if (replaceTools && isToolLike(itemStack)) return i;

            if (trashItems.getValue().contains(itemStack.getItem())) thrashSlot = i;

            if (itemStack.getItem() instanceof BlockItem blockItem && (blocksToPlace.getValue().contains(blockItem.getBlock()) || blocksToPlace.getValue().contains(Blocks.OBSIDIAN) && blockItem == Items.ENDER_CHEST)) {
                slotsWithBlocks++;

                if (itemStack.getCount() < slotWithLeastBlocksCount) {
                    slotWithLeastBlocksCount = itemStack.getCount();
                    slotWithLeastBlocks = i;
                }
            }
        }

        if (thrashSlot != -1) return thrashSlot;

        if (slotsWithBlocks > 0) return slotWithLeastBlocks;

        error("No empty space in hotbar.");
        return -1;
    }

    private boolean hasItem(Predicate<ItemStack> predicate) {
        for (int i = 0; i < 36; i++) {
            if (predicate.test(mc.player.getInventory().getStack(i))) return true;
        }

        return false;
    }

    private int countItem(Predicate<ItemStack> predicate) {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (predicate.test(stack)) count += stack.getCount();
        }

        return count;
    }

    private int findAndMoveToHotbar(Predicate<ItemStack> predicate) {
        int slot = findSlot(predicate, true);
        if (slot != -1) return slot;

        int hotbarSlot = findHotbarSlot(false);
        if (hotbarSlot == -1) return -1;

        slot = findSlot(predicate, false);
        if (slot == -1) return -1;

        movePairToHotbar(slot, hotbarSlot);

        return hotbarSlot;
    }

    private int findAndMoveBestToolToHotbar(BlockState blockState, boolean noSilkTouch) {
        if (mc.player.isCreative()) return mc.player.getInventory().selectedSlot;

        double bestScore = -1;
        int bestSlot = -1;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            double score = toolScore(stack, blockState, noSilkTouch);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }

        if (bestSlot == -1) return mc.player.getInventory().selectedSlot;

        ItemStack bestStack = mc.player.getInventory().getStack(bestSlot);
        if (bestStack.isIn(ItemTags.PICKAXES)) {
            int count = countItem(stack -> stack.isIn(ItemTags.PICKAXES));

            if (count <= savePickaxes.getIntValue() && !(restockTask.pickaxes && bestStack.getMaxDamage() - bestStack.getDamage() > (bestStack.getMaxDamage() * (breakDurability.getValue() / 100.0)))) {
                if (!restockTask.pickaxes && (searchEnderChest.getValue() || searchShulkers.getValue())) {
                    restockTask.setPickaxes();
                } else {
                    error("Found less than the minimum amount of pickaxes required: " + count + "/" + (savePickaxes.getIntValue() + 1));
                }

                return -1;
            }
        }

        if (bestSlot < 9) return bestSlot;

        int hotbarSlot = findHotbarSlot(true);
        if (hotbarSlot == -1) return -1;

        movePairToHotbar(bestSlot, hotbarSlot);

        return hotbarSlot;
    }

    private double toolScore(ItemStack stack, BlockState state, boolean noSilkTouch) {
        if (stack.isEmpty() || !stack.isDamageable()) return -1;
        if (noSilkTouch && hasEnchantment(stack, Enchantments.SILK_TOUCH)) return -1;
        if (dontBreakTools.getValue()
            && stack.getMaxDamage() - stack.getDamage() <= stack.getMaxDamage() * (breakDurability.getValue() / 100.0)) {
            return -1;
        }
        double score = 0;
        if (stack.isSuitableFor(state)) score += 100;
        score += effLevel(stack) * 10;
        score += stack.getMaxDamage() / 128.0;
        return score;
    }

    private boolean hasEnchantment(ItemStack stack, net.minecraft.registry.RegistryKey<net.minecraft.enchantment.Enchantment> key) {
        try {
            RegistryEntry<net.minecraft.enchantment.Enchantment> entry = mc.world.getRegistryManager()
                .getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(key);
            return stack.getEnchantments().getEnchantments().stream().anyMatch(e -> e.equals(entry));
        } catch (Exception ignored) {
            return false;
        }
    }

    private int effLevel(ItemStack stack) {
        try {
            RegistryEntry<net.minecraft.enchantment.Enchantment> entry = mc.world.getRegistryManager()
                .getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY);
            return stack.getEnchantments().getLevel(entry);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static boolean isShulker(Item item) {
        return item instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    private static boolean isToolLike(ItemStack stack) {
        return !stack.isEmpty() && stack.isDamageable() && !(stack.getItem() instanceof BlockItem);
    }

    private int findBlocksToPlace() {
        int slot = findAndMoveToHotbar(stack -> stack.getItem() instanceof BlockItem blockItem && blocksToPlace.getValue().contains(blockItem.getBlock()));

        if (slot == -1) {
            if (mineEnderChests.getValue() && blocksToPlace.getValue().contains(Blocks.OBSIDIAN) && countItem(stack -> stack.getItem() == Items.ENDER_CHEST) > saveEchests.getIntValue()) {
                setState(State.MineEnderChests);
            } else if (searchEnderChest.getValue() || searchShulkers.getValue()) {
                restockTask.setMaterials();
            } else {
                error("Out of blocks to place.");
            }

            return -1;
        }

        return slot;
    }

    private int findBlocksToPlacePrioritizeTrash() {
        int slot = findAndMoveToHotbar(stack -> {
            if (!(stack.getItem() instanceof BlockItem)) return false;
            return trashItems.getValue().contains(stack.getItem());
        });

        return slot != -1 ? slot : findBlocksToPlace();
    }

    /** PICKUP-пара: слот инвентаря -> слот хотбара с возвратом вытесненного предмета в исходный слот. */
    private void movePairToHotbar(int fromInv, int toHotbar) {
        if (mc.player == null || mc.interactionManager == null) return;
        int from = fromInv < 9 ? 36 + fromInv : fromInv;
        int to = 36 + toHotbar;
        if (from == to) return;
        int syncId = mc.player.currentScreenHandler.syncId;
        mc.interactionManager.clickSlot(syncId, from, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, to, 0, SlotActionType.PICKUP, mc.player);
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            mc.interactionManager.clickSlot(syncId, from, 0, SlotActionType.PICKUP, mc.player);
        }
    }

    private void quickMove(int containerSlot) {
        if (mc.player == null || mc.interactionManager == null) return;
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId,
            containerSlot, 0, SlotActionType.QUICK_MOVE, mc.player);
    }

    private void dropSlot(int invSlot) {
        if (mc.player == null || mc.interactionManager == null) return;
        int slot = invSlot < 9 ? 36 + invSlot : invSlot;
        int syncId = mc.player.currentScreenHandler.syncId;
        mc.interactionManager.clickSlot(syncId, slot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, -999, 0, SlotActionType.PICKUP, mc.player);
    }

    private void dropCursor() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (mc.player.currentScreenHandler.getCursorStack().isEmpty()) return;
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId,
            -999, 0, SlotActionType.PICKUP, mc.player);
    }

    private EndCrystalEntity nearestCrystal() {
        if (mc.player == null || mc.world == null) return null;
        EndCrystalEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity crystal)) continue;
            double d = crystal.squaredDistanceTo(mc.player);
            if (d > 12 * 12 || d < 24 * 24) continue;
            if (ignoreCrystals.contains(crystal)) continue;
            Vec3 vec1 = mc.player.getEyePos();
            Vec3 vec2 = new Vec3(entity.getX(), entity.getY() + 0.5, entity.getZ());
            if (mc.world.raycast(new RaycastContext(vec1, vec2,
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player)).getType() != HitResult.Type.MISS) {
                continue;
            }
            if (d < bestDist) {
                bestDist = d;
                best = crystal;
            }
        }
        return best;
    }

    /** Квадрат дистанции от центра блока до глаз (без аллокаций). */
    private double distToCenterSq(BlockPos pos, Vec3 eye) {
        double dx = pos.getX() + 0.5 - eye.x;
        double dy = pos.getY() + 0.5 - eye.y;
        double dz = pos.getZ() + 0.5 - eye.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private enum State {
        Center {
            @Override
            protected void start(HighwayBuilderHIG b) {
                if (b.mc.player.getEntityPos().distanceTo(
                    new Vec3(Math.floor(b.mc.player.getX()) + 0.5, b.mc.player.getY(), Math.floor(b.mc.player.getZ()) + 0.5)) < 0.1) {
                    stop(b);
                }
            }

            @Override
            protected void tick(HighwayBuilderHIG b) {
                double x = Math.abs(b.mc.player.getX() - (int) b.mc.player.getX()) - 0.5;
                double z = Math.abs(b.mc.player.getZ() - (int) b.mc.player.getZ()) - 0.5;

                boolean isX = Math.abs(x) <= 0.1;
                boolean isZ = Math.abs(z) <= 0.1;

                if (isX && isZ) {
                    stop(b);
                } else {
                    b.rotateTo(0, b.mc.player.getPitch());

                    if (!isZ) {
                        boolean fwd = z < 0;
                        boolean back = z > 0;
                        if (b.mc.player.getZ() < 0) {
                            boolean t = fwd;
                            fwd = back;
                            back = t;
                        }
                        b.inFwd = fwd;
                        b.inBack = back;
                    }

                    if (!isX) {
                        boolean right = x > 0;
                        boolean left = x < 0;
                        if (b.mc.player.getX() < 0) {
                            boolean t = right;
                            right = left;
                            left = t;
                        }
                        b.inRight = right;
                        b.inLeft = left;
                    }

                    b.inSneak = true;
                }
            }

            private void stop(HighwayBuilderHIG b) {
                b.stopInput();
                b.mc.player.setVelocity(Vec3.ZERO);
                b.mc.player.setPosition((int) b.mc.player.getX() + (b.mc.player.getX() < 0 ? -0.5 : 0.5),
                    b.mc.player.getY(), (int) b.mc.player.getZ() + (b.mc.player.getZ() < 0 ? -0.5 : 0.5));
                b.setState(b.lastState);
            }
        },

        Forward {
            @Override
            protected void start(HighwayBuilderHIG b) {
                checkTasks(b);

                if (b.state == Forward) b.rotateTo(b.dir.yaw, b.mc.player.getPitch());
            }

            @Override
            protected void tick(HighwayBuilderHIG b) {
                checkTasks(b);

                if (b.state == Forward) b.inFwd = true;
            }

            private void checkTasks(HighwayBuilderHIG b) {
                if (b.destroyCrystalTraps.getValue() && isCrystalTrap(b)) b.setState(DefuseCrystalTraps);
                else if (needsToPlace(b, b.blockPosProvider.getLiquids(), true)) b.setState(FillLiquids);
                else if (needsToMine(b, b.blockPosProvider.getFront(), true)) b.setState(MineFront);
                else if (b.floor.getValue() == Floor.Replace && needsToMine(b, b.blockPosProvider.getFloor(), false)) b.setState(MineFloor);
                else if (b.railings.getValue() && needsToMine(b, b.blockPosProvider.getRailings(0), false)) b.setState(MineRailings);
                else if (b.mineAboveRailings.getValue() && needsToMine(b, b.blockPosProvider.getRailings(1), true)) b.setState(MineAboveRailings);
                else if (b.railings.getValue() && needsToPlace(b, b.blockPosProvider.getRailings(0), false)) {
                    if (b.cornerBlock.getValue() && needsToPlace(b, b.blockPosProvider.getRailings(-1), false)) b.setState(PlaceCornerBlock);
                    else b.setState(PlaceRailings);
                }
                else if (needsToPlace(b, b.blockPosProvider.getFloor(), false)) b.setState(PlaceFloor);
            }

            private boolean needsToMine(HighwayBuilderHIG b, HighwayBuilderHIG.MBPIterator it, boolean mineBlocksToPlace) {
                for (MBlockPos pos : it) {
                    if (b.canMine(pos, mineBlocksToPlace)) return true;
                }
                return false;
            }

            private boolean needsToPlace(HighwayBuilderHIG b, HighwayBuilderHIG.MBPIterator it, boolean liquids) {
                for (MBlockPos pos : it) {
                    if (b.canPlace(pos, liquids)) return true;
                }
                return false;
            }

            private boolean isCrystalTrap(HighwayBuilderHIG b) {
                for (Entity entity : b.mc.world.getEntities()) {
                    if (!(entity instanceof EndCrystalEntity endCrystal)) continue;
                    double d = endCrystal.squaredDistanceTo(b.mc.player);
                    if (d > 12 * 12 || d < 24 * 24) continue;
                    if (b.ignoreCrystals.contains(endCrystal)) continue;

                    Vec3 vec1 = b.mc.player.getEyePos();
                    Vec3 vec2 = new Vec3(entity.getX(), entity.getY() + 0.5, entity.getZ());
                    if (b.mc.world.raycast(new RaycastContext(vec1, vec2,
                        RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, b.mc.player)).getType() == HitResult.Type.MISS) {
                        return true;
                    }
                }
                return false;
            }
        },

        ReLevel {
            private BlockPos pos = new BlockPos(0, 0, 0);
            private BlockPos startPos = new BlockPos(0, 0, 0);
            private int timer = 30;

            @Override
            protected void start(HighwayBuilderHIG b) {
                startPos = new BlockPos((int) Math.floor(b.start.x), (int) Math.floor(b.start.y), (int) Math.floor(b.start.z));
            }

            @Override
            protected void tick(HighwayBuilderHIG b) {
                Vec3 vec = b.mc.player.getEntityPos().add(b.mc.player.getVelocity()).add(0, -0.75, 0);
                pos = new BlockPos(b.mc.player.getBlockX(), (int) Math.floor(vec.y), b.mc.player.getBlockZ());

                if (pos.getY() >= b.mc.player.getBlockPos().getY()) {
                    pos = new BlockPos(pos.getX(), b.mc.player.getBlockPos().getY() - 1, pos.getZ());
                }

                if (pos.getY() >= startPos.getY()) pos = new BlockPos(pos.getX(), startPos.getY() - 1, pos.getZ());

                if (b.mc.player.getY() > b.start.y - 0.5 && !b.mc.world.getBlockState(pos).isReplaceable()) {
                    b.inJump = false;

                    if (timer > 0) timer--;
                    else {
                        b.setState(Forward);
                        timer = 30;
                    }

                    return;
                }

                if (b.placeTimer > 0) return;

                if (timer < 30) timer = 30;
                b.inJump = true;

                int slot = -1;
                if (pos.getY() == startPos.down().getY()) {
                    slot = b.findAndMoveToHotbar(stack -> stack.getItem() instanceof BlockItem blockItem
                        && b.blocksToPlace.getValue().contains(blockItem.getBlock()));
                }

                if (slot == -1) {
                    slot = findAcceptablePlacementBlock(b);
                    if (slot == -1) return;
                }

                if (b.placeBlock(pos, slot)) {
                    b.placedFx.put(pos.toImmutable(), System.currentTimeMillis());
                    b.placeTimer = b.placeDelay.getIntValue();
                }
            }

            private int findAcceptablePlacementBlock(HighwayBuilderHIG b) {
                int slot = b.findAndMoveToHotbar(stack -> {
                    if (!(stack.getItem() instanceof BlockItem)) return false;
                    return b.trashItems.getValue().contains(stack.getItem());
                });

                if (slot == -1) slot = b.findAndMoveToHotbar(stack -> {
                    if (!(stack.getItem() instanceof BlockItem bi)) return false;
                    return b.blocksToPlace.getValue().contains(bi.getBlock());
                });

                return slot != -1 ? slot : b.findAndMoveToHotbar(stack -> {
                    if (!(stack.getItem() instanceof BlockItem bi)) return false;
                    if (isShulker(stack.getItem())) return false;
                    Block block = bi.getBlock();

                    if (!block.getDefaultState().isSolidBlock(b.mc.world, pos)) return false;
                    return true;
                });
            }
        },

        FillLiquids {
            @Override
            protected void tick(HighwayBuilderHIG b) {
                int slot = b.findBlocksToPlacePrioritizeTrash();
                if (slot == -1) return;

                b.place(b, new MBPIteratorFilter(b.blockPosProvider.getLiquids(),
                    mpos -> !mpos.getState().getFluidState().isEmpty()), slot, Forward);
            }
        },

        MineFront {
            @Override
            protected void start(HighwayBuilderHIG b) {
                b.mine(b, b.blockPosProvider.getFront(), true, Forward, this);
            }

            @Override
            protected void tick(HighwayBuilderHIG b) {
                b.mine(b, b.blockPosProvider.getFront(), true, Forward, this);
            }
        },

        MineFloor {
            @Override
            protected void start(HighwayBuilderHIG b) {
                b.mine(b, b.blockPosProvider.getFloor(), false, Forward, this);
            }

            @Override
            protected void tick(HighwayBuilderHIG b) {
                b.mine(b, b.blockPosProvider.getFloor(), false, Forward, this);
            }
        },

        MineRailings {
            @Override
            protected void start(HighwayBuilderHIG b) {
                b.mine(b, b.blockPosProvider.getRailings(0), false, Forward, this);
            }

            @Override
            protected void tick(HighwayBuilderHIG b) {
                b.mine(b, b.blockPosProvider.getRailings(0), false, Forward, this);
            }
        },

        MineAboveRailings {
            @Override
            protected void start(HighwayBuilderHIG b) {
                b.mine(b, b.blockPosProvider.getRailings(1), true, Forward, this);
            }

            @Override
            protected void tick(HighwayBuilderHIG b) {
                b.mine(b, b.blockPosProvider.getRailings(1), true, Forward, this);
            }
        },

        PlaceCornerBlock {
            @Override
            protected void start(HighwayBuilderHIG b) {
                int slot = b.findBlocksToPlacePrioritizeTrash();
                if (slot == -1) return;

                b.place(b, b.blockPosProvider.getRailings(-1), slot, Forward);
            }

            @Override
            protected void tick(HighwayBuilderHIG b) {
                int slot = b.findBlocksToPlacePrioritizeTrash();
                if (slot == -1) return;

                b.place(b, b.blockPosProvider.getRailings(-1), slot, Forward);
            }
        },

        PlaceRailings {
            @Override
            protected void start(HighwayBuilderHIG b) {
                int slot = b.findBlocksToPlace();
                if (slot == -1) return;

                b.place(b, b.blockPosProvider.getRailings(0), slot, Forward);
            }

            @Override
            protected void tick(HighwayBuilderHIG b) {
                int slot = b.findBlocksToPlace();
                if (slot == -1) return;

                b.place(b, b.blockPosProvider.getRailings(0), slot, Forward);
            }
        },

        PlaceFloor {
            @Override
            protected void start(HighwayBuilderHIG b) {
                int slot = b.findBlocksToPlace();
                if (slot == -1) return;

                b.place(b, b.blockPosProvider.getFloor(), slot, Forward);
            }

            @Override
            protected void tick(HighwayBuilderHIG b) {
                int slot = b.findBlocksToPlace();
                if (slot == -1) return;

                b.place(b, b.blockPosProvider.getFloor(), slot, Forward);
            }
        },

        ThrowOutTrash {
            private int skipSlot;
            private boolean timerEnabled;
            private boolean firstTick;
            private boolean threwItems;
            private int timer;

            @Override
            protected void start(HighwayBuilderHIG b) {
                int biggestCount = 0;

                for (int i = 0; i < 36; i++) {
                    ItemStack itemStack = b.mc.player.getInventory().getStack(i);

                    if (itemStack.getItem() instanceof BlockItem && b.trashItems.getValue().contains(itemStack.getItem()) && itemStack.getCount() > biggestCount) {
                        biggestCount = itemStack.getCount();
                        skipSlot = i;

                        if (biggestCount >= 64) break;
                    }
                }

                if (biggestCount == 0) skipSlot = -1;
                timerEnabled = false;
                firstTick = true;
                threwItems = false;
            }

            @Override
            protected void tick(HighwayBuilderHIG b) {
                if (timerEnabled) {
                    if (timer > 0) timer--;
                    else b.setState(b.lastState);

                    return;
                }

                b.rotateTo(b.dir.yaw, -25);

                if (firstTick) {
                    firstTick = false;
                    return;
                }

                if (!b.mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                    b.dropCursor();
                    return;
                }

                for (int i = 0; i < 36; i++) {
                    if (i == skipSlot) continue;

                    ItemStack itemStack = b.mc.player.getInventory().getStack(i);

                    if (b.trashItems.getValue().contains(itemStack.getItem())) {
                        b.dropSlot(i);
                        threwItems = true;
                        return;
                    }

                    if (b.ejectUselessShulkers.getValue() && isShulker(itemStack.getItem())) {
                        List<ItemStack> contents = ShulkerDataParser.parseShulkerContentsAsList(itemStack);
                        boolean eject = true;
                        for (ItemStack stack : contents) {
                            if (stack.getItem() instanceof BlockItem bi && (b.blocksToPlace.getValue().contains(bi.getBlock()) || (b.blocksToPlace.getValue().contains(Blocks.OBSIDIAN) && bi == Items.ENDER_CHEST))) {
                                eject = false;
                                break;
                            }
                            if (stack.isIn(ItemTags.PICKAXES)) {
                                eject = false;
                                break;
                            }
                            if (stack.get(DataComponentTypes.FOOD) != null) {
                                eject = false;
                                break;
                            }
                        }

                        if (eject) {
                            b.dropSlot(i);
                            threwItems = true;
                            return;
                        }
                    }
                }

                timerEnabled = true;
                timer = threwItems ? 10 : 1;
            }
        },

        PlaceEChestBlockade {
            @Override
            protected void tick(HighwayBuilderHIG b) {
                int slot = b.findBlocksToPlacePrioritizeTrash();
                if (slot == -1) return;

                b.place(b, b.blockPosProvider.getBlockade(false, b.blockadeType.getValue()), slot, MineEnderChests);
            }
        },

        MineEChestBlockade {
            @Override
            protected void tick(HighwayBuilderHIG b) {
                b.mine(b, b.blockPosProvider.getBlockade(true, b.blockadeType.getValue()), true, Center, Forward);
            }
        },

        MineEnderChests {
            private final MBlockPos pos = new MBlockPos();
            private int minimumObsidian;
            private boolean first;
            private boolean primed;
            private boolean stopTimerEnabled;
            private int stopTimer;
            private int moveTimer;
            private int rebreakTimer;
            private int timeout;

            @Override
            protected void start(HighwayBuilderHIG b) {
                if (b.lastState != Center && b.lastState != ThrowOutTrash && b.lastState != PlaceEChestBlockade) {
                    b.setState(Center);
                    return;
                } else if (b.lastState == Center) {
                    b.setState(ThrowOutTrash);
                    return;
                } else if (b.lastState == ThrowOutTrash) {
                    b.setState(PlaceEChestBlockade);
                    return;
                }

                int emptySlots = 0;
                for (int i = 0; i < 36; i++) {
                    if (b.mc.player.getInventory().getStack(i).isEmpty()) emptySlots++;
                }

                if (emptySlots == 0) {
                    b.error("No empty slots.");
                    return;
                }

                int minimumSlots = Math.max(emptySlots - b.minEmpty.getIntValue(), 1);
                minimumObsidian = minimumSlots * 64;
                first = true;
                moveTimer = timeout = 0;

                stopTimerEnabled = false;
                primed = false;
            }

            @Override
            protected void tick(HighwayBuilderHIG b) {
                if (stopTimerEnabled) {
                    if (stopTimer > 0) stopTimer--;
                    else b.setState(MineEChestBlockade);

                    return;
                }

                HorizontalDirection dir = b.dir.diagonal ? b.dir.rotateLeft().rotateLeftSkipOne() : b.dir.opposite();
                pos.set(b.mc.player).offset(dir);

                if (moveTimer > 0) {
                    b.rotateTo(dir.yaw, b.mc.player.getPitch());
                    b.inFwd = moveTimer > 2;

                    moveTimer--;
                    return;
                }

                int obsidianCount = 0;

                for (Entity entity : b.mc.world.getOtherEntities(b.mc.player,
                    new AABB(pos.x, pos.y, pos.z, pos.x + 1, pos.y + 2, pos.z + 1))) {
                    if (entity instanceof ItemEntity itemEntity && itemEntity.getStack().getItem() == Items.OBSIDIAN) {
                        obsidianCount += itemEntity.getStack().getCount();
                    }
                }

                for (int i = 0; i < 36; i++) {
                    ItemStack itemStack = b.mc.player.getInventory().getStack(i);
                    if (itemStack.getItem() == Items.OBSIDIAN) obsidianCount += itemStack.getCount();
                }

                if (obsidianCount >= minimumObsidian) {
                    stopTimerEnabled = true;
                    stopTimer = 12;
                    return;
                }

                BlockPos bp = pos.getBlockPos();
                BlockState blockState = b.mc.world.getBlockState(bp);

                if (blockState.getBlock() == Blocks.ENDER_CHEST) {
                    if (b.mc.currentScreen instanceof GenericContainerScreen screen) {
                        if (screen.getScreenHandler().syncId != b.containerId) return;

                        b.mc.setScreen(null);
                    }

                    if (!EChestMemory.isKnown()) {
                        Direction interactDir = getInteractDirection(bp);
                        Vec3 hitVec = new Vec3(bp.getX() + 0.5 + interactDir.getOffsetX() * 0.45,
                            bp.getY() + 0.5 + interactDir.getOffsetY() * 0.45,
                            bp.getZ() + 0.5 + interactDir.getOffsetZ() * 0.45);
                        if (b.rotation.getValue().place) {
                            float[] yp = b.lookAt(b.mc.player.getEyePos(), hitVec);
                            b.rotateTo(yp[0], yp[1]);
                        }
                        b.mc.interactionManager.interactBlock(b.mc.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(hitVec, interactDir, bp, false));
                        return;
                    }

                    if (first) {
                        moveTimer = 8;
                        first = false;
                        return;
                    }

                    int slot = b.findAndMoveBestToolToHotbar(blockState, true);
                    if (slot == -1) {
                        b.error("Cannot find pickaxe without silk touch to mine ender chests.");
                        return;
                    }

                    InventoryUtil.swap(slot);

                    if (b.rebreakEchests.getValue() && primed) {
                        timeout++;
                        if (timeout > 60) {
                            primed = false;
                            timeout = 0;
                            return;
                        }

                        if (rebreakTimer > 0) {
                            rebreakTimer--;
                            return;
                        }

                        rebreakTimer = b.rebreakTimer.getIntValue();

                        if (b.rotation.getValue().mine) {
                            float[] yp = b.lookAt(bp);
                            b.rotateTo(yp[0], yp[1]);
                        }
                        b.sendAction(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, bp);
                    } else {
                        if (b.rotation.getValue().mine) {
                            float[] yp = b.lookAt(bp);
                            b.rotateTo(yp[0], yp[1]);
                        }
                        b.breakBlockAt(bp);
                    }
                } else {
                    int slot = b.findAndMoveToHotbar(stack -> stack.getItem() == Items.ENDER_CHEST);
                    if (slot == -1 || b.countItem(stack -> stack.getItem() == Items.ENDER_CHEST) <= b.saveEchests.getIntValue()) {
                        stopTimerEnabled = true;
                        stopTimer = 12;
                        return;
                    }

                    if (b.countItem(stack -> stack.isIn(ItemTags.PICKAXES)) <= b.savePickaxes.getIntValue()) {
                        if (b.searchEnderChest.getValue() || b.searchShulkers.getValue()) {
                            b.restockTask.setPickaxes();
                        }
                    }

                    if (!first) primed = true;

                    Direction placeDir = getInteractDirection(bp);
                    Vec3 hitVec = new Vec3(bp.getX() + 0.5 + placeDir.getOffsetX() * 0.45,
                        bp.getY() + 0.5 + placeDir.getOffsetY() * 0.45,
                        bp.getZ() + 0.5 + placeDir.getOffsetZ() * 0.45);
                    BlockHitResult hit = new BlockHitResult(hitVec, placeDir, bp, false);
                    InventoryUtil.swap(slot);
                    if (b.rotation.getValue().place) {
                        float[] yp = b.lookAt(b.mc.player.getEyePos(), hitVec);
                        b.rotateTo(yp[0], yp[1]);
                    }
                    b.mc.interactionManager.interactBlock(b.mc.player, InteractionHand.MAIN_HAND, hit);
                    b.mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(InteractionHand.MAIN_HAND));
                    InventoryUtil.swapBack();
                    timeout = 0;
                }
            }
        },

        Restock {
            private final MBlockPos pos = new MBlockPos();
            private int minimumSlots;
            private int stopTimer;
            private int delayTimer;
            private boolean breakContainer;
            private boolean indicateStopping;
            private Predicate<ItemStack> shulkerPredicate;
            private int slot = -1;

            @Override
            protected void start(HighwayBuilderHIG b) {
                slot = -1;

                if (shulkerPredicate == null) setShulkerPredicate(b);

                if (b.restockTask.tasksInactive()) {
                    b.setState(Forward);
                    return;
                }

                if (b.lastState != Center && b.lastState != ThrowOutTrash && b.lastState != PlaceShulkerBlockade && b.lastState != this) {
                    b.setState(Center);
                    return;
                } else if (b.lastState == Center) {
                    b.setState(ThrowOutTrash);
                    return;
                }

                if (slot == -1 && b.searchShulkers.getValue()) {
                    slot = b.findAndMoveToHotbar(shulkerPredicate);

                    if (slot != -1 && b.lastState != PlaceShulkerBlockade) {
                        b.setState(PlaceShulkerBlockade);
                    }
                }

                if (slot == -1 && b.searchEnderChest.getValue() && b.countItem(stack -> stack.getItem() == Items.ENDER_CHEST) > 0) {
                    boolean stop = EChestMemory.isKnown();
                    if (EChestMemory.isKnown()) {
                        for (ItemStack stack : EChestMemory.ITEMS) {
                            if (b.restockTask.materials && stack.getItem() instanceof BlockItem bi) {
                                if (b.blocksToPlace.getValue().contains(bi.getBlock()) || (b.blocksToPlace.getValue().contains(Blocks.OBSIDIAN) && bi == Items.ENDER_CHEST)) {
                                    stop = false;
                                    break;
                                }
                            }
                            if (b.restockTask.pickaxes && stack.isIn(ItemTags.PICKAXES)) {
                                stop = false;
                                break;
                            }
                            if (b.restockTask.food && stack.get(DataComponentTypes.FOOD) != null) {
                                stop = false;
                                break;
                            }

                            if (b.searchShulkers.getValue() && shulkerPredicate.test(stack)) {
                                stop = false;
                                break;
                            }
                        }
                    }

                    if (!stop) slot = b.findAndMoveToHotbar(stack -> stack.getItem() == Items.ENDER_CHEST);
                }

                if (slot == -1) {
                    boolean restockOccurred = (
                        (b.restockTask.materials && (b.hasItem(stack -> stack.getItem() instanceof BlockItem bi && b.blocksToPlace.getValue().contains(bi.getBlock())) || b.blocksToPlace.getValue().contains(Blocks.OBSIDIAN) && b.countItem(stack -> stack.getItem() == Items.ENDER_CHEST) > b.saveEchests.getIntValue())) ||
                            (b.restockTask.pickaxes && b.countItem(stack -> stack.isIn(ItemTags.PICKAXES)) > b.savePickaxes.getIntValue()) ||
                            (b.restockTask.food && b.hasItem(stack -> stack.get(DataComponentTypes.FOOD) != null))
                    );

                    if (restockOccurred) {
                        b.setState(ThrowOutTrash, Forward);
                    } else b.error("Unable to perform restock for '" + b.restockTask.item() + "'.");

                    return;
                }

                int restockSlots = -b.minEmpty.getIntValue();
                for (int i = 0; i < 36; i++) {
                    if (b.mc.player.getInventory().getStack(i).isEmpty()) restockSlots++;
                }

                if (restockSlots <= 0) {
                    b.error("No empty slots for restocking items.");
                    return;
                }

                minimumSlots = b.restockTask.materials ? restockSlots : 1;

                HorizontalDirection dir = b.dir.diagonal ? b.dir.rotateLeft().rotateLeftSkipOne() : b.dir.opposite();
                pos.set(b.mc.player).offset(dir);

                breakContainer = b.mc.world.getBlockState(pos.getBlockPos()).getBlock() == Blocks.ENDER_CHEST;

                indicateStopping = false;
                delayTimer = b.inventoryDelay.getIntValue();
            }

            @Override
            protected void tick(HighwayBuilderHIG b) {
                if (slot == -1) {
                    b.error("Invalid restocking action.");
                    return;
                }

                if (indicateStopping && !breakContainer) {
                    if (stopTimer > 0) stopTimer--;
                    else {
                        if (b.lastState == PlaceShulkerBlockade) {
                            b.setState(MineShulkerBlockade);
                        } else {
                            b.setState(ThrowOutTrash, Forward);
                        }
                    }

                    return;
                }

                if (b.restockTask.tasksInactive()) {
                    b.setState(Forward);
                    return;
                }

                if (delayTimer > 0) {
                    delayTimer--;
                    return;
                }

                int slotsPulled = 0;
                if (b.restockTask.materials) {
                    slotsPulled += countSlots(stack -> stack.getItem() instanceof BlockItem bi && b.blocksToPlace.getValue().contains(bi.getBlock()));
                    if (b.blocksToPlace.getValue().contains(Blocks.OBSIDIAN))
                        slotsPulled += ((b.countItem(stack -> stack.getItem() == Items.ENDER_CHEST) - b.saveEchests.getIntValue()) * 8) / 64;
                }
                if (b.restockTask.pickaxes)
                    slotsPulled += countSlots(stack -> stack.isIn(ItemTags.PICKAXES)) - b.savePickaxes.getIntValue();
                if (b.restockTask.food)
                    slotsPulled += countSlots(stack -> stack.get(DataComponentTypes.FOOD) != null);

                if (slotsPulled >= minimumSlots && !indicateStopping) {
                    indicateStopping = true;
                    breakContainer = true;
                    stopTimer = 12;
                    if (b.mc.currentScreen != null) b.mc.setScreen(null);
                    return;
                }

                BlockPos blockPos = pos.getBlockPos();
                BlockState blockState = b.mc.world.getBlockState(blockPos);

                switch (blockState.getBlock()) {
                    case ShulkerBoxBlock shulker -> {
                        if (b.mc.currentScreen instanceof ShulkerBoxScreen screen) {
                            if (screen.getScreenHandler().syncId != b.containerId) return;

                            if (restockItems(b, screen.getScreenHandler(), 27)) {
                                delayTimer = b.inventoryDelay.getIntValue();
                                return;
                            }

                            b.mc.setScreen(null);
                            breakContainer = true;
                        } else {
                            if (!b.searchShulkers.getValue()) breakContainer = true;
                            handleContainerBlock(b, blockPos);
                        }
                    }

                    case EnderChestBlock echest -> {
                        if (b.mc.currentScreen instanceof GenericContainerScreen screen) {
                            if (screen.getScreenHandler().syncId != b.containerId) return;

                            EChestMemory.updateFrom(screen.getScreenHandler());

                            if (restockItems(b, screen.getScreenHandler(), screen.getScreenHandler() instanceof GenericContainerScreenHandler handler ? handler.getRows() * 9 : 27)) {
                                delayTimer = b.inventoryDelay.getIntValue();
                                return;
                            }

                            if (b.searchShulkers.getValue()) {
                                ScreenHandler menu = screen.getScreenHandler();
                                for (int i = 0; i < 27 && i < menu.slots.size(); i++) {
                                    if (shulkerPredicate.test(menu.slots.get(i).getStack())) {
                                        b.quickMove(i);
                                        delayTimer = b.inventoryDelay.getIntValue();
                                        break;
                                    }
                                }
                            }

                            b.mc.setScreen(null);
                            breakContainer = true;
                        } else {
                            if (!b.searchEnderChest.getValue()) breakContainer = true;
                            handleContainerBlock(b, blockPos);
                        }
                    }

                    case AirBlock air -> {
                        if (breakContainer) {
                            breakContainer = false;

                            if (indicateStopping) b.restockTask.complete();
                            else start(b);

                            return;
                        }

                        b.placeBlock(blockPos, slot);
                    }

                    default -> b.error("Invalid block at container restocking position?");
                }
            }

            private boolean restockItems(HighwayBuilderHIG b, ScreenHandler menu, int containerSize) {
                if (b.restockTask.materials) {
                    if (grabFromInventory(b, menu, containerSize, stack -> stack.getItem() instanceof BlockItem bi && b.blocksToPlace.getValue().contains(bi.getBlock())))
                        return true;

                    if (b.blocksToPlace.getValue().contains(Blocks.OBSIDIAN)) {
                        if (grabFromInventory(b, menu, containerSize, stack -> stack.getItem() == Items.ENDER_CHEST)) return true;
                    }
                }
                if (b.restockTask.pickaxes) {
                    if (grabFromInventory(b, menu, containerSize, stack -> stack.isIn(ItemTags.PICKAXES))) return true;
                }
                if (b.restockTask.food) {
                    return grabFromInventory(b, menu, containerSize, stack -> stack.get(DataComponentTypes.FOOD) != null);
                }

                return false;
            }

            private boolean grabFromInventory(HighwayBuilderHIG b, ScreenHandler menu, int containerSize, Predicate<ItemStack> filterItem) {
                for (int i = 0; i < containerSize && i < menu.slots.size(); i++) {
                    if (filterItem.test(menu.slots.get(i).getStack())) {
                        b.quickMove(i);
                        return true;
                    }
                }

                return false;
            }

            private void setShulkerPredicate(HighwayBuilderHIG b) {
                shulkerPredicate = itemStack -> {
                    if (!isShulker(itemStack.getItem())) return false;
                    List<ItemStack> contents = ShulkerDataParser.parseShulkerContentsAsList(itemStack);

                    for (ItemStack stack : contents) {
                        if (b.restockTask.materials && stack.getItem() instanceof BlockItem bi) {
                            if (b.blocksToPlace.getValue().contains(bi.getBlock()) || (b.blocksToPlace.getValue().contains(Blocks.OBSIDIAN) && bi == Items.ENDER_CHEST))
                                return true;
                        }
                        if (b.restockTask.pickaxes && stack.isIn(ItemTags.PICKAXES)) return true;
                        if (b.restockTask.food && stack.get(DataComponentTypes.FOOD) != null) return true;
                    }

                    return false;
                };
            }

            private void handleContainerBlock(HighwayBuilderHIG b, BlockPos bp) {
                if (breakContainer) {
                    BlockState state = b.mc.world.getBlockState(bp);
                    int toolSlot = b.findAndMoveBestToolToHotbar(state, false);
                    InventoryUtil.swap(toolSlot);

                    if (b.rotation.getValue().mine) {
                        float[] yp = b.lookAt(bp);
                        b.rotateTo(yp[0], yp[1]);
                    }
                    b.breakBlockAt(bp);
                    Direction dir = getInteractDirection(bp);
                    Vec3 hitVec = new Vec3(bp.getX() + 0.5 + dir.getOffsetX() * 0.45,
                        bp.getY() + 0.5 + dir.getOffsetY() * 0.45,
                        bp.getZ() + 0.5 + dir.getOffsetZ() * 0.45);
                    InventoryUtil.swap(slot);
                    if (b.rotation.getValue().place) {
                        float[] yp = b.lookAt(b.mc.player.getEyePos(), hitVec);
                        b.rotateTo(yp[0], yp[1]);
                    }
                    b.mc.interactionManager.interactBlock(b.mc.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(hitVec, dir, bp, false));
                    b.mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(InteractionHand.MAIN_HAND));
                    delayTimer = b.inventoryDelay.getIntValue();
                }
            }

            private int countSlots(Predicate<ItemStack> predicate) {
                int count = 0;
                for (int i = 0; i < 36; i++) {
                    if (predicate.test(mc.player.getInventory().getStack(i))) count++;
                }

                return count;
            }
        },

        PlaceShulkerBlockade {
            @Override
            protected void tick(HighwayBuilderHIG b) {
                int slot = b.findBlocksToPlacePrioritizeTrash();
                if (slot == -1) return;

                b.place(b, b.blockPosProvider.getBlockade(false, BlockadeType.Shulker), slot, Restock);
            }
        },

        MineShulkerBlockade {
            private boolean stopTimerEnabled;
            private int stopTimer;

            @Override
            protected void start(HighwayBuilderHIG b) {
                stopTimerEnabled = false;
                if (b.lastState == this) {
                    stopTimerEnabled = true;
                    stopTimer = 12;
                }
            }

            @Override
            protected void tick(HighwayBuilderHIG b) {
                if (!stopTimerEnabled) {
                    b.mine(b, b.blockPosProvider.getBlockade(true, b.blockadeType.getValue()), true, this, this);
                } else {
                    stopTimer--;
                    if (stopTimer <= 0) {
                        b.setState(ThrowOutTrash, Forward);
                    }
                }
            }
        },

        DefuseCrystalTraps {
            private int cooldown;
            private int shots;
            private EndCrystalEntity target;
            private long drawStartMs;

            @Override
            protected void start(HighwayBuilderHIG b) {
                if (!InventoryUtil.find(Items.BOW).found()
                    || (!InventoryUtil.find(stack -> stack.getItem() instanceof ArrowItem).found() && !b.mc.player.isCreative())) {
                    b.destroyCrystalTraps.setValue(false);
                    b.warning("No bow found to destroy crystal traps with. Toggling the setting off.");
                    b.setState(Forward);
                }

                shots = cooldown = 0;
                target = null;
                drawStartMs = 0L;
            }

            @Override
            protected void tick(HighwayBuilderHIG b) {
                if (cooldown > 0) {
                    cooldown--;
                    return;
                }

                if (!InventoryUtil.testInMainHand(stack -> stack.isOf(Items.BOW))) {
                    int slot = b.findAndMoveToHotbar(stack -> stack.getItem() instanceof BowItem);
                    if (slot == -1) {
                        b.destroyCrystalTraps.setValue(false);
                        b.warning("No bow found to destroy crystal traps with. Toggling the setting off.");
                        b.setState(Forward);
                        b.mc.player.stopUsingItem();
                        b.drawingBow = false;
                        drawStartMs = 0L;
                        return;
                    }

                    InventoryUtil.swap(slot);
                }

                EndCrystalEntity potentialTarget = b.nearestCrystal();
                if (target == null || target.isRemoved()) {
                    if (potentialTarget == null) {
                        b.setState(Forward);
                        b.mc.player.stopUsingItem();
                        b.drawingBow = false;
                        drawStartMs = 0L;
                        return;
                    } else {
                        target = potentialTarget;
                        shots = 0;
                    }
                }

                if (shots >= 3) {
                    b.ignoreCrystals.add(target);
                    b.warning("Detected potential hangup on a crystal. Adding it to ignore list and continuing forward.");
                    b.setState(Forward);
                    b.mc.player.stopUsingItem();
                    b.drawingBow = false;
                    drawStartMs = 0L;
                    return;
                }

                float[] yp = b.lookAt(target);
                b.rotateTo(yp[0], yp[1]);

                int powerTicks = drawStartMs == 0L ? 0 : (int) ((System.currentTimeMillis() - drawStartMs) / 50L);
                if (BowItem.getPullProgress(powerTicks) >= 1.0f) {
                    b.mc.player.stopUsingItem();
                    b.drawingBow = false;
                    drawStartMs = 0L;
                    cooldown = 20;
                    shots++;
                } else {
                    if (!b.drawingBow) drawStartMs = System.currentTimeMillis();
                    b.drawingBow = true;
                    b.mc.interactionManager.interactItem(b.mc.player, InteractionHand.MAIN_HAND);
                }
            }

            private float aim(HighwayBuilderHIG b, Entity target) {
                float velocity = BowItem.getPullProgress(drawStartMs == 0L ? 0 : (int) ((System.currentTimeMillis() - drawStartMs) / 50L));

                Vec3 pos = target.getEntityPos();

                double relativeX = pos.x - b.mc.player.getX();
                double relativeY = pos.y + 0.5 - b.mc.player.getEyePos().y;
                double relativeZ = pos.z - b.mc.player.getZ();

                double hDistance = Math.sqrt(relativeX * relativeX + relativeZ * relativeZ);
                double hDistanceSq = hDistance * hDistance;
                float g = 0.006f;
                float velocitySq = velocity * velocity;

                return (float) -Math.toDegrees(Math.atan((velocitySq - Math.sqrt(velocitySq * velocitySq - g * (g * hDistanceSq + 2 * relativeY * velocitySq))) / (g * hDistance)));
            }
        };

        protected void start(HighwayBuilderHIG b) {
        }

        protected abstract void tick(HighwayBuilderHIG b);
    }

    private interface MBPIterator extends Iterator<MBlockPos>, Iterable<MBlockPos> {
        void save();

        void restore();

        @Override
        default Iterator<MBlockPos> iterator() {
            return this;
        }

        default int placementsPerTick(HighwayBuilderHIG b) {
            return b.placementsPerTick.getIntValue();
        }
    }

    private static class MBPIteratorFilter implements MBPIterator {
        private final MBPIterator it;
        private final Predicate<MBlockPos> predicate;

        private MBlockPos pos;
        private boolean isOld = true;
        private boolean pisOld = true;

        public MBPIteratorFilter(MBPIterator it, Predicate<MBlockPos> predicate) {
            this.it = it;
            this.predicate = predicate;
        }

        @Override
        public void save() {
            it.save();
            pisOld = isOld;
            isOld = true;
        }

        @Override
        public void restore() {
            it.restore();
            isOld = pisOld;
        }

        @Override
        public boolean hasNext() {
            if (isOld) {
                isOld = false;
                pos = null;

                while (it.hasNext()) {
                    pos = it.next();

                    if (predicate.test(pos)) return true;
                    else pos = null;
                }
            }

            return pos != null && predicate.test(pos);
        }

        @Override
        public MBlockPos next() {
            isOld = true;
            return pos;
        }
    }

    private interface IBlockPosProvider {
        MBPIterator getFront();

        MBPIterator getFloor();

        MBPIterator getRailings(int state);

        MBPIterator getLiquids();

        MBPIterator getBlockade(boolean mine, BlockadeType blockadeType);
    }

    private class StraightBlockPosProvider implements IBlockPosProvider {
        private final MBlockPos pos = new MBlockPos();
        private final MBlockPos pos2 = new MBlockPos();

        @Override
        public MBPIterator getFront() {
            pos.coerceBlockLevel(mc.player).offset(dir).offset(leftDir, getWidthLeft());

            return new MBPIterator() {
                private int w;
                private int y;
                private int pw;
                private int py;

                @Override
                public boolean hasNext() {
                    return w < width.getIntValue() && y < height.getIntValue();
                }

                @Override
                public MBlockPos next() {
                    pos2.set(pos).offset(rightDir, w).add(0, y, 0);

                    w++;
                    if (w >= width.getIntValue()) {
                        w = 0;
                        y++;
                    }

                    return pos2;
                }

                @Override
                public void save() {
                    pw = w;
                    py = y;
                    w = y = 0;
                }

                @Override
                public void restore() {
                    w = pw;
                    y = py;
                }
            };
        }

        @Override
        public MBPIterator getFloor() {
            pos.coerceBlockLevel(mc.player).offset(dir).offset(leftDir, getWidthLeft()).add(0, -1, 0);

            return new MBPIterator() {
                private int w;
                private int pw;

                @Override
                public boolean hasNext() {
                    return w < width.getIntValue();
                }

                @Override
                public MBlockPos next() {
                    return pos2.set(pos).offset(rightDir, w++);
                }

                @Override
                public void save() {
                    pw = w;
                    w = 0;
                }

                @Override
                public void restore() {
                    w = pw;
                }
            };
        }

        @Override
        public MBPIterator getRailings(int state) {
            pos.coerceBlockLevel(mc.player).offset(dir);

            return new MBPIterator() {
                private int i;
                private int y = state;
                private int pi;
                private int py;

                @Override
                public boolean hasNext() {
                    return i < 2 && y < (state == 1 ? height.getIntValue() : state + 1);
                }

                @Override
                public MBlockPos next() {
                    if (i == 0) pos2.set(pos).offset(leftDir, getWidthLeft() + 1).add(0, y, 0);
                    else pos2.set(pos).offset(rightDir, getWidthRight() + 1).add(0, y, 0);

                    y++;
                    if (y >= (state == 1 ? height.getIntValue() : state + 1)) {
                        y = state;
                        i++;
                    }

                    return pos2;
                }

                @Override
                public void save() {
                    pi = i;
                    py = y;
                    i = 0;
                    y = state;
                }

                @Override
                public void restore() {
                    i = pi;
                    y = py;
                }
            };
        }

        @Override
        public MBPIterator getLiquids() {
            pos.coerceBlockLevel(mc.player).offset(dir, 2).offset(leftDir, getWidthLeft() + (mineAboveRailings.getValue() ? 2 : 1));

            return new MBPIterator() {
                private int w;
                private int y;
                private int pw;
                private int py;

                private int getWidth() {
                    return width.getIntValue() + (mineAboveRailings.getValue() ? 2 : 0);
                }

                @Override
                public boolean hasNext() {
                    return w < getWidth() + 2 && y < height.getIntValue() + 1;
                }

                @Override
                public MBlockPos next() {
                    pos2.set(pos).offset(rightDir, w).add(0, y, 0);

                    w++;
                    if (w >= getWidth() + 2) {
                        w = 0;
                        y++;
                    }

                    return pos2;
                }

                @Override
                public void save() {
                    pw = w;
                    py = y;
                    w = y = 0;
                }

                @Override
                public void restore() {
                    w = pw;
                    y = py;
                }
            };
        }

        @Override
        public MBPIterator getBlockade(boolean mine, BlockadeType blockadeType) {
            return new MBPIterator() {
                private int i = mine ? -1 : 0;
                private int y;
                private int pi;
                private int py;

                private MBlockPos get(int i) {
                    pos.coerceBlockLevel(mc.player).offset(dir.opposite());

                    return switch (i) {
                        case -1 -> pos;
                        case 0 -> pos.offset(dir.opposite());
                        case 1 -> pos.offset(leftDir);
                        case 2 -> pos.offset(rightDir);
                        case 3 -> pos.offset(dir, 2);
                        case 4 -> pos.offset(dir).offset(leftDir);
                        case 5 -> pos.offset(dir).offset(rightDir);
                        default -> throw new IllegalStateException("Unexpected value: " + i);
                    };
                }

                @Override
                public boolean hasNext() {
                    return i < blockadeType.columns && y < 2;
                }

                @Override
                public MBlockPos next() {
                    if (width.getIntValue() == 1 && railings.getValue() && i > 0 && y == 0) y++;

                    MBlockPos pos = get(i).add(0, y, 0);

                    y++;
                    if (y > 1) {
                        y = 0;
                        i++;
                    }

                    return pos;
                }

                @Override
                public void save() {
                    pi = i;
                    py = y;
                    i = y = 0;
                }

                @Override
                public void restore() {
                    i = pi;
                    y = py;
                }

                @Override
                public int placementsPerTick(HighwayBuilderHIG b) {
                    return 1;
                }
            };
        }
    }

    private class DiagonalBlockPosProvider implements IBlockPosProvider {
        private final MBlockPos pos = new MBlockPos();
        private final MBlockPos pos2 = new MBlockPos();

        @Override
        public MBPIterator getFront() {
            pos.coerceBlockLevel(mc.player).offset(dir.rotateLeft()).offset(leftDir, getWidthLeft() - 1);

            return new MBPIterator() {
                private int i;
                private int w;
                private int y;
                private int pi;
                private int pw;
                private int py;

                @Override
                public boolean hasNext() {
                    return i < 2 && w < width.getIntValue() && y < height.getIntValue();
                }

                @Override
                public MBlockPos next() {
                    pos2.set(pos).offset(rightDir, w).add(0, y++, 0);

                    if (y >= height.getIntValue()) {
                        y = 0;
                        w++;

                        if (w >= (i == 0 ? width.getIntValue() - 1 : width.getIntValue())) {
                            w = 0;
                            i++;

                            pos.coerceBlockLevel(mc.player).offset(dir).offset(leftDir, getWidthLeft());
                        }
                    }

                    return pos2;
                }

                private void initPos() {
                    if (i == 0) pos.coerceBlockLevel(mc.player).offset(dir.rotateLeft()).offset(leftDir, getWidthLeft() - 1);
                    else pos.coerceBlockLevel(mc.player).offset(dir).offset(leftDir, getWidthLeft());
                }

                @Override
                public void save() {
                    pi = i;
                    pw = w;
                    py = y;
                    i = w = y = 0;

                    initPos();
                }

                @Override
                public void restore() {
                    i = pi;
                    w = pw;
                    y = py;

                    initPos();
                }
            };
        }

        @Override
        public MBPIterator getFloor() {
            pos.coerceBlockLevel(mc.player).add(0, -1, 0).offset(dir.rotateLeft()).offset(leftDir, getWidthLeft() - 1);

            return new MBPIterator() {
                private int i;
                private int w;
                private int pi;
                private int pw;

                @Override
                public boolean hasNext() {
                    return i < 2 && w < width.getIntValue();
                }

                @Override
                public MBlockPos next() {
                    pos2.set(pos).offset(rightDir, w++);

                    if (w >= (i == 0 ? width.getIntValue() - 1 : width.getIntValue())) {
                        w = 0;
                        i++;

                        pos.coerceBlockLevel(mc.player).add(0, -1, 0).offset(dir).offset(leftDir, getWidthLeft());
                    }

                    return pos2;
                }

                private void initPos() {
                    if (i == 0) pos.coerceBlockLevel(mc.player).add(0, -1, 0).offset(dir.rotateLeft()).offset(leftDir, getWidthLeft() - 1);
                    else pos.coerceBlockLevel(mc.player).add(0, -1, 0).offset(dir).offset(leftDir, getWidthLeft());
                }

                @Override
                public void save() {
                    pi = i;
                    pw = w;
                    i = w = 0;

                    initPos();
                }

                @Override
                public void restore() {
                    i = pi;
                    w = pw;

                    initPos();
                }
            };
        }

        @Override
        public MBPIterator getRailings(int state) {
            pos.coerceBlockLevel(mc.player).offset(dir.rotateLeft()).offset(leftDir, getWidthLeft());

            return new MBPIterator() {
                private int i;
                private int y = state;
                private int pi;
                private int py;

                @Override
                public boolean hasNext() {
                    return i < 2 && y < (state == 1 ? height.getIntValue() : state + 1);
                }

                @Override
                public MBlockPos next() {
                    pos2.set(pos).add(0, y++, 0);

                    if (y >= (state == 1 ? height.getIntValue() : state + 1)) {
                        y = state;
                        i++;

                        pos.coerceBlockLevel(mc.player).offset(dir.rotateRight()).offset(rightDir, getWidthRight());
                    }

                    return pos2;
                }

                private void initPos() {
                    if (i == 0) pos.coerceBlockLevel(mc.player).offset(dir.rotateLeft()).offset(leftDir, getWidthLeft());
                    else pos.coerceBlockLevel(mc.player).offset(dir.rotateRight()).offset(rightDir, getWidthRight());
                }

                @Override
                public void save() {
                    pi = i;
                    py = y;
                    i = 0;
                    y = state;

                    initPos();
                }

                @Override
                public void restore() {
                    i = pi;
                    y = py;

                    initPos();
                }
            };
        }

        @Override
        public MBPIterator getLiquids() {
            boolean m = mineAboveRailings.getValue();
            pos.coerceBlockLevel(mc.player).offset(dir).offset(dir.rotateLeft()).offset(leftDir, getWidthLeft());

            return new MBPIterator() {
                private int i;
                private int w;
                private int y;
                private int pi;
                private int pw;
                private int py;

                private int getWidth() {
                    return width.getIntValue() + (i == 0 ? 1 : 0) + (m && i == 1 ? 2 : 0);
                }

                @Override
                public boolean hasNext() {
                    if (m && i == 1 && y == height.getIntValue() && w == getWidth() - 1) return false;
                    return i < 2 && w < getWidth() && y < height.getIntValue() + 1;
                }

                private void updateW() {
                    w++;

                    if (w >= getWidth()) {
                        w = 0;
                        i++;

                        pos.coerceBlockLevel(mc.player).offset(dir, 2).offset(leftDir, getWidthLeft() + (m ? 1 : 0));
                    }
                }

                @Override
                public MBlockPos next() {
                    if (i == (m ? 1 : 0) && y == height.getIntValue() && (w == 0 || w == getWidth() - 1)) {
                        y = 0;
                        updateW();
                    }

                    pos2.set(pos).offset(rightDir, w).add(0, y++, 0);

                    if (y >= height.getIntValue() + 1) {
                        y = 0;
                        updateW();
                    }

                    return pos2;
                }

                private void initPos() {
                    if (i == 0) pos.coerceBlockLevel(mc.player).offset(dir).offset(dir.rotateLeft()).offset(leftDir, getWidthLeft());
                    else pos.coerceBlockLevel(mc.player).offset(dir, 2).offset(leftDir, getWidthLeft() + (m ? 1 : 0));
                }

                @Override
                public void save() {
                    pi = i;
                    pw = w;
                    py = y;
                    i = w = y = 0;

                    initPos();
                }

                @Override
                public void restore() {
                    i = pi;
                    w = pw;
                    y = py;

                    initPos();
                }
            };
        }

        @Override
        public MBPIterator getBlockade(boolean mine, BlockadeType blockadeType) {
            return new MBPIterator() {
                private int i = mine ? -1 : 0;
                private int y;
                private int pi;
                private int py;

                private MBlockPos get(int i) {
                    HorizontalDirection dir2 = dir.rotateLeft().rotateLeftSkipOne();

                    pos.coerceBlockLevel(mc.player).offset(dir2);

                    return switch (i) {
                        case -1 -> pos;
                        case 0 -> pos.offset(dir2);
                        case 1 -> pos.offset(dir2.rotateLeftSkipOne());
                        case 2 -> pos.offset(dir2.rotateLeftSkipOne().opposite());
                        case 3 -> pos.offset(dir2.opposite(), 2);
                        case 4 -> pos.offset(dir2.opposite()).offset(dir2.rotateLeftSkipOne());
                        case 5 -> pos.offset(dir2.opposite()).offset(dir2.rotateLeftSkipOne().opposite());
                        default -> throw new IllegalStateException("Unexpected value: " + i);
                    };
                }

                @Override
                public boolean hasNext() {
                    return i < blockadeType.columns && y < 2;
                }

                @Override
                public MBlockPos next() {
                    MBlockPos pos = get(i).add(0, y, 0);

                    y++;
                    if (y > 1) {
                        y = 0;
                        i++;
                    }

                    return pos;
                }

                @Override
                public void save() {
                    pi = i;
                    py = y;
                    i = y = 0;
                }

                @Override
                public void restore() {
                    i = pi;
                    y = py;
                }

                @Override
                public int placementsPerTick(HighwayBuilderHIG b) {
                    return 1;
                }
            };
        }
    }

    public static class DoubleMineBlock {
        public static boolean rateLimited = false;
        public final BlockPos blockPos;
        public final BlockState blockState;

        private final Block block;
        private final HighwayBuilderHIG b;
        private final Vec3 vec = new Vec3(0, 0, 0);

        private int normalStartTime;
        private int packetStartTime;
        private boolean packet;

        public DoubleMineBlock(HighwayBuilderHIG b, BlockPos pos) {
            this.b = b;
            this.blockPos = pos;
            this.blockState = b.mc.world.getBlockState(this.blockPos);
            this.block = this.blockState.getBlock();
        }

        public DoubleMineBlock startDestroying() {
            if (b.mc.getNetworkHandler() != null) {
                Direction dir = getInteractDirection(this.blockPos);
                int seq = me.collective.util.GrimUtils.nextSequence();
                b.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, this.blockPos, dir, seq));
                b.mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(InteractionHand.MAIN_HAND));
            }
            normalStartTime = b.mc.player.age;
            return this;
        }

        public DoubleMineBlock stopDestroying() {
            if (b.mc.getNetworkHandler() != null) {
                Direction dir = getInteractDirection(this.blockPos);
                int seq = me.collective.util.GrimUtils.nextSequence();
                if (b.rotation.getValue().mine) {
                    float[] yp = b.lookAt(this.blockPos);
                    b.rotateTo(yp[0], yp[1]);
                }
                b.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, this.blockPos, dir, seq));
                b.mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(InteractionHand.MAIN_HAND));
            }
            return this;
        }

        public DoubleMineBlock packetMine() {
            packetStartTime = b.mc.player.age;
            packet = true;
            return stopDestroying();
        }

        public boolean isReady() {
            return progress() >= 1.0;
        }

        public boolean shouldRemove() {
            Vec3 eye = b.mc.player.getEyePos();
            double dx = blockPos.getX() - eye.x;
            double dy = blockPos.getY() - eye.y;
            double dz = blockPos.getZ() - eye.z;
            boolean distance = !packet && dx * dx + dy * dy + dz * dz
                > b.mc.player.getBlockInteractionRange() * b.mc.player.getBlockInteractionRange();

            boolean timeout = progress() > 2 && (b.mc.player.age - (packet ? packetStartTime : normalStartTime) > 60);

            return distance || timeout;
        }

        public double progress() {
            int elapsed = b.mc.player.age - (packet ? packetStartTime : normalStartTime);
            return blockState.calcBlockBreakingDelta(b.mc.player, b.mc.world, blockPos) * (elapsed + 1);
        }

        public void renderLetter(GuiGraphics context) {
            double cx = blockPos.getX() + 0.5;
            double cy = blockPos.getY() + 0.5;
            double cz = blockPos.getZ() + 0.5;
            org.joml.Vector2f sp = RenderUtil3D.worldToScreen(cx, cy, cz,
                b.mc.getWindow().getScaledWidth(), b.mc.getWindow().getScaledHeight());
            if (sp == null) return;

            String letter = packet ? "P" : "N";
            float w = FontManager.INSTANCE.getStringWidth(letter) / 2f;
            FontManager.INSTANCE.drawStringWithShadow(context, letter, sp.x - w, sp.y, Color.WHITE);
        }
    }

    private class RestockTask {
        public boolean materials;
        public boolean pickaxes;
        public boolean food;
        private final HighwayBuilderHIG b;

        public RestockTask(HighwayBuilderHIG b) {
            this.b = b;
        }

        public void setMaterials() {
            setTask(0);
        }

        public void setPickaxes() {
            setTask(1);
        }

        public void setFood() {
            setTask(2);
        }

        private void setTask(int value) {
            complete();

            switch (value) {
                case 0 -> materials = true;
                case 1 -> pickaxes = true;
                case 2 -> food = true;
            }

            setState(State.Restock);
            b.info("Starting new restock task for " + item());
        }

        public void complete() {
            materials = false;
            pickaxes = false;
            food = false;
        }

        public boolean tasksInactive() {
            return !materials && !pickaxes && !food;
        }

        public String item() {
            if (materials) return "building materials";
            if (pickaxes) return "pickaxes";
            if (food) return "food";
            return "unknown";
        }
    }

    private static class EChestMemory {
        static final ItemStack[] ITEMS = new ItemStack[27];
        private static boolean known = false;

        static {
            java.util.Arrays.fill(ITEMS, ItemStack.EMPTY);
        }

        static boolean isKnown() {
            return known;
        }

        static void updateFrom(ScreenHandler menu) {
            for (int i = 0; i < 27 && i < menu.slots.size(); i++) {
                ItemStack stack = menu.slots.get(i).getStack();
                ITEMS[i] = stack == null ? ItemStack.EMPTY : stack.copy();
            }
            known = true;
        }
    }
}
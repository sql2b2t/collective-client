package me.collective.module.combat;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.FindItemResult;
import me.collective.util.InventoryUtil;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.tags.ItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class Offhand extends Module {
    public enum Mode {
        TOTEM, CRYSTAL, GAPPLE
    }

    public final EnumSetting<Mode> defaultItem = add(new EnumSetting<>("Default", Mode.TOTEM));
    public final NumberSetting healthThreshold = add(new NumberSetting("Health", 14.0, 1.0, 36.0, 1.0));
    public final BooleanSetting crystalCheck = add(new BooleanSetting("CrystalCheck", true));
    public final BooleanSetting anchorCheck = add(new BooleanSetting("AnchorCheck", true));
    public final BooleanSetting swordGap = add(new BooleanSetting("SwordGap", true));
    public final BooleanSetting elytraCheck = add(new BooleanSetting("ElytraProtect", true));
    public final BooleanSetting fallCheck = add(new BooleanSetting("FallProtect", true));
    public final NumberSetting delay = add(new NumberSetting("Delay", 0.0, 0.0, 10.0, 1.0));

    public static Offhand INSTANCE;

    private int timer = 0;
    private int anchorTimer = 0;
    private boolean cachedAnchorNear = false;

    public Offhand() {
        super("Offhand", "Умный менеджер второй руки: держит тотемы, кристаллы или щит с проверкой опасности.", Category.COMBAT);
        INSTANCE = this;
    }

    public void onTotemPop() {
        if (!isToggled() || mc.player == null || !InventoryUtil.canUsePlayerInventory()) return;
        FindItemResult totem = InventoryUtil.find(Items.TOTEM_OF_UNDYING);
        if (totem.found()) {
            InventoryUtil.moveToOffhand(totem.slot());
            timer = delay.getValue().intValue();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || !InventoryUtil.canUsePlayerInventory()) return;

        if (timer > 0) {
            timer--;
            return;
        }

        Item target = getTargetItem();
        if (mc.player.getOffHandStack().isOf(target)) return;
        if (target == Items.ENCHANTED_GOLDEN_APPLE && mc.player.getOffHandStack().isOf(Items.GOLDEN_APPLE)
                && !InventoryUtil.find(Items.ENCHANTED_GOLDEN_APPLE).found()) {
            return;
        }

        FindItemResult result = InventoryUtil.find(target);
        if (result.found()) {
            InventoryUtil.moveToOffhand(result.slot());
            timer = delay.getValue().intValue();
        } else if (target == Items.ENCHANTED_GOLDEN_APPLE) {
            FindItemResult regularGap = InventoryUtil.find(Items.GOLDEN_APPLE);
            if (regularGap.found()) {
                if (!mc.player.getOffHandStack().isOf(Items.GOLDEN_APPLE)) {
                    InventoryUtil.moveToOffhand(regularGap.slot());
                    timer = delay.getValue().intValue();
                }
            } else {
                FindItemResult totem = InventoryUtil.find(Items.TOTEM_OF_UNDYING);
                if (totem.found() && !mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                    InventoryUtil.moveToOffhand(totem.slot());
                    timer = delay.getValue().intValue();
                }
            }
        } else if (target != Items.TOTEM_OF_UNDYING) {
            // Fallback to totem
            FindItemResult totem = InventoryUtil.find(Items.TOTEM_OF_UNDYING);
            if (totem.found() && !mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                InventoryUtil.moveToOffhand(totem.slot());
                timer = delay.getValue().intValue();
            }
        }
    }

    @Override
    public void onActivate() {
        timer = 0;
    }

    @Override
    public void onWorldChange() {
        timer = 0;
    }

    private Item getTargetItem() {
        if (mc.player == null || mc.world == null) return Items.TOTEM_OF_UNDYING;

        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();

        boolean lowHp = hp <= healthThreshold.getValue().floatValue();
        boolean gliding = elytraCheck.getValue()
            && mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)
            && mc.player.isGliding();
        boolean falling = fallCheck.getValue() && mc.player.fallDistance > 3.0f;

        // Кристаллы поблизости — опасность
        boolean crystalNear = false;
        if (crystalCheck.getValue()) {
            for (var entity : mc.world.getEntities()) {
                if (entity instanceof EndCrystalEntity crystal && crystal.distanceTo(mc.player) <= 6.0f) {
                    crystalNear = true;
                    break;
                }
            }
        }

        // Заряженный якорь поблизости (с кэшированием на 3 тика и сферическим радиусом)
        boolean anchorNear = false;
        if (anchorCheck.getValue() && mc.world.getRegistryKey() != Level.NETHER) {
            if (--anchorTimer <= 0) {
                anchorTimer = 3;
                cachedAnchorNear = false;
                BlockPos pPos = mc.player.getBlockPos();
                outer:
                for (int x = -5; x <= 5; x++) {
                    for (int y = -4; y <= 4; y++) {
                        for (int z = -5; z <= 5; z++) {
                            if (x * x + y * y + z * z > 26) continue;
                            var state = mc.world.getBlockState(pPos.add(x, y, z));
                            if (state.isOf(Blocks.RESPAWN_ANCHOR) && state.get(RespawnAnchorBlock.CHARGES) > 0) {
                                cachedAnchorNear = true;
                                break outer;
                            }
                        }
                    }
                }
            }
            anchorNear = cachedAnchorNear;
        }

        if (lowHp || gliding || falling || crystalNear || anchorNear) {
            return Items.TOTEM_OF_UNDYING;
        }

        // SwordGap: держим меч/топор + зажат ПКМ -> яблоко
        if (swordGap.getValue() && mc.options.useKey.isPressed()) {
            var mainStack = mc.player.getMainHandStack();
            if (mainStack.isIn(ItemTags.SWORDS) || mainStack.isIn(ItemTags.AXES)) {
                return Items.ENCHANTED_GOLDEN_APPLE;
            }
        }

        return switch (defaultItem.getValue()) {
            case TOTEM  -> Items.TOTEM_OF_UNDYING;
            case CRYSTAL -> Items.END_CRYSTAL;
            case GAPPLE -> Items.ENCHANTED_GOLDEN_APPLE;
        };
    }
}
package me.collective.module.player;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.*;
import me.collective.util.InvUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Replenish — порт {@code bep.hax.modules.Replenish} (Mojmap, CFR) на 1.21.11.
 *
 * Добивает стак в хотбаре из инвентаря shift-кликом по пакету (без открытия
 * экрана). Настройки и логика сохранены 1:1, кроме правок декомпиляции и
 * переездов API:
 * <ul>
 *   <li>{@code stack.getComponents().contains(FOOD_COMPONENT)} вместо обёртки
 *       с {@code canEat()} (1.21.11: еда — компонент, а не метод на Item);</li>
 *   <li>инструменты и оружие определяются по компонентам {@code TOOL} и
 *       {@code WEAPON} — в 1.21.11 классов {@code PickaxeItem} и
 *       {@code SwordItem} больше нет, у кирки и мечей это обычные {@code Item}
 *       с компонентами (в декомпиляции bep.hax инструменты ловились строкой
 *       {@code toString().contains("pickaxe")});</li>
 *   <li>в {@code attemptRefill} декомпиляция дважды звала {@code findSourceSlot} —
 *       второй вызов убран (первый же проверял кастомные имена);</li>
 *   <li>поля {@code containerMenu}/{@code inventoryMenu} — 1.21.11
 *       переименованы, syncId берётся из хендлера.</li>
 * </ul>
 */
public class Replenish extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgItems = settings.createGroup("Предметы");
    private final SettingGroup sgAdvanced = settings.createGroup("Продвинутое");

    private final Setting<Integer> threshold = sgGeneral.add(new IntSetting.Builder()
        .name("порог")
        .description("Добивать, когда в стаке осталось столько предметов.")
        .defaultValue(8)
        .min(1)
        .max(63)
        .sliderMin(1)
        .sliderMax(63)
        .build());

    private final Setting<Integer> tickDelay = sgGeneral.add(new IntSetting.Builder()
        .name("задержка-тиков")
        .description("Пауза в тиках между операциями восполнения.")
        .defaultValue(0)
        .min(0)
        .max(10)
        .sliderMin(0)
        .sliderMax(10)
        .build());

    private final Setting<Boolean> pauseOnUse = sgGeneral.add(new BoolSetting.Builder()
        .name("пауза-при-использовании")
        .description("Не восполнять, пока используется предмет.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> smartRefill = sgGeneral.add(new BoolSetting.Builder()
        .name("умное-восполнение")
        .description("Добивать только когда стак почти опустел (1 предмет и убывает).")
        .defaultValue(false)
        .build());

    private final Setting<StackPreference> stackPreference = sgGeneral.add(new EnumSetting.Builder<StackPreference>()
        .name("приоритет-стака")
        .description("Какой стак в инвентаре брать первым.")
        .defaultValue(StackPreference.Маленькие)
        .build());

    private final Setting<Boolean> refillBlocks = sgItems.add(new BoolSetting.Builder()
        .name("блоки")
        .description("Добивать строительные блоки.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> refillFood = sgItems.add(new BoolSetting.Builder()
        .name("еда")
        .description("Добивать еду.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> refillTools = sgItems.add(new BoolSetting.Builder()
        .name("инструменты")
        .description("Добивать инструменты (кирка, топор, лопата, мотыга).")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> refillWeapons = sgItems.add(new BoolSetting.Builder()
        .name("оружие")
        .description("Добивать оружие (меч, лук, арбалет, трезубец).")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> refillProjectiles = sgItems.add(new BoolSetting.Builder()
        .name("снаряды")
        .description("Добивать снаряды (стрелы, фейерверки).")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> refillPearls = sgItems.add(new BoolSetting.Builder()
        .name("жемчуг")
        .description("Добивать жемчуг края.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> refillPotions = sgItems.add(new BoolSetting.Builder()
        .name("зелья")
        .description("Добивать зелья.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> refillTotems = sgItems.add(new BoolSetting.Builder()
        .name("тотемы")
        .description("Добивать тотемы бессмертия.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> refillGaps = sgItems.add(new BoolSetting.Builder()
        .name("золотые-яблоки")
        .description("Добивать золотые и зачарованные золотые яблоки.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> refillFireworks = sgItems.add(new BoolSetting.Builder()
        .name("фейерверки")
        .description("Добивать фейерверки.")
        .defaultValue(true)
        .build());

    private final Setting<List<Item>> blacklist = sgItems.add(new ItemListSetting.Builder()
        .name("чёрный-список")
        .description("Эти предметы не восполняются никогда, независимо от остальных настроек.")
        .build());

    private final Setting<Boolean> useShiftClick = sgAdvanced.add(new BoolSetting.Builder()
        .name("через-shift-клик")
        .description("Совместимость со старым конфигом: пополнение всегда адресное, с возвратом остатка.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> silentRefill = sgAdvanced.add(new BoolSetting.Builder()
        .name("тихое-восполнение")
        .description("Отправлять клик сразу, не дожидаясь открытого экрана инвентаря.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> maxRefillsPerTick = sgAdvanced.add(new IntSetting.Builder()
        .name("максимум-операций")
        .description("Сколько операций восполнения максимум за тик.")
        .defaultValue(1)
        .min(1)
        .max(5)
        .sliderMin(1)
        .sliderMax(5)
        .build());

    private final Setting<Boolean> maintainTool = sgAdvanced.add(new BoolSetting.Builder()
        .name("сохранять-тип-инструмента")
        .description("Инструменты добирать только того же типа и материала.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> respectCustomNames = sgAdvanced.add(new BoolSetting.Builder()
        .name("учитывать-кастомные-имена")
        .description("Не смешивать предметы с разными именами, пока стак не опустел полностью.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> refillAllStackable = sgAdvanced.add(new BoolSetting.Builder()
        .name("восполнять-всё-стекаемое")
        .description("Добивать любые стакаемые предметы, а не только перечисленные категории.")
        .defaultValue(true)
        .build());

    private int delayTicks;
    private final Map<Integer, Integer> lastStackSizes = new HashMap<>();
    private final Map<Integer, String> hotbarItemNames = new HashMap<>();
    private final Map<Integer, ItemStack> trackedSlotStacks = new HashMap<>();

    public static Replenish INSTANCE;

    public Replenish() {
        super("Replenish", "Добивает стаки в хотбаре из инвентаря.", Category.PLAYER);
        INSTANCE = this;
    }

    @Override
    public void onActivate() {
        this.delayTicks = 0;
        this.lastStackSizes.clear();
        this.hotbarItemNames.clear();
        this.trackedSlotStacks.clear();
    }

    @Override
    public void onDeactivate() {
        onActivate();
    }

    @Override
    public void onWorldChange() {
        onActivate();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (this.mc.player == null || this.mc.level == null || !InvUtils.canUsePlayerInventory()) return;
        if (!this.silentRefill.get() && !(this.mc.gui.screen() instanceof InventoryScreen)) return;

        if (this.delayTicks > 0) {
            this.delayTicks--;
            return;
        }

        if (this.pauseOnUse.get() && this.mc.player.isUsingItem()) return;

        checkHotbar();
    }

    private void checkHotbar() {
        int refillsThisTick = 0;

        for (int slot = 0; slot < 9 && refillsThisTick < this.maxRefillsPerTick.get(); slot++) {
            ItemStack stack = this.mc.player.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                ItemStack lastStack = this.trackedSlotStacks.get(slot);
                if (lastStack != null && !lastStack.isEmpty() && shouldRefill(lastStack)) {
                    int sourceSlot = findSourceSlot(lastStack);
                    if (sourceSlot != -1) {
                        InvUtils.move().from(sourceSlot).to(slot);
                        refillsThisTick++;
                        continue;
                    }
                }
                this.hotbarItemNames.remove(slot);
                this.trackedSlotStacks.remove(slot);
                continue;
            }

            this.trackedSlotStacks.put(slot, stack.copy());
            if (!shouldRefill(stack)) continue;

            int count = stack.getCount();
            int maxCount = stack.getMaxStackSize();

            if (this.smartRefill.get()) {
                Integer lastSize = this.lastStackSizes.get(slot);
                if (lastSize != null && lastSize > count && count <= 1 && attemptRefill(slot, stack)) refillsThisTick++;
                this.lastStackSizes.put(slot, count);
                continue;
            }

            if (count > this.threshold.get() || count >= maxCount || !attemptRefill(slot, stack)) continue;
            refillsThisTick++;
        }

        if (refillsThisTick > 0) this.delayTicks = this.tickDelay.get();
    }

    private boolean shouldRefill(ItemStack stack) {
        Item item = stack.getItem();
        if (this.blacklist.get().contains(item)) return false;

        if (this.refillAllStackable.get() && stack.getMaxStackSize() > 1) return true;
        if (this.refillTotems.get() && item == Items.TOTEM_OF_UNDYING) return true;
        if (this.refillPearls.get() && item == Items.ENDER_PEARL) return true;
        if (this.refillGaps.get() && (item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE)) return true;
        if (this.refillFireworks.get() && item == Items.FIREWORK_ROCKET) return true;
        if (this.refillBlocks.get() && item instanceof BlockItem) return true;
        if (this.refillFood.get() && item.components().has(DataComponents.FOOD)) return true;
        if (this.refillTools.get() && isTool(item)) return true;
        if (this.refillWeapons.get() && isWeapon(item)) return true;
        if (this.refillProjectiles.get() && (item == Items.ARROW || item == Items.FIREWORK_ROCKET || item == Items.SPECTRAL_ARROW)) return true;
        return this.refillPotions.get() && item instanceof PotionItem;
    }

    private boolean isTool(Item item) {
        // В 1.21.11 классов PickaxeItem/AxeItem/ShovelItem больше нет у кирки,
        // а у топоров/лопат/мотыг — есть, поэтому надёжнее проверять сам компонент
        // TOOL: он есть у любого инструмента (в том числе у кирки).
        return item.components().has(DataComponents.TOOL);
    }

    private boolean isWeapon(Item item) {
        // Мечей как класса тоже не осталось: у клинкового оружия компонент WEAPON.
        return item.components().has(DataComponents.WEAPON)
            || item instanceof BowItem
            || item instanceof CrossbowItem
            || item instanceof TridentItem;
    }

    private boolean attemptRefill(int hotbarSlot, ItemStack hotbarStack) {
        if (this.respectCustomNames.get() && hotbarStack.getMaxStackSize() > 1) {
            String currentName = itemName(hotbarStack);
            String trackedName = this.hotbarItemNames.get(hotbarSlot);
            if (trackedName == null) {
                this.hotbarItemNames.put(hotbarSlot, currentName);
                trackedName = currentName;
            }

            int sourceSlot = findSourceSlot(hotbarStack);
            if (sourceSlot == -1) {
                this.hotbarItemNames.remove(hotbarSlot);
                return false;
            }

            String sourceName = itemName(this.mc.player.getInventory().getItem(sourceSlot));
            if (!trackedName.equals(sourceName)) {
                // Не выкидываем остаток стака ради предмета с другим именем.
                if (hotbarStack.getCount() > 1) return false;
                this.hotbarItemNames.put(hotbarSlot, sourceName);
            }
        } else if (findSourceSlot(hotbarStack) == -1) {
            return false;
        }

        int sourceSlot = findSourceSlot(hotbarStack);
        if (sourceSlot == -1) return false;

        // Complete pickup, targeted merge and overflow return synchronously. No queued
        // inventory indices survive a screen change or consume a later user's stack.
        InvUtils.move().from(sourceSlot).to(hotbarSlot);
        return true;
    }

    private String itemName(ItemStack stack) {
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            Component customName = stack.get(DataComponents.CUSTOM_NAME);
            if (customName != null) return customName.getString();
        }

        return stack.getHoverName().getString();
    }

    private int findSourceSlot(ItemStack targetStack) {
        int bestSlot = -1;
        int bestCount = 0;
        boolean fullStacks = this.stackPreference.get() == StackPreference.Полные;
        boolean smallStacks = this.stackPreference.get() == StackPreference.Маленькие;
        if (smallStacks) bestCount = Integer.MAX_VALUE;

        for (int i = 9; i < 36; i++) {
            ItemStack stack = this.mc.player.getInventory().getItem(i);
            if (stack.isEmpty() || !canStack(targetStack, stack)) continue;
            if (this.maintainTool.get() && isTool(targetStack.getItem()) && stack.getItem() != targetStack.getItem()) continue;

            if (this.stackPreference.get() == StackPreference.По_порядку) return i;
            if (fullStacks) {
                if (stack.getCount() <= bestCount) continue;
                bestCount = stack.getCount();
                bestSlot = i;
                continue;
            }
            if (!smallStacks || stack.getCount() >= bestCount) continue;
            bestCount = stack.getCount();
            bestSlot = i;
        }

        return bestSlot;
    }

    private boolean canStack(ItemStack a, ItemStack b) {
        if (a.getItem() != b.getItem()) return false;
        if (a.getMaxStackSize() == 1) return true;
        return ItemStack.isSameItemSameComponents(a, b);
    }

    /** Константы по-русски — EnumSetting показывает toString(). */
    public enum StackPreference {
        По_порядку,
        Полные,
        Маленькие
    }

}
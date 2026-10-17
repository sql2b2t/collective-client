package me.collective.module.player;

import me.collective.setting.Setting;
import me.collective.setting.SettingGroup;
import me.collective.setting.BoolSetting;
import me.collective.setting.DoubleSetting;
import me.collective.setting.IntSetting;
import me.collective.setting.StringSetting;

import me.collective.event.events.InteractEntityEvent;
import me.collective.event.events.StartBreakingBlockEvent;
import me.collective.event.events.PacketEvent;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.category.Category;
import me.collective.event.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.SetTradeOffersS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.VillagerProfession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Роллер торговли жителей — порт VillagerRoller из meteor-villager-roller
 * (maxsupermanhd). Автоматически перебирает villagers: ломает блок, ставит его
 * заново, пока торговец не возьмёт профессию, затем ловитtrade-пакет и
 * проверяет зачарования зачарованной книги.
 *
 * Отличия от оригинала (Mojmap + старая версия MC):
 *  - MerchantOffers -> TradeOfferList, MerchantOffer -> TradeOffer;
 *  - ClientboundMerchantOffersPacket -> SetTradeOffersS2CPacket;
 *  - getBaseCostA().getCount() -> getFirstBuyItem().count();
 *  - окно выбора зачарований (EnchantmentSelectScreen) заменено настройками
 *    (список зачарований + уровень + цена задаются в настройках модуля);
 *  - InvUtils/BlockUtils заменены на прямые вызовы interactionManager.
 */
public class VillagerRoller extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSound = this.settings.createGroup("Звук");
    private final SettingGroup sgChat = this.settings.createGroup("Чат");

    // Основные

    private final Setting<Boolean> disableIfFound = sgGeneral.add(new BoolSetting.Builder()
        .name("выключать-при-находке")
        .description("Отключать зачарование из списка, когда оно найдено.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disconnectIfFound = sgGeneral.add(new BoolSetting.Builder()
        .name("выходить-при-находке")
        .description("Выходить с сервера, когда найдено нужное зачарование.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> pauseOnScreen = sgGeneral.add(new BoolSetting.Builder()
        .name("пауза-на-экранах")
        .description("Останавливать ролл, пока открыт любой экран.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> headRotateOnPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("смотреть-при-установке")
        .description("Поворачиваться на блок при его установке.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> failedToPlaceDelay = sgGeneral.add(new IntSetting.Builder()
        .name("задержка-после-ошибки")
        .description("Задержка между сообщениями об ошибке установки (мс).")
        .defaultValue(1500)
        .min(0)
        .sliderMax(10000)
        .build()
    );

    private final Setting<Boolean> failedToPlaceDisable = sgGeneral.add(new BoolSetting.Builder()
        .name("выключать-при-ошибке")
        .description("Выключать модуль, если блок не удалось поставить.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> maxProfessionWaitTime = sgGeneral.add(new IntSetting.Builder()
        .name("макс-ожидание-профессии")
        .description("Сколько ждать, пока житель возьмёт профессию (мс). 0 — без ограничения.")
        .defaultValue(0)
        .min(0)
        .sliderMax(10000)
        .build()
    );

    private final Setting<Boolean> instantRebreak = sgGeneral.add(new BoolSetting.Builder()
        .name("civbreak")
        .description("Мгновенно ломать пюпитр пакетами (CivBreak). Лучше стоять над ним.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> interactRetry = sgGeneral.add(new IntSetting.Builder()
        .name("повтор-взаимодействия")
        .description("Если сервер не подтвердил взаимодействие, повторить через N тиков. 0 — без повторов.")
        .defaultValue(0)
        .min(0)
        .sliderMax(200)
        .build()
    );

    // Список зачарований (в оригинале — отдельное окно выбора)

    private final Setting<String> wantedEnchants = sgGeneral.add(new me.collective.setting.StringSetting("зачарования", "minecraft:sharpness"));

    private final Setting<Integer> minLevel = sgGeneral.add(new IntSetting.Builder()
        .name("мин-уровень")
        .description("Минимальный уровень зачарования. 0 — только максимальный.")
        .defaultValue(0)
        .min(0)
        .max(10)
        .build()
    );

    private final Setting<Integer> maxCost = sgGeneral.add(new IntSetting.Builder()
        .name("макс-цена")
        .description("Максимальная цена в изумрудах. 0 — без ограничения.")
        .defaultValue(0)
        .min(0)
        .max(64)
        .build()
    );

    // Звук

    private final Setting<Boolean> enablePlaySound = sgSound.add(new BoolSetting.Builder()
        .name("звук")
        .description("Проигрывать звук при нахождении нужного трейда.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> soundPitch = sgSound.add(new DoubleSetting.Builder()
        .name("высота-звука")
        .description("Высота звука.")
        .defaultValue(1.0)
        .min(0)
        .sliderMax(8)
        .build()
    );

    private final Setting<Double> soundVolume = sgSound.add(new DoubleSetting.Builder()
        .name("громкость-звука")
        .description("Громкость звука.")
        .defaultValue(1.0)
        .min(0)
        .sliderMax(1)
        .build()
    );

    // Чат

    private final Setting<Boolean> cfSetup = sgChat.add(new BoolSetting.Builder()
        .name("подсказки")
        .description("Подсказки по настройке в начале.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cfPausedOnScreen = sgChat.add(new BoolSetting.Builder()
        .name("пауза-экран")
        .description("Ролл на паузе, взаимодействуй с жителем чтобы продолжить.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cfLowerLevel = sgChat.add(new BoolSetting.Builder()
        .name("низкий-уровень")
        .description("Найдено зачарование, но уровень ниже нужного.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cfTooExpensive = sgChat.add(new BoolSetting.Builder()
        .name("слишком-дорого")
        .description("Найдено зачарование, но цена выше максимальной.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cfNotInList = sgChat.add(new BoolSetting.Builder()
        .name("не-из-списка")
        .description("Найдено зачарование, которого нет в списке.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cfFoundMatching = sgChat.add(new BoolSetting.Builder()
        .name("найдено-совпадение")
        .description("Сообщить, что именно найдено перед остановкой.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cfDiscrepancy = sgChat.add(new BoolSetting.Builder()
        .name("несоответствие")
        .description("Модуль попал в неожиданное состояние.")
        .defaultValue(true)
        .build()
    );

    private enum State {
        DISABLED,
        WAITING_FOR_TARGET_BLOCK,
        WAITING_FOR_TARGET_VILLAGER,
        ROLLING_BREAKING_BLOCK,
        ROLLING_WAITING_FOR_PROFESSION_CLEAR,
        ROLLING_PLACING_BLOCK,
        ROLLING_WAITING_FOR_PROFESSION_NEW,
        ROLLING_WAITING_FOR_TRADES
    }

    private State currentState = State.DISABLED;
    private VillagerEntity rollingVillager;
    private BlockPos rollingBlockPos;
    private Block rollingBlock;
    private long failedToPlacePrevMsg = System.currentTimeMillis();
    private long currentProfessionWaitTime;
    private long waitingForTradesTicks;
    private int retryPlaceTicks;

    public VillagerRoller() {
        super("VillagerRoller", "Роллер торговли жителей: ломает блок, ставит заново, ловит трейды.", Category.PLAYER);
    }

    @Override
    public void onActivate() {
        currentState = State.WAITING_FOR_TARGET_BLOCK;
        if (cfSetup.get()) info("Ударь по блоку, который хочешь прокрутить");
    }

    @Override
    public void onDeactivate() {
        currentState = State.DISABLED;
    }

    @Override
    public String getInfoString() {
        return currentState.toString();
    }

    // ------------------------------------------------------------------ события

    @EventHandler
    private void onStartBreakingBlock(StartBreakingBlockEvent event) {
        if (currentState != State.WAITING_FOR_TARGET_BLOCK) return;

        rollingBlockPos = event.blockPos;
        rollingBlock = mc.world.getBlockState(rollingBlockPos).getBlock();
        currentState = State.WAITING_FOR_TARGET_VILLAGER;

        if (instantRebreak.get()) {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, rollingBlockPos, Direction.UP));
        }

        if (cfSetup.get()) info("Блок выбран, теперь взаимодействуй с жителем");
    }

    @EventHandler
    private void onInteractEntity(InteractEntityEvent event) {
        if (currentState != State.WAITING_FOR_TARGET_VILLAGER) return;
        if (!(event.entity instanceof VillagerEntity villager)) return;

        rollingVillager = villager;
        currentState = State.ROLLING_BREAKING_BLOCK;
        if (cfSetup.get()) info("Житель получен");
        event.cancel();
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (currentState != State.ROLLING_WAITING_FOR_TRADES) return;
        if (!(event.packet instanceof SetTradeOffersS2CPacket packet)) return;

        mc.execute(() -> checkTrades(packet.getOffers()));
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        switch (currentState) {
            case ROLLING_BREAKING_BLOCK -> tickBreaking();
            case ROLLING_WAITING_FOR_PROFESSION_CLEAR -> tickWaitProfessionClear();
            case ROLLING_PLACING_BLOCK -> tickPlacing();
            case ROLLING_WAITING_FOR_PROFESSION_NEW -> tickWaitProfessionNew();
            case ROLLING_WAITING_FOR_TRADES -> tickWaitTrades();
            default -> { }
        }
    }

    private void tickBreaking() {
        if (instantRebreak.get()) {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, rollingBlockPos, Direction.DOWN));
        }

        if (mc.world.getBlockState(rollingBlockPos).isAir()) {
            currentState = State.ROLLING_WAITING_FOR_PROFESSION_CLEAR;
        }
        else if (!instantRebreak.get() && !mc.interactionManager.attackBlock(rollingBlockPos, Direction.UP)) {
            error("Не удаётся сломать этот блок");
            toggle();
        }
    }

    private void tickWaitProfessionClear() {
        if (mc.world.getBlockState(rollingBlockPos).isOf(Blocks.LECTERN)) {
            if (cfDiscrepancy.get()) info("Разрушение блока откатилось?");
            currentState = State.ROLLING_BREAKING_BLOCK;
            return;
        }

        if (rollingVillager == null) return;

        // В 1.21.11 профессия лежит прямо в record VillagerData (без Optional).
        if (rollingVillager.getVillagerData().profession().matchesKey(VillagerProfession.NONE)) {
            currentState = State.ROLLING_PLACING_BLOCK;
        }
    }

    private void tickPlacing() {
        if (mc.world.getBlockState(rollingBlockPos).isOf(Blocks.LECTERN)) {
            if (cfDiscrepancy.get()) info("Установка пюпитра откатилась?");
            currentState = State.ROLLING_WAITING_FOR_PROFESSION_NEW;
            return;
        }

        int slot = findInHotbar(rollingBlock.asItem());
        if (slot < 0) {
            placeFailed("Пюпитр не найден в хотбаре");
            return;
        }

        if (slot != mc.player.getInventory().getSelectedSlot()) {
            mc.player.getInventory().setSelectedSlot(slot);
        }

        // Хит-пойнт — центр ВЕРХНЕЙ грани блока (ofCenter уже даёт +0.5;
        // прошлый вариант добавлял ещё 0.5 и вылезал за хитбокс — сервер
        // отклонял interact, потому пюпитр и «не ставился».
        net.minecraft.util.hit.BlockHitResult hit = new net.minecraft.util.hit.BlockHitResult(
            net.minecraft.util.math.Vec3d.ofCenter(rollingBlockPos).add(0, 0.5, 0),
            Direction.UP, rollingBlockPos, false);

        var result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        if (!result.isAccepted()) {
            // Не переходим в ожидание профессии: сервер не принял установку.
            retryPlaceTicks++;
            placeFailed("Сервер отклонил установку пюпитра");
            if (retryPlaceTicks >= 10) {
                return; // ждём, не заспамим — статус не меняем
            }
            return;
        }
        retryPlaceTicks = 0;

        currentState = State.ROLLING_WAITING_FOR_PROFESSION_NEW;
        if (maxProfessionWaitTime.get() > 0) currentProfessionWaitTime = System.currentTimeMillis();
    }

    private void tickWaitProfessionNew() {
        if (maxProfessionWaitTime.get() > 0 && currentProfessionWaitTime + maxProfessionWaitTime.get() <= System.currentTimeMillis()) {
            currentState = State.ROLLING_BREAKING_BLOCK;
            return;
        }

        if (mc.world.getBlockState(rollingBlockPos).isAir()) {
            if (cfDiscrepancy.get()) info("Сервер откатил установку пюпитра?");
            currentState = State.ROLLING_PLACING_BLOCK;
            return;
        }

        if (rollingVillager == null) return;

        if (!rollingVillager.getVillagerData().profession().matchesKey(VillagerProfession.NONE)) {
            currentState = State.ROLLING_WAITING_FOR_TRADES;
            triggerInteract();
        }
    }

    private void tickWaitTrades() {
        int retry = interactRetry.get();
        if (retry <= 0) return;

        if (waitingForTradesTicks >= retry) {
            triggerInteract();
            waitingForTradesTicks = 0;
        }
        else {
            waitingForTradesTicks++;
        }
    }

    private void placeFailed(String msg) {
        if (failedToPlacePrevMsg + failedToPlaceDelay.get() <= System.currentTimeMillis()) {
            if (cfDiscrepancy.get()) error(msg);
            failedToPlacePrevMsg = System.currentTimeMillis();
        }
        if (failedToPlaceDisable.get()) toggle();
    }

    private int findInHotbar(net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        }
        return -1;
    }

    private void triggerInteract() {
        if (pauseOnScreen.get() && mc.currentScreen != null) {
            if (cfPausedOnScreen.get()) info("Ролл на паузе, взаимодействуй с жителем");
            return;
        }

        if (rollingVillager == null) return;
        mc.interactionManager.interactEntity(mc.player, rollingVillager, Hand.MAIN_HAND);
        waitingForTradesTicks = 0;
    }

    // ------------------------------------------------------------------ трейды

    /** Убирает элемент из списка «зачарования». Возвращает true, если список ещё не пуст после удаления. */
    private boolean removeWanted(String wanted) {
        String current = wantedEnchants.get();
        String trimmed = wanted.trim();
        StringBuilder remaining = new StringBuilder();
        boolean first = true;
        int removedCount = 0;
        for (String part : current.split(",")) {
            if (part.trim().equalsIgnoreCase(trimmed) && removedCount == 0) {
                removedCount++;
                continue; // первый совпавший убираем
            }
            if (part.trim().isEmpty()) continue;
            if (!first) remaining.append(", ");
            remaining.append(part.trim());
            first = false;
        }
        wantedEnchants.setValue(remaining.toString());
        return !remaining.isEmpty();
    }

    private void checkTrades(TradeOfferList offers) {
        for (TradeOffer offer : offers) {
            ItemStack sellItem = offer.getSellItem();
            if (!sellItem.isOf(Items.ENCHANTED_BOOK)) continue;
            if (!sellItem.contains(DataComponentTypes.STORED_ENCHANTMENTS)) continue;

            ItemEnchantmentsComponent enchants = sellItem.get(DataComponentTypes.STORED_ENCHANTMENTS);
            if (enchants == null) continue;

            int cost = offer.getFirstBuyItem().count();

            for (var entry : enchants.getEnchantmentEntries()) {
                Enchantment enchantment = entry.getKey().value();
                int level = entry.getIntValue();

                // Реестр зачарований — через RegistryKeys (в этом билде нет Registries.ENCHANTMENT).
                String id = mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT)
                    .getId(enchantment).toString();
                String name = Enchantment.getName(entry.getKey(), level).getString();

                boolean found = false;
                for (String wanted : wantedEnchants.get().split(",")) {
                    if (!matches(wanted, id, entry.getKey())) continue;
                    found = true;

                    int maxLevel = enchantment.getMaxLevel();
                    if (minLevel.get() <= 0) {
                        if (level < maxLevel) {
                            if (cfLowerLevel.get()) info("Найдено %s, но уровень ниже максимума: %d > %d".formatted(name, maxLevel, level));
                            continue;
                        }
                    }
                    else if (minLevel.get() > level) {
                        if (cfLowerLevel.get()) info("Найдено %s, но уровень слишком низкий: %d > %d".formatted(name, minLevel.get(), level));
                        continue;
                    }

                    if (maxCost.get() > 0 && cost > maxCost.get()) {
                        if (cfTooExpensive.get()) info("Найдено %s, но слишком дорого: %d < %d".formatted(name, maxCost.get(), cost));
                        continue;
                    }

                    if (cfFoundMatching.get()) {
                        info("Найдено нужное зачарование %s (уровень %d) за %d изумрудов — останавливаюсь."
                            .formatted(name, level, cost));
                    }

                    if (enablePlaySound.get()) {
                        SoundEvent sound = SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME;
                        mc.getSoundManager().play(
                            net.minecraft.client.sound.PositionedSoundInstance.ambient(
                                sound, soundPitch.get().floatValue(), soundVolume.get().floatValue()));
                    }

                    if (disconnectIfFound.get()) {
                        mc.getNetworkHandler().getConnection().disconnect(net.minecraft.text.Text.literal(
                            "Villager Roller: найдено %s%s за %d изумрудов — выход.".formatted(
                                name, level > 1 ? " " + level : "", cost)));
                    }

                    // disableIfFound: убираем найденное из списка и продолжаем
                    // роллить остальное; если список опустел — выключаем модуль.
                    if (disableIfFound.get()) {
                        if (!removeWanted(wanted)) toggle();
                    } else {
                        toggle();
                    }
                    // Экран не закрываем через хвост метода: ролл остановлен
                    // (или список изменился) — выходим немедленно.
                    mc.player.closeHandledScreen();
                    currentState = State.ROLLING_BREAKING_BLOCK;
                    return;
                }

                if (!found && cfNotInList.get()) info("Найдено зачарование %s, но его нет в списке.".formatted(name));
            }
        }

        mc.player.closeHandledScreen();
        currentState = State.ROLLING_BREAKING_BLOCK;
    }

    /** Пустой список — принимаем любое зачарование; иначе сравниваем по ID или имени. */
    private boolean matches(String wanted, String id, RegistryEntry<Enchantment> entry) {
        if (wanted == null || wanted.isBlank()) return true;

        String w = wanted.trim().toLowerCase();
        if (id.toLowerCase().contains(w)) return true;

        try {
            Identifier parsed = Identifier.of(w);
            return entry.matchesKey(net.minecraft.registry.RegistryKey.of(RegistryKeys.ENCHANTMENT, parsed));
        }
        catch (Exception ignored) {
            return false;
        }
    }
}

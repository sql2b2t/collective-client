package me.collective.module.render;

import me.collective.setting.Setting;
import me.collective.setting.SettingGroup;
import me.collective.setting.BoolSetting;

import me.collective.util.tracker.ChestTrackerDataManager;
import me.collective.util.tracker.ContainerIndexRecorder;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.category.Category;
import me.collective.event.EventHandler;

/**
 * Индекс контейнеров (порт из BepHax, там — ChestTracker).
 *
 * <p>Помнит, что и где лежит: при каждом открытии контейнера его снимок
 * записывается в json рядом с модулями Meteor, отдельно на каждый сервер.
 * На этом индексе работают тултипы содержимого и автосортировщик стеша.
 *
 * <p>Включать постоянно: пока модуль выключен, индекс не пишется и
 * содержимое в подсказках будет устаревшим.
 */
public class ContainerIndex extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> trackShulkerBoxes = sgGeneral.add(new BoolSetting.Builder()
        .name("сундуки-шалкеры")
        .description("Записывать содержимое шалкеров, поставленных как блок (не только шалкеров-итемов).")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> trackEnderChests = sgGeneral.add(new BoolSetting.Builder()
        .name("эндер-сундуки")
        .description("Записывать эндер-сундуки. Их содержимое общее для всего аккаунта, но записей будет много.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> autosave = sgGeneral.add(new BoolSetting.Builder()
        .name("автосохранение")
        .description("Сохранять индекс раз в минуту, чтобы не потерять его при краше.")
        .defaultValue(true)
        .build());

    public ContainerIndex() {
        super("ContainerIndex", "Помнит, что и где лежит: пишет содержимое контейнеров при открытии.", Category.RENDER);
    }

    @Override
    public void onActivate() {
        ChestTrackerDataManager.onModuleActivate();
        applyOptions();
    }

    @Override
    public void onDeactivate() {
        ChestTrackerDataManager.saveData();
        ChestTrackerDataManager.onModuleDeactivate();
    }

    private void applyOptions() {
        ContainerIndexRecorder.setOptions(trackShulkerBoxes.get(), trackEnderChests.get(), autosave.get());
    }

    

    

    @EventHandler
    private void onTick(TickEvent.Post event) {
        applyOptions();
        ContainerIndexRecorder.onTick();
    }

    @EventHandler
    private void onPacketReceive(me.collective.event.events.PacketEvent.Receive event) {
        if (event.getPacket() instanceof net.minecraft.network.packet.s2c.play.InventoryS2CPacket invPacket) {
            ContainerIndexRecorder.onInventoryPacket(invPacket);
        }
    }
}
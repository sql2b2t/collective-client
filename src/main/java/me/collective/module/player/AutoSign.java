package me.collective.module.player;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.PacketEvent;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.NumberSetting;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.core.BlockPos;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Автоматическая запись табличек. Текст первой отредактированной таблички
 * запоминается и применяется ко всем последующим открытым табличкам —
 * экран редактирования не открывается.
 *
 * Порт из Meteor Client (modules/world/AutoSign), пункт про паузу между
 * пакетами сохранён: на 2b2t пакет таблички не должен уходить слишком
 * рано после пакетов установки блока и свинга.
 */
public class AutoSign extends Module {
    public static AutoSign INSTANCE;

    public final NumberSetting delay = add(new NumberSetting("Delay", 10.0, 0.0, 100.0, 1.0));

    private String[] capturedLines;
    private Boolean capturedFront;

    private final Queue<net.minecraft.network.protocol.game.ServerboundSignUpdatePacket> queue = new ArrayDeque<>();
    private int timer;

    public AutoSign() {
        super("AutoSign", "Автоматически заполняет текст табличек текстом с первой написанной таблички.", Category.PLAYER);
        INSTANCE = this;
    }

    @Override
    public void onDeactivate() {
        this.capturedLines = null;
        this.capturedFront = null;
        this.queue.clear();
        this.timer = 0;
    }

    @EventHandler
    public void onTick(TickEvent.Post event) {
        if (mc.player == null || queue.peek() == null) {
            timer = 0;
            return;
        }

        int ticks = (int) Math.round(delay.getValue());
        if (timer < ticks) {
            timer++;
            return;
        }

        mc.getConnection().send(queue.poll());

        timer = 0;
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (!(event.packet instanceof net.minecraft.network.protocol.game.ServerboundSignUpdatePacket sign)) return;

        this.capturedLines = sign.lines().toArray(new String[0]);
        this.capturedFront = sign.slot() == net.minecraft.world.level.block.entity.SignTextSlot.FRONT;
    }

    /**
     * Вызывается миксином при открытии экрана редактирования таблички.
     * Возвращает true, если пакет с запомненным текстом поставлен в очередь
     * и экран можно не открывать.
     */
    public boolean queueAutoFill(BlockPos signPos) {
        if (!isToggled() || capturedLines == null || capturedFront == null) return false;

        queue.add(new net.minecraft.network.protocol.game.ServerboundSignUpdatePacket(
            signPos,
            java.util.List.of(capturedLines),
            capturedFront ? net.minecraft.world.level.block.entity.SignTextSlot.FRONT : net.minecraft.world.level.block.entity.SignTextSlot.BACK
        ));
        return true;
    }
}
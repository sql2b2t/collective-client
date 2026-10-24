package me.collective.module.misc;

import com.mojang.authlib.GameProfile;
import me.collective.category.Category;
import me.collective.module.Module;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Порт FakePlayer из Meteor Client.
 *
 * <p>Спавнит клиентского болванчика для теста аур.
 */
public class FakePlayer extends Module {
    private static final UUID FAKE_UUID = UUID.randomUUID();
    private net.minecraft.client.player.RemotePlayer fakeEntity;
    private ClientLevel fakeWorld;

    static boolean isLocalFake(Player player) {
        return FAKE_UUID.equals(player.getUUID());
    }

    public FakePlayer() {
        super("FakePlayer", "Болванчик для проверки аур.", Category.MISC);
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.level == null) {
            setToggled(false);
            return;
        }
        if (fakeEntity != null && fakeEntity.isAlive()) {
            info("Болванчик уже на месте.");
            return;
        }
        Vec3 look = mc.player.getViewVector(1.0f);
        Vec3 pos = mc.player.position().add(look.x * 2, 0, look.z * 2);
        net.minecraft.client.player.RemotePlayer fake = new net.minecraft.client.player.RemotePlayer(mc.level,
            new GameProfile(FAKE_UUID, "FakePlayer")) {
            @Override
            public boolean isPushable() {
                // Без коллизии: сквозь болванчика можно ходить
                return false;
            }
        };
        fake.setPos(pos.x, pos.y, pos.z);
        fake.setYRot(mc.player.getYRot());
        fakeEntity = fake;
        fakeWorld = mc.level;
        fakeWorld.addEntity(fake);
        info("Болванчик заспавнен.");
    }

    @Override
    public void onDisable() {
        if (fakeWorld != null && fakeEntity != null && fakeWorld.getEntity(fakeEntity.getId()) == fakeEntity) {
            fakeWorld.removeEntity(fakeEntity.getId(), Entity.RemovalReason.DISCARDED);
        }
        fakeEntity = null;
        fakeWorld = null;
    }

    @Override
    public void onUpdate() {
        // Мир сменился (переход/дисконнект) — ссылка протухла, чищем чтобы
        // старый id не выкосил чужую сущность из нового мира.
        if (fakeEntity != null && (mc.level != fakeWorld || fakeEntity.isRemoved() || !fakeEntity.isAlive()
            || fakeWorld.getEntity(fakeEntity.getId()) != fakeEntity)) {
            setToggled(false);
        }
    }
}
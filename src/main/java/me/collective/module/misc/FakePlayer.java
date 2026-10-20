package me.collective.module.misc;

import com.mojang.authlib.GameProfile;
import me.collective.category.Category;
import me.collective.module.Module;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * Порт FakePlayer из Meteor Client.
 *
 * <p>Спавнит клиентского болванчика для теста аур.
 */
public class FakePlayer extends Module {
    private OtherClientPlayerEntity fakeEntity;

    public FakePlayer() {
        super("FakePlayer", "Болванчик для проверки аур.", Category.MISC);
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.world == null) {
            setToggled(false);
            return;
        }
        if (fakeEntity != null && fakeEntity.isAlive()) {
            info("Болванчик уже на месте.");
            return;
        }
        Vec3d look = mc.player.getRotationVec(1.0f);
        Vec3d pos = mc.player.getEntityPos().add(look.x * 2, 0, look.z * 2);
        OtherClientPlayerEntity fake = new OtherClientPlayerEntity(mc.world,
            new GameProfile(UUID.randomUUID(), "FakePlayer")) {
            @Override
            public boolean isPushable() {
                // Без коллизии: сквозь болванчика можно ходить
                return false;
            }
        };
        fake.setPosition(pos.x, pos.y, pos.z);
        fake.setYaw(mc.player.getYaw());
        fakeEntity = fake;
        mc.world.addEntity(fake);
        info("Болванчик заспавнен.");
    }

    @Override
    public void onDisable() {
        if (mc.world != null && fakeEntity != null) {
            mc.world.removeEntity(fakeEntity.getId(), Entity.RemovalReason.DISCARDED);
        }
        fakeEntity = null;
    }

    @Override
    public void onUpdate() {
        // Мир сменился (переход/дисконнект) — ссылка протухла, чищем чтобы
        // старый id не выкосил чужую сущность из нового мира.
        if (fakeEntity != null && (mc.world == null || fakeEntity.isRemoved() || fakeEntity.isAlive() && fakeEntity.getId() != -1 && mc.world.getEntityById(fakeEntity.getId()) == null)) {
            fakeEntity = null;
            setToggled(false);
        }
    }
}

package me.collective.mixin;

import com.mojang.authlib.GameProfile;
import me.collective.client.ClientAccess;
import me.collective.client.utils.combat.RemotePlayerDamageAccess;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(RemotePlayer.class)
public abstract class RemotePlayerMixin extends Player implements ClientAccess, RemotePlayerDamageAccess {

   public RemotePlayerMixin(Level level, GameProfile profile) {
      super(level, BlockPos.ZERO, 0.0F, profile);
   }

   @Override
   public boolean damageSuper(DamageSource source, float amount) {
      return super.hurtServer((ServerLevel)this.level(), source, amount);
   }
}

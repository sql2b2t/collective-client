package me.collective.mixin;

import com.mojang.authlib.GameProfile;
import me.collective.client.events.AbstractClientPlayerTickEvent;
import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.render.CapeEntry;
import me.collective.client.modules.player.NameProtectModule;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.ClientAsset.ResourceTexture;
import net.minecraft.core.ClientAsset.Texture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin extends Player {
   private static final Identifier protectedSkinTexture = Identifier.fromNamespaceAndPath(
      "collective-mount", "textures/skin_protect.png"
   );
   @Unique
   private static final List<CapeEntry> playerCapes = List.of(
      new CapeEntry("cattyyyn", "pepsi.png"), new CapeEntry("u3o", "tetris.png"), new CapeEntry("asphyxia1337", "suckmy.png")
   );

   public AbstractClientPlayerMixin(Level level, GameProfile profile) {
      super(level, profile);
   }

   @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
   private void overridePlayerSkin(CallbackInfoReturnable<PlayerSkin> callback) {
      LocalPlayer localPlayer = Minecraft.getInstance().player;
      NameProtectModule nameProtectModule = ClientContext.getModule(NameProtectModule.class);
      PlayerSkin originalSkin = (PlayerSkin)callback.getReturnValue();
      Texture bodyTexture = originalSkin.body();
      PlayerModelType modelType = originalSkin.model();
      if (nameProtectModule != null
         && nameProtectModule.isToggled()
         && nameProtectModule.skinSetting.getValue()
         && localPlayer != null
         && localPlayer.getGameProfile().name().equals(this.getGameProfile().name())) {
         bodyTexture = new ResourceTexture(
            protectedSkinTexture,
            protectedSkinTexture
         );
         modelType = PlayerModelType.WIDE;
         if (nameProtectModule.slimSetting.getValue()) {
            modelType = PlayerModelType.SLIM;
         }

         callback.setReturnValue(new PlayerSkin(bodyTexture, originalSkin.cape(), originalSkin.elytra(), modelType, originalSkin.secure()));
      }

      String playerName = this.getGameProfile().name();
      CapeEntry capeEntry = null;
      for (CapeEntry candidate : playerCapes) {
         if (candidate.playerName().equals(playerName)) {
            capeEntry = candidate;
            break;
         }
      }
      if (capeEntry != null) {
         ResourceTexture capeTexture = new ResourceTexture(
            capeEntry.getTexture(),
            capeEntry.getTexture()
         );
         callback.setReturnValue(new PlayerSkin(bodyTexture, capeTexture, originalSkin.elytra(), modelType, originalSkin.secure()));
         callback.cancel();
      }
   }

   @Inject(method = "tick", at = @At("TAIL"))
   public void postLocalPlayerTickEvent(CallbackInfo callback) {
      if (ClientAccess.isLocalPlayer(this)) {
         ClientAccess.eventBus
            .post(new AbstractClientPlayerTickEvent());
      }
   }
}

package me.collective.client.managers;

import me.collective.client.modules.misc.SwingModule.HandChoice;
import me.collective.client.modules.misc.SwingModule.TypeChoice;

import me.collective.client.utils.entity.PlayerState;
import me.collective.client.utils.entity.SwingAction;
import me.collective.client.utils.inventory.InventoryUtils;
import me.collective.client.utils.network.PacketSender;

import me.collective.client.utils.ElapsedTimer;
import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.events.EventHandler;
import me.collective.client.events.LocalPlayerTickEvent;
import me.collective.client.events.bus.EventBus;
import me.collective.client.modules.combat.AutoCrystalModule;
import me.collective.client.modules.misc.SwingModule;
import me.collective.client.settings.Setting;
import me.collective.client.settings.options.AutoCrystalInstantChoice;
import me.collective.client.settings.options.AutoCrystalWeaknessChoice;

import java.util.function.Predicate;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class CrystalInteractionManager implements ClientAccess {
   private static SwingModule swing;
   private static AutoCrystalModule autoCrystal;
   private final ElapsedTimer attackCooldownTimer = new ElapsedTimer();
   private long additionalCooldownMillis;
   private int lastAttackedEntityId;

   public int getMinimumCrystalAge() {
      return autoCrystal().ticksExistedSetting.getValue();
   }

   public void setLastAttackedEntityId(int entityId) {
      this.lastAttackedEntityId = entityId;
   }

   public boolean isRotationEnabled() {
      return autoCrystal().rotateSetting.getValue();
   }

   public boolean isInstantAttackEnabled() {
      return autoCrystal().instantSetting.getValue() != AutoCrystalInstantChoice.NONE;
   }

   private boolean selectWeaknessWeapon() {
      ItemStack heldStack = ClientAccess.collectiveMinecraft.player.getMainHandItem();
      DataComponentType<?> toolComponent = DataComponents.TOOL;

      boolean holdingTool = heldStack.has(toolComponent);

      if (autoCrystal().weaknessSetting.getValue() == AutoCrystalWeaknessChoice.NONE) {
         return false;
      }
      LocalPlayer weaknessPlayer = ClientAccess.collectiveMinecraft.player;
      Holder<MobEffect> weaknessEffect = MobEffects.WEAKNESS;
      if (!weaknessPlayer.hasEffect(weaknessEffect) || holdingTool) {
         return false;
      }
      Predicate<ItemStack> weaponPredicate = candidateStack -> {
         DataComponentType<?> weaponComponent = DataComponents.WEAPON;
         return candidateStack.has(weaponComponent);
      };

      int weaponSlot = InventoryUtils.findHotbarSlot(weaponPredicate);
      if (weaponSlot == -1) {
         return false;
      }

      if (autoCrystal().weaknessSetting.getValue() == AutoCrystalWeaknessChoice.SILENT) {
         autoCrystal().ignoreNextSlotChange();
      }

      InventoryUtils.selectHotbarSlotForPlacement(weaponSlot);
      return true;
   }

   public int getAttackDelayMillis() {
      return autoCrystal().breakDelaySetting.getValue();
   }

   private void scheduleClientAttack(int entityId) {
      if (!RotationManager.isMovementSyncEnabled()) {
         return;
      }
      if (RotationManager.is2b2tMode()) {
         return;
      }
      var eventLoop = ClientAccess.collectiveMinecraft;
      Runnable attackTask = () -> {
         Entity targetEntity = ClientAccess.collectiveMinecraft.level.getEntity(entityId);
         if (targetEntity != null) {
            ClientAccess.collectiveMinecraft.player.attack(targetEntity);
         }
      };

      eventLoop.execute(attackTask);
   }

   public CrystalInteractionManager() {
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribe(this);
   }

   private static SwingModule swing() {
      if (swing == null) {
         swing = ClientContext.getModule(SwingModule.class);
      }
      return swing;
   }

   private static AutoCrystalModule autoCrystal() {
      if (autoCrystal == null) {
         autoCrystal = ClientContext.getModule(AutoCrystalModule.class);
      }
      return autoCrystal;
   }

   private void resetAttackCooldown(InteractionHand hand) {
      ((PlayerState)ClientAccess.collectiveMinecraft.player).collective$resetLastAttackedServer();
      ElapsedTimer cooldownTimerRef = this.attackCooldownTimer;
      long elapsedThreshold = 200L + this.additionalCooldownMillis;
      if (!cooldownTimerRef.hasElapsedAndReset(elapsedThreshold) || hand != InteractionHand.MAIN_HAND) {
         return;
      }
      Setting<HandChoice> handSetting = swing().handSetting;
      if (handSetting.getValue() == SwingModule.HandChoice.OFFHAND) {
         return;
      }
      Setting<TypeChoice> typeSetting = swing().typeSetting;
      if (typeSetting.getValue() == SwingModule.TypeChoice.VANILLA) {
         return;
      }

      ClientAccess.collectiveMinecraft.player.resetOnlyAttackStrengthTicker();
      double randomFraction = Math.random();

      this.additionalCooldownMillis = (long)(
         randomFraction * 300.0D
      );
   }

   public boolean attackEntity(int entityId, boolean instantAttack) {
      if (!this.isInstantAttackEnabled() && instantAttack) {
         return false;
      }

      if (this.lastAttackedEntityId != -1 && RotationManager.isMovementSyncEnabled()) {
         return false;
      }

      int previousSlot = InventoryUtils.getSelectedSlot();

      Entity targetEntity = ClientAccess.collectiveMinecraft.level.getEntity(entityId);
      boolean switchedForWeakness = false;
      if (targetEntity instanceof EndCrystal) {
         switchedForWeakness = this.selectWeaknessWeapon();
      }

      this.lastAttackedEntityId = entityId;

      InteractionHand hand = this.selectAttackHand(targetEntity, instantAttack);

      PacketSender.sendAttack(this.lastAttackedEntityId);
      if (!instantAttack) {
         ClientContext.swingManager.swingClientSide(hand);
      }

      SwingAction swingAction = SwingAction.HIT;
      InteractionHand packetSwingHand = InteractionHand.MAIN_HAND;

      ClientContext.swingManager.swingPacket(packetSwingHand, swingAction);

      this.resetAttackCooldown(hand);
      this.scheduleClientAttack(entityId);
      if (switchedForWeakness) {
         if (autoCrystal().weaknessSetting.getValue() == AutoCrystalWeaknessChoice.SILENT) {
            autoCrystal().ignoreNextSlotChange();

            InventoryUtils.selectHotbarSlotForPlacement(previousSlot);
         }
      }

      return true;
   }

   private InteractionHand selectAttackHand(
      Entity entity, boolean instantAttack
   ) {
      if (entity instanceof EndCrystal && !instantAttack) {
         return ClientAccess.collectiveMinecraft.player.getOffhandItem().getItem() == Items.END_CRYSTAL ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
      } else {
         return InteractionHand.MAIN_HAND;
      }
   }

   public boolean attackEntity(int entityId) {
      return this.attackEntity(entityId, false);
   }

   @EventHandler(priority = -300)
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      this.lastAttackedEntityId = -1;
   }
}

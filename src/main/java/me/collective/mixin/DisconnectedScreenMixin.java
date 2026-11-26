package me.collective.mixin;

import me.collective.client.modules.misc.AutoReconnectModule;
import me.collective.client.ClientContext;
import me.collective.client.modules.client.UIModule;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Button.Builder;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisconnectedScreen.class)
public class DisconnectedScreenMixin extends Screen {
   private static AutoReconnectModule autoReconnectModule;
   private static UIModule uiModule;
   private static AutoReconnectModule autoReconnectModule() {
      if (autoReconnectModule == null) {
         autoReconnectModule = ClientContext.getModule(AutoReconnectModule.class);
      }
      return autoReconnectModule;
   }
   private static UIModule uiModule() {
      if (uiModule == null) {
         uiModule = ClientContext.getModule(UIModule.class);
      }
      return uiModule;
   }
   @Unique
   private long reconnectTimerStartMillis;
   @Shadow
   @Final
   private DisconnectionDetails details;
   @Shadow
   @Final
   private LinearLayout layout;
   @Unique
   private Button reconnectButton;
   @Unique
   private Button autoReconnectButton;

   protected DisconnectedScreenMixin(Component title) {
      super(title);
   }

   @Inject(method = "init()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/DisconnectedScreen;repositionElements()V", shift = Shift.BEFORE))
   private void addReconnectButtons(CallbackInfo callback) {
      this.reconnectTimerStartMillis = System.currentTimeMillis();
      Builder reconnectBuilder = Button.builder(
            Component.literal("Reconnect"),
            button -> {
               ServerData serverData = ClientContext.serverStateManager.getServerData();
               if (serverData != null) {
                  ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), this.minecraft, ServerAddress.parseString(serverData.ip), serverData, true, null);
               }
            }
         )
         .width(200);
      Builder autoReconnectBuilder = Button.builder(
            Component.literal("AutoReconnect"),
            button -> {
               if (!autoReconnectModule().isToggled()) {
                  ClientContext.serverStateManager.autoLogged = false;
               }

               autoReconnectModule().toggle();
               this.reconnectTimerStartMillis = System.currentTimeMillis();
               button.setFocused(false);
            }
         )
         .width(200);
      this.reconnectButton = reconnectBuilder.build();
      this.autoReconnectButton = autoReconnectBuilder.build();
      if (ClientContext.serverStateManager.getServerData()
         != null) {
         this.addRenderableWidget(this.reconnectButton);
         this.addRenderableWidget(this.autoReconnectButton);
      }
   }

   @Inject(method = "repositionElements", at = @At("TAIL"))
   private void repositionReconnectButtons(CallbackInfo callback) {
      if (this.reconnectButton != null) {
         int buttonX = this.width
               / (
                  2
               )
            - 100;
         this.reconnectButton
            .setPosition(
               buttonX,
               Math.min(
                     this.height
                           / 2
                        + this.layout.getHeight()
                           / 2,
                     this.height
                        - 30
                  )
                  - 1
            );
         this.autoReconnectButton
            .setPosition(
               buttonX,
               Math.min(
                     this.height
                           / 2
                        + this.layout.getHeight()
                           / 2,
                     this.height
                        - 30
                  )
                  + 20
            );
      }
   }

   @Override
   public boolean keyPressed(KeyEvent keyEvent) {
      if (keyEvent.key()
            == uiModule().getKeybind()
               .code()
         && !(this.getFocused() instanceof EditBox)) {
         uiModule().enable();
         ClientContext.getClickGui()
            .open();
         return false;
      } else {
         return super.keyPressed(keyEvent);
      }
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      String reasonText = this.details.reason().getString();
      if (reasonText.contains("Collective") && reasonText.contains("[AutoLog]")) {
         this.autoReconnectButton
            .setX(-1000);
         this.autoReconnectButton
            .setY(-1000);
      } else {
         this.tryAutoReconnect();
         this.autoReconnectButton
            .setMessage(this.getAutoReconnectButtonMessage());
         this.autoReconnectButton.setFocused(false);
         super.extractRenderState(graphics, mouseX, mouseY, partialTick);
      }
   }

   private void tryAutoReconnect() {
      if (autoReconnectModule().isToggled()) {
         float remainingSeconds = this.getReconnectSecondsRemaining(
            autoReconnectModule()
         );
         if (!(remainingSeconds > 0.0F)) {
            ServerData serverData = ClientContext.serverStateManager.getServerData();
            if (serverData != null) {
               ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), this.minecraft, ServerAddress.parseString(serverData.ip), serverData, true, null);
            }
         }
      }
   }

   private Component getAutoReconnectButtonMessage() {
      MutableComponent message = Component.literal("AutoReconnect ");
      String reasonText = this.details.reason().getString();
      boolean showCountdown = autoReconnectModule().isToggled()
         && (!reasonText.contains("Collective") || !reasonText.contains("[AutoLog]"));
      float remainingSeconds = this.getReconnectSecondsRemaining(
         autoReconnectModule()
      );
      if (ClientContext.serverStateManager.autoLogged
         )
       {
         return message.append(ChatFormatting.RED + "AutoLogged");
      } else {
         return showCountdown ? message.append(ChatFormatting.GREEN + "%.1fs".formatted(remainingSeconds)) : message.append(ChatFormatting.RED + "OFF");
      }
   }

   private float getReconnectSecondsRemaining(AutoReconnectModule module) {
      if (module == null) {
         return 0.1F;
      }

      float delaySeconds = module.secondsSetting.getValue()
         + module.minutesSetting.getValue()
            * 60;
      return Math.max(
         delaySeconds
            - (float)(System.currentTimeMillis() - this.reconnectTimerStartMillis)
               / 1000.0F,
         0.0F
      );
   }
}

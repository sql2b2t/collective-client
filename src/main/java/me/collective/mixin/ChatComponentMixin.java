package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import me.collective.client.modules.misc.BetterChatModule;
import me.collective.client.modules.hud.ChatModule;
import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.events.ChatMessageEvent;
import me.collective.client.access.ChatLineAccess;
import me.collective.client.access.VisibleChatLinesAccess;
import me.collective.client.utils.chat.ChatUtils;
import me.collective.client.render.FontActiveTextCollector;
import me.collective.client.managers.CommandRegistry;
import me.collective.client.modules.client.FontsModule;
import me.collective.client.commands.PanicCommand;
import java.util.ConcurrentModificationException;
import java.util.List;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessage.Line;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.ChatComponent.ChatGraphicsAccess;
import net.minecraft.client.gui.components.ChatComponent.DrawingBackgroundGraphicsAccess;
import net.minecraft.client.gui.components.ChatComponent.DrawingFocusedGraphicsAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin implements ClientAccess, VisibleChatLinesAccess {
   private static BetterChatModule betterChatModule;
   private static ChatModule chatModule;
   private static FontsModule fontsModule;
   private static BetterChatModule betterChatModule() {
      if (betterChatModule == null) {
         betterChatModule = ClientContext.getModule(BetterChatModule.class);
      }
      return betterChatModule;
   }
   private static ChatModule chatModule() {
      if (chatModule == null) {
         chatModule = ClientContext.getModule(ChatModule.class);
      }
      return chatModule;
   }
   private static FontsModule fontsModule() {
      if (fontsModule == null) {
         fontsModule = ClientContext.getModule(FontsModule.class);
      }
      return fontsModule;
   }
   @Shadow
   @Final
   public List trimmedMessages;
   @Unique
   private MessageSignature pendingMessageSignature;
   @Unique
   private static boolean skip;

   @Shadow
   protected abstract void logChatMessage(GuiMessage message);

   @Shadow
   protected abstract void addMessageToDisplayQueue(GuiMessage message);

   @Shadow
   protected abstract void addMessageToQueue(GuiMessage message);

   @Shadow
   protected abstract void addMessage(Component message, @Nullable MessageSignature messageSignature, net.minecraft.client.multiplayer.chat.GuiMessageSource source, @Nullable GuiMessageTag tag);

   @Inject(method = "logChatMessage", at = @At("HEAD"), cancellable = true)
   private void cancelTaggedMessageLogging(GuiMessage message, CallbackInfo callback) {
      if (message.tag() == ChatUtils.MESSAGE_TAG) {
         callback.cancel();
      }
   }

   @Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"), cancellable = true)
   private void beforeAddMessageToDisplayQueue(GuiMessage message, CallbackInfo callback) {
      if (message.tag() == ChatUtils.MESSAGE_TAG
         && PanicCommand.unloaded) {
         callback.cancel();
      } else {
         BetterChatModule.addingMessageToDisplayQueue = true;
         MessageSignature messageSignature = message.signature();
         this.pendingMessageSignature = messageSignature;
         if (messageSignature != null && messageSignature.asByteBuffer().getInt() < 0) {
            try {
               ClientAccess.collectiveMinecraft.gui.hud
                  .getChat()
                  .trimmedMessages
                  .removeIf(candidateLine -> candidateLine != null && ((ChatLineAccess)(Object)candidateLine).getSignature() != null && ((ChatLineAccess)(Object)candidateLine).getSignature().equals(messageSignature));
            } catch (ConcurrentModificationException ignoredConcurrentModification) {
            }
         }
      }
   }

   @Inject(method = "addMessageToDisplayQueue", at = @At("RETURN"))
   private void afterAddMessageToDisplayQueue(GuiMessage message, CallbackInfo callback) {
      BetterChatModule.addingMessageToDisplayQueue = false;
   }

   @WrapWithCondition(method = "addMessageToDisplayQueue", at = @At(value = "INVOKE", target = "Ljava/util/List;addFirst(Ljava/lang/Object;)V", ordinal = 0))
   private boolean attachSignatureAndPostChatLineEvent(List lines, Object entry) {
      ((ChatLineAccess)entry).setSignature(this.pendingMessageSignature);
      Line line = (Line)entry;
      ChatMessageEvent event = new ChatMessageEvent(line);
      ClientAccess.eventBus
         .post(event);
      return true;
   }

   // Mojang leaves the chat background render lambda unnamed.
   @WrapWithCondition(method = "method_75802(IILnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IFLnet/minecraft/client/GuiMessage$Line;IF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;fill(IIIII)V", ordinal = 0))
   private static boolean queueCustomChatBackground(
      ChatGraphicsAccess graphicsAccess, int left, int top, int right, int bottom, int color
   ) {
      if (fontsModule().isToggled()
         && fontsModule().chatSetting.getValue()) {
         Matrix3x2fStack poses = switch (graphicsAccess) {
            case DrawingFocusedGraphicsAccess focusedGraphics -> focusedGraphics.graphics.pose();
            case DrawingBackgroundGraphicsAccess backgroundGraphics -> backgroundGraphics.graphics.pose();
            default -> null;
         };
         if (poses == null) {
            return true;
         }

         fontsModule().enqueueRectangle(
            poses, left, top, right, bottom, color
         );
         return false;
      } else {
         return true;
      }
   }

   // Mojang leaves the chat translation lambda unnamed.
   @Inject(method = "method_75801(FLorg/joml/Matrix3x2f;)V", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix3x2f;translate(FF)Lorg/joml/Matrix3x2f;", remap = false, ordinal = 0))
   private static void applyChatTranslation(
      float ignoredRenderFloat, Matrix3x2f matrix, CallbackInfo callback
   ) {
      float offsetX = 0.0F;
      float offsetY = 0.0F;
      if (chatModule().isToggled()) {
         offsetX += chatModule().getChatOffsetX();
         offsetY += chatModule().getChatOffsetY();
      }

      if (betterChatModule().isAnimationEnabled()
         && betterChatModule().typeSetting.getValue()
            == BetterChatModule.TypeChoice.BOUNCE) {
         offsetY += betterChatModule().bounceAnimation
               .getValue()
            * 8.0F;
      }

      if (offsetX != 0.0F || offsetY != 0.0F) {
         matrix.translate(offsetX, offsetY);
      }
   }

   @Inject(
      method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;logChatMessage(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V", shift = Shift.BEFORE),
      cancellable = true
   )
   private void interceptChatMessage(
      Component message, MessageSignature messageSignature, net.minecraft.client.multiplayer.chat.GuiMessageSource source, GuiMessageTag tag, CallbackInfo callback, @Local GuiMessage queuedMessage
   ) {
      if (!skip) {
         Component originalMessage = message;
         ChatMessageEvent event = new ChatMessageEvent(tag, messageSignature, message);
         ClientAccess.eventBus
            .post(event);
         message = event.getMessage();
         messageSignature = event.getSignature();
         callback.cancel();
         if (!event.isCancelled()) {
            if (!originalMessage.equals(message)) {
               skip = true;
               this.addMessage(message, messageSignature, source, tag);
               skip = false;
               return;
            }

            this.logChatMessage(queuedMessage);
            this.addMessageToDisplayQueue(queuedMessage);
            this.addMessageToQueue(queuedMessage);
         }
      }
   }

   @ModifyExpressionValue(method = "addMessageToDisplayQueue", at = @At(value = "INVOKE", target = "Ljava/util/List;size()I", ordinal = 2))
   public int overrideDisplayQueueSize(int originalSize) {
      return betterChatModule().isToggled()
            && betterChatModule().longChatHistorySetting.getValue()
         ? 1
         : originalSize;
   }

   @ModifyExpressionValue(method = "addMessageToQueue", at = @At(value = "INVOKE", target = "Ljava/util/List;size()I", ordinal = 0))
   public int overrideMessageQueueSize(int originalSize) {
      return betterChatModule().isToggled()
            && betterChatModule().longChatHistorySetting.getValue()
         ? 1
         : originalSize;
   }

   @Inject(method = "clearMessages", at = @At("HEAD"), cancellable = true)
   private void preserveChatHistoryOnClear(boolean clearHistory, CallbackInfo callback) {
      if (betterChatModule().isToggled()
         && betterChatModule().noResetSetting.getValue()
         && !BetterChatModule.clearingChatFromDebugKey) {
         callback.cancel();
      }
   }

   @ModifyExpressionValue(method = "addRecentChat", at = @At(value = "INVOKE", target = "Ljava/lang/String;startsWith(Ljava/lang/String;)Z"))
   private boolean recognizeClientCommandPrefix(
      boolean originalCommandMatch, @Local(argsOnly = true) String message
   ) {
      if (originalCommandMatch) {
         return true;
      }
      return message.startsWith(
            CommandRegistry.getPrefix()
         );
   }

   @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIIZZ)V", at = @At("HEAD"))
   private void enableCustomChatText(
      GuiGraphicsExtractor graphics, Font font, int ignoredRenderInt1, int ignoredRenderInt2, int ignoredRenderInt3, boolean ignoredRenderFlag1, boolean ignoredRenderFlag2, CallbackInfo callback
   ) {
      FontActiveTextCollector.customChatTextEnabled = fontsModule().isToggled()
         && fontsModule().chatSetting.getValue();
   }

   @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIIZZ)V", at = @At("RETURN"))
   private void disableCustomChatText(
      GuiGraphicsExtractor graphics, Font font, int ignoredRenderInt1, int ignoredRenderInt2, int ignoredRenderInt3, boolean ignoredRenderFlag1, boolean ignoredRenderFlag2, CallbackInfo callback
   ) {
      FontActiveTextCollector.customChatTextEnabled = false;
   }

   @Override
   public List getVisible() {
      return this.trimmedMessages;
   }
}

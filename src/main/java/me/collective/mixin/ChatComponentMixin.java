package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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
import me.collective.client.modules.render.NoRenderModule;
import me.collective.client.commands.PanicCommand;
import java.util.ConcurrentModificationException;
import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessage.Line;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
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
   private static NoRenderModule noRenderModule;
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
   private static NoRenderModule noRenderModule() {
      if (noRenderModule == null) {
         noRenderModule = ClientContext.getModule(NoRenderModule.class);
      }
      return noRenderModule;
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
   public abstract void addMessage(Component message, @Nullable MessageSignature messageSignature, @Nullable GuiMessageTag tag);

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
               ClientAccess.collectiveMinecraft.gui
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

   @WrapWithCondition(method = "addMessageToDisplayQueue", at = @At(value = "INVOKE", target = "Ljava/util/List;add(ILjava/lang/Object;)V", ordinal = 0))
   private boolean attachSignatureAndPostChatLineEvent(List lines, int index, Object entry) {
      ((ChatLineAccess)entry).setSignature(this.pendingMessageSignature);
      Line line = (Line)entry;
      ChatMessageEvent event = new ChatMessageEvent(line);
      ClientAccess.eventBus
         .post(event);
      return true;
   }

   @WrapWithCondition(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", ordinal = 0))
   private boolean queueCustomChatBackground(
      GuiGraphics graphics, int left, int top, int right, int bottom, int color
   ) {
      if (fontsModule().isToggled()
         && fontsModule().chatSetting.getValue()) {
         Matrix4f m = graphics.pose().last().pose();
         Matrix3x2f mat2d = new Matrix3x2f(m.m00(), m.m01(), m.m10(), m.m11(), m.m30(), m.m31());
         fontsModule().enqueueRectangle(
            mat2d, left, top, right, bottom, color
         );
         return false;
      } else {
         return true;
      }
   }

   @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0, shift = Shift.AFTER))
   private void applyChatTranslation(
      GuiGraphics graphics, int tickCount, int mouseX, int mouseY, boolean focused, CallbackInfo callback
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
         graphics.pose().translate(offsetX, offsetY, 0.0F);
      }
   }

   @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/GuiMessage$Line;tag()Lnet/minecraft/client/GuiMessageTag;"))
   private GuiMessageTag hideMessageIndicator(GuiMessageTag originalTag) {
      return noRenderModule().isToggled()
            && noRenderModule().messageIndicatorSetting.getValue()
         ? null
         : originalTag;
   }

   @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I"))
   private int animateAndOffsetChatLine(
      GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color, Operation<Integer> original, @Local Line line
   ) {
      ChatLineAccess lineAccess = (ChatLineAccess)(Object)line;
      Minecraft minecraft = Minecraft.getInstance();
      if (noRenderModule().isToggled() && noRenderModule().messageIndicatorSetting.getValue()) {
         x -= 3;
      }
      if (betterChatModule().isAnimationEnabled()) {
         lineAccess.getProgress().setTarget(true);
         float progress = lineAccess.getProgress().updateAndGet();
         long addedAtMillis = lineAccess.collective$getAddTime();
         boolean animateBothAxes = betterChatModule().typeSetting.getValue() == BetterChatModule.TypeChoice.BOTH;
         if (betterChatModule().typeSetting.getValue() == BetterChatModule.TypeChoice.VERTICAL || animateBothAxes) {
            y = (int)(y + 9.0F * (1.0F - progress));
         }
         if (betterChatModule().typeSetting.getValue() == BetterChatModule.TypeChoice.HORIZONTAL || animateBothAxes) {
            x = (int)(x - minecraft.font.width(text) * (1.0F - progress));
         }
         if (betterChatModule().alphaSetting.getValue() != 0) {
            float fadeProgress = Mth.clamp(
               (float)(System.currentTimeMillis() - addedAtMillis)
                  / (betterChatModule().alphaSetting.getValue()).intValue(),
               0.0F,
               1.0F
            );
            int alpha = (int)(((color >>> 24) & 0xFF) * fadeProgress);
            if (alpha < 13) {
               alpha = 0;
            }
            color = (color & 0x00FFFFFF) | (alpha << 24);
         }
      }
      return original.call(graphics, font, text, x, y, color);
   }

   @Inject(
      method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;logChatMessage(Lnet/minecraft/client/GuiMessage;)V", shift = Shift.BEFORE),
      cancellable = true
   )
   private void interceptChatMessage(
      Component message, MessageSignature messageSignature, GuiMessageTag tag, CallbackInfo callback, @Local GuiMessage queuedMessage
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
               this.addMessage(message, messageSignature, tag);
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

   @ModifyExpressionValue(method = "addRecentChat", at = @At(value = "INVOKE", target = "Ljava/lang/String;startsWith(Ljava/lang/String;)Z"), require = 0)
   private boolean recognizeClientCommandPrefix(
      boolean originalCommandMatch, @Local(argsOnly = true) String message
   ) {
      if (originalCommandMatch) {
         return true;
      }
      return CommandRegistry.isClientCommand(message);
   }

   @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIIZ)V", at = @At("HEAD"))
   private void enableCustomChatText(
      GuiGraphics graphics, int tickCount, int mouseX, int mouseY, boolean focused, CallbackInfo callback
   ) {
      FontActiveTextCollector.customChatTextEnabled = fontsModule().isToggled()
         && fontsModule().chatSetting.getValue();
   }

   @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIIZ)V", at = @At("RETURN"))
   private void disableCustomChatText(
      GuiGraphics graphics, int tickCount, int mouseX, int mouseY, boolean focused, CallbackInfo callback
   ) {
      FontActiveTextCollector.customChatTextEnabled = false;
   }

   @Override
   public List getVisible() {
      return this.trimmedMessages;
   }
}

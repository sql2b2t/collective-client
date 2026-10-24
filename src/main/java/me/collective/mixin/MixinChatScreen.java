package me.collective.mixin;

import me.collective.command.CommandManager;
import me.collective.event.EventBus;
import me.collective.event.events.SendMessageEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public class MixinChatScreen {

    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "handleChatInput(Ljava/lang/String;Z)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String modifyChatMessage(String chatText) {
        if (chatText == null) return null;
        if (chatText.startsWith(CommandManager.INSTANCE.getPrefix())) {
            return chatText;
        }

        SendMessageEvent event = new SendMessageEvent(chatText);
        EventBus.INSTANCE.post(event);
        if (event.isCancelled()) {
            return "";
        }
        return event.message != null ? event.message : chatText;
    }

    @Inject(method = "handleChatInput(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
    private void onSendMessage(String chatText, boolean addToHistory, CallbackInfo ci) {
        if (chatText != null && chatText.startsWith(CommandManager.INSTANCE.getPrefix())) {
            boolean handled = CommandManager.INSTANCE.execute(chatText);
            if (handled) {
                ci.cancel();
            }
        }
    }
}
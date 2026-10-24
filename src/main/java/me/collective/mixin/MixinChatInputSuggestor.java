package me.collective.mixin;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.collective.command.Command;
import me.collective.command.CommandManager;
import me.collective.module.Module;
import me.collective.module.ModuleManager;
import me.collective.setting.Setting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mixin(ChatInputSuggestor.class)
public abstract class MixinChatInputSuggestor {

    @Shadow
    @Final
    TextFieldWidget textField;

    @Shadow
    @Final
    TextRenderer textRenderer;

    @Shadow
    @Final
    private Screen owner;

    @Shadow
    private List<OrderedText> messages;

    @Shadow
    private int x;

    @Shadow
    private int width;

    @Shadow
    private CompletableFuture<Suggestions> pendingSuggestions;

    @Shadow
    public abstract void show(boolean narrateFirstSuggestion);

    @Inject(method = "refresh", at = @At("HEAD"), cancellable = true)
    private void onRefresh(CallbackInfo ci) {
        String text = this.textField.getText();
        String prefix = CommandManager.INSTANCE.getPrefix();

        if (text != null && text.startsWith(prefix)) {
            ci.cancel();

            this.messages.clear();

            String query = text.substring(prefix.length());
            int cursor = this.textField.getCursor();

            // Позиция начала последнего слова (аргумента)
            int lastSpace = text.lastIndexOf(' ');
            int wordStart = (lastSpace == -1) ? prefix.length() : lastSpace + 1;
            String currentArg = (lastSpace == -1) ? query : text.substring(wordStart);

            SuggestionsBuilder builder = new SuggestionsBuilder(text, wordStart);

            Command matchedCmd = null;
            String matchedCmdName = null;

            if (!query.contains(" ")) {
                // 1. Предложения названий команд и алиасов
                for (Command cmd : CommandManager.INSTANCE.getCommands()) {
                    if (cmd.getName().toLowerCase().startsWith(query.toLowerCase())) {
                        builder.suggest(cmd.getName(), new LiteralMessage(cmd.getDescription()));
                    }
                    for (String alias : cmd.getAliases()) {
                        if (alias.toLowerCase().startsWith(query.toLowerCase())) {
                            builder.suggest(alias, new LiteralMessage(cmd.getDescription()));
                        }
                    }
                }

                // Если уже совпадает ровно одна команда — покажем синтаксис
                for (Command cmd : CommandManager.INSTANCE.getCommands()) {
                    if (cmd.getName().equalsIgnoreCase(query)) {
                        matchedCmd = cmd;
                        break;
                    }
                    for (String a : cmd.getAliases()) {
                        if (a.equalsIgnoreCase(query)) {
                            matchedCmd = cmd;
                            break;
                        }
                    }
                }
            } else {
                // 2. Предложения аргументов для команды
                String[] parts = query.split("\\s+", -1);
                String cmdName = parts[0];

                for (Command cmd : CommandManager.INSTANCE.getCommands()) {
                    if (cmd.getName().equalsIgnoreCase(cmdName)) {
                        matchedCmd = cmd;
                        matchedCmdName = cmd.getName();
                        break;
                    }
                    for (String a : cmd.getAliases()) {
                        if (a.equalsIgnoreCase(cmdName)) {
                            matchedCmd = cmd;
                            matchedCmdName = a;
                            break;
                        }
                    }
                }

                if (matchedCmd != null) {
                    fillArgumentSuggestions(matchedCmd, parts, currentArg, builder);
                }
            }

            // 3. Вывод подсказки по синтаксису команды (Usage) над полем ввода
            if (matchedCmd != null) {
                String syntax = matchedCmd.getSyntax();
                if (syntax != null && !syntax.isEmpty()) {
                    this.messages.add(OrderedText.styledForwardsVisitedString(
                        syntax,
                        Style.EMPTY.withColor(ChatFormatting.GRAY)
                    ));

                    if (matchedCmd.getDescription() != null && !matchedCmd.getDescription().isEmpty()) {
                        this.messages.add(OrderedText.styledForwardsVisitedString(
                            "  " + matchedCmd.getDescription(),
                            Style.EMPTY.withColor(ChatFormatting.DARK_GRAY)
                        ));
                    }

                    int textWidth = 0;
                    for (OrderedText ot : this.messages) {
                        textWidth = Math.max(textWidth, this.textRenderer.getWidth(ot));
                    }

                    int charX = this.textField.getCharacterX(prefix.length());
                    int maxRight = this.textField.getCharacterX(0) + this.textField.getInnerWidth();
                    this.x = MathHelper.clamp(charX, 0, Math.max(0, maxRight - textWidth));
                    this.width = textWidth;
                }
            }

            // 4. Построение выпадающего списка подсказок
            this.pendingSuggestions = builder.buildFuture();
            this.pendingSuggestions.thenRun(() -> {
                if (this.pendingSuggestions.isDone()) {
                    this.show(false);
                }
            });
        }
    }

    private void fillArgumentSuggestions(Command cmd, String[] parts, String currentArg, SuggestionsBuilder builder) {
        String name = cmd.getName().toLowerCase();
        int argIndex = parts.length - 1; // 1 = первый аргумент, 2 = второй, ...

        switch (name) {
            case "toggle", "t", "drawn", "d" -> {
                if (argIndex == 1) {
                    suggestModules(currentArg, builder);
                }
            }
            case "bind", "b" -> {
                if (argIndex == 1) {
                    suggestMatching(Arrays.asList("set", "clear"), currentArg, builder);
                } else if (argIndex == 2) {
                    if ("clear".equalsIgnoreCase(parts[1])) {
                        suggestMatching(List.of("all"), currentArg, builder);
                    }
                    suggestModules(currentArg, builder);
                }
            }
            case "elytrafly", "efly", "belytra" -> {
                if (argIndex == 1) {
                    suggestMatching(Arrays.asList("cancel", "stop", "goal", "~ ~"), currentArg, builder);
                }
            }
            case "сорт" -> {
                if (argIndex == 1) {
                    suggestMatching(Arrays.asList("зона", "метка", "старт", "стоп", "очистить", "помощь"), currentArg, builder);
                } else if (argIndex == 2 && "зона".equalsIgnoreCase(parts[1])) {
                    suggestMatching(List.of("очистить"), currentArg, builder);
                } else if (argIndex == 2 && "метка".equalsIgnoreCase(parts[1])) {
                    suggestMatching(List.of("снять"), currentArg, builder);
                }
            }
            case "перенос" -> {
                if (argIndex == 1) {
                    suggestMatching(Arrays.asList("вход", "выход", "старт", "помощь"), currentArg, builder);
                } else if (argIndex == 2 && ("вход".equalsIgnoreCase(parts[1]) || "выход".equalsIgnoreCase(parts[1]))) {
                    suggestMatching(Arrays.asList("1", "2", "очистить"), currentArg, builder);
                }
            }
            case "prefix" -> {
                if (argIndex == 1) {
                    suggestMatching(Arrays.asList(".", ",", "!", "@", "#", "$", "-", "/"), currentArg, builder);
                }
            }
            case "scripts", "script", "скрипты" -> {
                if (argIndex == 1) {
                    suggestMatching(Arrays.asList("list", "load", "unload", "reload", "folder"), currentArg, builder);
                } else if (argIndex == 2 && ("load".equalsIgnoreCase(parts[1]) || "unload".equalsIgnoreCase(parts[1]))) {
                    java.io.File scriptsDir = me.collective.scripting.ScriptManager.getInstance().getScriptsFolder().toFile();
                    if (scriptsDir.exists() && scriptsDir.isDirectory()) {
                        java.io.File[] files = scriptsDir.listFiles((dir, n) ->
                            n.endsWith(".kts") || n.endsWith(".kt") || n.endsWith(".js") || n.endsWith(".groovy"));
                        if (files != null) {
                            List<String> names = new ArrayList<>();
                            for (java.io.File f : files) names.add(f.getName());
                            suggestMatching(names, currentArg, builder);
                        }
                    }
                }
            }
            case "addon" -> {
                if (argIndex == 1) {
                    suggestMatching(Arrays.asList("list", "reload", "folder"), currentArg, builder);
                }
            }
            case "config", "cfg", "конфиг" -> {
                if (argIndex == 1) {
                    suggestMatching(Arrays.asList("save", "load", "list", "delete", "folder"), currentArg, builder);
                } else if (argIndex == 2 && ("load".equalsIgnoreCase(parts[1]) || "delete".equalsIgnoreCase(parts[1]))) {
                    suggestMatching(me.collective.config.ConfigManager.getInstance().list(), currentArg, builder);
                }
            }
            case "hudimage", "setimage", "img" -> {
                if (argIndex == 1) {
                    suggestMatching(Arrays.asList("Collective", "Komod1", "Komod2", "Komod3"), currentArg, builder);
                }
            }
            default -> {
                // Для других команд ничего не подсказываем автоматически
            }
        }
    }

    private void suggestModules(String currentArg, SuggestionsBuilder builder) {
        String query = currentArg.toLowerCase();
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (m.getName().toLowerCase().startsWith(query)) {
                builder.suggest(m.getName(), new LiteralMessage(m.getDescription()));
            }
        }
    }

    private void suggestMatching(Iterable<String> candidates, String currentArg, SuggestionsBuilder builder) {
        String query = currentArg.toLowerCase();
        for (String c : candidates) {
            if (c.toLowerCase().startsWith(query)) {
                builder.suggest(c);
            }
        }
    }
}
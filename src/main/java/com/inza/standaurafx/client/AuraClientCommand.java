package com.inza.standaurafx.client;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.inza.standaurafx.client.render.AuraRuntimeSettings;
import com.inza.standaurafx.client.render.AuraRuntimeSettings.AuraMode;
import com.inza.standaurafx.client.render.AuraRuntimeSettings.Parameter;

import net.minecraft.client.Minecraft;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.ClientChatEvent;

public final class AuraClientCommand {
    private static final String COMMAND = "/standaurafx";
    private static final String SHORT_COMMAND = "/safx";
    private static final String PREFIX = TextFormatting.AQUA + "[StandAuraFX] " + TextFormatting.RESET;
    private static CommandDispatcher<ISuggestionProvider> registeredDispatcher;

    private AuraClientCommand() {
    }

    public static void register(CommandDispatcher<ISuggestionProvider> dispatcher) {
        if (dispatcher == null) {
            return;
        }

        boolean hasShortCommand = dispatcher.getRoot().getChild("safx") != null;
        boolean hasFullCommand = dispatcher.getRoot().getChild("standaurafx") != null;
        if (dispatcher == registeredDispatcher && hasShortCommand && hasFullCommand) {
            return;
        }

        if (!hasShortCommand) {
            registerRoot(dispatcher, "safx");
        }
        if (!hasFullCommand) {
            registerRoot(dispatcher, "standaurafx");
        }

        registeredDispatcher = dispatcher;
    }

    private static void registerRoot(CommandDispatcher<ISuggestionProvider> dispatcher, String name) {
        dispatcher.register(
            literal(name)
                .executes(context -> {
                    sendHelp();
                    return 1;
                })
                .then(literal("help")
                    .executes(context -> {
                        sendHelp();
                        return 1;
                    }))
                .then(literal("list")
                    .executes(context -> {
                        sendParameterList();
                        return 1;
                    }))
                .then(literal("params")
                    .executes(context -> {
                        sendParameterList();
                        return 1;
                    }))
                .then(literal("mode")
                    .executes(context -> {
                        sendMode();
                        return 1;
                    })
                    .then(argument("mode", StringArgumentType.word())
                        .suggests(AuraClientCommand::suggestModes)
                        .executes(AuraClientCommand::executeMode)))
                .then(literal("get")
                    .then(argument("parameter", StringArgumentType.word())
                        .suggests(AuraClientCommand::suggestParameters)
                        .executes(AuraClientCommand::executeGet)))
                .then(literal("set")
                    .then(argument("parameter", StringArgumentType.word())
                        .suggests(AuraClientCommand::suggestParameters)
                        .then(argument("value", FloatArgumentType.floatArg())
                            .executes(AuraClientCommand::executeSet))))
                .then(literal("reset")
                    .executes(context -> {
                        AuraRuntimeSettings.resetAll();
                        sendOk("All aura parameters reset");
                        return 1;
                    })
                    .then(argument("parameter", StringArgumentType.word())
                        .suggests(AuraClientCommand::suggestParameters)
                        .executes(AuraClientCommand::executeReset)))
        );
    }

    private static LiteralArgumentBuilder<ISuggestionProvider> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private static <T> RequiredArgumentBuilder<ISuggestionProvider, T> argument(
        String name,
        com.mojang.brigadier.arguments.ArgumentType<T> type
    ) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    private static CompletableFuture<Suggestions> suggestParameters(
        CommandContext<ISuggestionProvider> context,
        SuggestionsBuilder builder
    ) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (Parameter parameter : AuraRuntimeSettings.getParameters()) {
            if (parameter.getName().toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(parameter.getName());
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestModes(
        CommandContext<ISuggestionProvider> context,
        SuggestionsBuilder builder
    ) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (AuraMode mode : AuraMode.values()) {
            if (mode.getName().startsWith(remaining)) {
                builder.suggest(mode.getName());
            }
        }
        return builder.buildFuture();
    }

    private static int executeMode(CommandContext<ISuggestionProvider> context) {
        String name = StringArgumentType.getString(context, "mode");
        AuraMode mode = AuraMode.fromName(name);
        if (mode == null) {
            sendUnknownMode(name);
            return 0;
        }

        AuraRuntimeSettings.setAuraMode(mode);
        sendMode();
        return 1;
    }

    private static int executeGet(CommandContext<ISuggestionProvider> context) {
        Parameter parameter = AuraRuntimeSettings.getParameter(StringArgumentType.getString(context, "parameter"));
        if (parameter == null) {
            sendUnknownParameter(StringArgumentType.getString(context, "parameter"));
            return 0;
        }

        sendParameter(parameter);
        return 1;
    }

    private static int executeSet(CommandContext<ISuggestionProvider> context) {
        String name = StringArgumentType.getString(context, "parameter");
        Parameter parameter = AuraRuntimeSettings.getParameter(name);
        if (parameter == null) {
            sendUnknownParameter(name);
            return 0;
        }

        float value = FloatArgumentType.getFloat(context, "value");
        if (!parameter.canSet(value)) {
            sendError(
                parameter.getName() + " must be between "
                    + format(parameter.getMinValue()) + " .. "
                    + format(parameter.getMaxValue())
            );
            return 0;
        }

        parameter.set(value);
        sendOk(parameter.getName() + " = " + format(parameter.get()));
        return 1;
    }

    private static int executeReset(CommandContext<ISuggestionProvider> context) {
        String name = StringArgumentType.getString(context, "parameter");
        Parameter parameter = AuraRuntimeSettings.getParameter(name);
        if (parameter == null) {
            sendUnknownParameter(name);
            return 0;
        }

        parameter.reset();
        sendOk(parameter.getName() + " reset to " + format(parameter.get()));
        return 1;
    }

    public static void handle(ClientChatEvent event) {
        String message = event.getMessage().trim();
        if (message.isEmpty()) {
            return;
        }

        String[] tokens = message.split("\\s+");
        if (!COMMAND.equalsIgnoreCase(tokens[0]) && !SHORT_COMMAND.equalsIgnoreCase(tokens[0])) {
            return;
        }

        event.setCanceled(true);
        execute(tokens);
    }

    private static void execute(String[] tokens) {
        if (tokens.length == 1 || "help".equalsIgnoreCase(tokens[1])) {
            sendHelp();
            return;
        }

        String action = tokens[1].toLowerCase(Locale.ROOT);
        if ("list".equals(action) || "params".equals(action)) {
            sendParameterList();
            return;
        }

        if ("get".equals(action)) {
            handleGet(tokens);
            return;
        }

        if ("mode".equals(action)) {
            handleMode(tokens);
            return;
        }

        if ("set".equals(action)) {
            handleSet(tokens);
            return;
        }

        if ("reset".equals(action)) {
            handleReset(tokens);
            return;
        }

        sendError("Unknown subcommand: " + tokens[1]);
        sendHelp();
    }

    private static void handleGet(String[] tokens) {
        if (tokens.length != 3) {
            sendError("Usage: /safx get <parameter>");
            return;
        }

        Parameter parameter = AuraRuntimeSettings.getParameter(tokens[2]);
        if (parameter == null) {
            sendUnknownParameter(tokens[2]);
            return;
        }

        sendParameter(parameter);
    }

    private static void handleMode(String[] tokens) {
        if (tokens.length == 2) {
            sendMode();
            return;
        }

        if (tokens.length != 3) {
            sendError("Usage: /safx mode <close|auto|open>");
            return;
        }

        AuraMode mode = AuraMode.fromName(tokens[2]);
        if (mode == null) {
            sendUnknownMode(tokens[2]);
            return;
        }

        AuraRuntimeSettings.setAuraMode(mode);
        sendMode();
    }

    private static void handleSet(String[] tokens) {
        if (tokens.length != 4) {
            sendError("Usage: /safx set <parameter> <value>");
            return;
        }

        Parameter parameter = AuraRuntimeSettings.getParameter(tokens[2]);
        if (parameter == null) {
            sendUnknownParameter(tokens[2]);
            return;
        }

        float value;
        try {
            value = Float.parseFloat(tokens[3]);
        }
        catch (NumberFormatException exception) {
            sendError("Invalid value: " + tokens[3]);
            return;
        }

        if (!parameter.canSet(value)) {
            sendError(
                parameter.getName() + " must be between "
                    + format(parameter.getMinValue()) + " .. "
                    + format(parameter.getMaxValue())
            );
            return;
        }

        parameter.set(value);
        sendOk(parameter.getName() + " = " + format(parameter.get()));
    }

    private static void handleReset(String[] tokens) {
        if (tokens.length == 2) {
            AuraRuntimeSettings.resetAll();
            sendOk("All aura parameters reset");
            return;
        }

        if (tokens.length != 3) {
            sendError("Usage: /safx reset [parameter]");
            return;
        }

        Parameter parameter = AuraRuntimeSettings.getParameter(tokens[2]);
        if (parameter == null) {
            sendUnknownParameter(tokens[2]);
            return;
        }

        parameter.reset();
        sendOk(parameter.getName() + " reset to " + format(parameter.get()));
    }

    private static void sendHelp() {
        sendInfo("Usage:");
        sendInfo("/safx list");
        sendInfo("/safx mode [close|auto|open]");
        sendInfo("/safx get <parameter>");
        sendInfo("/safx set <parameter> <value>");
        sendInfo("/safx reset [parameter]");
        sendInfo("Example: /safx set fillAlphaBase 0.45");
    }

    private static void sendParameterList() {
        sendInfo("Editable parameters:");
        for (Parameter parameter : AuraRuntimeSettings.getParameters()) {
            sendParameter(parameter);
        }
    }

    private static void sendMode() {
        AuraMode mode = AuraRuntimeSettings.getAuraMode();
        sendInfo("Automatic aura mode = " + mode.getName());
    }

    private static void sendParameter(Parameter parameter) {
        sendInfo(
            parameter.getName()
                + " = " + format(parameter.get())
                + " default " + format(parameter.getDefaultValue())
                + " range " + format(parameter.getMinValue())
                + ".." + format(parameter.getMaxValue())
        );
    }

    private static void sendUnknownParameter(String name) {
        sendError("Unknown parameter: " + name + ". Use /safx list");
    }

    private static void sendUnknownMode(String name) {
        sendError("Unknown mode: " + name + ". Use close, auto, or open");
    }

    private static String format(float value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static void sendOk(String message) {
        send(TextFormatting.GREEN + message);
    }

    private static void sendInfo(String message) {
        send(TextFormatting.GRAY + message);
    }

    private static void sendError(String message) {
        send(TextFormatting.RED + message);
    }

    private static void send(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.ingameGUI != null) {
            minecraft.ingameGUI.getChatGUI().printChatMessage(new StringTextComponent(PREFIX + message));
        }
    }
}

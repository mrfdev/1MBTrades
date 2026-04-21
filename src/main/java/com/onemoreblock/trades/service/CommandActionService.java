package com.onemoreblock.trades.service;

import com.onemoreblock.trades.config.PluginSettings;
import com.onemoreblock.trades.model.TradeDefinition;
import com.onemoreblock.trades.model.TradeTrigger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class CommandActionService {
    private final JavaPlugin plugin;
    private final TradeManager tradeManager;
    private final PlaceholderService placeholderService;
    private PluginSettings settings;

    public CommandActionService(JavaPlugin plugin, TradeManager tradeManager, PlaceholderService placeholderService, PluginSettings settings) {
        this.plugin = plugin;
        this.tradeManager = tradeManager;
        this.placeholderService = placeholderService;
        this.settings = settings;
    }

    public void setSettings(PluginSettings settings) {
        this.settings = settings;
    }

    public void runIndexOpen(Player player) {
        execute(player, null, TradeTrigger.OPEN_INDEX, Map.of());
    }

    public void runTradeTrigger(Player player, TradeDefinition trade, TradeTrigger trigger, Map<String, String> extraPlaceholders) {
        execute(player, trade, trigger, extraPlaceholders);
    }

    public boolean hasChatFeedbackCommand(TradeDefinition trade, TradeTrigger trigger) {
        return collectCommands(trade, trigger).stream().anyMatch(this::looksLikeChatFeedbackCommand);
    }

    private void execute(Player player, TradeDefinition trade, TradeTrigger trigger, Map<String, String> extraPlaceholders) {
        List<String> commands = collectCommands(trade, trigger);
        if (commands.isEmpty()) {
            return;
        }

        Map<String, String> replacements = basePlaceholders(player, trade);
        replacements.putAll(extraPlaceholders);
        for (String rawCommand : commands) {
            executeOne(player, rawCommand, replacements);
        }
    }

    private List<String> collectCommands(TradeDefinition trade, TradeTrigger trigger) {
        List<String> commands = new ArrayList<>(settings.globalCommands(trigger));
        if (trade != null && trigger != TradeTrigger.OPEN_INDEX) {
            commands.addAll(trade.commands(trigger));
            String effectiveCtextFile = tradeManager.effectiveCtextFile(trade);
            if (trigger == TradeTrigger.INFO && commands.isEmpty() && !effectiveCtextFile.isBlank()) {
                commands.add("console:cmi ctext " + effectiveCtextFile + " %player%");
            }
        }
        return commands;
    }

    private boolean looksLikeChatFeedbackCommand(String rawCommand) {
        if (rawCommand == null || rawCommand.isBlank()) {
            return false;
        }

        String command = rawCommand.trim();
        ExecutionMode mode = ExecutionMode.CONSOLE;
        int prefixSeparator = command.indexOf(':');
        if (prefixSeparator > 0) {
            String prefix = command.substring(0, prefixSeparator).trim().toLowerCase(Locale.ROOT);
            ExecutionMode parsedMode = ExecutionMode.fromPrefix(prefix);
            if (parsedMode != null) {
                mode = parsedMode;
                command = command.substring(prefixSeparator + 1).trim();
            }
        }

        if (mode == ExecutionMode.MESSAGE) {
            return true;
        }
        if (mode == ExecutionMode.ACTIONBAR) {
            return false;
        }

        String lowered = command.toLowerCase(Locale.ROOT);
        return lowered.startsWith("cmi msg ")
            || lowered.startsWith("/cmi msg ")
            || lowered.startsWith("tellraw ")
            || lowered.startsWith("/tellraw ")
            || lowered.startsWith("minecraft:tellraw ");
    }

    private void executeOne(Player player, String rawCommand, Map<String, String> replacements) {
        if (rawCommand == null || rawCommand.isBlank()) {
            return;
        }

        ExecutionMode mode = ExecutionMode.CONSOLE;
        String command = rawCommand.trim();
        int prefixSeparator = command.indexOf(':');
        if (prefixSeparator > 0) {
            String prefix = command.substring(0, prefixSeparator).trim().toLowerCase();
            ExecutionMode parsedMode = ExecutionMode.fromPrefix(prefix);
            if (parsedMode != null) {
                mode = parsedMode;
                command = command.substring(prefixSeparator + 1).trim();
            }
        }

        String resolved = placeholderService.apply(player, command, replacements);
        if (usesDispatchedCommand(mode) && shouldRenderMiniMessageForCommand(resolved)) {
            resolved = placeholderService.miniMessageToLegacySection(resolved);
        }
        if (resolved.startsWith("/")) {
            resolved = resolved.substring(1);
        }
        if (resolved.isBlank()) {
            return;
        }

        switch (mode) {
            case CONSOLE -> dispatch(Bukkit.getConsoleSender(), resolved);
            case PLAYER -> dispatch(player, resolved);
            case MESSAGE -> player.sendMessage(placeholderService.component(player, resolved, Map.of()));
            case ACTIONBAR -> player.sendActionBar(placeholderService.component(player, resolved, Map.of()));
        }
    }

    private boolean usesDispatchedCommand(ExecutionMode mode) {
        return mode == ExecutionMode.CONSOLE || mode == ExecutionMode.PLAYER;
    }

    private boolean shouldRenderMiniMessageForCommand(String command) {
        if (!placeholderService.hasMiniMessageTags(command)) {
            return false;
        }

        String normalized = command == null ? "" : command.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }
        String lowered = normalized.toLowerCase(Locale.ROOT);
        if (lowered.startsWith("cmi ")) {
            lowered = lowered.substring("cmi ".length()).trim();
        } else if (lowered.startsWith("cmi:")) {
            lowered = lowered.substring("cmi:".length()).trim();
        }

        return lowered.startsWith("msg ")
            || lowered.startsWith("titlemsg ")
            || lowered.startsWith("actionbarmsg ")
            || lowered.startsWith("bossbarmsg ")
            || lowered.startsWith("broadcast ")
            || lowered.startsWith("toast ");
    }

    private void dispatch(CommandSender sender, String command) {
        Bukkit.dispatchCommand(sender, command);
    }

    private Map<String, String> basePlaceholders(Player player, TradeDefinition trade) {
        HashMap<String, String> replacements = new HashMap<>();
        replacements.put("player", player.getName());
        replacements.put("player_name", player.getName());
        replacements.put("player_uuid", player.getUniqueId().toString());
        if (trade != null) {
            int tradeUses = tradeManager.tradeUses(player, trade);
            String maxTrades = tradeManager.formattedMaxTrades(trade);
            String remainingTrades = tradeManager.formattedRemainingTrades(player, trade);
            double balance = tradeManager.playerBalance(player);
            replacements.put("id", trade.id());
            replacements.put("trade_id", trade.id());
            replacements.put("trade_name", trade.displayName());
            replacements.put("trade_description", String.join(" ", trade.description()));
            replacements.put("category", trade.category());
            replacements.put("trade_permission", tradeManager.effectivePermission(trade));
            replacements.put("ctext_file", tradeManager.effectiveCtextFile(trade));
            replacements.put("allowed_worlds", tradeManager.allowedWorldsDescription(trade));
            replacements.put("money_cost", tradeManager.formatMoney(trade.moneyCost()));
            replacements.put("exp_cost", String.valueOf(trade.expCost()));
            replacements.put("start_date", tradeManager.formattedDate(trade.startDate()));
            replacements.put("end_date", tradeManager.formattedDate(trade.endDate()));
            replacements.put("player_money", tradeManager.formatMoney(balance));
            replacements.put("player_level", String.valueOf(player.getLevel()));
            replacements.put("current_world", player.getWorld().getName());
            replacements.put("requirements_count", String.valueOf(trade.requirements().size()));
            replacements.put("required_items", tradeManager.summarizeItems(trade.requirements()));
            replacements.put("item_cost", String.valueOf(tradeManager.totalItemAmount(trade.requirements())));
            replacements.put("trade_uses", String.valueOf(tradeUses));
            replacements.put("max_trades", maxTrades);
            replacements.put("max_uses", maxTrades);
            replacements.put("remaining_trades", remainingTrades);
            replacements.put("remaining_uses", remainingTrades);
        } else {
            replacements.put("id", "");
            replacements.put("trade_id", "");
            replacements.put("trade_name", "");
            replacements.put("trade_description", "");
            replacements.put("category", "");
            replacements.put("trade_permission", "");
            replacements.put("ctext_file", "");
            replacements.put("allowed_worlds", "");
            replacements.put("money_cost", "0");
            replacements.put("exp_cost", "0");
            replacements.put("start_date", "");
            replacements.put("end_date", "");
            replacements.put("player_money", "0");
            replacements.put("player_level", player == null ? "0" : String.valueOf(player.getLevel()));
            replacements.put("current_world", player == null ? "" : player.getWorld().getName());
            replacements.put("requirements_count", "0");
            replacements.put("required_items", "");
            replacements.put("item_cost", "0");
            replacements.put("trade_uses", "0");
            replacements.put("max_trades", "0");
            replacements.put("max_uses", "0");
            replacements.put("remaining_trades", "0");
            replacements.put("remaining_uses", "0");
        }
        replacements.putIfAbsent("missing_items", "");
        replacements.putIfAbsent("missing_amount", "0");
        replacements.putIfAbsent("missing_money", "0");
        replacements.putIfAbsent("missing_exp", "0");
        replacements.putIfAbsent("missing_summary", "");
        return replacements;
    }

    private enum ExecutionMode {
        CONSOLE,
        PLAYER,
        MESSAGE,
        ACTIONBAR;

        private static ExecutionMode fromPrefix(String prefix) {
            return switch (prefix) {
                case "console" -> CONSOLE;
                case "player" -> PLAYER;
                case "message" -> MESSAGE;
                case "actionbar" -> ACTIONBAR;
                default -> null;
            };
        }
    }
}

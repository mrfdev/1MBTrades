package com.onemoreblock.trades.placeholder;

import com.onemoreblock.trades.OneMBTradesPlugin;
import com.onemoreblock.trades.model.TradeCheckResult;
import com.onemoreblock.trades.model.TradeDefinition;
import com.onemoreblock.trades.service.PlaceholderService;
import com.onemoreblock.trades.service.TradeManager;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class OneMBTradesPlaceholderExpansion extends PlaceholderExpansion implements PlaceholderRegistration {
    private static final String DEFAULT_FIELD = "display_name";

    private final OneMBTradesPlugin plugin;
    private final TradeManager tradeManager;
    private final PlaceholderService placeholderService;

    public OneMBTradesPlaceholderExpansion(
        OneMBTradesPlugin plugin,
        TradeManager tradeManager,
        PlaceholderService placeholderService
    ) {
        this.plugin = plugin;
        this.tradeManager = tradeManager;
        this.placeholderService = placeholderService;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "onembtrades";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean registerExpansion() {
        return register();
    }

    @Override
    public void unregisterExpansion() {
        unregister();
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        String normalized = params.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        Player player = offlinePlayer == null ? null : offlinePlayer.getPlayer();
        String key = normalized.toLowerCase(Locale.ROOT);

        return switch (key) {
            case "version", "plugin_version" -> plugin.getPluginMeta().getVersion();
            case "build" -> extractBuildNumber(plugin.getPluginMeta().getVersion());
            case "trades_loaded" -> String.valueOf(tradeManager.allTrades().size());
            case "enabled_trades" -> String.valueOf(enabledTradesCount());
            case "tracked_players" -> String.valueOf(tradeManager.trackedPlayersCount());
            case "visible_trades" -> player == null
                ? String.valueOf(enabledTradesCount())
                : String.valueOf(tradeManager.visibleTrades(player, player.hasPermission(plugin.settings().adminPermission())).size());
            case "ready_trades" -> player == null ? "" : String.valueOf(readyTradesCount(player));
            case "completed_trades" -> player == null ? "" : String.valueOf(completedTradesCount(player));
            default -> key.startsWith("trade.") ? resolveTradePlaceholder(player, normalized.substring("trade.".length())) : null;
        };
    }

    private @Nullable String resolveTradePlaceholder(@Nullable Player player, String spec) {
        TradePlaceholderQuery query = parseTradeQuery(spec);
        if (query == null) {
            return null;
        }

        TradeDefinition trade = tradeManager.findTrade(query.tradeId()).orElse(null);
        if (trade == null) {
            return null;
        }

        TradeCheckResult result = player == null ? null : tradeManager.evaluateTrade(player, trade);
        String field = query.field();
        return switch (field) {
            case DEFAULT_FIELD, "name", "display", "trade_name" -> plainTradeName(player, trade);
            case "display_formatted", "name_formatted" -> formattedTradeName(player, trade);
            case "display_name_plain", "name_plain" -> plainTradeName(player, trade);
            case "id", "trade_id" -> trade.id();
            case "enabled" -> yesNo(trade.enabled());
            case "enabled_raw" -> String.valueOf(trade.enabled());
            case "sort", "sort_order" -> String.valueOf(trade.sortOrder());
            case "description" -> plainTradeDescription(player, trade);
            case "description_formatted" -> formattedTradeDescription(player, trade);
            case "permission", "trade_permission" -> tradeManager.effectivePermission(trade);
            case "completion_permission" -> tradeManager.effectiveCompletionPermission(trade);
            case "ctext", "ctext_file" -> tradeManager.effectiveCtextFile(trade);
            case "requirements", "requirements_count" -> String.valueOf(trade.requirements().size());
            case "item_cost" -> String.valueOf(totalItemAmount(trade.requirements()));
            case "item_cost_summary", "required_items", "required_items_summary" -> tradeManager.summarizeItems(trade.requirements());
            case "trade_uses", "uses" -> player == null ? "" : String.valueOf(tradeManager.tradeUses(player, trade));
            case "remaining_trades", "remaining_uses" -> player == null ? "" : tradeManager.formattedRemainingTrades(player, trade);
            case "max_trades", "max_uses" -> tradeManager.formattedMaxTrades(trade);
            case "reward_item", "reward_preview" -> trade.rewardItem() == null ? "" : placeholderService.plainItemName(trade.rewardItem());
            case "icon_item" -> trade.iconItem() == null ? "" : placeholderService.plainItemName(trade.iconItem());
            case "status" -> statusText(player, trade, result);
            case "status_key" -> statusKey(player, trade, result);
            case "can_access" -> player == null ? "" : yesNo(trade.enabled() && tradeManager.hasAccess(player, trade, false));
            case "can_access_raw" -> player == null ? "" : String.valueOf(trade.enabled() && tradeManager.hasAccess(player, trade, false));
            case "can_trade" -> player == null || result == null ? "" : yesNo(result.success());
            case "can_trade_raw" -> player == null || result == null ? "" : String.valueOf(result.success());
            case "completed" -> player == null ? "" : yesNo(tradeManager.isCompleted(player, trade));
            case "completed_raw" -> player == null ? "" : String.valueOf(tradeManager.isCompleted(player, trade));
            case "missing_items" -> player == null || result == null ? "" : tradeManager.summarizeItems(result.missingItems());
            case "missing_amount" -> player == null || result == null ? "" : String.valueOf(totalItemAmount(result.missingItems()));
            default -> null;
        };
    }

    private @Nullable TradePlaceholderQuery parseTradeQuery(String spec) {
        if (spec == null || spec.isBlank()) {
            return null;
        }

        String trimmed = spec.trim();
        int dotIndex = trimmed.indexOf('.');
        if (dotIndex >= 0) {
            String tradeId = trimmed.substring(0, dotIndex);
            String field = trimmed.substring(dotIndex + 1);
            if (tradeId.isBlank()) {
                return null;
            }
            return new TradePlaceholderQuery(tradeId, field.isBlank() ? DEFAULT_FIELD : normalizeField(field));
        }

        for (String alias : knownFieldAliases()) {
            String suffix = "_" + alias;
            if (!trimmed.endsWith(suffix)) {
                continue;
            }
            String tradeId = trimmed.substring(0, trimmed.length() - suffix.length());
            if (!tradeId.isBlank()) {
                return new TradePlaceholderQuery(tradeId, normalizeField(alias));
            }
        }

        return new TradePlaceholderQuery(trimmed, DEFAULT_FIELD);
    }

    private List<String> knownFieldAliases() {
        return List.of(
            "completion_permission",
            "description_formatted",
            "display_formatted",
            "item_cost_summary",
            "required_items_summary",
            "remaining_trades",
            "remaining_uses",
            "requirements_count",
            "can_access_raw",
            "can_trade_raw",
            "completed_raw",
            "display_name_plain",
            "status_key",
            "sort_order",
            "missing_amount",
            "missing_items",
            "trade_permission",
            "ctext_file",
            "reward_preview",
            "reward_item",
            "can_access",
            "can_trade",
            "completed",
            "icon_item",
            "item_cost",
            "max_trades",
            "max_uses",
            "trade_name",
            "trade_id",
            "trade_uses",
            "uses",
            "required_items",
            "enabled_raw",
            "enabled",
            "display_name",
            "description",
            "permission",
            "requirements",
            "status",
            "display",
            "ctext",
            "name",
            "sort",
            "id"
        ).stream()
            .sorted(Comparator.comparingInt(String::length).reversed())
            .toList();
    }

    private String normalizeField(String field) {
        return field.trim().toLowerCase(Locale.ROOT);
    }

    private String plainTradeName(@Nullable Player player, TradeDefinition trade) {
        return placeholderService.plainText(player, trade.displayName(), tradeReplacements(trade));
    }

    private String formattedTradeName(@Nullable Player player, TradeDefinition trade) {
        return placeholderService.legacyText(player, trade.displayName(), tradeReplacements(trade));
    }

    private String plainTradeDescription(@Nullable Player player, TradeDefinition trade) {
        return placeholderService.plainText(player, String.join(" ", trade.description()), tradeReplacements(trade));
    }

    private String formattedTradeDescription(@Nullable Player player, TradeDefinition trade) {
        return placeholderService.legacyText(player, String.join(" ", trade.description()), tradeReplacements(trade));
    }

    private java.util.Map<String, String> tradeReplacements(TradeDefinition trade) {
        return java.util.Map.of(
            "id", trade.id(),
            "trade_id", trade.id(),
            "trade_name", trade.displayName(),
            "trade_permission", tradeManager.effectivePermission(trade),
            "ctext_file", tradeManager.effectiveCtextFile(trade)
        );
    }

    private int enabledTradesCount() {
        return (int) tradeManager.allTrades().stream()
            .filter(TradeDefinition::enabled)
            .count();
    }

    private int readyTradesCount(Player player) {
        return (int) tradeManager.allTrades().stream()
            .filter(trade -> tradeManager.evaluateTrade(player, trade).success())
            .count();
    }

    private int completedTradesCount(Player player) {
        return (int) tradeManager.allTrades().stream()
            .filter(trade -> tradeManager.isCompleted(player, trade))
            .count();
    }

    private int totalItemAmount(List<ItemStack> items) {
        return items.stream()
            .filter(item -> item != null && !item.getType().isAir())
            .mapToInt(ItemStack::getAmount)
            .sum();
    }

    private String statusText(@Nullable Player player, TradeDefinition trade, @Nullable TradeCheckResult result) {
        return switch (statusKey(player, trade, result)) {
            case "ready" -> "Ready";
            case "collecting" -> "Collecting";
            case "unlocked" -> "Unlocked";
            case "limit_reached" -> "Limit Reached";
            case "locked" -> "Locked";
            case "disabled" -> "Disabled";
            case "enabled" -> "Enabled";
            default -> "";
        };
    }

    private String statusKey(@Nullable Player player, TradeDefinition trade, @Nullable TradeCheckResult result) {
        if (player == null || result == null) {
            return trade.enabled() ? "enabled" : "disabled";
        }
        return switch (result.status()) {
            case SUCCESS -> "ready";
            case MISSING_ITEMS -> "collecting";
            case ALREADY_COMPLETED -> trade.maxTrades() == 1 ? "unlocked" : "limit_reached";
            case DISABLED -> "disabled";
            case NO_PERMISSION -> "locked";
        };
    }

    private String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }

    private String extractBuildNumber(String version) {
        int dashIndex = version.lastIndexOf('-');
        return dashIndex >= 0 && dashIndex + 1 < version.length()
            ? version.substring(dashIndex + 1)
            : version;
    }

    private record TradePlaceholderQuery(String tradeId, String field) {
    }
}

package com.onemoreblock.trades.config;

import com.onemoreblock.trades.model.TradeTrigger;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public record PluginSettings(
    String adminPermission,
    String tradePermissionPrefix,
    String globalAlias,
    String globalCommand,
    String localeFileName,
    String indexTitle,
    String tradeTitle,
    String playerExpPlaceholder,
    String playerBalancePlaceholder,
    boolean closeOnSuccess,
    boolean sendPluginResultMessages,
    long tradeClickCooldownMillis,
    boolean hideCompletedOnDirectOpen,
    List<String> blacklistedWorlds,
    ConfiguredItemSpec fillerItem,
    ConfiguredItemSpec infoBookItem,
    ConfiguredItemSpec tradeButtonItem,
    ConfiguredItemSpec backButtonItem,
    ConfiguredItemSpec closeButtonItem,
    ConfiguredItemSpec previousPageItem,
    ConfiguredItemSpec nextPageItem,
    ConfiguredItemSpec pageIndicatorItem,
    ConfiguredItemSpec readyStatusItem,
    ConfiguredItemSpec missingStatusItem,
    ConfiguredItemSpec lockedStatusItem,
    ConfiguredItemSpec rewardPreviewItem,
    String playerHeadName,
    List<String> playerHeadLore,
    Map<TradeTrigger, List<String>> globalCommands,
    String playersOnlyMessage,
    String noPermissionMessage,
    String playerNotFoundMessage,
    String consoleNeedsPlayerMessage,
    String tradeNotFoundMessage,
    String invalidTradeIdMessage,
    String tradeDisabledMessage,
    String tradeLockedMessage,
    String tradeCompletedMessage,
    String tradeLimitReachedMessage,
    String tradeWorldBlockedMessage,
    String tradeNotStartedMessage,
    String tradeExpiredMessage,
    String tradeMissingMessage,
    String tradeSuccessMessage,
    String tradeBusyMessage,
    String reloadCompleteMessage,
    String tradeCreatedMessage,
    String tradeClonedMessage,
    String tradeDeletedMessage,
    String tradeDeleteConfirmMessage,
    String requirementsCapturedMessage,
    String iconCapturedMessage,
    String rewardCapturedMessage,
    String commandAddedMessage,
    String commandsClearedMessage,
    String propertyUpdatedMessage,
    String toggleUpdatedMessage,
    String debugResetPlayerMessage,
    String debugResetAllMessage,
    String unavailableBalanceText,
    String defaultTradeDescription,
    String tradeIndexRewardLine,
    String tradeIndexRequirementsLine,
    String tradeIndexStatusLine,
    String tradeIndexAdminFileLine,
    String tradeIndexInspectLine,
    String rewardPreviewExtraLine,
    String requirementProgressLine,
    String requirementMissingLine,
    String statusMoneyLine,
    String statusExpLine,
    String indexStatusReady,
    String indexStatusCollecting,
    String indexStatusUnlocked,
    String indexStatusLimitReached,
    String indexStatusDisabled,
    String indexStatusLocked,
    String indexStatusScheduled,
    String indexStatusExpired
) {
    public static PluginSettings load(FileConfiguration config, FileConfiguration locale) {
        ConfigurationSection settings = config.getConfigurationSection("settings");
        ConfigurationSection guiConfig = config.getConfigurationSection("gui");
        ConfigurationSection guiLocale = locale.getConfigurationSection("gui");
        ConfigurationSection messages = locale.getConfigurationSection("messages");
        ConfigurationSection text = locale.getConfigurationSection("text");
        ConfigurationSection globalCommandsSection = config.getConfigurationSection("global-commands");

        EnumMap<TradeTrigger, List<String>> globalCommands = new EnumMap<>(TradeTrigger.class);
        for (TradeTrigger trigger : TradeTrigger.values()) {
            List<String> commands = globalCommandsSection == null
                ? List.of()
                : List.copyOf(globalCommandsSection.getStringList(trigger.globalConfigKey()));
            globalCommands.put(trigger, commands);
        }

        return new PluginSettings(
            readString(settings, "admin-permission", "onembtrade.admin"),
            readString(settings, "trade-permission-prefix", "onembtrade."),
            readString(settings, "global-alias", ""),
            readString(settings, "global-command", ""),
            readString(settings, "locale-file", "Locale_EN.yml"),
            readString(guiLocale, "index-title", "<gold>Trades</gold>"),
            readString(guiLocale, "trade-title", "<gold>Trade:</gold> <white>%trade_name%</white>"),
            readString(settings, "player-exp-placeholder", "%cmi_user_level%"),
            readString(settings, "player-balance-placeholder", "%cmi_user_balance%"),
            settings != null && settings.getBoolean("close-on-success", true),
            settings == null || settings.getBoolean("send-plugin-result-messages", true),
            settings == null ? 750L : Math.max(250L, settings.getLong("trade-click-cooldown-ms", 750L)),
            settings == null || settings.getBoolean("hide-completed-on-direct-open", true),
            readWorldList(settings, "blacklisted-worlds"),
            readItem(guiConfig, guiLocale, "filler", Material.BLACK_STAINED_GLASS_PANE, " ", List.of()),
            readItem(guiConfig, guiLocale, "info-book", Material.WRITTEN_BOOK, "<aqua>Trade Info</aqua>", List.of()),
            readItem(guiConfig, guiLocale, "trade-button", Material.EMERALD, "<green>Confirm Trade</green>", List.of()),
            readItem(guiConfig, guiLocale, "back-button", Material.ARROW, "<yellow>Back</yellow>", List.of()),
            readItem(guiConfig, guiLocale, "close-button", Material.BARRIER, "<red>Close</red>", List.of()),
            readItem(guiConfig, guiLocale, "previous-page", Material.SPECTRAL_ARROW, "<yellow>Previous Page</yellow>", List.of()),
            readItem(guiConfig, guiLocale, "next-page", Material.SPECTRAL_ARROW, "<yellow>Next Page</yellow>", List.of()),
            readItem(guiConfig, guiLocale, "page-indicator", Material.PAPER, "<white>Page <yellow>%page%</yellow>/<yellow>%max_page%</yellow></white>", List.of()),
            readItem(guiConfig, guiLocale, "ready-status", Material.LIME_DYE, "<green>Ready To Trade</green>", List.of()),
            readItem(guiConfig, guiLocale, "missing-status", Material.RED_DYE, "<red>Missing Requirements</red>", List.of()),
            readItem(guiConfig, guiLocale, "locked-status", Material.GRAY_DYE, "<gray>Unavailable</gray>", List.of()),
            readItem(guiConfig, guiLocale, "reward-preview", Material.NETHER_STAR, "<gold>Reward Preview</gold>", List.of()),
            readString(guiLocale, "player-head-name", "<yellow>%player_name%</yellow>"),
            readStringList(guiLocale, "player-head-lore"),
            Map.copyOf(globalCommands),
            readString(messages, "players-only", "<red>Only players can use that.</red>"),
            readString(messages, "no-permission", "<red>You do not have permission to do that.</red>"),
            readString(messages, "player-not-found", "<red>Player '<white>%target%</white>' is not online.</red>"),
            readString(messages, "console-needs-player", "<red>Console must specify a player.</red>"),
            readString(messages, "trade-not-found", "<red>Trade '<white>%trade_id%</white>' was not found.</red>"),
            readString(messages, "invalid-trade-id", "<red>Invalid trade id.</red>"),
            readString(messages, "trade-disabled", "<red>That trade is currently disabled.</red>"),
            readString(messages, "trade-locked", "<red>You cannot access that trade right now.</red>"),
            readString(messages, "trade-completed", "<yellow>You already unlocked this trade reward.</yellow>"),
            readString(messages, "trade-limit-reached", "<yellow>You already reached the usage limit for this trade.</yellow>"),
            readString(messages, "trade-world-blocked", "<red>You cannot execute this trade in '<white>%current_world%</white>'. Switch to <white>%allowed_worlds%</white> and try again.</red>"),
            readString(messages, "trade-not-started", "<yellow>This trade is not active yet. It starts on <white>%start_date%</white>.</yellow>"),
            readString(messages, "trade-expired", "<yellow>This trade ended on <white>%end_date%</white>.</yellow>"),
            readString(messages, "trade-missing", "<red>Trade failed. Missing: <white>%missing_summary%</white></red>"),
            readString(messages, "trade-success", "<green>Trade complete.</green>"),
            readString(messages, "trade-busy", "<yellow>Please wait a moment before trying that trade again.</yellow>"),
            readString(messages, "reload-complete", "<green>1MB-Trades reloaded successfully.</green>"),
            readString(messages, "trade-created", "<green>Created trade '<white>%trade_id%</white>'.</green>"),
            readString(messages, "trade-cloned", "<green>Cloned '<white>%source_trade%</white>' into '<white>%trade_id%</white>'.</green>"),
            readString(messages, "trade-deleted", "<green>Deleted trade '<white>%trade_id%</white>'.</green>"),
            readString(messages, "trade-delete-confirm", "<red>Delete '<white>%trade_id%</white>'?</red> <click:run_command:'/_trade delete confirm %trade_id%'><yellow><bold>Click here to confirm</bold></yellow></click>"),
            readString(messages, "requirements-captured", "<green>Captured requirements from your main inventory for '<white>%trade_id%</white>'.</green>"),
            readString(messages, "icon-captured", "<green>Captured the GUI icon from your main hand for '<white>%trade_id%</white>'.</white></green>"),
            readString(messages, "reward-captured", "<green>Captured the reward preview item from your main hand for '<white>%trade_id%</white>'.</white></green>"),
            readString(messages, "command-added", "<green>Added a '<white>%group%</white>' command to '<white>%trade_id%</white>'.</green>"),
            readString(messages, "commands-cleared", "<green>Cleared '<white>%group%</white>' commands for '<white>%trade_id%</white>'.</green>"),
            readString(messages, "property-updated", "<green>Updated '<white>%property%</white>' for '<white>%trade_id%</white>'.</green>"),
            readString(messages, "toggle-updated", "<green>Set '<white>%trade_id%</white>' enabled=<white>%value%</white>.</green>"),
            readString(messages, "debug-reset-player", "<green>Reset '<white>%trade_id%</white>' for '<white>%target%</white>'. Removed <white>%previous_uses%</white> recorded use(s).</green>"),
            readString(messages, "debug-reset-all", "<green>Reset '<white>%trade_id%</white>' for <white>%affected_players%</white> tracked player file(s).</green>"),
            readString(text, "unavailable-balance", "<gray>Unavailable</gray>"),
            readString(text, "default-trade-description", "<gray>Describe this trade here.</gray>"),
            readString(text, "trade-index-reward-line", "<gray>Reward:</gray> <white>%reward_name%</white>"),
            readString(text, "trade-index-requirements-line", "<gray>Requirements:</gray> <white>%requirements_count%</white> item entr%requirements_suffix%"),
            readString(text, "trade-index-status-line", "<gray>Status:</gray> %trade_status%"),
            readString(text, "trade-index-admin-file-line", "<dark_gray>%trade_file%</dark_gray>"),
            readString(text, "trade-index-inspect-line", "<yellow>Click to inspect this trade.</yellow>"),
            readString(text, "reward-preview-extra-line", "<gray>Preview only. Rewards still come from commands.</gray>"),
            readString(text, "requirement-progress-line", "<gray>Progress:</gray> <white>%owned_amount%</white>/<white>%required_amount%</white>"),
            readString(text, "requirement-missing-line", "<gray>Missing:</gray> <white>%item_missing_amount%</white>"),
            readString(text, "status-money-line", "<gray>Money:</gray> <white>%player_money%</white>/<white>%money_cost%</white>"),
            readString(text, "status-exp-line", "<gray>Levels:</gray> <white>%player_level%</white>/<white>%exp_cost%</white>"),
            readString(text, "index-status-ready", "<#8BE28B>Ready</#8BE28B>"),
            readString(text, "index-status-collecting", "<#72DDF7>Collecting</#72DDF7>"),
            readString(text, "index-status-unlocked", "<#8BE28B>Unlocked</#8BE28B>"),
            readString(text, "index-status-limit-reached", "<#F6D365>Limit Reached</#F6D365>"),
            readString(text, "index-status-disabled", "<#FF6B6B>Disabled</#FF6B6B>"),
            readString(text, "index-status-locked", "<gray>Locked</gray>"),
            readString(text, "index-status-scheduled", "<#F6D365>Scheduled</#F6D365>"),
            readString(text, "index-status-expired", "<#FF6B6B>Expired</#FF6B6B>")
        );
    }

    public List<String> globalCommands(TradeTrigger trigger) {
        return globalCommands.getOrDefault(trigger, List.of());
    }

    public String defaultTradePermission(String tradeId) {
        return tradePermissionPrefix.isBlank() ? tradeId : tradePermissionPrefix + tradeId;
    }

    private static ConfiguredItemSpec readItem(
        ConfigurationSection materialRoot,
        ConfigurationSection textRoot,
        String path,
        Material defaultMaterial,
        String defaultName,
        List<String> defaultLore
    ) {
        ConfigurationSection materialSection = materialRoot == null ? null : materialRoot.getConfigurationSection(path);
        ConfigurationSection textSection = textRoot == null ? null : textRoot.getConfigurationSection(path);
        Material material = defaultMaterial;
        if (materialSection != null) {
            String rawMaterial = materialSection.getString("material", defaultMaterial.name());
            Material parsed = Material.matchMaterial(rawMaterial.toUpperCase(Locale.ROOT));
            if (parsed != null) {
                material = parsed;
            }
        }
        return new ConfiguredItemSpec(
            material,
            readString(textSection, "name", defaultName),
            textSection == null ? defaultLore : List.copyOf(textSection.getStringList("lore"))
        );
    }

    private static String readString(ConfigurationSection section, String path, String fallback) {
        if (section == null) {
            return fallback;
        }
        return section.getString(path, fallback);
    }

    private static List<String> readStringList(ConfigurationSection section, String path) {
        if (section == null) {
            return List.of();
        }
        return List.copyOf(new ArrayList<>(section.getStringList(path)));
    }

    private static List<String> readWorldList(ConfigurationSection section, String path) {
        if (section == null) {
            return List.of();
        }
        return section.getStringList(path).stream()
            .map(world -> world == null ? "" : world.trim())
            .filter(world -> !world.isBlank())
            .toList();
    }
}

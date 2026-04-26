package com.onemoreblock.trades.command;

import com.onemoreblock.trades.OneMBTradesPlugin;
import com.onemoreblock.trades.config.PluginSettings;
import com.onemoreblock.trades.gui.TradeGuiService;
import com.onemoreblock.trades.model.TradeCheckResult;
import com.onemoreblock.trades.model.TradeDefinition;
import com.onemoreblock.trades.model.TradeTrigger;
import com.onemoreblock.trades.service.AuditLogService;
import com.onemoreblock.trades.service.CommandActionService;
import com.onemoreblock.trades.service.PlaceholderService;
import com.onemoreblock.trades.service.TradeManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;

public final class TradeCommand implements TabExecutor, Listener {
    private final OneMBTradesPlugin plugin;
    private final TradeManager tradeManager;
    private final TradeGuiService tradeGuiService;
    private final CommandActionService commandActionService;
    private final PlaceholderService placeholderService;
    private final AuditLogService auditLogService;

    public TradeCommand(
        OneMBTradesPlugin plugin,
        TradeManager tradeManager,
        TradeGuiService tradeGuiService,
        CommandActionService commandActionService,
        PlaceholderService placeholderService,
        AuditLogService auditLogService
    ) {
        this.plugin = plugin;
        this.tradeManager = tradeManager;
        this.tradeGuiService = tradeGuiService;
        this.commandActionService = commandActionService;
        this.placeholderService = placeholderService;
        this.auditLogService = auditLogService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                tradeGuiService.openIndex(player);
            } else {
                sendHelp(sender);
            }
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        try {
            return switch (subcommand) {
                case "open" -> handleOpen(sender, Arrays.copyOfRange(args, 1, args.length));
                case "reload" -> handleReload(sender);
                case "debug" -> handleDebug(sender, Arrays.copyOfRange(args, 1, args.length));
                case "create" -> handleCreate(sender, Arrays.copyOfRange(args, 1, args.length));
                case "clone" -> handleClone(sender, Arrays.copyOfRange(args, 1, args.length));
                case "delete" -> handleDelete(sender, Arrays.copyOfRange(args, 1, args.length));
                case "capture" -> handleCapture(sender, Arrays.copyOfRange(args, 1, args.length));
                case "set" -> handleSet(sender, Arrays.copyOfRange(args, 1, args.length));
                case "toggle" -> handleToggle(sender, Arrays.copyOfRange(args, 1, args.length));
                case "command" -> handleCommandSubcommand(sender, Arrays.copyOfRange(args, 1, args.length));
                case "test" -> handleTest(sender, Arrays.copyOfRange(args, 1, args.length));
                case "help" -> {
                    sendHelp(sender);
                    yield true;
                }
                default -> handleFallbackOpen(sender, subcommand);
            };
        } catch (IllegalArgumentException exception) {
            sendSystemLine(sender, "<red>%message%</red>", Map.of("message", exception.getMessage()));
            return true;
        } catch (IOException exception) {
            sendSystemLine(sender, "<red>Could not update trade data: <white>%message%</white></red>", Map.of("message", exception.getMessage()));
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("open", "reload", "debug", "create", "clone", "delete", "capture", "set", "toggle", "command", "test", "help"), args[0]);
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "open" -> filter(Stream.concat(Stream.of("index", "category"), tradeManager.tradeIds().stream()).toList(), args[1]);
                case "debug" -> filter(Stream.concat(Stream.of("index", "player"), tradeManager.tradeIds().stream()).toList(), args[1]);
                case "toggle", "delete", "test" -> filter(tradeManager.tradeIds(), args[1]);
                case "clone" -> filter(tradeManager.tradeIds(), args[1]);
                case "capture" -> filter(List.of("requirements", "icon", "reward"), args[1]);
                case "set" -> filter(List.of("display", "description", "permission", "completion", "max", "ctext", "sort", "hide", "worlds", "money", "exp", "start", "end", "category"), args[1]);
                case "command" -> filter(List.of("add", "clear"), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "open" -> switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "category" -> filter(tradeManager.tradeCategories(), args[2]);
                    case "index" -> filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
                    default -> filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
                };
                case "debug" -> {
                    if ("player".equalsIgnoreCase(args[1])) {
                        yield filter(debugResetTargets(), args[2]);
                    }
                    yield "reset".startsWith(args[2].toLowerCase(Locale.ROOT)) && !"index".equalsIgnoreCase(args[1])
                        ? List.of("reset")
                        : List.of();
                }
                case "clone", "capture", "set" -> filter(tradeManager.tradeIds(), args[2]);
                case "toggle" -> filter(List.of("true", "false"), args[2]);
                case "delete" -> "confirm".startsWith(args[2].toLowerCase(Locale.ROOT)) ? List.of("confirm") : List.of();
                case "command" -> switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "add", "clear" -> filter(tradeManager.tradeIds(), args[2]);
                    default -> List.of();
                };
                case "test" -> filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
                default -> List.of();
            };
        }
        if (args.length == 4) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "open" -> "category".equalsIgnoreCase(args[1])
                    ? filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[3])
                    : List.of();
                case "set" -> switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "sort" -> filter(List.of("0", "10", "20"), args[3]);
                    case "max" -> filter(List.of("1", "3", "5", "-1", "unlimited"), args[3]);
                    case "hide" -> filter(List.of("true", "false"), args[3]);
                    case "worlds" -> filter(List.of("global"), args[3]);
                    case "money" -> filter(List.of("0", "25000", "50000"), args[3]);
                    case "exp" -> filter(List.of("0", "5", "10"), args[3]);
                    case "start", "end" -> filter(List.of("01-14-2027", "02-14-2027", "12-25-2027", "none"), args[3]);
                    case "category" -> filter(List.of("general", "summer", "vote", "christmas"), args[3]);
                    default -> List.of();
                };
                case "debug" -> "reset".equalsIgnoreCase(args[2])
                    ? filter(debugResetTargets(), args[3])
                    : List.of();
                case "delete" -> List.of();
                case "command" -> filter(TradeTrigger.perTradeTriggers().stream().map(TradeTrigger::tradeConfigKey).toList(), args[3]);
                default -> List.of();
            };
        }
        return List.of();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerCommandAlias(PlayerCommandPreprocessEvent event) {
        PluginSettings settings = plugin.settings();
        String alias = settings.globalAlias();
        if (alias == null || alias.isBlank()) {
            return;
        }
        if (plugin.hasRegisteredGlobalAlias(alias)) {
            return;
        }

        String message = event.getMessage();
        if (message == null || !message.startsWith("/")) {
            return;
        }

        String commandLine = message.substring(1);
        String[] parts = commandLine.split(" ", 2);
        if (!parts[0].equalsIgnoreCase(alias)) {
            return;
        }

        event.setCancelled(true);
        String extraArgs = parts.length > 1 ? parts[1] : "";
        String[] extraArgsArray = extraArgs.isBlank() ? new String[0] : extraArgs.split(" ");
        plugin.executeGlobalAlias(event.getPlayer(), extraArgsArray);
    }

    private boolean handleFallbackOpen(CommandSender sender, String possibleTradeId) {
        Optional<TradeDefinition> trade = tradeManager.findTrade(possibleTradeId);
        if (trade.isPresent() && sender instanceof Player player) {
            return openTradeDirect(sender, player, trade.get(), null);
        }
        if (sender instanceof Player player) {
            tradeGuiService.openIndex(player);
            return true;
        }
        sendHelp(sender);
        return true;
    }

    private boolean handleOpen(CommandSender sender, String[] args) {
        Player senderPlayer = sender instanceof Player player ? player : null;

        if (args.length == 0) {
            if (senderPlayer == null) {
                placeholderService.sendMessage(sender, null, plugin.settings().consoleNeedsPlayerMessage(), Map.of());
                return true;
            }
            tradeGuiService.openIndex(senderPlayer);
            return true;
        }

        if ("category".equalsIgnoreCase(args[0])) {
            if (args.length < 2) {
                throw new IllegalArgumentException("Provide a category name.");
            }
            Player target = resolveOpenTarget(sender, senderPlayer, args.length >= 3 ? args[2] : null);
            if (target == null) {
                return true;
            }
            tradeGuiService.openIndex(target, 0, args[1]);
            return true;
        }

        if ("index".equalsIgnoreCase(args[0])) {
            Player target = resolveOpenTarget(sender, senderPlayer, args.length >= 2 ? args[1] : null);
            if (target == null) {
                return true;
            }
            tradeGuiService.openIndex(target);
            return true;
        }

        if (args.length == 1) {
            Optional<TradeDefinition> trade = tradeManager.findTrade(args[0]);
            if (trade.isPresent()) {
                if (senderPlayer == null) {
                    placeholderService.sendMessage(sender, null, plugin.settings().consoleNeedsPlayerMessage(), Map.of());
                    return true;
                }
                return openTradeDirect(sender, senderPlayer, trade.get(), null);
            }

            Player target = Bukkit.getPlayerExact(args[0]);
            if (target != null) {
                if (senderPlayer != null && !senderPlayer.getUniqueId().equals(target.getUniqueId()) && !hasAdmin(sender)) {
                    placeholderService.sendMessage(sender, senderPlayer, plugin.settings().noPermissionMessage(), Map.of());
                    return true;
                }
                tradeGuiService.openIndex(target);
                return true;
            }

            if (senderPlayer != null) {
                tradeGuiService.openIndex(senderPlayer);
                return true;
            }

            placeholderService.sendMessage(sender, null, plugin.settings().playerNotFoundMessage(), Map.of("target", args[0]));
            return true;
        }

        Player target = resolveOpenTarget(sender, senderPlayer, args[1]);
        if (target == null) {
            return true;
        }

        Optional<TradeDefinition> trade = tradeManager.findTrade(args[0]);
        if (trade.isPresent()) {
            return openTradeDirect(sender, target, trade.get(), null);
        }

        tradeGuiService.openIndex(target);
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return true;
        }
        plugin.reloadPluginState();
        auditLogService.logAdminAction(sender, "reload", Map.of("warnings", String.valueOf(tradeManager.validationWarnings().size())));
        placeholderService.sendMessage(sender, sender instanceof Player player ? player : null, plugin.settings().reloadCompleteMessage(), Map.of());
        if (!tradeManager.validationWarnings().isEmpty()) {
            sendSystemLine(sender, "<yellow>Validation warnings:</yellow> <white>%count%</white>", Map.of("count", String.valueOf(tradeManager.validationWarnings().size())));
        }
        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return true;
        }

        Player context = sender instanceof Player player ? player : null;
        if (args.length == 0 || "index".equalsIgnoreCase(args[0])) {
            OneMBTradesPlugin.BuildInfo buildInfo = plugin.buildInfo();
            String declaredApiVersion = Optional.ofNullable(plugin.getPluginMeta().getAPIVersion())
                .orElse(buildInfo.declaredApiVersion());
            sendSystemLine(sender, "<gradient:#F6D365:#FDA085>[1MB-Trades] Environment</gradient>");
            sendSystemLine(sender, "<gray>Plugin:</gray> <white>%value%</white>", Map.of("value", plugin.getPluginMeta().getVersion()));
            sendSystemLine(sender, "<gray>Build Java target:</gray> <white>%value%</white>", Map.of("value", buildInfo.targetJavaVersion()));
            sendSystemLine(sender, "<gray>Compile Paper API:</gray> <white>%value%</white>", Map.of("value", buildInfo.paperApiVersion()));
            sendSystemLine(sender, "<gray>Declared api-version:</gray> <white>%value%</white>", Map.of("value", declaredApiVersion));
            sendSystemLine(sender, "<gray>Java:</gray> <white>%value%</white>", Map.of("value", System.getProperty("java.version")));
            sendSystemLine(sender, "<gray>Server:</gray> <white>%value%</white>", Map.of("value", Bukkit.getName() + " / " + Bukkit.getVersion()));
            sendSystemLine(sender, "<gray>Bukkit:</gray> <white>%value%</white>", Map.of("value", Bukkit.getBukkitVersion()));
            sendSystemLine(sender, "<gray>Data folder:</gray> <white>%value%</white>", Map.of("value", plugin.getDataFolder().getAbsolutePath()));
            sendSystemLine(sender, "<gray>Player data folder:</gray> <white>%value%</white>", Map.of("value", tradeManager.playerDataDirectory().toAbsolutePath().toString()));
            sendSystemLine(sender, "<gray>Locale file:</gray> <white>%value%</white>", Map.of("value", plugin.settings().localeFileName()));
            sendSystemLine(sender, "<gray>Trades loaded:</gray> <white>%value%</white>", Map.of("value", String.valueOf(tradeManager.allTrades().size())));
            sendSystemLine(sender, "<gray>Tracked players:</gray> <white>%value%</white>", Map.of("value", String.valueOf(tradeManager.trackedPlayersCount())));
            sendSystemLine(sender, "<gray>Validation warnings:</gray> <white>%value%</white>", Map.of("value", String.valueOf(tradeManager.validationWarnings().size())));
            sendSystemLine(sender, "<gray>Admin permission:</gray> <white>%value%</white>", Map.of("value", plugin.settings().adminPermission()));
            sendSystemLine(sender, "<gray>Trade prefix:</gray> <white>%value%</white>", Map.of("value", plugin.settings().tradePermissionPrefix()));
            sendSystemLine(sender, "<gray>Global alias:</gray> <white>%value%</white>", Map.of("value", plugin.settings().globalAlias()));
            sendSystemLine(sender, "<gray>Global command:</gray> <white>%value%</white>", Map.of("value", plugin.settings().globalCommand()));
            sendSystemLine(sender, "<gray>Blacklisted worlds:</gray> <white>%value%</white>", Map.of("value", String.join(", ", plugin.settings().blacklistedWorlds())));
            sendSystemLine(sender, "<gray>Trade click cooldown:</gray> <white>%value% ms</white>", Map.of("value", String.valueOf(plugin.settings().tradeClickCooldownMillis())));
            sendSystemLine(sender, "<gray>Hide completed on direct open:</gray> <white>%value%</white>", Map.of("value", String.valueOf(plugin.settings().hideCompletedOnDirectOpen())));
            sendSystemLine(sender, "<gray>PlaceholderAPI:</gray> <white>%value%</white>", Map.of("value", placeholderService.hasPlaceholderApi() ? "enabled" : "not detected"));
            sendSystemLine(sender, "<gray>CMI:</gray> <white>%value%</white>", Map.of("value", String.valueOf(plugin.getServer().getPluginManager().isPluginEnabled("CMI"))));
            sendSystemLine(sender, "<gray>LuckPerms:</gray> <white>%value%</white>", Map.of("value", String.valueOf(plugin.getServer().getPluginManager().isPluginEnabled("LuckPerms"))));
            sendSystemLine(sender, "<gray>Vault:</gray> <white>%value%</white>", Map.of("value", String.valueOf(plugin.getServer().getPluginManager().isPluginEnabled("Vault"))));
            sendSystemLine(sender, "<gray>Economy provider:</gray> <white>%value%</white>", Map.of("value", tradeManager.economyAvailable() ? tradeManager.economyProviderName() : "not available"));
            sendSystemLine(sender, "<gradient:#F6D365:#FDA085>[1MB-Trades] Commands</gradient>");
            sendSystemLine(sender, "<yellow>/_trade</yellow> <gray>- open the trade index for yourself</gray>");
            sendSystemLine(sender, "<yellow>/_trade open [trade] [player]</yellow> <gray>- open the index or one trade</gray>");
            sendSystemLine(sender, "<yellow>/_trade open category <category> [player]</yellow> <gray>- open a filtered category index</gray>");
            sendSystemLine(sender, "<yellow>/_trade reload</yellow> <gray>- reload config.yml, locale, and trade files</gray>");
            sendSystemLine(sender, "<yellow>/_trade debug [trade]</yellow> <gray>- show environment or trade details</gray>");
            sendSystemLine(sender, "<yellow>/_trade debug player <player></yellow> <gray>- show stored usage data</gray>");
            sendSystemLine(sender, "<yellow>/_trade debug <trade> reset <player|all></yellow> <gray>- reset tracked player usage data</gray>");
            sendSystemLine(sender, "<yellow>/_trade create <id></yellow>");
            sendSystemLine(sender, "<yellow>/_trade clone <source> <newId></yellow>");
            sendSystemLine(sender, "<yellow>/_trade delete <trade></yellow>");
            sendSystemLine(sender, "<yellow>/_trade capture requirements|icon|reward <trade></yellow>");
            sendSystemLine(sender, "<yellow>/_trade set display|description|permission|completion|max|ctext|sort|hide|worlds|money|exp|start|end|category <trade> <value></yellow>");
            sendSystemLine(sender, "<yellow>/_trade toggle <trade> <true|false></yellow>");
            sendSystemLine(sender, "<yellow>/_trade command add|clear <trade> <open|info|success|fail> <command></yellow>");
            sendSystemLine(sender, "<yellow>/_trade test <trade> [player]</yellow>");
            sendSystemLine(sender, "<gradient:#F6D365:#FDA085>[1MB-Trades] Command Prefixes</gradient>");
            sendSystemLine(sender, "<yellow>console:</yellow> <gray>run command as console</gray>");
            sendSystemLine(sender, "<yellow>player:</yellow> <gray>run command as the player</gray>");
            sendSystemLine(sender, "<yellow>message:</yellow> <gray>send a formatted chat message</gray>");
            sendSystemLine(sender, "<yellow>actionbar:</yellow> <gray>send a formatted action bar</gray>");
            sendSystemLine(sender, "<gradient:#F6D365:#FDA085>[1MB-Trades] Placeholders</gradient>");
            sendSystemLine(sender, "<yellow>%player%</yellow> <gray>%player_name% %player_uuid% %id% %trade_id% %trade_name% %category%</gray>");
            sendSystemLine(sender, "<yellow>%trade_description%</yellow> <gray>%trade_permission% %ctext_file% %allowed_worlds% %current_world%</gray>");
            sendSystemLine(sender, "<yellow>%required_items%</yellow> <gray>%item_cost% %requirements_count% %money_cost% %exp_cost%</gray>");
            sendSystemLine(sender, "<yellow>%trade_uses%</yellow> <gray>%max_trades% %remaining_trades% %player_money% %player_level%</gray>");
            sendSystemLine(sender, "<yellow>%missing_items%</yellow> <gray>%missing_amount% %missing_money% %missing_exp% %missing_summary%</gray>");
            if (placeholderService.hasPlaceholderApi()) {
                sendSystemLine(sender, "<gray>Any PlaceholderAPI placeholder is also supported where a player context exists.</gray>");
            }
            if (!tradeManager.validationWarnings().isEmpty()) {
                sendSystemLine(sender, "<gradient:#F6D365:#FDA085>[1MB-Trades] Validation Warnings</gradient>");
                for (String warning : tradeManager.validationWarnings()) {
                    sendSystemLine(sender, "<dark_gray>-</dark_gray> <yellow>%value%</yellow>", Map.of("value", warning));
                }
            }
            return true;
        }

        if ("player".equalsIgnoreCase(args[0])) {
            if (args.length < 2) {
                throw new IllegalArgumentException("Provide a player name or UUID.");
            }
            return handleDebugPlayer(sender, args[1]);
        }

        TradeDefinition trade = tradeManager.findTrade(args[0]).orElse(null);
        if (trade == null) {
            placeholderService.sendMessage(sender, context, plugin.settings().tradeNotFoundMessage(), Map.of("trade_id", args[0]));
            return true;
        }

        if (args.length == 2 && "reset".equalsIgnoreCase(args[1])) {
            throw new IllegalArgumentException("Provide a player name, UUID, or 'all' after reset.");
        }
        if (args.length >= 3 && "reset".equalsIgnoreCase(args[1])) {
            return handleDebugReset(sender, context, trade, args[2]);
        }

        sendSystemLine(sender, "<gradient:#F6D365:#FDA085>[1MB-Trades] Trade Debug</gradient><gray>:</gray> <white>%trade_id%</white>", Map.of("trade_id", trade.id()));
        sendSystemLine(sender, "<gray>File:</gray> <white>%value%</white>", Map.of("value", trade.file().toAbsolutePath().toString()));
        sendSystemLine(sender, "<gray>Enabled:</gray> <white>%value%</white>", Map.of("value", String.valueOf(trade.enabled())));
        sendSystemLine(sender, "<gray>Sort order:</gray> <white>%value%</white>", Map.of("value", String.valueOf(trade.sortOrder())));
        sendSystemLine(sender, "<gray>Category:</gray> <white>%value%</white>", Map.of("value", trade.category()));
        sendSystemLine(sender, "<gray>Display:</gray> %value%", Map.of("value", trade.displayName()));
        sendSystemLine(sender, "<gray>Permission:</gray> <white>%value%</white>", Map.of("value", tradeManager.effectivePermission(trade)));
        sendSystemLine(sender, "<gray>Completion permission:</gray> <white>%value%</white>", Map.of("value", tradeManager.effectiveCompletionPermission(trade)));
        sendSystemLine(sender, "<gray>Max trades:</gray> <white>%value%</white>", Map.of("value", tradeManager.formattedMaxTrades(trade)));
        sendSystemLine(sender, "<gray>Hide when completed:</gray> <white>%value%</white>", Map.of("value", String.valueOf(trade.hideWhenCompleted())));
        sendSystemLine(sender, "<gray>Allowed worlds:</gray> <white>%value%</white>", Map.of("value", tradeManager.allowedWorldsDescription(trade)));
        sendSystemLine(sender, "<gray>Money cost:</gray> <white>%value%</white>", Map.of("value", tradeManager.formatMoney(trade.moneyCost())));
        sendSystemLine(sender, "<gray>Exp cost:</gray> <white>%value%</white>", Map.of("value", String.valueOf(trade.expCost())));
        sendSystemLine(sender, "<gray>Start date:</gray> <white>%value%</white>", Map.of("value", tradeManager.formattedDate(trade.startDate())));
        sendSystemLine(sender, "<gray>End date:</gray> <white>%value%</white>", Map.of("value", tradeManager.formattedDate(trade.endDate())));
        sendSystemLine(sender, "<gray>ctext file:</gray> <white>%value%</white>", Map.of("value", tradeManager.effectiveCtextFile(trade)));
        sendSystemLine(sender, "<gray>Requirements:</gray> <white>%value%</white>", Map.of("value", String.valueOf(trade.requirements().size())));
        if (context != null) {
            sendSystemLine(sender, "<gray>Your uses:</gray> <white>%value%</white>", Map.of("value", String.valueOf(tradeManager.tradeUses(context, trade))));
            sendSystemLine(sender, "<gray>Your remaining uses:</gray> <white>%value%</white>", Map.of("value", tradeManager.formattedRemainingTrades(context, trade)));
        }
        for (ItemStack requirement : trade.requirements()) {
            sendSystemLine(sender, "<dark_gray>-</dark_gray> <white>%amount%x %item%</white>", Map.of(
                "amount", String.valueOf(requirement.getAmount()),
                "item", placeholderService.plainItemName(requirement)
            ));
        }
        sendSystemLine(sender, "<gray>Reward preview:</gray> <white>%value%</white>", Map.of(
            "value", trade.rewardItem() == null ? "none" : placeholderService.plainItemName(trade.rewardItem())
        ));
        sendSystemLine(sender, "<gray>Commands:</gray>");
        for (TradeTrigger trigger : TradeTrigger.perTradeTriggers()) {
            List<String> commands = trade.commands(trigger);
            sendSystemLine(sender, "<dark_gray>-</dark_gray> <yellow>%group%</yellow> <gray>(%count%)</gray>", Map.of(
                "group", trigger.tradeConfigKey(),
                "count", String.valueOf(commands.size())
            ));
            for (String commandLine : commands) {
                sendSystemLine(sender, "<gray>   %command%</gray>", Map.of("command", commandLine));
            }
        }
        return true;
    }

    private boolean handleDebugPlayer(CommandSender sender, String input) {
        ResetTarget target = resolveResetTarget(input);
        if (target == null) {
            throw new IllegalArgumentException("Player not found for debug: " + input);
        }

        sendSystemLine(sender, "<gradient:#F6D365:#FDA085>[1MB-Trades] Player Debug</gradient><gray>:</gray> <white>%name%</white>", Map.of("name", target.name()));
        sendSystemLine(sender, "<gray>UUID:</gray> <white>%value%</white>", Map.of("value", target.uuid().toString()));
        Map<String, Integer> usages = new LinkedHashMap<>(tradeManager.usageCounts(target.uuid()));
        if (usages.isEmpty()) {
            sendSystemLine(sender, "<gray>No tracked trade usage found.</gray>");
            return true;
        }

        usages.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
            .forEach(entry -> sendSystemLine(sender, "<dark_gray>-</dark_gray> <yellow>%trade%</yellow> <gray>uses:</gray> <white>%uses%</white>", Map.of(
                "trade", entry.getKey(),
                "uses", String.valueOf(entry.getValue())
            )));
        return true;
    }

    private boolean handleCreate(CommandSender sender, String[] args) throws IOException {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length < 1 || !tradeManager.isValidTradeId(args[0])) {
            placeholderService.sendMessage(sender, sender instanceof Player player ? player : null, plugin.settings().invalidTradeIdMessage(), Map.of());
            return true;
        }

        tradeManager.createTrade(args[0]);
        auditLogService.logAdminAction(sender, "create_trade", Map.of("trade_id", args[0]));
        placeholderService.sendMessage(sender, sender instanceof Player player ? player : null, plugin.settings().tradeCreatedMessage(), Map.of("trade_id", args[0]));
        return true;
    }

    private boolean handleClone(CommandSender sender, String[] args) throws IOException {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: /_trade clone <source> <newId>");
        }

        tradeManager.cloneTrade(args[0], args[1]);
        auditLogService.logAdminAction(sender, "clone_trade", Map.of("source_trade", args[0], "trade_id", args[1]));
        placeholderService.sendMessage(
            sender,
            sender instanceof Player player ? player : null,
            plugin.settings().tradeClonedMessage(),
            Map.of("source_trade", args[0], "trade_id", args[1])
        );
        return true;
    }

    private boolean handleDelete(CommandSender sender, String[] args) throws IOException {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: /_trade delete <trade> [confirm]");
        }

        if ("confirm".equalsIgnoreCase(args[0])) {
            if (args.length < 2) {
                throw new IllegalArgumentException("Usage: /_trade delete confirm <trade>");
            }
            tradeManager.deleteTrade(args[1]);
            auditLogService.logAdminAction(sender, "delete_trade", Map.of("trade_id", args[1]));
            placeholderService.sendMessage(sender, sender instanceof Player player ? player : null, plugin.settings().tradeDeletedMessage(), Map.of("trade_id", args[1]));
            return true;
        }

        placeholderService.sendMessage(sender, sender instanceof Player player ? player : null, plugin.settings().tradeDeleteConfirmMessage(), Map.of("trade_id", args[0]));
        return true;
    }

    private boolean handleCapture(CommandSender sender, String[] args) throws IOException {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            placeholderService.sendMessage(sender, null, plugin.settings().playersOnlyMessage(), Map.of());
            return true;
        }
        if (args.length < 2) {
            sendHelp(sender);
            return true;
        }

        String mode = args[0].toLowerCase(Locale.ROOT);
        String tradeId = args[1];
        switch (mode) {
            case "requirements" -> {
                tradeManager.captureRequirements(tradeId, player);
                auditLogService.logAdminAction(sender, "capture_requirements", Map.of("trade_id", tradeId));
                placeholderService.sendMessage(sender, player, plugin.settings().requirementsCapturedMessage(), Map.of("trade_id", tradeId));
            }
            case "icon" -> {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {
                    throw new IllegalArgumentException("Hold the icon item in your main hand first.");
                }
                tradeManager.captureIcon(tradeId, hand);
                auditLogService.logAdminAction(sender, "capture_icon", Map.of("trade_id", tradeId, "item", placeholderService.plainItemName(hand)));
                placeholderService.sendMessage(sender, player, plugin.settings().iconCapturedMessage(), Map.of("trade_id", tradeId));
            }
            case "reward" -> {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {
                    throw new IllegalArgumentException("Hold the reward preview item in your main hand first.");
                }
                tradeManager.captureReward(tradeId, hand);
                auditLogService.logAdminAction(sender, "capture_reward", Map.of("trade_id", tradeId, "item", placeholderService.plainItemName(hand)));
                placeholderService.sendMessage(sender, player, plugin.settings().rewardCapturedMessage(), Map.of("trade_id", tradeId));
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private boolean handleSet(CommandSender sender, String[] args) throws IOException {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length < 3) {
            sendHelp(sender);
            return true;
        }

        String property = args[0].toLowerCase(Locale.ROOT);
        String tradeId = args[1];
        String value = requireInlineInput(joinFrom(args, 2), property);
        String normalizedValue = "none".equalsIgnoreCase(value) || "-".equals(value) ? "" : value;

        switch (property) {
            case "display" -> tradeManager.setDisplayName(tradeId, value);
            case "description" -> tradeManager.setDescription(tradeId, value);
            case "permission" -> tradeManager.setPermission(tradeId, normalizedValue);
            case "completion" -> tradeManager.setCompletionPermission(tradeId, normalizedValue);
            case "max" -> tradeManager.setMaxTrades(tradeId, parseMaxTrades(value));
            case "ctext" -> tradeManager.setCtextFile(tradeId, normalizedValue);
            case "sort" -> tradeManager.setSortOrder(tradeId, Integer.parseInt(value));
            case "hide" -> tradeManager.setHideWhenCompleted(tradeId, parseBooleanStrict(value));
            case "worlds" -> tradeManager.setAllowedWorlds(tradeId, normalizedValue);
            case "money" -> tradeManager.setMoneyCost(tradeId, Double.parseDouble(value));
            case "exp" -> tradeManager.setExpCost(tradeId, Integer.parseInt(value));
            case "start" -> tradeManager.setStartDate(tradeId, normalizedValue);
            case "end" -> tradeManager.setEndDate(tradeId, normalizedValue);
            case "category" -> tradeManager.setCategory(tradeId, normalizedValue);
            default -> {
                sendHelp(sender);
                return true;
            }
        }

        auditLogService.logAdminAction(sender, "set_trade_property", Map.of("trade_id", tradeId, "property", property, "value", value));
        placeholderService.sendMessage(
            sender,
            sender instanceof Player player ? player : null,
            plugin.settings().propertyUpdatedMessage(),
            Map.of("trade_id", tradeId, "property", property)
        );
        return true;
    }

    private boolean handleToggle(CommandSender sender, String[] args) throws IOException {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length < 2) {
            sendHelp(sender);
            return true;
        }

        boolean enabled = parseBooleanStrict(args[1]);
        tradeManager.setEnabled(args[0], enabled);
        auditLogService.logAdminAction(sender, "toggle_trade", Map.of("trade_id", args[0], "enabled", String.valueOf(enabled)));
        placeholderService.sendMessage(
            sender,
            sender instanceof Player player ? player : null,
            plugin.settings().toggleUpdatedMessage(),
            Map.of("trade_id", args[0], "value", String.valueOf(enabled))
        );
        return true;
    }

    private boolean handleCommandSubcommand(CommandSender sender, String[] args) throws IOException {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length < 3) {
            sendHelp(sender);
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        String tradeId = args[1];
        TradeTrigger trigger = TradeTrigger.fromTradeConfigKey(args[2]);
        if (trigger == null) {
            throw new IllegalArgumentException("Unknown command group: " + args[2]);
        }

        if ("add".equals(action)) {
            if (args.length < 4) {
                throw new IllegalArgumentException("Provide a command to add.");
            }
            String commandLine = requireInlineInput(joinFrom(args, 3), "command");
            tradeManager.addCommand(tradeId, trigger, commandLine);
            auditLogService.logAdminAction(sender, "add_trade_command", Map.of("trade_id", tradeId, "group", trigger.tradeConfigKey(), "command", commandLine));
            placeholderService.sendMessage(
                sender,
                sender instanceof Player player ? player : null,
                plugin.settings().commandAddedMessage(),
                Map.of("trade_id", tradeId, "group", trigger.tradeConfigKey())
            );
            return true;
        }
        if ("clear".equals(action)) {
            tradeManager.clearCommands(tradeId, trigger);
            auditLogService.logAdminAction(sender, "clear_trade_commands", Map.of("trade_id", tradeId, "group", trigger.tradeConfigKey()));
            placeholderService.sendMessage(
                sender,
                sender instanceof Player player ? player : null,
                plugin.settings().commandsClearedMessage(),
                Map.of("trade_id", tradeId, "group", trigger.tradeConfigKey())
            );
            return true;
        }

        sendHelp(sender);
        return true;
    }

    private boolean handleTest(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: /_trade test <trade> [player]");
        }

        TradeDefinition trade = tradeManager.findTrade(args[0]).orElseThrow(() -> new IllegalArgumentException("Trade not found: " + args[0]));
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                throw new IllegalArgumentException("Player not found: " + args[1]);
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            throw new IllegalArgumentException("Console must specify a player.");
        }

        TradeCheckResult result = tradeManager.evaluateTrade(target, trade);
        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put("trade_id", trade.id());
        replacements.put("trade_name", trade.displayName());
        replacements.put("target", target.getName());
        replacements.put("status", result.status().name());
        replacements.put("world", target.getWorld().getName());
        replacements.put("allowed_worlds", tradeManager.allowedWorldsDescription(trade));
        replacements.put("missing_summary", tradeManager.summarizeMissingRequirements(result));
        replacements.put("money_cost", tradeManager.formatMoney(trade.moneyCost()));
        replacements.put("exp_cost", String.valueOf(trade.expCost()));
        replacements.put("player_money", tradeManager.formatMoney(tradeManager.playerBalance(target)));
        replacements.put("player_level", String.valueOf(target.getLevel()));

        sendSystemLine(sender, "<gradient:#F6D365:#FDA085>[1MB-Trades] Dry Run</gradient><gray>:</gray> <white>%trade_id%</white> <gray>for</gray> <white>%target%</white>", replacements);
        sendSystemLine(sender, "<gray>Status:</gray> <white>%status%</white>", replacements);
        sendSystemLine(sender, "<gray>World:</gray> <white>%world%</white> <gray>(allowed: %allowed_worlds%)</gray>", replacements);
        sendSystemLine(sender, "<gray>Money:</gray> <white>%player_money%</white>/<white>%money_cost%</white>", replacements);
        sendSystemLine(sender, "<gray>Levels:</gray> <white>%player_level%</white>/<white>%exp_cost%</white>", replacements);
        sendSystemLine(sender, "<gray>Missing:</gray> <white>%missing_summary%</white>", replacements);

        TradeTrigger commandGroup = result.success() ? TradeTrigger.SUCCESS : TradeTrigger.FAIL;
        List<String> commands = trade.commands(commandGroup);
        sendSystemLine(sender, "<gray>%group% commands:</gray> <white>%count%</white>", Map.of(
            "group", commandGroup.tradeConfigKey(),
            "count", String.valueOf(commands.size())
        ));
        for (String commandLine : commands) {
            sendSystemLine(sender, "<dark_gray>-</dark_gray> <white>%command%</white>", Map.of("command", commandLine));
        }

        auditLogService.logAdminAction(sender, "test_trade", Map.of("trade_id", trade.id(), "target", target.getName(), "status", result.status().name()));
        return true;
    }

    private boolean handleDebugReset(CommandSender sender, Player context, TradeDefinition trade, String targetInput) {
        if ("all".equalsIgnoreCase(targetInput)) {
            int affectedPlayers = tradeManager.resetTradeUsageForAll(trade.id());
            auditLogService.logAdminAction(sender, "reset_trade_usage_all", Map.of("trade_id", trade.id(), "affected_players", String.valueOf(affectedPlayers)));
            placeholderService.sendMessage(
                sender,
                context,
                plugin.settings().debugResetAllMessage(),
                Map.of("trade_id", trade.id(), "affected_players", String.valueOf(affectedPlayers))
            );
            return true;
        }

        ResetTarget target = resolveResetTarget(targetInput);
        if (target == null) {
            throw new IllegalArgumentException("Player not found for reset: " + targetInput);
        }

        int previousUses = tradeManager.resetTradeUsage(trade.id(), target.uuid(), target.name());
        auditLogService.logAdminAction(sender, "reset_trade_usage_player", Map.of("trade_id", trade.id(), "target", target.name(), "previous_uses", String.valueOf(previousUses)));
        placeholderService.sendMessage(
            sender,
            context,
            plugin.settings().debugResetPlayerMessage(),
            Map.of(
                "trade_id", trade.id(),
                "target", target.name(),
                "previous_uses", String.valueOf(previousUses)
            )
        );
        return true;
    }

    private boolean openTradeDirect(CommandSender sender, Player target, TradeDefinition trade, String category) {
        if (target == null) {
            placeholderService.sendMessage(sender, null, plugin.settings().consoleNeedsPlayerMessage(), Map.of());
            return true;
        }
        if (sender instanceof Player player && !player.getUniqueId().equals(target.getUniqueId()) && !hasAdmin(sender)) {
            placeholderService.sendMessage(sender, player, plugin.settings().noPermissionMessage(), Map.of());
            return true;
        }
        if (!target.hasPermission(plugin.settings().adminPermission())
            && plugin.settings().hideCompletedOnDirectOpen()
            && trade.hideWhenCompleted()
            && tradeManager.isCompleted(target, trade)) {
            placeholderService.sendMessage(target, target, trade.maxTrades() == 1 ? plugin.settings().tradeCompletedMessage() : plugin.settings().tradeLimitReachedMessage(), Map.of());
            return true;
        }
        tradeGuiService.openTrade(target, trade, 0, category);
        return true;
    }

    private Player resolveOpenTarget(CommandSender sender, Player senderPlayer, String targetName) {
        if (targetName == null || targetName.isBlank()) {
            if (senderPlayer == null) {
                placeholderService.sendMessage(sender, null, plugin.settings().consoleNeedsPlayerMessage(), Map.of());
                return null;
            }
            return senderPlayer;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            placeholderService.sendMessage(sender, senderPlayer, plugin.settings().playerNotFoundMessage(), Map.of("target", targetName));
            return null;
        }
        if (senderPlayer != null && !senderPlayer.getUniqueId().equals(target.getUniqueId()) && !hasAdmin(sender)) {
            placeholderService.sendMessage(sender, senderPlayer, plugin.settings().noPermissionMessage(), Map.of());
            return null;
        }
        return target;
    }

    private boolean requireAdmin(CommandSender sender) {
        if (hasAdmin(sender)) {
            return true;
        }
        placeholderService.sendMessage(sender, sender instanceof Player player ? player : null, plugin.settings().noPermissionMessage(), Map.of());
        return false;
    }

    private boolean hasAdmin(CommandSender sender) {
        return !(sender instanceof Player player) || player.hasPermission(plugin.settings().adminPermission());
    }

    private void sendHelp(CommandSender sender) {
        sendSystemLine(sender, "<gradient:#F6D365:#FDA085>[1MB-Trades] Usage</gradient>");
        sendSystemLine(sender, "<yellow>/_trade</yellow> <gray>- open the main trade index</gray>");
        sendSystemLine(sender, "<yellow>/_trade open [trade] [player]</yellow> <gray>- open a trade or the index</gray>");
        sendSystemLine(sender, "<yellow>/_trade open category <category> [player]</yellow> <gray>- open a filtered category index</gray>");
        if (hasAdmin(sender)) {
            sendSystemLine(sender, "<yellow>/_trade reload</yellow>");
            sendSystemLine(sender, "<yellow>/_trade debug [trade]</yellow>");
            sendSystemLine(sender, "<yellow>/_trade debug player <player></yellow>");
            sendSystemLine(sender, "<yellow>/_trade debug <trade> reset <player|all></yellow>");
            sendSystemLine(sender, "<yellow>/_trade create <id></yellow>");
            sendSystemLine(sender, "<yellow>/_trade clone <source> <newId></yellow>");
            sendSystemLine(sender, "<yellow>/_trade delete <trade></yellow>");
            sendSystemLine(sender, "<yellow>/_trade capture requirements|icon|reward <trade></yellow>");
            sendSystemLine(sender, "<yellow>/_trade set display|description|permission|completion|max|ctext|sort|hide|worlds|money|exp|start|end|category <trade> <value></yellow>");
            sendSystemLine(sender, "<yellow>/_trade toggle <trade> <true|false></yellow>");
            sendSystemLine(sender, "<yellow>/_trade command add|clear <trade> <open|info|success|fail> <command></yellow>");
            sendSystemLine(sender, "<yellow>/_trade test <trade> [player]</yellow>");
        }
    }

    private void sendSystemLine(CommandSender sender, String template) {
        sendSystemLine(sender, template, Map.of());
    }

    private void sendSystemLine(CommandSender sender, String template, Map<String, String> replacements) {
        Player context = sender instanceof Player player ? player : null;
        placeholderService.sendMessage(sender, context, template, replacements);
    }

    private String joinFrom(String[] args, int start) {
        return String.join(" ", Arrays.copyOfRange(args, start, args.length));
    }

    private String requireInlineInput(String input, String fieldName) {
        if (input == null) {
            return "";
        }
        if (input.indexOf('\r') >= 0 || input.indexOf('\n') >= 0 || input.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(fieldName + " cannot contain line breaks or NUL characters.");
        }
        return input;
    }

    private int parseMaxTrades(String input) {
        String normalized = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("unlimited") || normalized.equals("infinite")) {
            return -1;
        }
        int parsed = Integer.parseInt(normalized);
        if (parsed == 0) {
            throw new IllegalArgumentException("max must be 1 or greater, or -1 for unlimited.");
        }
        return parsed;
    }

    private boolean parseBooleanStrict(String input) {
        String normalized = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized)) {
            return false;
        }
        throw new IllegalArgumentException("Use true or false.");
    }

    private List<String> debugResetTargets() {
        List<String> options = new ArrayList<>();
        options.add("all");
        options.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        options.addAll(Arrays.stream(Bukkit.getOfflinePlayers())
            .map(OfflinePlayer::getName)
            .filter(name -> name != null && !name.isBlank())
            .distinct()
            .toList());
        return options.stream().distinct().toList();
    }

    private ResetTarget resolveResetTarget(String input) {
        Player onlinePlayer = Bukkit.getPlayerExact(input);
        if (onlinePlayer != null) {
            return new ResetTarget(onlinePlayer.getUniqueId(), onlinePlayer.getName());
        }

        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            if (offlinePlayer.getName() != null && offlinePlayer.getName().equalsIgnoreCase(input)) {
                return new ResetTarget(offlinePlayer.getUniqueId(), offlinePlayer.getName());
            }
        }

        Optional<com.onemoreblock.trades.service.PlayerTradeDataStore.StoredPlayerIdentity> recordedPlayer = tradeManager.findTrackedPlayer(input);
        if (recordedPlayer.isPresent()) {
            return new ResetTarget(recordedPlayer.get().uuid(), recordedPlayer.get().name());
        }

        try {
            UUID uuid = UUID.fromString(input);
            return new ResetTarget(uuid, input);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private List<String> filter(List<String> options, String token) {
        String lowered = token.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lowered)) {
                matches.add(option);
            }
        }
        return matches;
    }

    private record ResetTarget(UUID uuid, String name) {
    }
}

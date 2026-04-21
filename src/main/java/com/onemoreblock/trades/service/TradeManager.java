package com.onemoreblock.trades.service;

import com.onemoreblock.trades.config.PluginSettings;
import com.onemoreblock.trades.model.TradeCheckResult;
import com.onemoreblock.trades.model.TradeDefinition;
import com.onemoreblock.trades.model.TradeTrigger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

public final class TradeManager {
    private static final Pattern VALID_TRADE_ID = Pattern.compile("^[a-z0-9_-]+$");
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));

    private final JavaPlugin plugin;
    private final PlaceholderService placeholderService;
    private final PlayerTradeDataStore playerTradeDataStore;
    private final EconomyService economyService;
    private PluginSettings settings;
    private final Path tradesDirectory;
    private final Map<String, TradeDefinition> trades;
    private final List<String> validationWarnings;

    public TradeManager(
        JavaPlugin plugin,
        PluginSettings settings,
        PlaceholderService placeholderService,
        PlayerTradeDataStore playerTradeDataStore,
        EconomyService economyService
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.placeholderService = placeholderService;
        this.playerTradeDataStore = playerTradeDataStore;
        this.economyService = economyService;
        this.tradesDirectory = plugin.getDataFolder().toPath().resolve("Trades");
        this.trades = new LinkedHashMap<>();
        this.validationWarnings = new ArrayList<>();
    }

    public void setSettings(PluginSettings settings) {
        this.settings = settings;
    }

    public void reloadAll() {
        trades.clear();
        validationWarnings.clear();
        playerTradeDataStore.reload();
        economyService.refresh();
        try {
            Files.createDirectories(tradesDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create trades directory", exception);
        }

        try (Stream<Path> files = Files.list(tradesDirectory)) {
            files
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                .sorted()
                .forEach(this::loadTradeFile);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not list trade files", exception);
        }
    }

    public Collection<TradeDefinition> allTrades() {
        return Collections.unmodifiableCollection(trades.values());
    }

    public List<String> tradeIds() {
        return trades.keySet().stream().sorted().toList();
    }

    public List<String> tradeCategories() {
        return trades.values().stream()
            .map(TradeDefinition::category)
            .map(this::normalizeCategory)
            .distinct()
            .sorted()
            .toList();
    }

    public Optional<TradeDefinition> findTrade(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(trades.get(id.trim().toLowerCase(Locale.ROOT)));
    }

    public List<TradeDefinition> visibleTrades(Player player, boolean adminView) {
        return visibleTrades(player, adminView, null);
    }

    public List<TradeDefinition> visibleTrades(Player player, boolean adminView, String categoryFilter) {
        Comparator<TradeDefinition> comparator = Comparator
            .comparingInt(TradeDefinition::sortOrder)
            .thenComparing(TradeDefinition::id, String.CASE_INSENSITIVE_ORDER);

        String normalizedCategory = normalizeOptionalCategory(categoryFilter);
        return trades.values().stream()
            .filter(trade -> categoryMatches(trade, normalizedCategory))
            .filter(trade -> canViewTrade(player, trade, adminView))
            .sorted(comparator)
            .toList();
    }

    public boolean isValidTradeId(String id) {
        return id != null && VALID_TRADE_ID.matcher(id).matches();
    }

    public Path createTrade(String id) throws IOException {
        String normalized = normalizeId(id);
        if (!isValidTradeId(normalized)) {
            throw new IllegalArgumentException("Invalid trade id: " + id);
        }
        if (findTrade(normalized).isPresent()) {
            throw new IllegalArgumentException("Trade already exists: " + normalized);
        }
        Path path = tradePath(normalized);
        if (Files.exists(path)) {
            throw new IllegalArgumentException("Trade already exists: " + normalized);
        }

        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("id", normalized);
        configuration.set("enabled", true);
        configuration.set("sort-order", 0);
        configuration.set("category", "general");
        configuration.set("display-name", prettifyId(normalized));
        configuration.set("description", List.of(settings.defaultTradeDescription()));
        configuration.set("permission", settings.defaultTradePermission(normalized));
        configuration.set("completion-permission", "");
        configuration.set("max-trades", 1);
        configuration.set("hide-when-completed", false);
        configuration.set("allowed-worlds", List.of("global"));
        configuration.set("money-cost", 0D);
        configuration.set("exp-cost", 0);
        configuration.set("start-date", "");
        configuration.set("end-date", "");
        configuration.set("ctext-file", "");
        configuration.set("requirements", List.of());
        configuration.set("icon-item", null);
        configuration.set("reward-item", null);
        configuration.set("commands.open", List.of());
        configuration.set("commands.info", List.of());
        configuration.set("commands.success", List.of());
        configuration.set("commands.fail", List.of());
        configuration.save(path.toFile());
        reloadAll();
        return path;
    }

    public Path cloneTrade(String sourceId, String newId) throws IOException {
        TradeDefinition source = findTrade(sourceId).orElseThrow(() -> new IllegalArgumentException("Trade not found: " + sourceId));
        String normalizedTarget = normalizeId(newId);
        if (!isValidTradeId(normalizedTarget)) {
            throw new IllegalArgumentException("Invalid trade id: " + newId);
        }
        if (findTrade(normalizedTarget).isPresent()) {
            throw new IllegalArgumentException("Trade already exists: " + normalizedTarget);
        }

        YamlConfiguration sourceConfig = YamlConfiguration.loadConfiguration(source.file().toFile());
        sourceConfig.set("id", normalizedTarget);
        Path targetPath = tradePath(normalizedTarget);
        sourceConfig.save(targetPath.toFile());
        reloadAll();
        return targetPath;
    }

    public void deleteTrade(String id) throws IOException {
        TradeDefinition trade = findTrade(id).orElseThrow(() -> new IllegalArgumentException("Trade not found: " + id));
        Files.deleteIfExists(trade.file());
        reloadAll();
    }

    public void captureRequirements(String id, Player player) throws IOException {
        List<ItemStack> captured = new ArrayList<>();
        ItemStack[] storageContents = player.getInventory().getStorageContents();
        int start = Math.min(storageContents.length, 9);
        for (int slot = start; slot < storageContents.length; slot++) {
            ItemStack item = storageContents[slot];
            if (item == null || item.getType().isAir()) {
                continue;
            }
            captured.add(item.clone());
        }

        mutateTrade(id, config -> config.set("requirements", captured));
    }

    public void captureIcon(String id, ItemStack item) throws IOException {
        ItemStack icon = item.clone();
        icon.setAmount(1);
        mutateTrade(id, config -> config.set("icon-item", icon));
    }

    public void captureReward(String id, ItemStack item) throws IOException {
        ItemStack reward = item.clone();
        reward.setAmount(1);
        mutateTrade(id, config -> config.set("reward-item", reward));
    }

    public void setEnabled(String id, boolean enabled) throws IOException {
        mutateTrade(id, config -> config.set("enabled", enabled));
    }

    public void setDisplayName(String id, String displayName) throws IOException {
        mutateTrade(id, config -> config.set("display-name", displayName));
    }

    public void setDescription(String id, String description) throws IOException {
        mutateTrade(id, config -> config.set("description", splitDescription(description)));
    }

    public void setPermission(String id, String permission) throws IOException {
        mutateTrade(id, config -> config.set("permission", permission));
    }

    public void setCompletionPermission(String id, String permission) throws IOException {
        mutateTrade(id, config -> config.set("completion-permission", permission));
    }

    public void setMaxTrades(String id, int maxTrades) throws IOException {
        mutateTrade(id, config -> config.set("max-trades", sanitizeMaxTrades(maxTrades)));
    }

    public void setHideWhenCompleted(String id, boolean hideWhenCompleted) throws IOException {
        mutateTrade(id, config -> config.set("hide-when-completed", hideWhenCompleted));
    }

    public void setAllowedWorlds(String id, String value) throws IOException {
        List<String> worlds = parseAllowedWorldsInput(value);
        mutateTrade(id, config -> config.set("allowed-worlds", worlds));
    }

    public void setMoneyCost(String id, double moneyCost) throws IOException {
        mutateTrade(id, config -> config.set("money-cost", sanitizeMoneyCost(moneyCost)));
    }

    public void setExpCost(String id, int expCost) throws IOException {
        mutateTrade(id, config -> config.set("exp-cost", sanitizeExpCost(expCost)));
    }

    public void setStartDate(String id, String value) throws IOException {
        mutateTrade(id, config -> config.set("start-date", normalizeDateText(value)));
    }

    public void setEndDate(String id, String value) throws IOException {
        mutateTrade(id, config -> config.set("end-date", normalizeDateText(value)));
    }

    public void setCategory(String id, String category) throws IOException {
        mutateTrade(id, config -> config.set("category", normalizeCategory(category)));
    }

    public void setCtextFile(String id, String ctextFile) throws IOException {
        mutateTrade(id, config -> config.set("ctext-file", ctextFile));
    }

    public void setSortOrder(String id, int sortOrder) throws IOException {
        mutateTrade(id, config -> config.set("sort-order", sortOrder));
    }

    public void addCommand(String id, TradeTrigger trigger, String command) throws IOException {
        mutateTrade(id, config -> {
            String path = "commands." + trigger.tradeConfigKey();
            List<String> commands = new ArrayList<>(config.getStringList(path));
            commands.add(command);
            config.set(path, commands);
        });
    }

    public void clearCommands(String id, TradeTrigger trigger) throws IOException {
        mutateTrade(id, config -> config.set("commands." + trigger.tradeConfigKey(), List.of()));
    }

    public TradeCheckResult evaluateTrade(Player player, TradeDefinition trade) {
        if (!trade.enabled()) {
            return TradeCheckResult.failed(TradeCheckResult.Status.DISABLED);
        }
        if (!hasAccess(player, trade, false)) {
            return TradeCheckResult.failed(TradeCheckResult.Status.NO_PERMISSION);
        }
        if (!isWorldAllowed(player, trade)) {
            return TradeCheckResult.blockedWorld(player.getWorld().getName(), displayAllowedWorlds(trade));
        }

        LocalDate today = LocalDate.now();
        if (trade.startDate() != null && today.isBefore(trade.startDate())) {
            return TradeCheckResult.notStarted(trade.startDate());
        }
        if (trade.endDate() != null && today.isAfter(trade.endDate())) {
            return TradeCheckResult.expired(trade.endDate());
        }
        if (isCompleted(player, trade)) {
            return TradeCheckResult.failed(TradeCheckResult.Status.ALREADY_COMPLETED);
        }

        List<ItemStack> missingItems = findMissingItems(player.getInventory(), trade.requirements());
        double missingMoney = missingMoney(player, trade);
        int missingExpLevels = missingExpLevels(player, trade);
        if (!missingItems.isEmpty() || missingMoney > 0D || missingExpLevels > 0) {
            return TradeCheckResult.missing(missingItems, missingMoney, missingExpLevels);
        }
        return TradeCheckResult.successful();
    }

    public TradeCheckResult consumeTrade(Player player, TradeDefinition trade) {
        TradeCheckResult initial = evaluateTrade(player, trade);
        if (!initial.success()) {
            return initial;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack[] working = cloneArray(inventory.getStorageContents());
        List<ItemStack> missingItems = removeRequirements(working, trade.requirements());
        double missingMoney = missingMoney(player, trade);
        int missingExpLevels = missingExpLevels(player, trade);
        if (!missingItems.isEmpty() || missingMoney > 0D || missingExpLevels > 0) {
            return TradeCheckResult.missing(missingItems, missingMoney, missingExpLevels);
        }

        double moneyCost = trade.moneyCost();
        if (moneyCost > 0D) {
            EconomyService.EconomyResult withdrawal = economyService.withdraw(player, moneyCost);
            if (!withdrawal.success()) {
                plugin.getLogger().warning("Could not withdraw " + moneyCost + " from " + player.getName() + ": " + withdrawal.message());
                return TradeCheckResult.missing(List.of(), moneyCost, 0);
            }
        }

        try {
            inventory.setStorageContents(working);
            if (trade.expCost() > 0) {
                player.giveExpLevels(-trade.expCost());
            }
            player.updateInventory();
            recordTradeUse(player, trade);
            return TradeCheckResult.successful();
        } catch (RuntimeException exception) {
            if (moneyCost > 0D) {
                EconomyService.EconomyResult refund = economyService.deposit(player, moneyCost);
                if (!refund.success()) {
                    plugin.getLogger().warning("Could not refund " + moneyCost + " to " + player.getName() + " after a failed trade apply.");
                }
            }
            throw exception;
        }
    }

    public String summarizeItems(List<ItemStack> items) {
        List<ItemStack> mergedItems = mergeSimilarItems(items);
        if (mergedItems.isEmpty()) {
            return "Nothing";
        }
        return mergedItems.stream()
            .filter(Objects::nonNull)
            .map(item -> item.getAmount() + "x " + placeholderService.escapeMiniMessageTokens(placeholderService.plainItemName(item)))
            .reduce((left, right) -> left + ", " + right)
            .orElse("Nothing");
    }

    public String summarizeMissingRequirements(TradeCheckResult result) {
        List<String> parts = new ArrayList<>();
        if (result.hasMissingItems()) {
            parts.add(summarizeItems(result.missingItems()));
        }
        if (result.hasMissingMoney()) {
            parts.add("Money: " + formatMoney(result.missingMoney()));
        }
        if (result.hasMissingExpLevels()) {
            parts.add("Levels: " + result.missingExpLevels());
        }
        if (parts.isEmpty()) {
            return "Nothing";
        }
        return String.join(" | ", parts);
    }

    public int totalItemAmount(List<ItemStack> items) {
        return items.stream()
            .filter(item -> item != null && !item.getType().isAir())
            .mapToInt(ItemStack::getAmount)
            .sum();
    }

    public int countMatchingItems(PlayerInventory inventory, ItemStack requiredItem) {
        if (requiredItem == null || requiredItem.getType().isAir()) {
            return 0;
        }
        return Arrays.stream(inventory.getStorageContents())
            .filter(Objects::nonNull)
            .filter(candidate -> !candidate.getType().isAir())
            .filter(candidate -> candidate.isSimilar(requiredItem))
            .mapToInt(ItemStack::getAmount)
            .sum();
    }

    public boolean canViewTrade(Player player, TradeDefinition trade, boolean adminView) {
        if (adminView) {
            return true;
        }
        if (!trade.enabled()) {
            return false;
        }
        if (!hasAccess(player, trade, false)) {
            return false;
        }
        if (trade.hideWhenCompleted() && isCompleted(player, trade)) {
            return false;
        }
        return true;
    }

    public boolean hasAccess(Player player, TradeDefinition trade, boolean adminView) {
        if (adminView) {
            return true;
        }
        String permission = effectivePermission(trade);
        return permission.isBlank() || player.hasPermission(permission);
    }

    public boolean isWorldAllowed(Player player, TradeDefinition trade) {
        String currentWorld = player.getWorld().getName();
        if (settings.blacklistedWorlds().stream().anyMatch(world -> world.equalsIgnoreCase(currentWorld))) {
            return false;
        }
        List<String> allowedWorlds = normalizedAllowedWorlds(trade.allowedWorlds());
        return allowedWorlds.isEmpty()
            || allowedWorlds.stream().anyMatch(world -> world.equalsIgnoreCase("global"))
            || allowedWorlds.stream().anyMatch(world -> world.equalsIgnoreCase(currentWorld));
    }

    public Path playerDataDirectory() {
        return playerTradeDataStore.directory();
    }

    public int trackedPlayersCount() {
        return playerTradeDataStore.trackedPlayersCount();
    }

    public List<String> validationWarnings() {
        return List.copyOf(validationWarnings);
    }

    public boolean economyAvailable() {
        return economyService.available();
    }

    public String economyProviderName() {
        return economyService.providerName();
    }

    public String effectivePermission(TradeDefinition trade) {
        String permission = trade.permission();
        if (permission == null || permission.isBlank()) {
            return settings.defaultTradePermission(trade.id());
        }
        return resolveTradeValue(trade, permission);
    }

    public String effectiveCompletionPermission(TradeDefinition trade) {
        return resolveTradeValue(trade, trade.completionPermission());
    }

    public String effectiveCtextFile(TradeDefinition trade) {
        return resolveTradeValue(trade, trade.ctextFile());
    }

    public boolean isCompleted(Player player, TradeDefinition trade) {
        return !isUnlimited(trade) && tradeUses(player, trade) >= trade.maxTrades();
    }

    public boolean isUnlimited(TradeDefinition trade) {
        return trade.maxTrades() < 0;
    }

    public int tradeUses(Player player, TradeDefinition trade) {
        int storedUses = playerTradeDataStore.usageCount(player.getUniqueId(), trade.id());
        if (playerTradeDataStore.isCompletionPermissionSynced(player.getUniqueId(), trade.id())) {
            return storedUses;
        }

        String completionPermission = effectiveCompletionPermission(trade);
        if (!completionPermission.isBlank() && player.hasPermission(completionPermission)) {
            playerTradeDataStore.synchronizeCompletionPermission(player, trade.id(), 1);
            return Math.max(storedUses, 1);
        }

        return storedUses;
    }

    public int remainingTrades(Player player, TradeDefinition trade) {
        if (isUnlimited(trade)) {
            return -1;
        }
        return Math.max(0, trade.maxTrades() - tradeUses(player, trade));
    }

    public String formattedMaxTrades(TradeDefinition trade) {
        return isUnlimited(trade) ? "unlimited" : String.valueOf(trade.maxTrades());
    }

    public String formattedRemainingTrades(Player player, TradeDefinition trade) {
        int remaining = remainingTrades(player, trade);
        return remaining < 0 ? "unlimited" : String.valueOf(remaining);
    }

    public double playerBalance(Player player) {
        return economyService.balance(player);
    }

    public String formatMoney(double amount) {
        return MONEY_FORMAT.format(Math.max(0D, amount));
    }

    public int missingExpLevels(Player player, TradeDefinition trade) {
        return Math.max(0, trade.expCost() - player.getLevel());
    }

    public double missingMoney(Player player, TradeDefinition trade) {
        if (trade.moneyCost() <= 0D) {
            return 0D;
        }
        if (!economyService.available()) {
            return trade.moneyCost();
        }
        return Math.max(0D, trade.moneyCost() - economyService.balance(player));
    }

    public List<String> displayAllowedWorlds(TradeDefinition trade) {
        List<String> allowedWorlds = normalizedAllowedWorlds(trade.allowedWorlds());
        if (allowedWorlds.isEmpty() || allowedWorlds.stream().anyMatch(world -> world.equalsIgnoreCase("global"))) {
            return List.of("any non-blacklisted world");
        }
        return allowedWorlds.stream().distinct().toList();
    }

    public String allowedWorldsDescription(TradeDefinition trade) {
        return String.join(", ", displayAllowedWorlds(trade));
    }

    public String formattedDate(LocalDate date) {
        return date == null ? "" : String.format(Locale.US, "%02d-%02d-%04d", date.getMonthValue(), date.getDayOfMonth(), date.getYear());
    }

    public String categoryDisplayName(String category) {
        return prettifyCategory(category);
    }

    public int resetTradeUsage(String tradeId, java.util.UUID uuid, String playerName) {
        return playerTradeDataStore.resetUsage(uuid, playerName, normalizeId(tradeId));
    }

    public int resetTradeUsageForAll(String tradeId) {
        return playerTradeDataStore.resetUsageForAll(normalizeId(tradeId));
    }

    public Optional<PlayerTradeDataStore.StoredPlayerIdentity> findTrackedPlayer(String input) {
        return playerTradeDataStore.findRecordedPlayer(input);
    }

    public Map<String, Integer> usageCounts(java.util.UUID uuid) {
        return playerTradeDataStore.usageCounts(uuid);
    }

    private void loadTradeFile(Path path) {
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(path.toFile());
            String id = normalizeId(config.getString("id", stripExtension(path.getFileName().toString())));
            if (!isValidTradeId(id)) {
                warn(path, "Trade id '" + id + "' is invalid.");
                return;
            }
            if (trades.containsKey(id)) {
                warn(path, "Trade id '" + id + "' is duplicated. Ignoring this file.");
                return;
            }

            boolean enabled = config.getBoolean("enabled", true);
            int sortOrder = config.getInt("sort-order", 0);
            String category = normalizeCategory(config.getString("category", "general"));
            String displayName = config.getString("display-name", prettifyId(id));
            List<String> description = List.copyOf(config.getStringList("description"));
            String permission = config.getString("permission", settings.defaultTradePermission(id));
            String completionPermission = config.getString("completion-permission", "");
            int maxTrades = sanitizeMaxTrades(readMaxTrades(config, path));
            boolean hideWhenCompleted = config.getBoolean("hide-when-completed", false);
            List<String> allowedWorlds = sanitizeAllowedWorlds(config.getStringList("allowed-worlds"));
            double moneyCost = sanitizeMoneyCost(readMoneyCost(config, path));
            int expCost = sanitizeExpCost(config.getInt("exp-cost", 0));
            LocalDate startDate = parseConfiguredDate(config.getString("start-date", ""), path, "start-date");
            LocalDate endDate = parseConfiguredDate(config.getString("end-date", ""), path, "end-date");
            if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
                warn(path, "end-date is earlier than start-date.");
            }
            String ctextFile = config.getString("ctext-file", "");
            ItemStack iconItem = deserializeItem(config.get("icon-item"));
            ItemStack rewardItem = deserializeItem(config.get("reward-item"));
            List<ItemStack> requirements = loadItemList(config.getList("requirements", List.of()));
            if (requirements.size() > 27) {
                warn(path, "Trade has " + requirements.size() + " requirement stacks. Only the first 27 can be displayed in the GUI.");
            }

            EnumMap<TradeTrigger, List<String>> commands = new EnumMap<>(TradeTrigger.class);
            ConfigurationSection commandSection = config.getConfigurationSection("commands");
            for (TradeTrigger trigger : TradeTrigger.perTradeTriggers()) {
                List<String> values = commandSection == null
                    ? List.of()
                    : commandSection.getStringList(trigger.tradeConfigKey()).stream()
                        .map(String::trim)
                        .filter(command -> !command.isEmpty())
                        .toList();
                commands.put(trigger, values);
            }

            trades.put(id, new TradeDefinition(
                id,
                path,
                enabled,
                sortOrder,
                category,
                displayName,
                description,
                permission == null ? "" : permission,
                completionPermission == null ? "" : completionPermission,
                maxTrades,
                hideWhenCompleted,
                allowedWorlds,
                moneyCost,
                expCost,
                startDate,
                endDate,
                ctextFile == null ? "" : ctextFile,
                iconItem,
                rewardItem,
                requirements,
                commands
            ));
        } catch (Exception exception) {
            warn(path, "Could not load trade file: " + exception.getMessage());
        }
    }

    private void mutateTrade(String id, ConfigMutator mutator) throws IOException {
        String normalized = normalizeId(id);
        Path path = tradePath(normalized);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Trade not found: " + normalized);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(path.toFile());
        mutator.accept(config);
        config.save(path.toFile());
        reloadAll();
    }

    private Path tradePath(String id) {
        TradeDefinition loaded = trades.get(normalizeId(id));
        if (loaded != null) {
            return loaded.file();
        }
        return tradesDirectory.resolve(tradeFileName(id));
    }

    private String normalizeId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeCategory(String category) {
        String normalized = category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? "general" : normalized;
    }

    private String normalizeOptionalCategory(String category) {
        String normalized = category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || normalized.equals("all")) {
            return "";
        }
        return normalized;
    }

    private boolean categoryMatches(TradeDefinition trade, String categoryFilter) {
        return categoryFilter == null || categoryFilter.isBlank() || normalizeCategory(trade.category()).equals(categoryFilter);
    }

    private String prettifyCategory(String category) {
        return prettifyId(normalizeCategory(category));
    }

    private List<String> splitDescription(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\\|"))
            .map(String::trim)
            .filter(part -> !part.isEmpty())
            .toList();
    }

    private List<ItemStack> findMissingItems(PlayerInventory inventory, List<ItemStack> requirements) {
        ItemStack[] working = cloneArray(inventory.getStorageContents());
        return removeRequirements(working, requirements);
    }

    private List<ItemStack> removeRequirements(ItemStack[] working, List<ItemStack> requirements) {
        List<ItemStack> missing = new ArrayList<>();
        for (ItemStack requirement : requirements) {
            if (requirement == null || requirement.getType().isAir()) {
                continue;
            }

            int remaining = requirement.getAmount();
            for (int slot = 0; slot < working.length && remaining > 0; slot++) {
                ItemStack candidate = working[slot];
                if (candidate == null || candidate.getType().isAir() || !candidate.isSimilar(requirement)) {
                    continue;
                }

                int removed = Math.min(candidate.getAmount(), remaining);
                remaining -= removed;
                int newAmount = candidate.getAmount() - removed;
                if (newAmount <= 0) {
                    working[slot] = null;
                } else {
                    candidate.setAmount(newAmount);
                }
            }

            if (remaining > 0) {
                ItemStack missingItem = requirement.clone();
                missingItem.setAmount(remaining);
                missing.add(missingItem);
            }
        }
        return missing;
    }

    private ItemStack[] cloneArray(ItemStack[] source) {
        ItemStack[] clone = new ItemStack[source.length];
        for (int index = 0; index < source.length; index++) {
            clone[index] = source[index] == null ? null : source[index].clone();
        }
        return clone;
    }

    private List<ItemStack> loadItemList(List<?> rawValues) {
        List<ItemStack> items = new ArrayList<>();
        for (Object rawValue : rawValues) {
            ItemStack item = deserializeItem(rawValue);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    @SuppressWarnings("unchecked")
    private ItemStack deserializeItem(Object rawValue) {
        if (rawValue instanceof ItemStack itemStack) {
            return itemStack.clone();
        }
        if (rawValue instanceof Map<?, ?> rawMap) {
            return ItemStack.deserialize((Map<String, Object>) rawMap);
        }
        return null;
    }

    private String stripExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index == -1 ? fileName : fileName.substring(0, index);
    }

    private String tradeFileName(String id) {
        String[] parts = normalizeId(id).replace('_', '-').split("-");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('-');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        if (builder.isEmpty()) {
            builder.append("Trade");
        }
        builder.append(".yml");
        return builder.toString();
    }

    private String prettifyId(String id) {
        String[] parts = id.replace('-', ' ').replace('_', ' ').split(" ");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private String resolveTradeValue(TradeDefinition trade, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return placeholderService.apply(null, value, tradeValuePlaceholders(trade)).trim();
    }

    private Map<String, String> tradeValuePlaceholders(TradeDefinition trade) {
        HashMap<String, String> replacements = new HashMap<>();
        replacements.put("id", trade.id());
        replacements.put("trade_id", trade.id());
        replacements.put("trade_name", trade.displayName());
        replacements.put("trade_description", String.join(" ", trade.description()));
        replacements.put("category", trade.category());
        return replacements;
    }

    private List<ItemStack> mergeSimilarItems(List<ItemStack> items) {
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }

            boolean mergedIntoExisting = false;
            for (ItemStack existing : merged) {
                if (existing != null && existing.isSimilar(item)) {
                    existing.setAmount(existing.getAmount() + item.getAmount());
                    mergedIntoExisting = true;
                    break;
                }
            }

            if (!mergedIntoExisting) {
                merged.add(item.clone());
            }
        }
        return merged;
    }

    private int recordTradeUse(Player player, TradeDefinition trade) {
        int baselineUses = tradeUses(player, trade);
        return playerTradeDataStore.incrementUsage(player, trade.id(), baselineUses);
    }

    private int readMaxTrades(YamlConfiguration configuration, Path path) {
        Object rawValue = configuration.get("max-trades");
        if (rawValue instanceof Number number) {
            return number.intValue();
        }
        if (rawValue instanceof String textValue) {
            String normalized = textValue.trim().toLowerCase(Locale.ROOT);
            if (normalized.isBlank()) {
                return 1;
            }
            if (normalized.equals("unlimited") || normalized.equals("infinite")) {
                return -1;
            }
            try {
                return Integer.parseInt(normalized);
            } catch (NumberFormatException ignored) {
                warn(path, "Invalid max-trades value '" + textValue + "'. Falling back to 1.");
                return 1;
            }
        }
        return 1;
    }

    private double readMoneyCost(YamlConfiguration configuration, Path path) {
        Object rawValue = configuration.get("money-cost");
        if (rawValue instanceof Number number) {
            return number.doubleValue();
        }
        if (rawValue instanceof String textValue) {
            try {
                return Double.parseDouble(textValue.trim());
            } catch (NumberFormatException ignored) {
                warn(path, "Invalid money-cost value '" + textValue + "'. Falling back to 0.");
            }
        }
        return 0D;
    }

    private int sanitizeMaxTrades(int value) {
        if (value < 0) {
            return -1;
        }
        return value == 0 ? 1 : value;
    }

    private double sanitizeMoneyCost(double value) {
        return value < 0D ? 0D : value;
    }

    private int sanitizeExpCost(int value) {
        return Math.max(0, value);
    }

    private List<String> parseAllowedWorldsInput(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("global")) {
            return List.of("global");
        }
        return sanitizeAllowedWorlds(Arrays.stream(value.split("[,|]"))
            .map(String::trim)
            .filter(part -> !part.isEmpty())
            .toList());
    }

    private List<String> sanitizeAllowedWorlds(List<String> worlds) {
        List<String> sanitized = worlds == null
            ? new ArrayList<>()
            : worlds.stream()
                .map(world -> world == null ? "" : world.trim())
                .filter(world -> !world.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
        if (sanitized.isEmpty()) {
            sanitized.add("global");
        }
        if (sanitized.stream().anyMatch(world -> world.equalsIgnoreCase("global"))) {
            return List.of("global");
        }
        return sanitized.stream().distinct().toList();
    }

    private List<String> normalizedAllowedWorlds(List<String> worlds) {
        return sanitizeAllowedWorlds(worlds).stream()
            .map(world -> world.toLowerCase(Locale.ROOT))
            .toList();
    }

    private String normalizeDateText(String input) {
        if (input == null || input.isBlank() || input.equalsIgnoreCase("none") || input.equals("-")) {
            return "";
        }
        LocalDate parsed = parseDateInput(input);
        return formattedDate(parsed);
    }

    private LocalDate parseConfiguredDate(String rawValue, Path path, String field) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return parseDateInput(rawValue);
        } catch (IllegalArgumentException exception) {
            warn(path, "Invalid " + field + " value '" + rawValue + "'. Use MM-dd-yyyy or yyyy-MM-dd.");
            return null;
        }
    }

    private LocalDate parseDateInput(String input) {
        String normalized = input.trim().replace('/', '-');
        String[] parts = normalized.split("-");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid date: " + input);
        }

        try {
            if (parts[0].length() == 4) {
                int year = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int day = Integer.parseInt(parts[2]);
                return LocalDate.of(year, month, day);
            }

            int first = Integer.parseInt(parts[0]);
            int second = Integer.parseInt(parts[1]);
            int year = Integer.parseInt(parts[2]);
            if (first > 12) {
                return LocalDate.of(year, second, first);
            }
            return LocalDate.of(year, first, second);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid date: " + input, exception);
        }
    }

    private void warn(Path path, String message) {
        String fullMessage = path.getFileName() + ": " + message;
        validationWarnings.add(fullMessage);
        plugin.getLogger().warning(fullMessage);
    }

    @FunctionalInterface
    private interface ConfigMutator {
        void accept(YamlConfiguration configuration) throws IOException;
    }
}

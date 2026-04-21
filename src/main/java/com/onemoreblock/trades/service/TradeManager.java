package com.onemoreblock.trades.service;

import com.onemoreblock.trades.config.PluginSettings;
import com.onemoreblock.trades.model.TradeCheckResult;
import com.onemoreblock.trades.model.TradeDefinition;
import com.onemoreblock.trades.model.TradeTrigger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.stream.Stream;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

public final class TradeManager {
    private static final Pattern VALID_TRADE_ID = Pattern.compile("^[a-z0-9_-]+$");

    private final JavaPlugin plugin;
    private final PlaceholderService placeholderService;
    private final PlayerTradeDataStore playerTradeDataStore;
    private PluginSettings settings;
    private final Path tradesDirectory;
    private final Map<String, TradeDefinition> trades;

    public TradeManager(
        JavaPlugin plugin,
        PluginSettings settings,
        PlaceholderService placeholderService,
        PlayerTradeDataStore playerTradeDataStore
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.placeholderService = placeholderService;
        this.playerTradeDataStore = playerTradeDataStore;
        this.tradesDirectory = plugin.getDataFolder().toPath().resolve("Trades");
        this.trades = new LinkedHashMap<>();
    }

    public void setSettings(PluginSettings settings) {
        this.settings = settings;
    }

    public void reloadAll() {
        trades.clear();
        playerTradeDataStore.reload();
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

    public Optional<TradeDefinition> findTrade(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(trades.get(id.toLowerCase(Locale.ROOT)));
    }

    public List<TradeDefinition> visibleTrades(Player player, boolean adminView) {
        Comparator<TradeDefinition> comparator = Comparator
            .comparingInt(TradeDefinition::sortOrder)
            .thenComparing(TradeDefinition::id, String.CASE_INSENSITIVE_ORDER);

        return trades.values().stream()
            .filter(trade -> canViewTrade(player, trade, adminView))
            .sorted(comparator)
            .toList();
    }

    public boolean isValidTradeId(String id) {
        return id != null && VALID_TRADE_ID.matcher(id).matches();
    }

    public Path createTrade(String id) throws IOException {
        String normalized = normalizeId(id);
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
        configuration.set("display-name", prettifyId(normalized));
        configuration.set("description", List.of(settings.defaultTradeDescription()));
        configuration.set("permission", settings.defaultTradePermission(normalized));
        configuration.set("completion-permission", "");
        configuration.set("max-trades", 1);
        configuration.set("hide-when-completed", false);
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
        int sanitizedMaxTrades = sanitizeMaxTrades(maxTrades);
        mutateTrade(id, config -> config.set("max-trades", sanitizedMaxTrades));
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
        if (isCompleted(player, trade)) {
            return TradeCheckResult.failed(TradeCheckResult.Status.ALREADY_COMPLETED);
        }

        List<ItemStack> missing = findMissingItems(player.getInventory(), trade.requirements());
        if (!missing.isEmpty()) {
            return TradeCheckResult.missing(missing);
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
        List<ItemStack> missing = removeRequirements(working, trade.requirements());
        if (!missing.isEmpty()) {
            return TradeCheckResult.missing(missing);
        }

        inventory.setStorageContents(working);
        player.updateInventory();
        recordTradeUse(player, trade);
        return TradeCheckResult.successful();
    }

    public String summarizeItems(List<ItemStack> items) {
        List<ItemStack> mergedItems = mergeSimilarItems(items);
        if (mergedItems.isEmpty()) {
            return "Nothing";
        }
        return mergedItems.stream()
            .filter(Objects::nonNull)
            .map(item -> item.getAmount() + "x " + placeholderService.plainItemName(item))
            .reduce((left, right) -> left + ", " + right)
            .orElse("Nothing");
    }

    public int totalItemAmount(List<ItemStack> items) {
        return items.stream()
            .filter(item -> item != null && !item.getType().isAir())
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
        return !(trade.hideWhenCompleted() && isCompleted(player, trade));
    }

    public boolean hasAccess(Player player, TradeDefinition trade, boolean adminView) {
        if (adminView) {
            return true;
        }
        String permission = effectivePermission(trade);
        return permission.isBlank() || player.hasPermission(permission);
    }

    public Path playerDataDirectory() {
        return playerTradeDataStore.directory();
    }

    public int trackedPlayersCount() {
        return playerTradeDataStore.trackedPlayersCount();
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

    public int resetTradeUsage(String tradeId, java.util.UUID uuid, String playerName) {
        return playerTradeDataStore.resetUsage(uuid, playerName, normalizeId(tradeId));
    }

    public int resetTradeUsageForAll(String tradeId) {
        return playerTradeDataStore.resetUsageForAll(normalizeId(tradeId));
    }

    public Optional<PlayerTradeDataStore.StoredPlayerIdentity> findTrackedPlayer(String input) {
        return playerTradeDataStore.findRecordedPlayer(input);
    }

    private void loadTradeFile(Path path) {
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(path.toFile());
            String id = normalizeId(config.getString("id", stripExtension(path.getFileName().toString())));
            boolean enabled = config.getBoolean("enabled", true);
            int sortOrder = config.getInt("sort-order", 0);
            String displayName = config.getString("display-name", prettifyId(id));
            List<String> description = List.copyOf(config.getStringList("description"));
            String permission = config.getString("permission", settings.defaultTradePermission(id));
            String completionPermission = config.getString("completion-permission", "");
            int maxTrades = sanitizeMaxTrades(readMaxTrades(config));
            boolean hideWhenCompleted = config.getBoolean("hide-when-completed", false);
            String ctextFile = config.getString("ctext-file", "");
            ItemStack iconItem = deserializeItem(config.get("icon-item"));
            ItemStack rewardItem = deserializeItem(config.get("reward-item"));
            List<ItemStack> requirements = loadItemList(config.getList("requirements", List.of()));

            EnumMap<TradeTrigger, List<String>> commands = new EnumMap<>(TradeTrigger.class);
            ConfigurationSection commandSection = config.getConfigurationSection("commands");
            for (TradeTrigger trigger : TradeTrigger.perTradeTriggers()) {
                List<String> values = commandSection == null
                    ? List.of()
                    : List.copyOf(commandSection.getStringList(trigger.tradeConfigKey()));
                commands.put(trigger, values);
            }

            trades.put(id, new TradeDefinition(
                id,
                path,
                enabled,
                sortOrder,
                displayName,
                description,
                permission == null ? "" : permission,
                completionPermission == null ? "" : completionPermission,
                maxTrades,
                hideWhenCompleted,
                ctextFile == null ? "" : ctextFile,
                iconItem,
                rewardItem,
                requirements,
                commands
            ));
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not load trade file " + path.getFileName() + ": " + exception.getMessage());
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

    private int readMaxTrades(YamlConfiguration configuration) {
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
                plugin.getLogger().warning("Invalid max-trades value '" + textValue + "'. Falling back to 1.");
                return 1;
            }
        }
        return 1;
    }

    private int sanitizeMaxTrades(int value) {
        if (value < 0) {
            return -1;
        }
        return value == 0 ? 1 : value;
    }

    @FunctionalInterface
    private interface ConfigMutator {
        void accept(YamlConfiguration configuration) throws IOException;
    }
}

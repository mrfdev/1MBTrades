package com.onemoreblock.trades;

import com.onemoreblock.trades.command.TradeCommand;
import com.onemoreblock.trades.config.PluginSettings;
import com.onemoreblock.trades.gui.TradeGuiService;
import com.onemoreblock.trades.gui.TradeMenuListener;
import com.onemoreblock.trades.placeholder.PlaceholderRegistration;
import com.onemoreblock.trades.placeholder.OneMBTradesPlaceholderExpansion;
import com.onemoreblock.trades.service.AuditLogService;
import com.onemoreblock.trades.service.CommandActionService;
import com.onemoreblock.trades.service.EconomyService;
import com.onemoreblock.trades.service.PlayerTradeDataStore;
import com.onemoreblock.trades.service.PlaceholderService;
import com.onemoreblock.trades.service.TradeManager;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class OneMBTradesPlugin extends JavaPlugin {
    private PluginSettings settings;
    private PlaceholderService placeholderService;
    private PlayerTradeDataStore playerTradeDataStore;
    private EconomyService economyService;
    private AuditLogService auditLogService;
    private TradeManager tradeManager;
    private CommandActionService commandActionService;
    private TradeGuiService tradeGuiService;
    private PlaceholderRegistration placeholderRegistration;
    private Command globalAliasCommand;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        mergeMissingConfigDefaults();
        migrateConfigDefaults();
        ensurePluginDataLayout();

        placeholderService = new PlaceholderService(this);
        playerTradeDataStore = new PlayerTradeDataStore(this);
        economyService = new EconomyService(this);
        auditLogService = new AuditLogService(this);
        settings = PluginSettings.load(getConfig(), loadLocaleConfig());
        tradeManager = new TradeManager(this, settings, placeholderService, playerTradeDataStore, economyService);
        commandActionService = new CommandActionService(this, tradeManager, placeholderService, settings);
        tradeGuiService = new TradeGuiService(this, tradeManager, commandActionService, placeholderService, auditLogService, settings);
        tradeManager.reloadAll();
        registerPlaceholderExpansion();

        TradeCommand tradeCommand = new TradeCommand(this, tradeManager, tradeGuiService, commandActionService, placeholderService, auditLogService);
        PluginCommand pluginCommand = getCommand("_trade");
        if (pluginCommand == null) {
            throw new IllegalStateException("Command '_trade' is missing from plugin.yml");
        }
        pluginCommand.setExecutor(tradeCommand);
        pluginCommand.setTabCompleter(tradeCommand);
        refreshGlobalAliasCommand();

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(tradeCommand, this);
        pluginManager.registerEvents(new TradeMenuListener(tradeGuiService), this);
    }

    @Override
    public void onDisable() {
        unregisterGlobalAliasCommand();
        if (placeholderRegistration != null) {
            placeholderRegistration.unregisterExpansion();
            placeholderRegistration = null;
        }
    }

    public void reloadPluginState() {
        reloadConfig();
        mergeMissingConfigDefaults();
        reloadConfig();
        settings = PluginSettings.load(getConfig(), loadLocaleConfig());
        tradeManager.setSettings(settings);
        tradeManager.reloadAll();
        commandActionService.setSettings(settings);
        tradeGuiService.setSettings(settings);
        refreshGlobalAliasCommand();
    }

    public PluginSettings settings() {
        return settings;
    }

    public AuditLogService auditLogService() {
        return auditLogService;
    }

    public boolean executeGlobalAlias(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            placeholderService.sendMessage(sender, null, settings.consoleNeedsPlayerMessage(), java.util.Map.of());
            return true;
        }

        String configuredCommand = settings.globalCommand();
        if (configuredCommand == null || configuredCommand.isBlank()) {
            return true;
        }

        String extraArgs = String.join(" ", args);
        String resolved = placeholderService.apply(player, configuredCommand, java.util.Map.of(
            "player", player.getName(),
            "player_name", player.getName(),
            "args", extraArgs
        ));
        if (resolved.startsWith("/")) {
            resolved = resolved.substring(1);
        }
        if (resolved.isBlank()) {
            return true;
        }

        getServer().dispatchCommand(getServer().getConsoleSender(), resolved);
        return true;
    }

    public boolean hasRegisteredGlobalAlias(String alias) {
        if (alias == null || alias.isBlank()) {
            return false;
        }
        Command command = getServer().getCommandMap().getCommand(alias);
        return command != null && command == globalAliasCommand;
    }

    private void registerPlaceholderExpansion() {
        if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }

        placeholderRegistration = new OneMBTradesPlaceholderExpansion(this, tradeManager, placeholderService);
        if (!placeholderRegistration.registerExpansion()) {
            getLogger().warning("Could not register PlaceholderAPI expansion for 1MB-Trades.");
            placeholderRegistration = null;
        }
    }

    private void ensurePluginDataLayout() {
        ensureFolder("Translations");
        ensureFolder("Trades");
        ensureFolder("playerData");
        ensureFolder("logs");
        saveBundledFile("Translations/Locale_EN.yml", "Translations/Locale_EN.yml");
        saveBundledFile("Trades/Example-Vote-Tokens.yml", "Trades/Example-Vote-Tokens.yml", "trades/Example-Vote-Tokens.yml");
        saveBundledFile("Trades/Summer-Event.yml", "Trades/Summer-Event.yml", "trades/Summer-Event.yml");
        migrateLegacyTradesFolder();
        normalizeTradeFileNames();
    }

    private void migrateConfigDefaults() {
        String legacyExpPlaceholder = "%cmi_user_exp%";
        String correctedLevelPlaceholder = "%cmi_user_level%";
        String configuredValue = getConfig().getString("settings.player-exp-placeholder", correctedLevelPlaceholder);
        if (legacyExpPlaceholder.equalsIgnoreCase(configuredValue)) {
            getConfig().set("settings.player-exp-placeholder", correctedLevelPlaceholder);
            saveConfig();
            getLogger().info("Updated settings.player-exp-placeholder to " + correctedLevelPlaceholder + " for player level display.");
        }
    }

    private FileConfiguration loadLocaleConfig() {
        String localeFileName = getConfig().getString("settings.locale-file", "Locale_EN.yml");
        File translationsFolder = ensureFolder("Translations");
        File configuredFile = new File(translationsFolder, localeFileName);
        if (!configuredFile.exists()) {
            String resourcePath = "Translations/" + localeFileName;
            if (getResource(resourcePath) != null) {
                saveBundledFile("Translations/" + localeFileName, resourcePath);
            } else {
                getLogger().warning("Locale file '" + localeFileName + "' was not bundled. Falling back to Locale_EN.yml.");
                configuredFile = new File(translationsFolder, "Locale_EN.yml");
                saveBundledFile("Translations/Locale_EN.yml", "Translations/Locale_EN.yml");
            }
        }
        mergeMissingYamlDefaults(configuredFile, "Translations/" + configuredFile.getName());
        FileConfiguration localeConfig = YamlConfiguration.loadConfiguration(configuredFile);
        if (migrateLocaleDefaults(configuredFile, localeConfig)) {
            localeConfig = YamlConfiguration.loadConfiguration(configuredFile);
        }
        return localeConfig;
    }

    private void refreshGlobalAliasCommand() {
        unregisterGlobalAliasCommand();

        String alias = settings.globalAlias();
        if (alias == null || alias.isBlank()) {
            syncCommands();
            return;
        }

        String normalizedAlias = alias.trim().toLowerCase(Locale.ROOT);
        CommandMap commandMap = getServer().getCommandMap();
        Command existingCommand = commandMap.getCommand(normalizedAlias);
        if (existingCommand != null) {
            getLogger().warning("Could not register global alias '/" + normalizedAlias + "' because a command with that name already exists.");
            syncCommands();
            return;
        }

        DynamicAliasCommand dynamicAliasCommand = new DynamicAliasCommand(normalizedAlias);
        dynamicAliasCommand.setDescription("Opens the configured 1MB-Trades entry point");
        dynamicAliasCommand.setUsage("/" + normalizedAlias);
        if (commandMap.register(getName().toLowerCase(Locale.ROOT), dynamicAliasCommand)) {
            globalAliasCommand = dynamicAliasCommand;
        } else {
            getLogger().warning("Could not register global alias '/" + normalizedAlias + "'.");
        }
        syncCommands();
    }

    private void unregisterGlobalAliasCommand() {
        if (globalAliasCommand == null) {
            return;
        }

        CommandMap commandMap = getServer().getCommandMap();
        globalAliasCommand.unregister(commandMap);
        Map<String, Command> knownCommands = extractKnownCommands(commandMap);
        if (knownCommands != null) {
            knownCommands.entrySet().removeIf(entry -> entry.getValue() == globalAliasCommand);
        }
        globalAliasCommand = null;
        syncCommands();
    }

    private void syncCommands() {
        try {
            Method syncCommandsMethod = getServer().getClass().getMethod("syncCommands");
            syncCommandsMethod.invoke(getServer());
        } catch (ReflectiveOperationException ignored) {
            // Older or different server implementations may not expose syncCommands publicly.
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Command> extractKnownCommands(CommandMap commandMap) {
        try {
            Field knownCommandsField = commandMap.getClass().getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            Object value = knownCommandsField.get(commandMap);
            return value instanceof Map<?, ?> map ? (Map<String, Command>) map : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private boolean migrateLocaleDefaults(File localeFile, FileConfiguration localeConfig) {
        boolean changed = false;

        changed |= migrateLocaleValue(
            localeConfig,
            "gui.index-title",
            "<gradient:#F6D365:#FDA085>Seasonal Trades</gradient>",
            "<#F6D365><bold>Seasonal Trades</bold></#F6D365>"
        );
        changed |= migrateLocaleValue(
            localeConfig,
            "gui.trade-title",
            "<gradient:#F6D365:#FDA085>Trade</gradient><gray>:</gray> <white>%trade_name%</white>",
            "<#F6D365><bold>Trade</bold></#F6D365><gray>:</gray> <white>%trade_name%</white>"
        );

        if (!changed) {
            return false;
        }

        try {
            localeConfig.save(localeFile);
            getLogger().info("Updated legacy locale defaults in " + localeFile.getName() + ".");
            return true;
        } catch (IOException exception) {
            getLogger().warning("Could not update locale file " + localeFile.getName() + ": " + exception.getMessage());
            return false;
        }
    }

    private boolean migrateLocaleValue(FileConfiguration localeConfig, String path, String legacyValue, String replacementValue) {
        String configuredValue = localeConfig.getString(path);
        if (configuredValue == null || !configuredValue.equals(legacyValue)) {
            return false;
        }
        localeConfig.set(path, replacementValue);
        return true;
    }

    private void mergeMissingConfigDefaults() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (mergeMissingYamlDefaults(configFile, "config.yml")) {
            reloadConfig();
        }
    }

    private boolean mergeMissingYamlDefaults(File targetFile, String resourcePath) {
        try (InputStream input = getResource(resourcePath)) {
            if (input == null || !targetFile.exists()) {
                return false;
            }

            YamlConfiguration existing = YamlConfiguration.loadConfiguration(targetFile);
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
            boolean changed = false;
            for (String path : defaults.getKeys(true)) {
                if (existing.contains(path)) {
                    continue;
                }
                existing.set(path, defaults.get(path));
                changed = true;
            }

            if (changed) {
                existing.save(targetFile);
            }
            return changed;
        } catch (IOException exception) {
            getLogger().warning("Could not merge defaults for " + targetFile.getName() + ": " + exception.getMessage());
            return false;
        }
    }

    private final class DynamicAliasCommand extends BukkitCommand {
        private DynamicAliasCommand(String name) {
            super(name, "Opens the configured 1MB-Trades entry point", "/" + name, List.of());
        }

        @Override
        public boolean execute(CommandSender sender, String commandLabel, String[] args) {
            return executeGlobalAlias(sender, args);
        }
    }

    private File ensureFolder(String name) {
        Path dataFolder = getDataFolder().toPath();
        try {
            Files.createDirectories(dataFolder);
            return ensureDirectoryCasing(dataFolder, name).toFile();
        } catch (IOException exception) {
            File fallback = new File(getDataFolder(), name);
            if (!fallback.exists() && !fallback.mkdirs()) {
                getLogger().warning("Could not create folder " + fallback.getAbsolutePath());
            }
            return fallback;
        }
    }

    private Path ensureDirectoryCasing(Path parent, String desiredName) throws IOException {
        Optional<Path> exactMatch = findChildDirectory(parent, desiredName, false);
        if (exactMatch.isPresent()) {
            return exactMatch.get();
        }

        Path exactPath = parent.resolve(desiredName);
        Optional<Path> caseInsensitiveMatch = findChildDirectory(parent, desiredName, true);
        if (caseInsensitiveMatch.isPresent()) {
            Path existing = caseInsensitiveMatch.get();
            Path temporary = parent.resolve(desiredName + ".casefix-" + System.nanoTime());
            Files.move(existing, temporary);
            Files.move(temporary, exactPath);
            getLogger().info("Normalized directory name to " + desiredName + "/");
            return exactPath;
        }

        Files.createDirectories(exactPath);
        return exactPath;
    }

    private void saveBundledFile(String destinationRelativePath, String... resourceCandidates) {
        File destination = new File(getDataFolder(), destinationRelativePath);
        if (destination.exists()) {
            return;
        }

        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            getLogger().warning("Could not create folder " + parent.getAbsolutePath());
            return;
        }

        for (String candidate : resourceCandidates) {
            try (InputStream input = getResource(candidate)) {
                if (input == null) {
                    continue;
                }
                Files.copy(input, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException exception) {
                getLogger().warning("Could not copy bundled file " + candidate + ": " + exception.getMessage());
                return;
            }
        }

        getLogger().warning("Bundled resource not found for " + destinationRelativePath);
    }

    private void migrateLegacyTradesFolder() {
        Path legacyFolder = getDataFolder().toPath().resolve("trades");
        Path newFolder = getDataFolder().toPath().resolve("Trades");
        if (!Files.isDirectory(legacyFolder)) {
            return;
        }

        try (Stream<Path> files = Files.list(legacyFolder)) {
            files.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".yml"))
                .forEach(source -> {
                    Path target = newFolder.resolve(source.getFileName().toString());
                    if (Files.exists(target)) {
                        return;
                    }
                    try {
                        Files.copy(source, target);
                        getLogger().info("Migrated legacy trade file " + source.getFileName() + " to Trades/");
                    } catch (IOException exception) {
                        getLogger().warning("Could not migrate legacy trade file " + source.getFileName() + ": " + exception.getMessage());
                    }
                });
        } catch (IOException exception) {
            getLogger().warning("Could not inspect legacy trades directory: " + exception.getMessage());
        }
    }

    private void normalizeTradeFileNames() {
        Path tradesFolder = getDataFolder().toPath().resolve("Trades");
        if (!Files.isDirectory(tradesFolder)) {
            return;
        }

        try (Stream<Path> files = Files.list(tradesFolder)) {
            files.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                .forEach(this::normalizeTradeFileName);
        } catch (IOException exception) {
            getLogger().warning("Could not normalize trade file names: " + exception.getMessage());
        }
    }

    private void normalizeTradeFileName(Path source) {
        String currentName = source.getFileName().toString();
        String normalizedName = preferredTradeFileName(currentName);
        if (currentName.equals(normalizedName)) {
            return;
        }

        Path target = source.getParent().resolve(normalizedName);
        if (Files.exists(target)) {
            return;
        }

        try {
            Path temporary = source.getParent().resolve(normalizedName + ".casefix-" + System.nanoTime());
            Files.move(source, temporary);
            Files.move(temporary, target);
            getLogger().info("Renamed trade file " + currentName + " to " + normalizedName);
        } catch (IOException exception) {
            getLogger().warning("Could not rename trade file " + currentName + ": " + exception.getMessage());
        }
    }

    private String preferredTradeFileName(String currentName) {
        String stem = currentName;
        int dotIndex = stem.lastIndexOf('.');
        if (dotIndex >= 0) {
            stem = stem.substring(0, dotIndex);
        }

        String[] parts = stem.replace('_', '-').split("-");
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

        return builder + ".yml";
    }

    private Optional<Path> findChildDirectory(Path parent, String name, boolean ignoreCase) throws IOException {
        try (Stream<Path> children = Files.list(parent)) {
            return children
                .filter(Files::isDirectory)
                .filter(path -> matchesName(path.getFileName().toString(), name, ignoreCase))
                .findFirst();
        }
    }

    private boolean matchesName(String current, String desired, boolean ignoreCase) {
        return ignoreCase ? current.equalsIgnoreCase(desired) : current.equals(desired);
    }
}

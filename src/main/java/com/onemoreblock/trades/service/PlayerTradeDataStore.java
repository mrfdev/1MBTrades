package com.onemoreblock.trades.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerTradeDataStore {
    private final JavaPlugin plugin;
    private final Path playerDataDirectory;
    private final Map<UUID, PlayerTradeProfile> profiles;

    public PlayerTradeDataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.playerDataDirectory = plugin.getDataFolder().toPath().resolve("playerData");
        this.profiles = new HashMap<>();
    }

    public void reload() {
        profiles.clear();
        try {
            Files.createDirectories(playerDataDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create playerData directory", exception);
        }
    }

    public Path directory() {
        return playerDataDirectory;
    }

    public int trackedPlayersCount() {
        try (Stream<Path> files = Files.list(playerDataDirectory)) {
            return (int) files
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                .count();
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not count playerData files: " + exception.getMessage());
            return profiles.size();
        }
    }

    public int usageCount(UUID uuid, String tradeId) {
        TradeProgress progress = loadProfile(uuid).tradeProgress(tradeId);
        return progress == null ? 0 : progress.uses();
    }

    public boolean isCompletionPermissionSynced(UUID uuid, String tradeId) {
        TradeProgress progress = loadProfile(uuid).tradeProgress(tradeId);
        return progress != null && progress.completionPermissionSynced();
    }

    public void synchronizeCompletionPermission(Player player, String tradeId, int minimumUses) {
        PlayerTradeProfile profile = loadProfile(player.getUniqueId());
        profile.setLastKnownName(player.getName());
        TradeProgress progress = profile.getOrCreateTradeProgress(tradeId);
        progress.setUses(Math.max(progress.uses(), minimumUses));
        progress.setCompletionPermissionSynced(true);
        saveProfile(profile);
    }

    public int incrementUsage(Player player, String tradeId, int baselineUses) {
        PlayerTradeProfile profile = loadProfile(player.getUniqueId());
        profile.setLastKnownName(player.getName());
        TradeProgress progress = profile.getOrCreateTradeProgress(tradeId);
        int newUses = Math.max(progress.uses(), baselineUses) + 1;
        progress.setUses(newUses);
        progress.setCompletionPermissionSynced(true);
        saveProfile(profile);
        return newUses;
    }

    public int resetUsage(UUID uuid, String playerName, String tradeId) {
        PlayerTradeProfile profile = loadProfile(uuid);
        if (playerName != null && !playerName.isBlank()) {
            profile.setLastKnownName(playerName);
        }
        TradeProgress progress = profile.getOrCreateTradeProgress(tradeId);
        int previousUses = progress.uses();
        progress.setUses(0);
        progress.setCompletionPermissionSynced(true);
        saveProfile(profile);
        return previousUses;
    }

    public int resetUsageForAll(String tradeId) {
        int affectedProfiles = 0;
        for (UUID uuid : knownProfileIds()) {
            PlayerTradeProfile profile = loadProfile(uuid);
            TradeProgress progress = profile.tradeProgress(tradeId);
            if (progress == null) {
                continue;
            }
            progress.setUses(0);
            progress.setCompletionPermissionSynced(true);
            saveProfile(profile);
            affectedProfiles++;
        }
        return affectedProfiles;
    }

    public Optional<StoredPlayerIdentity> findRecordedPlayer(String input) {
        String trimmed = input == null ? "" : input.trim();
        if (trimmed.isBlank()) {
            return Optional.empty();
        }

        try {
            UUID uuid = UUID.fromString(trimmed);
            PlayerTradeProfile profile = loadProfile(uuid);
            String lastKnownName = profile.lastKnownName().isBlank() ? trimmed : profile.lastKnownName();
            return Optional.of(new StoredPlayerIdentity(uuid, lastKnownName));
        } catch (IllegalArgumentException ignored) {
            // Fall through to last-known-name lookup.
        }

        for (UUID uuid : knownProfileIds()) {
            PlayerTradeProfile profile = loadProfile(uuid);
            if (profile.lastKnownName().equalsIgnoreCase(trimmed)) {
                return Optional.of(new StoredPlayerIdentity(uuid, profile.lastKnownName()));
            }
        }

        return Optional.empty();
    }

    public Map<String, Integer> usageCounts(UUID uuid) {
        PlayerTradeProfile profile = loadProfile(uuid);
        Map<String, Integer> usages = new LinkedHashMap<>();
        profile.tradeProgress().forEach((tradeId, progress) -> {
            if (progress.uses() > 0) {
                usages.put(tradeId, progress.uses());
            }
        });
        return usages;
    }

    private Collection<UUID> knownProfileIds() {
        List<UUID> uuids = new ArrayList<>(profiles.keySet());
        try (Stream<Path> files = Files.list(playerDataDirectory)) {
            files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                .map(path -> path.getFileName().toString())
                .map(fileName -> fileName.substring(0, fileName.length() - 4))
                .forEach(fileName -> {
                    try {
                        UUID uuid = UUID.fromString(fileName);
                        if (!uuids.contains(uuid)) {
                            uuids.add(uuid);
                        }
                    } catch (IllegalArgumentException ignored) {
                        plugin.getLogger().warning("Ignoring malformed playerData file: " + fileName + ".yml");
                    }
                });
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not inspect playerData files: " + exception.getMessage());
        }
        return uuids;
    }

    private PlayerTradeProfile loadProfile(UUID uuid) {
        return profiles.computeIfAbsent(uuid, this::loadProfileFromDisk);
    }

    private PlayerTradeProfile loadProfileFromDisk(UUID uuid) {
        Path file = profilePath(uuid);
        if (!Files.exists(file)) {
            return new PlayerTradeProfile(uuid, "", new LinkedHashMap<>());
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file.toFile());
        String lastKnownName = config.getString("last-known-name", "");
        Map<String, TradeProgress> tradeProgress = new LinkedHashMap<>();

        ConfigurationSection tradesSection = config.getConfigurationSection("trades");
        if (tradesSection != null) {
            for (String tradeId : tradesSection.getKeys(false)) {
                ConfigurationSection tradeSection = tradesSection.getConfigurationSection(tradeId);
                if (tradeSection == null) {
                    continue;
                }
                int uses = Math.max(0, tradeSection.getInt("uses", 0));
                boolean synced = tradeSection.getBoolean("completion-permission-synced", false);
                tradeProgress.put(normalizeTradeId(tradeId), new TradeProgress(uses, synced));
            }
        }

        return new PlayerTradeProfile(uuid, lastKnownName, tradeProgress);
    }

    private void saveProfile(PlayerTradeProfile profile) {
        try {
            Files.createDirectories(playerDataDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create playerData directory", exception);
        }

        YamlConfiguration config = new YamlConfiguration();
        config.set("uuid", profile.uuid().toString());
        config.set("last-known-name", profile.lastKnownName());

        for (Map.Entry<String, TradeProgress> entry : profile.tradeProgress().entrySet()) {
            String path = "trades." + entry.getKey();
            config.set(path + ".uses", entry.getValue().uses());
            config.set(path + ".completion-permission-synced", entry.getValue().completionPermissionSynced());
        }

        try {
            config.save(profilePath(profile.uuid()).toFile());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save playerData for " + profile.uuid(), exception);
        }
    }

    private Path profilePath(UUID uuid) {
        return playerDataDirectory.resolve(uuid + ".yml");
    }

    private String normalizeTradeId(String tradeId) {
        return tradeId == null ? "" : tradeId.trim().toLowerCase(Locale.ROOT);
    }

    public record StoredPlayerIdentity(UUID uuid, String name) {
    }

    private static final class PlayerTradeProfile {
        private final UUID uuid;
        private String lastKnownName;
        private final Map<String, TradeProgress> tradeProgress;

        private PlayerTradeProfile(UUID uuid, String lastKnownName, Map<String, TradeProgress> tradeProgress) {
            this.uuid = uuid;
            this.lastKnownName = lastKnownName == null ? "" : lastKnownName;
            this.tradeProgress = tradeProgress;
        }

        public UUID uuid() {
            return uuid;
        }

        public String lastKnownName() {
            return lastKnownName;
        }

        public void setLastKnownName(String lastKnownName) {
            this.lastKnownName = lastKnownName == null ? "" : lastKnownName;
        }

        public Map<String, TradeProgress> tradeProgress() {
            return tradeProgress;
        }

        public TradeProgress tradeProgress(String tradeId) {
            return tradeProgress.get(tradeId);
        }

        public TradeProgress getOrCreateTradeProgress(String tradeId) {
            return tradeProgress.computeIfAbsent(tradeId, ignored -> new TradeProgress(0, false));
        }
    }

    private static final class TradeProgress {
        private int uses;
        private boolean completionPermissionSynced;

        private TradeProgress(int uses, boolean completionPermissionSynced) {
            this.uses = Math.max(0, uses);
            this.completionPermissionSynced = completionPermissionSynced;
        }

        public int uses() {
            return uses;
        }

        public void setUses(int uses) {
            this.uses = Math.max(0, uses);
        }

        public boolean completionPermissionSynced() {
            return completionPermissionSynced;
        }

        public void setCompletionPermissionSynced(boolean completionPermissionSynced) {
            this.completionPermissionSynced = completionPermissionSynced;
        }
    }
}

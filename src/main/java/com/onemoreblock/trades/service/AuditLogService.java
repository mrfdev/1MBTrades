package com.onemoreblock.trades.service;

import com.onemoreblock.trades.model.TradeCheckResult;
import com.onemoreblock.trades.model.TradeDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.StringJoiner;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuditLogService {
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JavaPlugin plugin;
    private final Path logsDirectory;
    private final Path adminLogFile;
    private final Path tradeLogFile;

    public AuditLogService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logsDirectory = plugin.getDataFolder().toPath().resolve("logs");
        this.adminLogFile = logsDirectory.resolve("admin-actions.log");
        this.tradeLogFile = logsDirectory.resolve("player-trades.log");
        ensureDirectories();
    }

    public void logAdminAction(CommandSender actor, String action, Map<String, String> details) {
        String actorType = actor instanceof Player ? "player" : "console";
        String actorName = actor.getName();
        String actorId = actor instanceof Player player ? player.getUniqueId().toString() : "console";
        String line = String.format(
            "%s actor=%s name=%s id=%s action=%s%s",
            timestamp(),
            actorType,
            sanitize(actorName),
            actorId,
            sanitize(action),
            formatDetails(details)
        );
        write(adminLogFile, line);
    }

    public void logTradeAttempt(Player player, TradeDefinition trade, TradeCheckResult result, Map<String, String> details) {
        String line = String.format(
            "%s player=%s uuid=%s world=%s trade=%s category=%s status=%s%s",
            timestamp(),
            sanitize(player.getName()),
            player.getUniqueId(),
            sanitize(player.getWorld().getName()),
            sanitize(trade.id()),
            sanitize(trade.category()),
            sanitize(result.status().name()),
            formatDetails(details)
        );
        write(tradeLogFile, line);
    }

    private void ensureDirectories() {
        try {
            Files.createDirectories(logsDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create audit log directory", exception);
        }
    }

    private void write(Path file, String line) {
        ensureDirectories();
        try {
            Files.writeString(file, line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not write audit log " + file.getFileName() + ": " + exception.getMessage());
        }
    }

    private String timestamp() {
        return TIMESTAMP_FORMAT.format(LocalDateTime.now());
    }

    private String formatDetails(Map<String, String> details) {
        if (details == null || details.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(" ");
        details.forEach((key, value) -> joiner.add(sanitize(key) + "=" + sanitize(value)));
        return " " + joiner;
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "\"\"";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}

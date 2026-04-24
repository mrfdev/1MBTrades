package com.onemoreblock.trades.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class YamlConfigSupport {
    private YamlConfigSupport() {
    }

    public static YamlConfiguration load(File file, Logger logger) {
        return load(file == null ? null : file.toPath(), logger, file == null ? "unknown file" : file.getName());
    }

    public static YamlConfiguration load(Path path, Logger logger) {
        return load(path, logger, path == null ? "unknown path" : path.toAbsolutePath().toString());
    }

    public static YamlConfiguration load(Path path, Logger logger, String sourceDescription) {
        if (path == null || !Files.exists(path)) {
            return new YamlConfiguration();
        }

        try {
            return loadFromString(Files.readString(path, StandardCharsets.UTF_8), logger, sourceDescription);
        } catch (IOException exception) {
            logWarning(logger, sourceDescription, exception);
            return new YamlConfiguration();
        }
    }

    public static YamlConfiguration load(InputStream input, Logger logger, String sourceDescription) {
        if (input == null) {
            return new YamlConfiguration();
        }

        try (input) {
            return loadFromString(new String(input.readAllBytes(), StandardCharsets.UTF_8), logger, sourceDescription);
        } catch (IOException exception) {
            logWarning(logger, sourceDescription, exception);
            return new YamlConfiguration();
        }
    }

    private static YamlConfiguration loadFromString(String content, Logger logger, String sourceDescription) {
        YamlConfiguration configuration = new YamlConfiguration();
        if (content == null || content.isBlank()) {
            return configuration;
        }

        try {
            configuration.loadFromString(content);
        } catch (InvalidConfigurationException exception) {
            logWarning(logger, sourceDescription, exception);
        }
        return configuration;
    }

    private static void logWarning(Logger logger, String sourceDescription, Exception exception) {
        if (logger == null) {
            return;
        }
        logger.warning("Could not load YAML from " + sourceDescription + ": " + exception.getMessage());
    }
}

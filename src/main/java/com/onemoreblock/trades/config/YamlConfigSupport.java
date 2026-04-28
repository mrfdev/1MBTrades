package com.onemoreblock.trades.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
            return newConfiguration();
        }

        try {
            return loadFromString(Files.readString(path, StandardCharsets.UTF_8), logger, sourceDescription);
        } catch (IOException exception) {
            logWarning(logger, sourceDescription, exception);
            return newConfiguration();
        }
    }

    public static YamlConfiguration load(InputStream input, Logger logger, String sourceDescription) {
        if (input == null) {
            return newConfiguration();
        }

        try (input) {
            return loadFromString(new String(input.readAllBytes(), StandardCharsets.UTF_8), logger, sourceDescription);
        } catch (IOException exception) {
            logWarning(logger, sourceDescription, exception);
            return newConfiguration();
        }
    }

    public static boolean syncMissingKeysAndComments(File targetFile, InputStream defaultsInput, Logger logger, String sourceDescription) {
        if (targetFile == null || defaultsInput == null) {
            return false;
        }

        YamlConfiguration existing = load(targetFile, logger);
        YamlConfiguration defaults = load(defaultsInput, logger, sourceDescription);
        boolean changed = copyMissingValuesAndComments(existing, defaults);
        if (!changed) {
            return false;
        }

        try {
            save(targetFile.toPath(), existing);
            return true;
        } catch (IOException exception) {
            logWarning(logger, targetFile.getAbsolutePath(), exception);
            return false;
        }
    }

    public static void save(Path path, YamlConfiguration configuration) throws IOException {
        if (path == null || configuration == null) {
            return;
        }
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        configuration.save(path.toFile());
    }

    private static boolean copyMissingValuesAndComments(YamlConfiguration existing, YamlConfiguration defaults) {
        boolean changed = copyHeaderAndFooter(existing, defaults);
        for (String path : defaults.getKeys(true)) {
            if (!existing.contains(path)) {
                existing.set(path, defaults.get(path));
                changed = true;
            }
            changed |= copyCommentsIfMissing(existing, defaults, path);
        }
        return changed;
    }

    private static boolean copyHeaderAndFooter(YamlConfiguration existing, YamlConfiguration defaults) {
        boolean changed = false;
        List<String> defaultHeader = defaults.options().getHeader();
        if (!defaultHeader.isEmpty() && existing.options().getHeader().isEmpty()) {
            existing.options().setHeader(defaultHeader);
            changed = true;
        }
        List<String> defaultFooter = defaults.options().getFooter();
        if (!defaultFooter.isEmpty() && existing.options().getFooter().isEmpty()) {
            existing.options().setFooter(defaultFooter);
            changed = true;
        }
        return changed;
    }

    private static boolean copyCommentsIfMissing(YamlConfiguration existing, YamlConfiguration defaults, String path) {
        boolean changed = false;
        List<String> defaultComments = defaults.getComments(path);
        if (!defaultComments.isEmpty() && existing.getComments(path).isEmpty()) {
            existing.setComments(path, defaultComments);
            changed = true;
        }
        List<String> defaultInlineComments = defaults.getInlineComments(path);
        if (!defaultInlineComments.isEmpty() && existing.getInlineComments(path).isEmpty()) {
            existing.setInlineComments(path, defaultInlineComments);
            changed = true;
        }
        return changed;
    }

    private static YamlConfiguration loadFromString(String content, Logger logger, String sourceDescription) {
        YamlConfiguration configuration = newConfiguration();
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

    private static YamlConfiguration newConfiguration() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.options().parseComments(true);
        return configuration;
    }

    private static void logWarning(Logger logger, String sourceDescription, Exception exception) {
        if (logger == null) {
            return;
        }
        logger.warning("Could not load YAML from " + sourceDescription + ": " + exception.getMessage());
    }
}

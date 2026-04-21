package com.onemoreblock.trades.service;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class PlaceholderService {
    private final JavaPlugin plugin;
    private final MiniMessage miniMessage;
    private final LegacyComponentSerializer legacySerializer;
    private final LegacyComponentSerializer legacySectionSerializer;
    private final PlainTextComponentSerializer plainSerializer;
    private final Method placeholderMethod;

    public PlaceholderService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        this.legacySerializer = LegacyComponentSerializer.legacyAmpersand();
        this.legacySectionSerializer = LegacyComponentSerializer.legacySection();
        this.plainSerializer = PlainTextComponentSerializer.plainText();
        this.placeholderMethod = findPlaceholderMethod();
    }

    public boolean hasPlaceholderApi() {
        return placeholderMethod != null;
    }

    public String apply(Player player, String input, Map<String, String> replacements) {
        String resolved = input == null ? "" : input;
        Map<String, String> safeReplacements = replacements == null ? Map.of() : replacements;
        for (Map.Entry<String, String> entry : safeReplacements.entrySet()) {
            resolved = resolved.replace("%" + entry.getKey() + "%", entry.getValue() == null ? "" : entry.getValue());
        }

        if (player != null && placeholderMethod != null) {
            try {
                resolved = (String) placeholderMethod.invoke(null, (OfflinePlayer) player, resolved);
            } catch (ReflectiveOperationException exception) {
                plugin.getLogger().warning("Failed to resolve PlaceholderAPI placeholders: " + exception.getMessage());
            }
        }
        return resolved;
    }

    public Component component(Player player, String input, Map<String, String> replacements) {
        String resolved = apply(player, input, replacements);
        if (resolved.isBlank()) {
            return Component.empty();
        }
        if (looksLikeMiniMessage(resolved)) {
            return miniMessage.deserialize(resolved);
        }
        return legacySerializer.deserialize(resolved);
    }

    public List<Component> components(Player player, List<String> lines, Map<String, String> replacements) {
        return lines.stream()
            .map(line -> component(player, line, replacements))
            .toList();
    }

    public void sendMessage(CommandSender sender, Player player, String input, Map<String, String> replacements) {
        if (input == null || input.isBlank()) {
            return;
        }
        sender.sendMessage(component(player, input, replacements));
    }

    public void sendLines(CommandSender sender, Player player, List<String> lines, Map<String, String> replacements) {
        for (String line : lines) {
            sendMessage(sender, player, line, replacements);
        }
    }

    public String legacyText(Player player, String input, Map<String, String> replacements) {
        return legacySectionSerializer.serialize(component(player, input, replacements));
    }

    public String plainText(Player player, String input, Map<String, String> replacements) {
        return plainSerializer.serialize(component(player, input, replacements));
    }

    public String resolveExternalPlaceholder(Player player, String placeholderText, String fallback) {
        if (placeholderText == null || placeholderText.isBlank()) {
            return fallback;
        }
        String resolved = apply(player, placeholderText, Map.of());
        return resolved.equals(placeholderText) ? fallback : resolved;
    }

    public String plainItemName(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "Air";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.displayName() != null) {
            String text = plainSerializer.serialize(meta.displayName()).trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return prettyMaterialName(item.getType());
    }

    public String prettyMaterialName(Material material) {
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    public Map<String, String> merge(Map<String, String> base, Map<String, String> extra) {
        HashMap<String, String> merged = new HashMap<>();
        if (base != null) {
            merged.putAll(base);
        }
        if (extra != null) {
            merged.putAll(extra);
        }
        return merged;
    }

    private Method findPlaceholderMethod() {
        try {
            Class<?> placeholderApi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            return placeholderApi.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private boolean looksLikeMiniMessage(String input) {
        return input.indexOf('<') >= 0 && input.indexOf('>') > input.indexOf('<');
    }
}

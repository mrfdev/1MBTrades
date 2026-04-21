package com.onemoreblock.trades.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.inventory.ItemStack;

public final class TradeDefinition {
    private final String id;
    private final Path file;
    private final boolean enabled;
    private final int sortOrder;
    private final String displayName;
    private final List<String> description;
    private final String permission;
    private final String completionPermission;
    private final int maxTrades;
    private final boolean hideWhenCompleted;
    private final String ctextFile;
    private final ItemStack iconItem;
    private final ItemStack rewardItem;
    private final List<ItemStack> requirements;
    private final Map<TradeTrigger, List<String>> commands;

    public TradeDefinition(
        String id,
        Path file,
        boolean enabled,
        int sortOrder,
        String displayName,
        List<String> description,
        String permission,
        String completionPermission,
        int maxTrades,
        boolean hideWhenCompleted,
        String ctextFile,
        ItemStack iconItem,
        ItemStack rewardItem,
        List<ItemStack> requirements,
        Map<TradeTrigger, List<String>> commands
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.file = Objects.requireNonNull(file, "file");
        this.enabled = enabled;
        this.sortOrder = sortOrder;
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.description = Collections.unmodifiableList(new ArrayList<>(description));
        this.permission = Objects.requireNonNull(permission, "permission");
        this.completionPermission = Objects.requireNonNull(completionPermission, "completionPermission");
        this.maxTrades = maxTrades;
        this.hideWhenCompleted = hideWhenCompleted;
        this.ctextFile = Objects.requireNonNull(ctextFile, "ctextFile");
        this.iconItem = iconItem == null ? null : iconItem.clone();
        this.rewardItem = rewardItem == null ? null : rewardItem.clone();
        this.requirements = Collections.unmodifiableList(cloneItems(requirements));
        EnumMap<TradeTrigger, List<String>> copiedCommands = new EnumMap<>(TradeTrigger.class);
        commands.forEach((trigger, values) -> copiedCommands.put(trigger, List.copyOf(values)));
        this.commands = Collections.unmodifiableMap(copiedCommands);
    }

    public String id() {
        return id;
    }

    public Path file() {
        return file;
    }

    public boolean enabled() {
        return enabled;
    }

    public int sortOrder() {
        return sortOrder;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> description() {
        return description;
    }

    public String permission() {
        return permission;
    }

    public String completionPermission() {
        return completionPermission;
    }

    public int maxTrades() {
        return maxTrades;
    }

    public boolean hideWhenCompleted() {
        return hideWhenCompleted;
    }

    public String ctextFile() {
        return ctextFile;
    }

    public ItemStack iconItem() {
        return iconItem == null ? null : iconItem.clone();
    }

    public ItemStack rewardItem() {
        return rewardItem == null ? null : rewardItem.clone();
    }

    public List<ItemStack> requirements() {
        return cloneItems(requirements);
    }

    public List<String> commands(TradeTrigger trigger) {
        return commands.getOrDefault(trigger, List.of());
    }

    public Map<TradeTrigger, List<String>> allCommands() {
        return commands;
    }

    private static List<ItemStack> cloneItems(List<ItemStack> items) {
        List<ItemStack> clones = new ArrayList<>(items.size());
        for (ItemStack item : items) {
            clones.add(item == null ? null : item.clone());
        }
        return clones;
    }
}

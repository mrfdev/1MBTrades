package com.onemoreblock.trades.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.inventory.ItemStack;

public final class TradeCheckResult {
    public enum Status {
        SUCCESS,
        DISABLED,
        NO_PERMISSION,
        WORLD_BLOCKED,
        NOT_STARTED,
        EXPIRED,
        ALREADY_COMPLETED,
        MISSING_REQUIREMENTS
    }

    private final Status status;
    private final List<ItemStack> missingItems;
    private final double missingMoney;
    private final int missingExpLevels;
    private final String currentWorld;
    private final List<String> allowedWorlds;
    private final LocalDate startDate;
    private final LocalDate endDate;

    private TradeCheckResult(
        Status status,
        List<ItemStack> missingItems,
        double missingMoney,
        int missingExpLevels,
        String currentWorld,
        List<String> allowedWorlds,
        LocalDate startDate,
        LocalDate endDate
    ) {
        this.status = status;
        this.missingItems = Collections.unmodifiableList(new ArrayList<>(missingItems));
        this.missingMoney = Math.max(0D, missingMoney);
        this.missingExpLevels = Math.max(0, missingExpLevels);
        this.currentWorld = currentWorld == null ? "" : currentWorld;
        this.allowedWorlds = Collections.unmodifiableList(new ArrayList<>(allowedWorlds));
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public static TradeCheckResult successful() {
        return new TradeCheckResult(Status.SUCCESS, List.of(), 0D, 0, "", List.of(), null, null);
    }

    public static TradeCheckResult failed(Status status) {
        return new TradeCheckResult(status, List.of(), 0D, 0, "", List.of(), null, null);
    }

    public static TradeCheckResult blockedWorld(String currentWorld, List<String> allowedWorlds) {
        return new TradeCheckResult(Status.WORLD_BLOCKED, List.of(), 0D, 0, currentWorld, allowedWorlds, null, null);
    }

    public static TradeCheckResult notStarted(LocalDate startDate) {
        return new TradeCheckResult(Status.NOT_STARTED, List.of(), 0D, 0, "", List.of(), startDate, null);
    }

    public static TradeCheckResult expired(LocalDate endDate) {
        return new TradeCheckResult(Status.EXPIRED, List.of(), 0D, 0, "", List.of(), null, endDate);
    }

    public static TradeCheckResult missing(List<ItemStack> missingItems, double missingMoney, int missingExpLevels) {
        return new TradeCheckResult(Status.MISSING_REQUIREMENTS, missingItems, missingMoney, missingExpLevels, "", List.of(), null, null);
    }

    public Status status() {
        return status;
    }

    public boolean success() {
        return status == Status.SUCCESS;
    }

    public List<ItemStack> missingItems() {
        return missingItems;
    }

    public double missingMoney() {
        return missingMoney;
    }

    public int missingExpLevels() {
        return missingExpLevels;
    }

    public String currentWorld() {
        return currentWorld;
    }

    public List<String> allowedWorlds() {
        return allowedWorlds;
    }

    public LocalDate startDate() {
        return startDate;
    }

    public LocalDate endDate() {
        return endDate;
    }

    public boolean hasMissingItems() {
        return !missingItems.isEmpty();
    }

    public boolean hasMissingMoney() {
        return missingMoney > 0D;
    }

    public boolean hasMissingExpLevels() {
        return missingExpLevels > 0;
    }
}

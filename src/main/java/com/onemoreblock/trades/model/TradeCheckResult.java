package com.onemoreblock.trades.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.inventory.ItemStack;

public final class TradeCheckResult {
    public enum Status {
        SUCCESS,
        DISABLED,
        NO_PERMISSION,
        ALREADY_COMPLETED,
        MISSING_ITEMS
    }

    private final Status status;
    private final List<ItemStack> missingItems;

    private TradeCheckResult(Status status, List<ItemStack> missingItems) {
        this.status = status;
        this.missingItems = Collections.unmodifiableList(new ArrayList<>(missingItems));
    }

    public static TradeCheckResult successful() {
        return new TradeCheckResult(Status.SUCCESS, List.of());
    }

    public static TradeCheckResult failed(Status status) {
        return new TradeCheckResult(status, List.of());
    }

    public static TradeCheckResult missing(List<ItemStack> missingItems) {
        return new TradeCheckResult(Status.MISSING_ITEMS, missingItems);
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
}

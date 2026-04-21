package com.onemoreblock.trades.model;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public enum TradeTrigger {
    OPEN_INDEX("open-index", null, "Runs when the player opens the main trade index"),
    OPEN_TRADE("open-trade", "open", "Runs when the player opens a specific trade"),
    INFO("info", "info", "Runs when the info book is clicked"),
    SUCCESS("success", "success", "Runs after the trade consumes the required items"),
    FAIL("fail", "fail", "Runs after a trade attempt fails");

    private static final List<TradeTrigger> PER_TRADE = List.of(OPEN_TRADE, INFO, SUCCESS, FAIL);

    private final String globalConfigKey;
    private final String tradeConfigKey;
    private final String description;

    TradeTrigger(String globalConfigKey, String tradeConfigKey, String description) {
        this.globalConfigKey = globalConfigKey;
        this.tradeConfigKey = tradeConfigKey;
        this.description = description;
    }

    public String globalConfigKey() {
        return globalConfigKey;
    }

    public String tradeConfigKey() {
        return tradeConfigKey;
    }

    public String description() {
        return description;
    }

    public static List<TradeTrigger> perTradeTriggers() {
        return PER_TRADE;
    }

    public static TradeTrigger fromTradeConfigKey(String key) {
        String normalized = Objects.requireNonNullElse(key, "").trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
            .filter(trigger -> normalized.equals(trigger.tradeConfigKey))
            .findFirst()
            .orElse(null);
    }
}


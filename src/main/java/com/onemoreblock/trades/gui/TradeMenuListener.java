package com.onemoreblock.trades.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class TradeMenuListener implements Listener {
    private final TradeGuiService guiService;

    public TradeMenuListener(TradeGuiService guiService) {
        this.guiService = guiService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        guiService.handleClick(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        guiService.handleDrag(event);
    }
}

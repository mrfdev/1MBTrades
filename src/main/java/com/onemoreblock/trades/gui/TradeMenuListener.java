package com.onemoreblock.trades.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class TradeMenuListener implements Listener {
    private final TradeGuiService guiService;

    public TradeMenuListener(TradeGuiService guiService) {
        this.guiService = guiService;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        guiService.handleClick(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        guiService.handleDrag(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClose(InventoryCloseEvent event) {
        guiService.handleClose(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        guiService.handlePlayerDisconnect(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onKick(PlayerKickEvent event) {
        guiService.handlePlayerDisconnect(event.getPlayer());
    }
}

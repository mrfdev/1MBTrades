package com.onemoreblock.trades.gui;

import com.onemoreblock.trades.config.ConfiguredItemSpec;
import com.onemoreblock.trades.config.PluginSettings;
import com.onemoreblock.trades.model.TradeCheckResult;
import com.onemoreblock.trades.model.TradeDefinition;
import com.onemoreblock.trades.model.TradeTrigger;
import com.onemoreblock.trades.service.CommandActionService;
import com.onemoreblock.trades.service.PlaceholderService;
import com.onemoreblock.trades.service.TradeManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;

public final class TradeGuiService {
    private static final int MENU_SIZE = 54;
    private static final int INDEX_PAGE_SIZE = 45;
    private static final int SLOT_REWARD = 40;
    private static final int SLOT_PLAYER_HEAD = 45;
    private static final int SLOT_STATUS = 46;
    private static final int SLOT_INFO = 47;
    private static final int SLOT_PREVIOUS = 48;
    private static final int SLOT_PAGE = 49;
    private static final int SLOT_TRADE = 49;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_BACK = 51;
    private static final int SLOT_CLOSE = 53;
    private static final List<Integer> REQUIREMENT_SLOTS = buildRequirementSlots();

    private final JavaPlugin plugin;
    private final TradeManager tradeManager;
    private final CommandActionService commandActionService;
    private final PlaceholderService placeholderService;
    private PluginSettings settings;

    public TradeGuiService(
        JavaPlugin plugin,
        TradeManager tradeManager,
        CommandActionService commandActionService,
        PlaceholderService placeholderService,
        PluginSettings settings
    ) {
        this.plugin = plugin;
        this.tradeManager = tradeManager;
        this.commandActionService = commandActionService;
        this.placeholderService = placeholderService;
        this.settings = settings;
    }

    public void setSettings(PluginSettings settings) {
        this.settings = settings;
    }

    public void openIndex(Player player) {
        openIndex(player, 0);
    }

    public void openIndex(Player player, int page) {
        boolean adminView = player.hasPermission(settings.adminPermission());
        List<TradeDefinition> visibleTrades = tradeManager.visibleTrades(player, adminView);
        int maxPage = Math.max(1, (int) Math.ceil(visibleTrades.size() / (double) INDEX_PAGE_SIZE));
        int currentPage = Math.max(0, Math.min(page, maxPage - 1));

        TradeMenuHolder holder = new TradeMenuHolder(MenuType.INDEX, null, currentPage);
        Inventory inventory = Bukkit.createInventory(
            holder,
            MENU_SIZE,
            placeholderService.component(player, settings.indexTitle(), Map.of())
        );
        holder.setInventory(inventory);

        fillInventory(inventory);
        inventory.setItem(SLOT_PLAYER_HEAD, createPlayerHead(player));
        inventory.setItem(SLOT_CLOSE, createConfiguredItem(settings.closeButtonItem(), player, Map.of()));
        inventory.setItem(SLOT_PREVIOUS, createConfiguredItem(settings.previousPageItem(), player, Map.of()));
        inventory.setItem(SLOT_NEXT, createConfiguredItem(settings.nextPageItem(), player, Map.of()));
        inventory.setItem(SLOT_PAGE, createConfiguredItem(settings.pageIndicatorItem(), player, Map.of(
            "page", String.valueOf(currentPage + 1),
            "max_page", String.valueOf(maxPage)
        )));

        int startIndex = currentPage * INDEX_PAGE_SIZE;
        int endIndex = Math.min(startIndex + INDEX_PAGE_SIZE, visibleTrades.size());
        for (int index = startIndex; index < endIndex; index++) {
            TradeDefinition trade = visibleTrades.get(index);
            inventory.setItem(index - startIndex, createTradeIndexItem(player, trade, adminView));
        }

        player.openInventory(inventory);
        commandActionService.runIndexOpen(player);
    }

    public boolean openTrade(Player player, TradeDefinition trade, int returnPage) {
        boolean adminView = player.hasPermission(settings.adminPermission());
        if (!adminView) {
            if (!trade.enabled()) {
                sendTradeMessage(player, trade, settings.tradeDisabledMessage(), Map.of());
                return false;
            }
            if (!tradeManager.hasAccess(player, trade, false)) {
                sendTradeMessage(player, trade, settings.tradeLockedMessage(), Map.of());
                return false;
            }
        }

        Map<String, String> replacements = tradePlaceholders(player, trade);
        TradeMenuHolder holder = new TradeMenuHolder(MenuType.DETAIL, trade.id(), returnPage);
        Inventory inventory = Bukkit.createInventory(
            holder,
            MENU_SIZE,
            placeholderService.component(player, settings.tradeTitle(), replacements)
        );
        holder.setInventory(inventory);

        fillInventory(inventory);
        inventory.setItem(SLOT_PLAYER_HEAD, createPlayerHead(player));
        inventory.setItem(SLOT_STATUS, createStatusItem(player, trade));
        inventory.setItem(SLOT_INFO, createConfiguredItem(settings.infoBookItem(), player, replacements));
        inventory.setItem(SLOT_TRADE, createConfiguredItem(settings.tradeButtonItem(), player, replacements));
        inventory.setItem(SLOT_BACK, createConfiguredItem(settings.backButtonItem(), player, replacements));
        inventory.setItem(SLOT_CLOSE, createConfiguredItem(settings.closeButtonItem(), player, replacements));
        inventory.setItem(SLOT_REWARD, createRewardPreview(player, trade));

        List<ItemStack> requirements = trade.requirements();
        for (int index = 0; index < requirements.size() && index < REQUIREMENT_SLOTS.size(); index++) {
            ItemStack requirement = requirements.get(index);
            if (requirement == null || requirement.getType().isAir()) {
                continue;
            }
            inventory.setItem(REQUIREMENT_SLOTS.get(index), requirement.clone());
        }

        player.openInventory(inventory);
        commandActionService.runTradeTrigger(player, trade, TradeTrigger.OPEN_TRADE, replacements);
        return true;
    }

    public void handleClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof TradeMenuHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(topInventory)) {
            return;
        }

        int slot = event.getRawSlot();
        switch (holder.type()) {
            case INDEX -> handleIndexClick(player, holder.page(), slot);
            case DETAIL -> handleDetailClick(player, holder.tradeId(), holder.page(), slot);
        }
    }

    public void handleDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof TradeMenuHolder) {
            event.setCancelled(true);
        }
    }

    private void handleIndexClick(Player player, int page, int slot) {
        boolean adminView = player.hasPermission(settings.adminPermission());
        List<TradeDefinition> visibleTrades = tradeManager.visibleTrades(player, adminView);
        int maxPage = Math.max(1, (int) Math.ceil(visibleTrades.size() / (double) INDEX_PAGE_SIZE));

        if (slot >= 0 && slot < INDEX_PAGE_SIZE) {
            int index = page * INDEX_PAGE_SIZE + slot;
            if (index >= 0 && index < visibleTrades.size()) {
                TradeDefinition trade = visibleTrades.get(index);
                schedule(() -> openTrade(player, trade, page));
            }
            return;
        }

        if (slot == SLOT_PREVIOUS && page > 0) {
            schedule(() -> openIndex(player, page - 1));
            return;
        }
        if (slot == SLOT_NEXT && page + 1 < maxPage) {
            schedule(() -> openIndex(player, page + 1));
            return;
        }
        if (slot == SLOT_CLOSE) {
            schedule(player::closeInventory);
        }
    }

    private void handleDetailClick(Player player, String tradeId, int page, int slot) {
        TradeDefinition trade = tradeManager.findTrade(tradeId).orElse(null);
        if (trade == null) {
            placeholderService.sendMessage(player, player, settings.tradeNotFoundMessage(), Map.of("trade_id", tradeId));
            schedule(player::closeInventory);
            return;
        }

        if (slot == SLOT_INFO) {
            Map<String, String> replacements = detailPlaceholders(player, trade, tradeManager.evaluateTrade(player, trade));
            schedule(player::closeInventory);
            scheduleLater(() -> commandActionService.runTradeTrigger(player, trade, TradeTrigger.INFO, replacements), 1L);
            return;
        }
        if (slot == SLOT_TRADE) {
            TradeCheckResult result = tradeManager.consumeTrade(player, trade);
            Map<String, String> replacements = detailPlaceholders(player, trade, result);
            sendResultMessage(player, trade, result);
            if (result.success()) {
                commandActionService.runTradeTrigger(player, trade, TradeTrigger.SUCCESS, replacements);
                if (settings.closeOnSuccess()) {
                    schedule(player::closeInventory);
                } else {
                    schedule(() -> openTrade(player, trade, page));
                }
            } else {
                commandActionService.runTradeTrigger(player, trade, TradeTrigger.FAIL, replacements);
                schedule(() -> openTrade(player, trade, page));
            }
            return;
        }
        if (slot == SLOT_BACK) {
            schedule(() -> openIndex(player, page));
            return;
        }
        if (slot == SLOT_CLOSE) {
            schedule(player::closeInventory);
        }
    }

    private ItemStack createTradeIndexItem(Player player, TradeDefinition trade, boolean adminView) {
        ItemStack icon = trade.iconItem();
        if (icon == null || icon.getType().isAir()) {
            if (!trade.requirements().isEmpty()) {
                icon = trade.requirements().get(0).clone();
                icon.setAmount(1);
            } else {
                icon = new ItemStack(Material.CHEST);
            }
        } else {
            icon = icon.clone();
            icon.setAmount(1);
        }

        ItemMeta meta = icon.getItemMeta();
        Map<String, String> replacements = tradePlaceholders(player, trade);
        List<String> loreLines = new ArrayList<>(trade.description());
        if (!loreLines.isEmpty()) {
            loreLines.add("");
        }
        if (trade.rewardItem() != null) {
            loreLines.add(settings.tradeIndexRewardLine());
        }
        loreLines.add(settings.tradeIndexRequirementsLine());
        loreLines.add(settings.tradeIndexStatusLine());
        if (adminView) {
            loreLines.add(settings.tradeIndexAdminFileLine());
        }
        loreLines.add(settings.tradeIndexInspectLine());
        replacements.put("reward_name", trade.rewardItem() == null ? "" : placeholderService.plainItemName(trade.rewardItem()));
        replacements.put("requirements_count", String.valueOf(trade.requirements().size()));
        replacements.put("requirements_suffix", trade.requirements().size() == 1 ? "y" : "ies");
        replacements.put("trade_status", indexStatusText(player, trade, adminView));
        replacements.put("trade_file", trade.file().getFileName().toString());
        meta.displayName(placeholderService.component(player, trade.displayName(), replacements));
        meta.lore(placeholderService.components(player, loreLines, replacements));
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack createRewardPreview(Player player, TradeDefinition trade) {
        ItemStack reward = trade.rewardItem();
        if (reward == null || reward.getType().isAir()) {
            return createConfiguredItem(settings.rewardPreviewItem(), player, tradePlaceholders(player, trade));
        }

        ItemStack item = reward.clone();
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>();
        if (meta.lore() != null) {
            lore.addAll(meta.lore());
        }
        lore.add(Component.empty());
        lore.add(placeholderService.component(player, settings.rewardPreviewExtraLine(), Map.of()));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createStatusItem(Player player, TradeDefinition trade) {
        TradeCheckResult result = tradeManager.evaluateTrade(player, trade);
        Map<String, String> replacements = detailPlaceholders(player, trade, result);
        ConfiguredItemSpec spec = switch (result.status()) {
            case SUCCESS -> settings.readyStatusItem();
            case MISSING_ITEMS -> settings.missingStatusItem();
            case DISABLED, NO_PERMISSION, ALREADY_COMPLETED -> settings.lockedStatusItem();
        };

        List<String> extraLore = switch (result.status()) {
            case DISABLED -> List.of(settings.tradeDisabledMessage());
            case NO_PERMISSION -> List.of(settings.tradeLockedMessage());
            case ALREADY_COMPLETED -> List.of(completedMessage(trade));
            default -> List.of();
        };
        return createConfiguredItem(spec, player, replacements, extraLore);
    }

    private ItemStack createPlayerHead(Player player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(player);
        String resolvedExp = placeholderService.resolveExternalPlaceholder(
            player,
            settings.playerExpPlaceholder(),
            String.valueOf(player.getLevel())
        );
        String resolvedBalance = placeholderService.resolveExternalPlaceholder(
            player,
            settings.playerBalancePlaceholder(),
            settings.unavailableBalanceText()
        );

        Map<String, String> replacements = new HashMap<>();
        replacements.put("player", player.getName());
        replacements.put("player_name", player.getName());
        replacements.put("player_exp", resolvedExp);
        replacements.put("player_balance", resolvedBalance);
        meta.displayName(placeholderService.component(player, settings.playerHeadName(), replacements));
        meta.lore(placeholderService.components(player, settings.playerHeadLore(), replacements));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createConfiguredItem(ConfiguredItemSpec spec, Player player, Map<String, String> replacements) {
        return createConfiguredItem(spec, player, replacements, List.of());
    }

    private ItemStack createConfiguredItem(
        ConfiguredItemSpec spec,
        Player player,
        Map<String, String> replacements,
        List<String> extraLoreLines
    ) {
        ItemStack item = new ItemStack(spec.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(placeholderService.component(player, spec.name(), replacements));
        List<String> loreLines = new ArrayList<>(spec.lore());
        loreLines.addAll(extraLoreLines);
        meta.lore(placeholderService.components(player, loreLines, replacements));
        item.setItemMeta(meta);
        return item;
    }

    private void fillInventory(Inventory inventory) {
        ItemStack filler = createConfiguredItem(settings.fillerItem(), null, Map.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler.clone());
        }
    }

    private void sendResultMessage(Player player, TradeDefinition trade, TradeCheckResult result) {
        if (!settings.sendPluginResultMessages()) {
            return;
        }
        TradeTrigger feedbackTrigger = result.success() ? TradeTrigger.SUCCESS : TradeTrigger.FAIL;
        if (commandActionService.hasChatFeedbackCommand(trade, feedbackTrigger)) {
            return;
        }
        String message = switch (result.status()) {
            case SUCCESS -> settings.tradeSuccessMessage();
            case DISABLED -> settings.tradeDisabledMessage();
            case NO_PERMISSION -> settings.tradeLockedMessage();
            case ALREADY_COMPLETED -> completedMessage(trade);
            case MISSING_ITEMS -> settings.tradeMissingMessage();
        };
        sendTradeMessage(player, trade, message, Map.of(
            "missing_items", tradeManager.summarizeItems(result.missingItems()),
            "missing_amount", String.valueOf(tradeManager.totalItemAmount(result.missingItems()))
        ));
    }

    private void sendTradeMessage(Player player, TradeDefinition trade, String message, Map<String, String> extraPlaceholders) {
        placeholderService.sendMessage(player, player, message, placeholderService.merge(tradePlaceholders(player, trade), extraPlaceholders));
    }

    private String indexStatusText(Player player, TradeDefinition trade, boolean adminView) {
        if (adminView && !trade.enabled()) {
            return settings.indexStatusDisabled();
        }
        TradeCheckResult result = tradeManager.evaluateTrade(player, trade);
        return switch (result.status()) {
            case SUCCESS -> settings.indexStatusReady();
            case MISSING_ITEMS -> settings.indexStatusCollecting();
            case ALREADY_COMPLETED -> trade.maxTrades() == 1 ? settings.indexStatusUnlocked() : settings.indexStatusLimitReached();
            case DISABLED -> settings.indexStatusDisabled();
            case NO_PERMISSION -> settings.indexStatusLocked();
        };
    }

    private Map<String, String> tradePlaceholders(Player player, TradeDefinition trade) {
        HashMap<String, String> replacements = new HashMap<>();
        String maxTrades = tradeManager.formattedMaxTrades(trade);
        String remainingTrades = tradeManager.formattedRemainingTrades(player, trade);
        replacements.put("player", player.getName());
        replacements.put("player_name", player.getName());
        replacements.put("player_uuid", player.getUniqueId().toString());
        replacements.put("id", trade.id());
        replacements.put("trade_id", trade.id());
        replacements.put("trade_name", trade.displayName());
        replacements.put("trade_description", String.join(" ", trade.description()));
        replacements.put("trade_permission", tradeManager.effectivePermission(trade));
        replacements.put("ctext_file", tradeManager.effectiveCtextFile(trade));
        replacements.put("requirements_count", String.valueOf(trade.requirements().size()));
        replacements.put("required_items", tradeManager.summarizeItems(trade.requirements()));
        replacements.put("item_cost", String.valueOf(tradeManager.totalItemAmount(trade.requirements())));
        replacements.put("trade_uses", String.valueOf(tradeManager.tradeUses(player, trade)));
        replacements.put("max_trades", maxTrades);
        replacements.put("max_uses", maxTrades);
        replacements.put("remaining_trades", remainingTrades);
        replacements.put("remaining_uses", remainingTrades);
        replacements.put("missing_items", "");
        replacements.put("missing_amount", "0");
        return replacements;
    }

    private Map<String, String> detailPlaceholders(Player player, TradeDefinition trade, TradeCheckResult result) {
        HashMap<String, String> replacements = new HashMap<>(tradePlaceholders(player, trade));
        replacements.put("missing_items", tradeManager.summarizeItems(result.missingItems()));
        replacements.put("missing_amount", String.valueOf(tradeManager.totalItemAmount(result.missingItems())));
        return replacements;
    }

    private String completedMessage(TradeDefinition trade) {
        return trade.maxTrades() == 1 ? settings.tradeCompletedMessage() : settings.tradeLimitReachedMessage();
    }

    private void schedule(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    private void scheduleLater(Runnable task, long delayTicks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    private static List<Integer> buildRequirementSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < 27; slot++) {
            slots.add(slot);
        }
        return List.copyOf(slots);
    }

    private enum MenuType {
        INDEX,
        DETAIL
    }

    private static final class TradeMenuHolder implements InventoryHolder {
        private final MenuType type;
        private final String tradeId;
        private final int page;
        private Inventory inventory;

        private TradeMenuHolder(MenuType type, String tradeId, int page) {
            this.type = type;
            this.tradeId = tradeId;
            this.page = page;
        }

        public MenuType type() {
            return type;
        }

        public String tradeId() {
            return tradeId;
        }

        public int page() {
            return page;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}

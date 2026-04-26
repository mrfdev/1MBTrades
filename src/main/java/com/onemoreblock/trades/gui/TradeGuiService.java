package com.onemoreblock.trades.gui;

import com.onemoreblock.trades.config.ConfiguredItemSpec;
import com.onemoreblock.trades.config.PluginSettings;
import com.onemoreblock.trades.model.TradeCheckResult;
import com.onemoreblock.trades.model.TradeDefinition;
import com.onemoreblock.trades.model.TradeTrigger;
import com.onemoreblock.trades.service.AuditLogService;
import com.onemoreblock.trades.service.CommandActionService;
import com.onemoreblock.trades.service.PlaceholderService;
import com.onemoreblock.trades.service.TradeManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;

public final class TradeGuiService {
    private static final long GUI_CLOSE_DELAY_TICKS = 4L;
    private static final long GUI_FOLLOW_UP_DELAY_TICKS = 5L;
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
    private final AuditLogService auditLogService;
    private final Map<UUID, Long> lastTradeClickTimes;
    private final Map<UUID, Long> activeMenuSessions;
    private final Map<UUID, Long> processingTradeSessions;
    private PluginSettings settings;
    private long nextSessionId;

    public TradeGuiService(
        JavaPlugin plugin,
        TradeManager tradeManager,
        CommandActionService commandActionService,
        PlaceholderService placeholderService,
        AuditLogService auditLogService,
        PluginSettings settings
    ) {
        this.plugin = plugin;
        this.tradeManager = tradeManager;
        this.commandActionService = commandActionService;
        this.placeholderService = placeholderService;
        this.auditLogService = auditLogService;
        this.settings = settings;
        this.lastTradeClickTimes = new HashMap<>();
        this.activeMenuSessions = new HashMap<>();
        this.processingTradeSessions = new HashMap<>();
        this.nextSessionId = 1L;
    }

    public void setSettings(PluginSettings settings) {
        this.settings = settings;
    }

    public void openIndex(Player player) {
        openIndex(player, 0, null);
    }

    public void openIndex(Player player, int page) {
        openIndex(player, page, null);
    }

    public void openIndex(Player player, int page, String category) {
        if (!canUsePlayer(player)) {
            return;
        }

        boolean adminView = player.hasPermission(settings.adminPermission());
        List<TradeDefinition> visibleTrades = tradeManager.visibleTrades(player, adminView, category);
        int maxPage = Math.max(1, (int) Math.ceil(visibleTrades.size() / (double) INDEX_PAGE_SIZE));
        int currentPage = Math.max(0, Math.min(page, maxPage - 1));

        String categoryFilter = normalizeCategory(category);
        TradeMenuHolder holder = new TradeMenuHolder(MenuType.INDEX, null, currentPage, categoryFilter, nextSessionId());
        Map<String, String> titleReplacements = Map.of(
            "category", categoryFilter.isBlank() ? "all" : categoryFilter,
            "category_name", categoryFilter.isBlank() ? "All Trades" : tradeManager.categoryDisplayName(categoryFilter)
        );
        Inventory inventory = Bukkit.createInventory(
            holder,
            MENU_SIZE,
            placeholderService.component(player, settings.indexTitle(), titleReplacements)
        );
        holder.setInventory(inventory);

        fillInventory(inventory);
        inventory.setItem(SLOT_PLAYER_HEAD, createPlayerHead(player));
        inventory.setItem(SLOT_CLOSE, createConfiguredItem(settings.closeButtonItem(), player, titleReplacements));
        inventory.setItem(SLOT_PREVIOUS, createConfiguredItem(settings.previousPageItem(), player, titleReplacements));
        inventory.setItem(SLOT_NEXT, createConfiguredItem(settings.nextPageItem(), player, titleReplacements));
        inventory.setItem(SLOT_PAGE, createConfiguredItem(settings.pageIndicatorItem(), player, Map.of(
            "page", String.valueOf(currentPage + 1),
            "max_page", String.valueOf(maxPage),
            "category", titleReplacements.get("category"),
            "category_name", titleReplacements.get("category_name")
        )));

        int startIndex = currentPage * INDEX_PAGE_SIZE;
        int endIndex = Math.min(startIndex + INDEX_PAGE_SIZE, visibleTrades.size());
        for (int index = startIndex; index < endIndex; index++) {
            TradeDefinition trade = visibleTrades.get(index);
            inventory.setItem(index - startIndex, createTradeIndexItem(player, trade, adminView));
        }

        registerActiveSession(player, holder);
        player.openInventory(inventory);
        commandActionService.runIndexOpen(player);
    }

    public boolean openTrade(Player player, TradeDefinition trade, int returnPage) {
        return openTrade(player, trade, returnPage, null);
    }

    public boolean openTrade(Player player, TradeDefinition trade, int returnPage, String returnCategory) {
        if (!canUsePlayer(player)) {
            return false;
        }

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
        TradeMenuHolder holder = new TradeMenuHolder(MenuType.DETAIL, trade.id(), returnPage, normalizeCategory(returnCategory), nextSessionId());
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
            inventory.setItem(REQUIREMENT_SLOTS.get(index), createRequirementPreview(player, trade, requirement));
        }

        registerActiveSession(player, holder);
        player.openInventory(inventory);
        commandActionService.runTradeTrigger(player, trade, TradeTrigger.OPEN_TRADE, replacements);
        return true;
    }

    public void handleClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder(false) instanceof TradeMenuHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!isCurrentSession(player, holder)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= topInventory.getSize()) {
            return;
        }
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(topInventory)) {
            return;
        }
        if (!isActionableButtonClick(event.getClick())) {
            return;
        }
        switch (holder.type()) {
            case INDEX -> handleIndexClick(player, holder, slot);
            case DETAIL -> handleDetailClick(player, holder, slot);
        }
    }

    public void handleDrag(InventoryDragEvent event) {
        if (isTradeMenuInventory(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    public void handleClose(InventoryCloseEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder(false) instanceof TradeMenuHolder holder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        clearActiveSession(player.getUniqueId(), holder.sessionId());
        releaseTradeProcessing(player.getUniqueId(), holder.sessionId());
    }

    public void handlePlayerDisconnect(Player player) {
        if (player == null) {
            return;
        }
        clearPlayerState(player.getUniqueId(), true);
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isTradeMenuInventory(player.getOpenInventory().getTopInventory())) {
                player.closeInventory();
            }
            clearPlayerState(player.getUniqueId(), true);
        }
        activeMenuSessions.clear();
        processingTradeSessions.clear();
        lastTradeClickTimes.clear();
    }

    private void handleIndexClick(Player player, TradeMenuHolder holder, int slot) {
        int page = holder.page();
        String category = holder.category();
        boolean adminView = player.hasPermission(settings.adminPermission());
        List<TradeDefinition> visibleTrades = tradeManager.visibleTrades(player, adminView, category);
        int maxPage = Math.max(1, (int) Math.ceil(visibleTrades.size() / (double) INDEX_PAGE_SIZE));

        if (slot >= 0 && slot < INDEX_PAGE_SIZE) {
            int index = page * INDEX_PAGE_SIZE + slot;
            if (index >= 0 && index < visibleTrades.size()) {
                TradeDefinition trade = visibleTrades.get(index);
                schedule(() -> {
                    if (canUsePlayer(player)) {
                        openTrade(player, trade, page, category);
                    }
                });
            }
            return;
        }

        if (slot == SLOT_PREVIOUS && page > 0) {
            schedule(() -> {
                if (canUsePlayer(player)) {
                    openIndex(player, page - 1, category);
                }
            });
            return;
        }
        if (slot == SLOT_NEXT && page + 1 < maxPage) {
            schedule(() -> {
                if (canUsePlayer(player)) {
                    openIndex(player, page + 1, category);
                }
            });
            return;
        }
        if (slot == SLOT_CLOSE) {
            scheduleCloseIfViewingSession(player, holder.sessionId());
        }
    }

    private void handleDetailClick(Player player, TradeMenuHolder holder, int slot) {
        TradeDefinition trade = tradeManager.findTrade(holder.tradeId()).orElse(null);
        if (trade == null) {
            placeholderService.sendMessage(player, player, settings.tradeNotFoundMessage(), Map.of("trade_id", holder.tradeId()));
            scheduleCloseIfViewingSession(player, holder.sessionId());
            return;
        }

        if (slot == SLOT_INFO) {
            if (!canInteractWithTrade(player, trade)) {
                return;
            }
            Map<String, String> replacements = detailPlaceholders(player, trade, tradeManager.evaluateTrade(player, trade));
            scheduleCloseIfViewingSession(player, holder.sessionId());
            scheduleLater(() -> {
                if (canUsePlayer(player) && !isViewingTradeMenuSession(player, holder.sessionId())) {
                    commandActionService.runTradeTrigger(player, trade, TradeTrigger.INFO, replacements);
                }
            }, GUI_FOLLOW_UP_DELAY_TICKS);
            return;
        }
        if (slot == SLOT_TRADE) {
            if (!canInteractWithTrade(player, trade)) {
                return;
            }
            if (!claimTradeProcessing(player.getUniqueId(), holder.sessionId())) {
                placeholderService.sendMessage(player, player, settings.tradeBusyMessage(), Map.of());
                return;
            }

            boolean deferredRelease = false;
            try {
                if (tradeAttemptStillCoolingDown(player)) {
                    placeholderService.sendMessage(player, player, settings.tradeBusyMessage(), Map.of());
                    return;
                }

                TradeCheckResult result = tradeManager.consumeTrade(player, trade);
                Map<String, String> replacements = detailPlaceholders(player, trade, result);
                sendResultMessage(player, trade, result);
                auditLogService.logTradeAttempt(player, trade, result, Map.of(
                    "uses", String.valueOf(tradeManager.tradeUses(player, trade)),
                    "remaining_uses", tradeManager.formattedRemainingTrades(player, trade),
                    "money_cost", tradeManager.formatMoney(trade.moneyCost()),
                    "exp_cost", String.valueOf(trade.expCost()),
                    "missing_summary", tradeManager.summarizeMissingRequirements(result)
                ));
                if (result.success()) {
                    commandActionService.runTradeTrigger(player, trade, TradeTrigger.SUCCESS, replacements);
                    if (settings.closeOnSuccess()) {
                        scheduleCloseIfViewingSession(player, holder.sessionId());
                    } else {
                        schedule(() -> {
                            if (canUsePlayer(player)) {
                                openTrade(player, trade, holder.page(), holder.category());
                            }
                        });
                    }
                } else {
                    commandActionService.runTradeTrigger(player, trade, TradeTrigger.FAIL, replacements);
                    schedule(() -> {
                        if (canUsePlayer(player)) {
                            openTrade(player, trade, holder.page(), holder.category());
                        }
                    });
                }
                deferredRelease = true;
                schedule(() -> releaseTradeProcessing(player.getUniqueId(), holder.sessionId()));
                return;
            } finally {
                if (!deferredRelease) {
                    releaseTradeProcessing(player.getUniqueId(), holder.sessionId());
                }
            }
        }
        if (slot == SLOT_BACK) {
            schedule(() -> {
                if (canUsePlayer(player)) {
                    openIndex(player, holder.page(), holder.category());
                }
            });
            return;
        }
        if (slot == SLOT_CLOSE) {
            scheduleCloseIfViewingSession(player, holder.sessionId());
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
        replacements.put("reward_name", trade.rewardItem() == null
            ? ""
            : placeholderService.escapeMiniMessageTokens(placeholderService.plainItemName(trade.rewardItem())));
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

    private ItemStack createRequirementPreview(Player player, TradeDefinition trade, ItemStack requiredItem) {
        ItemStack item = requiredItem.clone();
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        if (!lore.isEmpty()) {
            lore.add(Component.empty());
        }

        int ownedAmount = tradeManager.countMatchingItems(player.getInventory(), requiredItem);
        int requiredAmount = requiredItem.getAmount();
        int missingAmount = Math.max(0, requiredAmount - ownedAmount);
        Map<String, String> replacements = Map.of(
            "owned_amount", String.valueOf(ownedAmount),
            "required_amount", String.valueOf(requiredAmount),
            "item_missing_amount", String.valueOf(missingAmount),
            "trade_id", trade.id()
        );
        lore.add(placeholderService.component(player, settings.requirementProgressLine(), replacements));
        if (missingAmount > 0) {
            lore.add(placeholderService.component(player, settings.requirementMissingLine(), replacements));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createStatusItem(Player player, TradeDefinition trade) {
        TradeCheckResult result = tradeManager.evaluateTrade(player, trade);
        Map<String, String> replacements = detailPlaceholders(player, trade, result);
        ConfiguredItemSpec spec = switch (result.status()) {
            case SUCCESS -> settings.readyStatusItem();
            case MISSING_REQUIREMENTS -> settings.missingStatusItem();
            default -> settings.lockedStatusItem();
        };

        List<String> extraLore = new ArrayList<>();
        switch (result.status()) {
            case DISABLED -> extraLore.add(settings.tradeDisabledMessage());
            case NO_PERMISSION -> extraLore.add(settings.tradeLockedMessage());
            case WORLD_BLOCKED -> extraLore.add(settings.tradeWorldBlockedMessage());
            case NOT_STARTED -> extraLore.add(settings.tradeNotStartedMessage());
            case EXPIRED -> extraLore.add(settings.tradeExpiredMessage());
            case ALREADY_COMPLETED -> extraLore.add(completedMessage(trade));
            default -> {
            }
        }
        if (trade.moneyCost() > 0D) {
            extraLore.add(settings.statusMoneyLine());
        }
        if (trade.expCost() > 0) {
            extraLore.add(settings.statusExpLine());
        }
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
            case WORLD_BLOCKED -> settings.tradeWorldBlockedMessage();
            case NOT_STARTED -> settings.tradeNotStartedMessage();
            case EXPIRED -> settings.tradeExpiredMessage();
            case ALREADY_COMPLETED -> completedMessage(trade);
            case MISSING_REQUIREMENTS -> settings.tradeMissingMessage();
        };
        sendTradeMessage(player, trade, message, detailPlaceholders(player, trade, result));
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
            case MISSING_REQUIREMENTS -> settings.indexStatusCollecting();
            case ALREADY_COMPLETED -> trade.maxTrades() == 1 ? settings.indexStatusUnlocked() : settings.indexStatusLimitReached();
            case DISABLED -> settings.indexStatusDisabled();
            case NOT_STARTED -> settings.indexStatusScheduled();
            case EXPIRED -> settings.indexStatusExpired();
            case WORLD_BLOCKED, NO_PERMISSION -> settings.indexStatusLocked();
        };
    }

    private Map<String, String> tradePlaceholders(Player player, TradeDefinition trade) {
        HashMap<String, String> replacements = new HashMap<>();
        String maxTrades = tradeManager.formattedMaxTrades(trade);
        String remainingTrades = tradeManager.formattedRemainingTrades(player, trade);
        double playerBalance = tradeManager.playerBalance(player);
        replacements.put("player", player.getName());
        replacements.put("player_name", player.getName());
        replacements.put("player_uuid", player.getUniqueId().toString());
        replacements.put("id", trade.id());
        replacements.put("trade_id", trade.id());
        replacements.put("trade_name", trade.displayName());
        replacements.put("trade_description", String.join(" ", trade.description()));
        replacements.put("category", trade.category());
        replacements.put("trade_permission", tradeManager.effectivePermission(trade));
        replacements.put("ctext_file", tradeManager.effectiveCtextFile(trade));
        replacements.put("allowed_worlds", tradeManager.allowedWorldsDescription(trade));
        replacements.put("money_cost", tradeManager.formatMoney(trade.moneyCost()));
        replacements.put("exp_cost", String.valueOf(trade.expCost()));
        replacements.put("start_date", tradeManager.formattedDate(trade.startDate()));
        replacements.put("end_date", tradeManager.formattedDate(trade.endDate()));
        replacements.put("player_money", tradeManager.formatMoney(playerBalance));
        replacements.put("player_level", String.valueOf(player.getLevel()));
        replacements.put("current_world", player.getWorld().getName());
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
        replacements.put("missing_money", "0");
        replacements.put("missing_exp", "0");
        replacements.put("missing_summary", "");
        return replacements;
    }

    private Map<String, String> detailPlaceholders(Player player, TradeDefinition trade, TradeCheckResult result) {
        HashMap<String, String> replacements = new HashMap<>(tradePlaceholders(player, trade));
        replacements.put("missing_items", tradeManager.summarizeItems(result.missingItems()));
        replacements.put("missing_amount", String.valueOf(tradeManager.totalItemAmount(result.missingItems())));
        replacements.put("missing_money", tradeManager.formatMoney(result.missingMoney()));
        replacements.put("missing_exp", String.valueOf(result.missingExpLevels()));
        replacements.put("missing_summary", tradeManager.summarizeMissingRequirements(result));
        replacements.put("allowed_worlds", result.allowedWorlds().isEmpty() ? tradeManager.allowedWorldsDescription(trade) : String.join(", ", result.allowedWorlds()));
        replacements.put("current_world", result.currentWorld().isBlank() ? player.getWorld().getName() : result.currentWorld());
        replacements.put("start_date", result.startDate() == null ? tradeManager.formattedDate(trade.startDate()) : tradeManager.formattedDate(result.startDate()));
        replacements.put("end_date", result.endDate() == null ? tradeManager.formattedDate(trade.endDate()) : tradeManager.formattedDate(result.endDate()));
        return replacements;
    }

    private String completedMessage(TradeDefinition trade) {
        return trade.maxTrades() == 1 ? settings.tradeCompletedMessage() : settings.tradeLimitReachedMessage();
    }

    private boolean tradeAttemptStillCoolingDown(Player player) {
        long now = System.currentTimeMillis();
        long lastAttempt = lastTradeClickTimes.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastAttempt < settings.tradeClickCooldownMillis()) {
            return true;
        }
        lastTradeClickTimes.put(player.getUniqueId(), now);
        return false;
    }

    private boolean canInteractWithTrade(Player player, TradeDefinition trade) {
        if (player.hasPermission(settings.adminPermission())) {
            return true;
        }
        if (!trade.enabled()) {
            sendTradeMessage(player, trade, settings.tradeDisabledMessage(), Map.of());
            return false;
        }
        if (!tradeManager.hasAccess(player, trade, false)) {
            sendTradeMessage(player, trade, settings.tradeLockedMessage(), Map.of());
            return false;
        }
        return true;
    }

    private boolean canUsePlayer(Player player) {
        return player != null && player.isOnline();
    }

    private boolean isTradeMenuInventory(Inventory inventory) {
        return inventory != null && inventory.getHolder(false) instanceof TradeMenuHolder;
    }

    private boolean isViewingTradeMenuSession(Player player, long sessionId) {
        if (!canUsePlayer(player)) {
            return false;
        }
        Inventory openTopInventory = player.getOpenInventory().getTopInventory();
        if (!(openTopInventory.getHolder(false) instanceof TradeMenuHolder holder)) {
            return false;
        }
        return holder.sessionId() == sessionId && isCurrentSession(player, holder);
    }

    private boolean isCurrentSession(Player player, TradeMenuHolder holder) {
        return activeMenuSessions.getOrDefault(player.getUniqueId(), -1L) == holder.sessionId();
    }

    private boolean isActionableButtonClick(ClickType clickType) {
        return clickType == ClickType.LEFT || clickType == ClickType.RIGHT;
    }

    private void registerActiveSession(Player player, TradeMenuHolder holder) {
        activeMenuSessions.put(player.getUniqueId(), holder.sessionId());
    }

    private boolean claimTradeProcessing(UUID playerId, long sessionId) {
        if (processingTradeSessions.containsKey(playerId)) {
            return false;
        }
        processingTradeSessions.put(playerId, sessionId);
        return true;
    }

    private void clearActiveSession(UUID playerId, long sessionId) {
        if (activeMenuSessions.getOrDefault(playerId, -1L) == sessionId) {
            activeMenuSessions.remove(playerId);
        }
    }

    private void releaseTradeProcessing(UUID playerId, long sessionId) {
        if (processingTradeSessions.getOrDefault(playerId, -1L) == sessionId) {
            processingTradeSessions.remove(playerId);
        }
    }

    private void clearPlayerState(UUID playerId, boolean clearCooldown) {
        activeMenuSessions.remove(playerId);
        processingTradeSessions.remove(playerId);
        if (clearCooldown) {
            lastTradeClickTimes.remove(playerId);
        }
    }

    private long nextSessionId() {
        if (nextSessionId == Long.MAX_VALUE) {
            nextSessionId = 1L;
        }
        return nextSessionId++;
    }

    private String normalizeCategory(String category) {
        return category == null ? "" : category.trim().toLowerCase();
    }

    private void schedule(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    private void scheduleLater(Runnable task, long delayTicks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    private void scheduleCloseIfViewingSession(Player player, long sessionId) {
        scheduleLater(() -> {
            if (isViewingTradeMenuSession(player, sessionId)) {
                player.closeInventory();
            }
        }, GUI_CLOSE_DELAY_TICKS);
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
        private final String category;
        private final long sessionId;
        private Inventory inventory;

        private TradeMenuHolder(MenuType type, String tradeId, int page, String category, long sessionId) {
            this.type = type;
            this.tradeId = tradeId;
            this.page = page;
            this.category = category == null ? "" : category;
            this.sessionId = sessionId;
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

        public String category() {
            return category;
        }

        public long sessionId() {
            return sessionId;
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

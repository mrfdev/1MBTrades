package com.onemoreblock.trades.service;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class EconomyService {
    private final JavaPlugin plugin;
    private Object provider;
    private Class<?> economyClass;
    private Method getBalanceMethod;
    private Method withdrawPlayerMethod;
    private Method depositPlayerMethod;
    private Method responseSuccessMethod;

    public EconomyService(JavaPlugin plugin) {
        this.plugin = plugin;
        refresh();
    }

    public void refresh() {
        provider = null;
        economyClass = null;
        getBalanceMethod = null;
        withdrawPlayerMethod = null;
        depositPlayerMethod = null;
        responseSuccessMethod = null;

        try {
            economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            @SuppressWarnings("unchecked")
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class<Object>) economyClass);
            if (registration == null) {
                return;
            }

            provider = registration.getProvider();
            if (provider == null) {
                return;
            }

            Class<?> offlinePlayerClass = Class.forName("org.bukkit.OfflinePlayer");
            getBalanceMethod = economyClass.getMethod("getBalance", offlinePlayerClass);
            withdrawPlayerMethod = economyClass.getMethod("withdrawPlayer", offlinePlayerClass, double.class);
            depositPlayerMethod = economyClass.getMethod("depositPlayer", offlinePlayerClass, double.class);

            Class<?> responseClass = Class.forName("net.milkbowl.vault.economy.EconomyResponse");
            responseSuccessMethod = responseClass.getMethod("transactionSuccess");
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("Could not hook into Vault economy provider: " + exception.getMessage());
            provider = null;
        }
    }

    public boolean available() {
        return provider != null && getBalanceMethod != null && withdrawPlayerMethod != null && depositPlayerMethod != null;
    }

    public String providerName() {
        return provider == null ? "" : provider.getClass().getSimpleName();
    }

    public double balance(Player player) {
        if (!available()) {
            return 0D;
        }
        try {
            Object rawBalance = getBalanceMethod.invoke(provider, player);
            return rawBalance instanceof Number number ? number.doubleValue() : 0D;
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("Could not read economy balance for " + player.getName() + ": " + exception.getMessage());
            return 0D;
        }
    }

    public EconomyResult withdraw(Player player, double amount) {
        if (amount <= 0D) {
            return EconomyResult.successful();
        }
        if (!available()) {
            return EconomyResult.failed("Vault economy provider is not available.");
        }
        try {
            Object response = withdrawPlayerMethod.invoke(provider, player, amount);
            return toResult(response);
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("Could not withdraw money from " + player.getName() + ": " + exception.getMessage());
            return EconomyResult.failed(exception.getMessage());
        }
    }

    public EconomyResult deposit(Player player, double amount) {
        if (amount <= 0D) {
            return EconomyResult.successful();
        }
        if (!available()) {
            return EconomyResult.failed("Vault economy provider is not available.");
        }
        try {
            Object response = depositPlayerMethod.invoke(provider, player, amount);
            return toResult(response);
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("Could not refund money to " + player.getName() + ": " + exception.getMessage());
            return EconomyResult.failed(exception.getMessage());
        }
    }

    private EconomyResult toResult(Object response) {
        if (response == null) {
            return EconomyResult.failed("No economy response was returned.");
        }
        try {
            boolean success = Boolean.TRUE.equals(responseSuccessMethod.invoke(response));
            if (success) {
                return EconomyResult.successful();
            }

            Object errorField = response.getClass().getField("errorMessage").get(response);
            String errorMessage = errorField == null ? "Unknown economy error." : errorField.toString();
            return EconomyResult.failed(errorMessage);
        } catch (ReflectiveOperationException exception) {
            return EconomyResult.failed(exception.getMessage());
        }
    }

    public record EconomyResult(boolean success, String message) {
        public static EconomyResult successful() {
            return new EconomyResult(true, "");
        }

        public static EconomyResult failed(String message) {
            return new EconomyResult(false, message == null ? "" : message);
        }
    }
}

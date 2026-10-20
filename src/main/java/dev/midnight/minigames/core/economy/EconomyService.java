package dev.midnight.minigames.core.economy;

import dev.midnight.minigames.MidnightMinigames;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

/**
 * Economy hook for coins. Provider is chosen in config.yml
 * ({@code economy.provider}): "internal" uses the plugin's own coins, "vault" uses any Vault economy.
 * Vault is accessed reflectively, so it is neither a compile-time nor a hard runtime dependency.
 */
public final class EconomyService {

    private final MidnightMinigames plugin;
    private Object vault;
    private Method getBalance;
    private Method withdraw;
    private Method deposit;
    private boolean useVault;

    public EconomyService(MidnightMinigames plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        useVault = false;
        vault = null;
        String provider = plugin.getConfigurationManager().config().getString("economy.provider", "internal").toLowerCase();
        if (!provider.equals("vault")) {
            return;
        }
        try {
            Class<?> eco = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(eco);
            if (rsp == null) {
                plugin.getLogger().warning("[config] economy.provider is 'vault' but no Vault economy is registered - using internal coins.");
                return;
            }
            vault = rsp.getProvider();
            getBalance = eco.getMethod("getBalance", org.bukkit.OfflinePlayer.class);
            withdraw = eco.getMethod("withdrawPlayer", org.bukkit.OfflinePlayer.class, double.class);
            deposit = eco.getMethod("depositPlayer", org.bukkit.OfflinePlayer.class, double.class);
            useVault = true;
            plugin.getLogger().info("Economy: using Vault (" + vault.getClass().getSimpleName() + ")");
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("[config] economy.provider is 'vault' but Vault is not available - using internal coins.");
        }
    }

    public String getProviderName() {
        return useVault ? "vault" : "internal";
    }

    public double getBalance(Player p) {
        if (useVault) {
            try {
                return (double) getBalance.invoke(vault, p);
            } catch (ReflectiveOperationException ex) {
                return 0;
            }
        }
        return plugin.getPlayerManager().getStats(p).getCoins();
    }

    public boolean has(Player p, double amount) {
        return getBalance(p) + 1e-9 >= amount;
    }

    public boolean withdraw(Player p, double amount) {
        if (amount <= 0) {
            return true;
        }
        if (!has(p, amount)) {
            return false;
        }
        if (useVault) {
            try {
                withdraw.invoke(vault, p, amount);
                return true;
            } catch (ReflectiveOperationException ex) {
                return false;
            }
        }
        plugin.getPlayerManager().addCoins(p, -amount);
        return true;
    }

    public void deposit(Player p, double amount) {
        if (amount <= 0) {
            return;
        }
        if (useVault) {
            try {
                deposit.invoke(vault, p, amount);
            } catch (ReflectiveOperationException ignored) {
            }
            return;
        }
        plugin.getPlayerManager().addCoins(p, amount);
    }
}

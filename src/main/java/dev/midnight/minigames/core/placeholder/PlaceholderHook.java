package dev.midnight.minigames.core.placeholder;

import dev.midnight.minigames.MidnightMinigames;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Soft-dependency wrapper. Every reference to PlaceholderAPI classes is isolated in this class and
 * {@link MidnightExpansion}, so the plugin runs normally without PlaceholderAPI installed.
 */
public final class PlaceholderHook {

    private final MidnightMinigames plugin;
    private Object expansion;
    private boolean available;

    public PlaceholderHook(MidnightMinigames plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        return available;
    }

    public void register() {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            plugin.getLogger().info("PlaceholderAPI not found - placeholder support disabled (scoreboard placeholders still work).");
            return;
        }
        try {
            MidnightExpansion exp = new MidnightExpansion(plugin);
            if (exp.register()) {
                expansion = exp;
                available = true;
                plugin.getLogger().info("Registered PlaceholderAPI expansion (%midnight_*%).");
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("PlaceholderAPI is installed but the expansion could not be registered: " + t);
        }
    }

    public void unregister() {
        if (expansion instanceof MidnightExpansion e) {
            try {
                e.unregister();
            } catch (Throwable ignored) {
            }
        }
        expansion = null;
        available = false;
    }

    /** Parses %placeholders% of other plugins in a string. Returns the input when PAPI is unavailable. */
    public String parse(Player player, String text) {
        if (!available) {
            return text;
        }
        try {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        } catch (Throwable t) {
            return text;
        }
    }
}

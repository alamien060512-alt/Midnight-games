package dev.midnight.minigames.core.game;

import dev.midnight.minigames.MidnightMinigames;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Central registry of minigame modules. Built-in games are registered at start-up; other plugins can call
 * {@link #register(Minigame)} (via the API) to plug in their own games.
 */
public final class MinigameManager {

    private final MidnightMinigames plugin;
    private final Map<String, Minigame> minigames = new LinkedHashMap<>();

    public MinigameManager(MidnightMinigames plugin) {
        this.plugin = plugin;
    }

    /** Registers and (if enabled in config.yml) enables a minigame. */
    public boolean register(Minigame minigame) {
        String id = minigame.getId().toLowerCase(Locale.ROOT);
        if (minigames.containsKey(id)) {
            plugin.getLogger().warning("Minigame '" + id + "' is already registered - ignoring duplicate.");
            return false;
        }
        minigames.put(id, minigame);
        ConfigurationSection section = plugin.getConfigurationManager().gameSection(id);
        boolean enabled = section == null || section.getBoolean("enabled", true);
        if (!enabled) {
            plugin.getLogger().info("Minigame '" + id + "' is disabled in config.yml.");
            return true;
        }
        try {
            minigame.enable();
            minigame.registerPlaceholders(plugin.getPlaceholderRegistry());
            plugin.getLogger().info("Enabled minigame: " + id);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Minigame '" + id + "' failed to enable and was skipped: " + ex.getMessage(), ex);
        }
        return true;
    }

    public void unregister(String id) {
        Minigame m = minigames.remove(id.toLowerCase(Locale.ROOT));
        if (m != null) {
            try {
                m.disable();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Error while disabling " + id + ": " + ex.getMessage(), ex);
            }
            plugin.getPlaceholderRegistry().unregister(id);
        }
    }

    public Minigame get(String id) {
        return id == null ? null : minigames.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Minigame> getAll() {
        return new ArrayList<>(minigames.values());
    }

    public void disableAll() {
        for (Minigame m : new ArrayList<>(minigames.values())) {
            try {
                m.disable();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Error while disabling " + m.getId() + ": " + ex.getMessage(), ex);
            }
        }
        minigames.clear();
    }

    public void reloadAll() {
        for (Minigame m : new ArrayList<>(minigames.values())) {
            try {
                m.reload();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Error while reloading " + m.getId() + ": " + ex.getMessage(), ex);
            }
        }
    }
}

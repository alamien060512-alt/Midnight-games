package dev.midnight.minigames.core.display;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.util.ItemFactory;
import dev.midnight.minigames.util.Keys;
import dev.midnight.minigames.util.LocationUtil;
import dev.midnight.minigames.util.PlayerUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Main lobby handling: spawn location, lobby compass, return-to-lobby behaviour. */
public final class LobbyService {

    private final MidnightMinigames plugin;
    private String rawLobby;
    private boolean lobbyLoaded;

    public LobbyService(MidnightMinigames plugin) {
        this.plugin = plugin;
    }

    private java.io.File lobbyFile() {
        return new java.io.File(plugin.getDataFolder(), "data/lobby.yml");
    }

    /** Main lobby set via /minigame setlobby (data/lobby.yml), falling back to config.yml lobby.location. */
    public Location getMainLobby() {
        if (!lobbyLoaded) {
            java.io.File f = lobbyFile();
            rawLobby = f.isFile() ? org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f).getString("location") : null;
            lobbyLoaded = true;
        }
        Location l = LocationUtil.deserialize(rawLobby);
        return l != null ? l : LocationUtil.deserialize(plugin.getConfigurationManager().config().getString("lobby.location"));
    }

    public void setMainLobby(Location l) {
        org.bukkit.configuration.file.YamlConfiguration y = new org.bukkit.configuration.file.YamlConfiguration();
        rawLobby = LocationUtil.serialize(l);
        lobbyLoaded = true;
        y.set("location", rawLobby);
        final String data = y.saveToString();
        final java.io.File f = lobbyFile();
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                f.getParentFile().mkdirs();
                java.nio.file.Files.writeString(f.toPath(), data);
            } catch (java.io.IOException ex) {
                plugin.getLogger().warning("Could not save lobby location: " + ex.getMessage());
            }
        });
    }

    public ItemStack createCompass() {
        ConfigurationSection s = plugin.getConfigurationManager().menus().getConfigurationSection("lobby-items.compass");
        if (s == null) {
            return null;
        }
        ConfigurationSection copy = s;
        ItemStack item = ItemFactory.build(copy);
        return ItemFactory.tag(item, Keys.ACTION, "open_menu");
    }

    public void giveCompass(Player p) {
        ConfigurationSection s = plugin.getConfigurationManager().menus().getConfigurationSection("lobby-items.compass");
        if (s == null || !s.getBoolean("enabled", true)) {
            return;
        }
        int slot = s.getInt("slot", 4);
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && "open_menu".equals(ItemFactory.readTag(it, Keys.ACTION))) {
                return;
            }
        }
        p.getInventory().setItem(slot, createCompass());
    }

    public ItemStack createLeaveItem() {
        ConfigurationSection s = plugin.getConfigurationManager().menus().getConfigurationSection("lobby-items.leave");
        if (s == null) {
            return null;
        }
        return ItemFactory.tag(ItemFactory.build(s), Keys.ACTION, "leave");
    }

    public int leaveItemSlot() {
        return plugin.getConfigurationManager().menus().getInt("lobby-items.leave.slot", 8);
    }

    /** Called on server join. */
    public void handleJoin(Player p) {
        var cfg = plugin.getConfigurationManager().config();
        if (cfg.getBoolean("lobby.teleport-on-join", true)) {
            Location l = getMainLobby();
            if (l != null) {
                p.teleport(l);
            }
        }
        if (cfg.getBoolean("lobby.reset-on-join", false)) {
            PlayerUtil.reset(p, GameMode.valueOf(cfg.getString("lobby.gamemode", "ADVENTURE").toUpperCase()));
        }
        if (cfg.getBoolean("lobby.give-compass-on-join", true)) {
            giveCompass(p);
        }
    }

    /** Called after a player left a game and their state was restored. */
    public void afterLeaveGame(Player p) {
        var cfg = plugin.getConfigurationManager().config();
        if (cfg.getBoolean("lobby.teleport-on-leave", true)) {
            Location l = getMainLobby();
            if (l != null) {
                p.teleport(l);
            }
        }
        if (cfg.getBoolean("lobby.give-compass-on-join", true)) {
            giveCompass(p);
        }
        if (plugin.getCosmetics() != null) {
            plugin.getCosmetics().applyHat(p);
        }
    }
}

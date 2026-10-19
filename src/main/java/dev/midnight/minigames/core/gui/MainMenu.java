package dev.midnight.minigames.core.gui;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.game.Minigame;
import dev.midnight.minigames.util.ItemFactory;
import dev.midnight.minigames.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/** Compass menu listing every minigame. Layout comes from menus.yml -&gt; main-menu. */
public final class MainMenu extends MenuHolder {

    private final MidnightMinigames plugin;
    private final Map<Integer, String> slotToGame = new HashMap<>();

    public MainMenu(MidnightMinigames plugin) {
        this.plugin = plugin;
        ConfigurationSection cfg = plugin.getConfigurationManager().menus().getConfigurationSection("main-menu");
        int rows = cfg == null ? 3 : Math.max(1, Math.min(6, cfg.getInt("rows", 3)));
        String title = cfg == null ? "<white>Minigames" : cfg.getString("title", "<white>Minigames");
        this.inventory = Bukkit.createInventory(this, rows * 9, TextUtil.parse(title));
        if (cfg == null) {
            return;
        }
        ConfigurationSection filler = cfg.getConfigurationSection("filler");
        if (filler != null && filler.getBoolean("enabled", true)) {
            ItemStack f = ItemFactory.build(filler);
            for (int i = 0; i < inventory.getSize(); i++) {
                inventory.setItem(i, f);
            }
        }
        ConfigurationSection items = cfg.getConfigurationSection("items");
        if (items == null) {
            return;
        }
        boolean showDisabled = cfg.getBoolean("show-disabled-games", false);
        for (String gameId : items.getKeys(false)) {
            ConfigurationSection it = items.getConfigurationSection(gameId);
            if (it == null) {
                continue;
            }
            Minigame mg = plugin.getMinigameManager().get(gameId);
            boolean available = mg != null && mg.isEnabled();
            if (!available && !showDisabled) {
                continue;
            }
            int slot = it.getInt("slot", 0);
            if (slot < 0 || slot >= inventory.getSize()) {
                plugin.getLogger().warning("[config] menus.yml -> main-menu.items." + gameId + ".slot " + slot + " is outside the menu");
                continue;
            }
            int count = mg == null ? 0 : mg.getPlayers().size();
            ItemStack item;
            if (available) {
                item = ItemFactory.build(it, "players", String.valueOf(count), "game", gameId);
            } else {
                ConfigurationSection off = cfg.getConfigurationSection("disabled-item");
                item = off != null ? ItemFactory.build(off, "game", gameId)
                        : ItemFactory.simple(Material.BARRIER, "<red>" + gameId + " (disabled)", null);
            }
            inventory.setItem(slot, item);
            if (available) {
                slotToGame.put(slot, gameId);
            }
        }
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        String game = slotToGame.get(event.getRawSlot());
        if (game == null) {
            return;
        }
        plugin.getEffects().play(player, "menu-click");
        new ModeMenu(plugin, plugin.getMinigameManager().get(game)).open(player);
    }
}

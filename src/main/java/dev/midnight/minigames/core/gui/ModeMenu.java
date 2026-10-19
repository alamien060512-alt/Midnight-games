package dev.midnight.minigames.core.gui;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.game.AbstractArenaMinigame;
import dev.midnight.minigames.core.game.Minigame;
import dev.midnight.minigames.core.game.ModeDefinition;
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

/** Game mode selection menu for one minigame (Solo / Doubles / Lucky...). Layout in menus.yml -&gt; mode-menu. */
public final class ModeMenu extends MenuHolder {

    private final MidnightMinigames plugin;
    private final Minigame minigame;
    private final Map<Integer, String> slotToMode = new HashMap<>();
    private int backSlot = -1;

    public ModeMenu(MidnightMinigames plugin, Minigame minigame) {
        this.plugin = plugin;
        this.minigame = minigame;
        ConfigurationSection cfg = plugin.getConfigurationManager().menus().getConfigurationSection("mode-menu");
        int rows = cfg == null ? 3 : Math.max(1, Math.min(6, cfg.getInt("rows", 3)));
        String title = cfg == null ? "{game}" : cfg.getString("title", "{game}");
        title = TextUtil.replace(title, "game", minigame.getDisplayName());
        this.inventory = Bukkit.createInventory(this, rows * 9, TextUtil.parse(title));

        if (cfg != null) {
            ConfigurationSection filler = cfg.getConfigurationSection("filler");
            if (filler != null && filler.getBoolean("enabled", true)) {
                ItemStack f = ItemFactory.build(filler);
                for (int i = 0; i < inventory.getSize(); i++) {
                    inventory.setItem(i, f);
                }
            }
            ConfigurationSection back = cfg.getConfigurationSection("back");
            if (back != null && back.getBoolean("enabled", true)) {
                backSlot = back.getInt("slot", inventory.getSize() - 5);
                if (backSlot >= 0 && backSlot < inventory.getSize()) {
                    inventory.setItem(backSlot, ItemFactory.build(back));
                } else {
                    backSlot = -1;
                }
            }
        }
        // First pass: explicit slots; second pass: automatic placement in the inner area.
        java.util.List<ModeDefinition> auto = new java.util.ArrayList<>();
        for (ModeDefinition m : minigame.getModes()) {
            int slot = m.getSlot();
            if (slot >= 0 && slot < inventory.getSize() && slot != backSlot) {
                place(slot, m);
            } else {
                auto.add(m);
            }
        }
        int next = 10;
        for (ModeDefinition m : auto) {
            while (next < inventory.getSize() && (inventory.getItem(next) != null && slotToMode.containsKey(next)
                    || next == backSlot || next % 9 == 0 || next % 9 == 8)) {
                next++;
            }
            if (next >= inventory.getSize()) {
                plugin.getLogger().warning("Mode menu of " + minigame.getId() + " has more modes than fit; '" + m.getId() + "' is hidden.");
                continue;
            }
            place(next++, m);
        }
    }

    private void place(int slot, ModeDefinition m) {
        int players = minigame instanceof AbstractArenaMinigame am ? am.countPlayers(m.getId()) : minigame.getPlayers().size();
        int queued = plugin.getQueueManager().getQueueSize(minigame.getId(), m.getId());
        ItemStack item = ItemFactory.build(m.getRaw(), "players", String.valueOf(players), "queued", String.valueOf(queued),
                "game", minigame.getDisplayName(), "mode", m.getDisplayName());
        inventory.setItem(slot, item);
        slotToMode.put(slot, m.getId());
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot == backSlot) {
            plugin.getEffects().play(player, "menu-click");
            plugin.getMenus().openMain(player);
            return;
        }
        String mode = slotToMode.get(slot);
        if (mode == null) {
            return;
        }
        plugin.getEffects().play(player, "menu-click");
        player.closeInventory();
        plugin.getQueueManager().enqueue(player, minigame.getId(), mode);
    }

}

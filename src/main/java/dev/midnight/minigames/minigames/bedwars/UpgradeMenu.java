package dev.midnight.minigames.minigames.bedwars;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.gui.MenuHolder;
import dev.midnight.minigames.util.ConfigUtil;
import dev.midnight.minigames.util.ItemFactory;
import dev.midnight.minigames.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Team upgrade shop. Upgrades and tier costs come from games/bedwars.yml -&gt; upgrades. */
final class UpgradeMenu extends MenuHolder {

    private final MidnightMinigames plugin;
    private final BedWarsGame game;
    private final Map<Integer, ConfigurationSection> bySlot = new HashMap<>();

    UpgradeMenu(MidnightMinigames plugin, BedWarsGame game, Player player) {
        this.plugin = plugin;
        this.game = game;
        YamlConfiguration cfg = plugin.getConfigurationManager().game("bedwars");
        this.inventory = Bukkit.createInventory(this, 36, TextUtil.parse(cfg.getString("upgrades.title", "<dark_gray>Team Upgrades")));
        render(player);
    }

    void render(Player player) {
        inventory.clear();
        bySlot.clear();
        ItemStack filler = ItemFactory.simple(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
        TeamState state = game.stateOf(player);
        ConfigurationSection upgrades = plugin.getConfigurationManager().game("bedwars").getConfigurationSection("upgrades");
        if (upgrades == null || state == null) {
            return;
        }
        for (ConfigurationSection def : ConfigUtil.sections(upgrades, "list")) {
            String id = def.getString("id");
            if ("trap".equals(def.getString("type"))) {
                ItemStack trapItem = ItemFactory.build(def);
                ItemMeta tm = trapItem.getItemMeta();
                List<net.kyori.adventure.text.Component> tl = tm.lore() == null ? new ArrayList<>() : new ArrayList<>(tm.lore());
                tl.add(TextUtil.item(plugin.getMessages().text("bedwars.trap-queue-lore", "count", String.valueOf(state.traps.size()),
                        "max", String.valueOf(game.trapMax()))));
                if (state.traps.size() < game.trapMax()) {
                    tl.add(TextUtil.item(plugin.getMessages().text("bedwars.shop-cost", "amount", String.valueOf(game.trapCost(state)),
                            "resource", game.resourceName(def.getString("currency", "diamond")))));
                }
                tm.lore(tl);
                trapItem.setItemMeta(tm);
                int trapSlot = def.getInt("slot", 19);
                if (trapSlot >= 0 && trapSlot < inventory.getSize()) {
                    inventory.setItem(trapSlot, trapItem);
                    bySlot.put(trapSlot, def);
                }
                continue;
            }
            List<ConfigurationSection> tiers = ConfigUtil.sections(def, "tiers");
            int level = state.level(id);
            ItemStack item = ItemFactory.build(def, "level", String.valueOf(level), "max", String.valueOf(tiers.size()));
            ItemMeta meta = item.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            if (level >= tiers.size()) {
                lore.add(TextUtil.item(plugin.getMessages().text("bedwars.upgrade-maxed")));
            } else {
                ConfigurationSection next = tiers.get(level);
                String currency = def.getString("currency", "diamond");
                lore.add(TextUtil.item(plugin.getMessages().text("bedwars.shop-cost", "amount", String.valueOf(next.getInt("cost", 1)),
                        "resource", game.resourceName(currency))));
                if (next.contains("description")) {
                    lore.add(TextUtil.item(next.getString("description")));
                }
            }
            meta.lore(lore);
            item.setItemMeta(meta);
            int slot = def.getInt("slot", bySlot.size() + 10);
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            inventory.setItem(slot, item);
            bySlot.put(slot, def);
        }
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        ConfigurationSection def = bySlot.get(event.getRawSlot());
        if (def != null && ("trap".equals(def.getString("type")) ? game.buyTrap(player, def) : game.buyUpgrade(player, def))) {
            render(player);
        }
    }
}

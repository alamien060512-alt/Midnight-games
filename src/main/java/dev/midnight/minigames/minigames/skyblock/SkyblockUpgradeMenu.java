package dev.midnight.minigames.minigames.skyblock;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.gui.MenuHolder;
import dev.midnight.minigames.core.island.Island;
import dev.midnight.minigames.util.ConfigUtil;
import dev.midnight.minigames.util.ItemFactory;
import dev.midnight.minigames.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Island upgrade shop; upgrades are defined in games/skyblock.yml -&gt; upgrades. */
final class SkyblockUpgradeMenu extends MenuHolder {

    private final MidnightMinigames plugin;
    private final SkyblockMinigame game;
    private final Island island;
    private final Map<Integer, String> bySlot = new HashMap<>();

    SkyblockUpgradeMenu(MidnightMinigames plugin, SkyblockMinigame game, Island island, Player player) {
        this.plugin = plugin;
        this.game = game;
        this.island = island;
        var cfg = plugin.getConfigurationManager().game("skyblock");
        this.inventory = Bukkit.createInventory(this, 27, TextUtil.parse(cfg.getString("upgrades-menu.title", "<dark_gray>Island Upgrades")));
        render();
    }

    private void render() {
        inventory.clear();
        bySlot.clear();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, ItemFactory.simple(Material.GRAY_STAINED_GLASS_PANE, " ", null));
        }
        ConfigurationSection ups = plugin.getConfigurationManager().game("skyblock").getConfigurationSection("upgrades");
        if (ups == null) {
            return;
        }
        for (String id : ups.getKeys(false)) {
            ConfigurationSection def = ups.getConfigurationSection(id);
            if (def == null) {
                continue;
            }
            List<ConfigurationSection> tiers = ConfigUtil.sections(def, "tiers");
            int level = island.getUpgrade(id);
            ItemStack item = ItemFactory.build(def, "level", String.valueOf(level + 1), "max", String.valueOf(tiers.size()));
            ItemMeta meta = item.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            if (level + 1 >= tiers.size()) {
                lore.add(TextUtil.item(plugin.getMessages().text("island.upgrade-maxed")));
            } else {
                ConfigurationSection next = tiers.get(level + 1);
                lore.add(TextUtil.item(plugin.getMessages().text("island.upgrade-next",
                        "cost", TextUtil.formatNumber(next.getDouble("cost", 0)),
                        "value", String.valueOf(next.getInt("radius", next.getInt("limit", 0))))));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
            int slot = def.getInt("slot", 11 + bySlot.size() * 2);
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, item);
                bySlot.put(slot, id);
            }
        }
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        String id = bySlot.get(event.getRawSlot());
        if (id != null && game.buy(player, island, id)) {
            render();
        }
    }
}

package dev.midnight.minigames.minigames.skygen;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.gui.MenuHolder;
import dev.midnight.minigames.core.island.Island;
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

/** SkyGen generator shop and upgrade pages. */
final class SkyGenMenu extends MenuHolder {

    enum Page { SHOP, UPGRADES }

    private final MidnightMinigames plugin;
    private final SkyGenMinigame game;
    private final Island island;
    private final Page page;
    private final Map<Integer, SkyGenMinigame.Tier> tierBySlot = new HashMap<>();
    private final Map<Integer, String> upgradeBySlot = new HashMap<>();

    SkyGenMenu(MidnightMinigames plugin, SkyGenMinigame game, Island island, Page page) {
        this.plugin = plugin;
        this.game = game;
        this.island = island;
        this.page = page;
        var cfg = plugin.getConfigurationManager().game("skygen");
        String title = page == Page.SHOP ? cfg.getString("menus.shop-title", "<dark_gray>Generator Shop")
                : cfg.getString("menus.upgrades-title", "<dark_gray>Upgrades");
        this.inventory = Bukkit.createInventory(this, 27, TextUtil.parse(title));
        render();
    }

    private void render() {
        inventory.clear();
        tierBySlot.clear();
        upgradeBySlot.clear();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, ItemFactory.simple(Material.GRAY_STAINED_GLASS_PANE, " ", null));
        }
        var cfg = plugin.getConfigurationManager().game("skygen");
        if (page == Page.SHOP) {
            int slot = 10;
            List<SkyGenMinigame.Tier> sorted = new ArrayList<>(game.getTiers().values());
            sorted.sort((a, b) -> Integer.compare(a.id, b.id));
            for (SkyGenMinigame.Tier t : sorted) {
                if (slot > 16) {
                    break;
                }
                ItemStack item = game.generatorItem(t);
                ItemMeta meta = item.getItemMeta();
                List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                lore.add(TextUtil.item(plugin.getMessages().text("skygen.shop-price", "price", TextUtil.formatNumber(t.price))));
                meta.lore(lore);
                item.setItemMeta(meta);
                inventory.setItem(slot, item);
                tierBySlot.put(slot, t);
                slot++;
            }
        } else {
            int slot = 11;
            for (String id : List.of("speed", "multiplier")) {
                ConfigurationSection def = cfg.getConfigurationSection("upgrades." + id);
                if (def == null) {
                    continue;
                }
                int level = island.getUpgrade(id);
                ItemStack item = ItemFactory.build(def, "level", String.valueOf(level), "max", String.valueOf(game.upgradeMax(id)));
                ItemMeta meta = item.getItemMeta();
                List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                lore.add(TextUtil.item(level >= game.upgradeMax(id) ? plugin.getMessages().text("island.upgrade-maxed")
                        : plugin.getMessages().text("skygen.shop-price", "price", TextUtil.formatNumber(game.upgradeCost(id, level)))));
                meta.lore(lore);
                item.setItemMeta(meta);
                inventory.setItem(slot, item);
                upgradeBySlot.put(slot, id);
                slot += 2;
            }
        }
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (page == Page.SHOP) {
            SkyGenMinigame.Tier t = tierBySlot.get(slot);
            if (t != null) {
                game.buyGenerator(player, t);
            }
        } else {
            String id = upgradeBySlot.get(slot);
            if (id != null && game.buyUpgrade(player, island, id)) {
                render();
            }
        }
    }

}

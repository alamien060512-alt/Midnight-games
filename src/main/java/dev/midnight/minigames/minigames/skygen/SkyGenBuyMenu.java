package dev.midnight.minigames.minigames.skygen;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.gui.MenuHolder;
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

/** Shared-mode item shop (games/skygen.yml -> shared.buy.items) plus a "sell all" button. */
final class SkyGenBuyMenu extends MenuHolder {

    private final MidnightMinigames plugin;
    private final SkyGenMinigame game;
    private final Map<Integer, ConfigurationSection> bySlot = new HashMap<>();
    private final int sellSlot;

    SkyGenBuyMenu(MidnightMinigames plugin, SkyGenMinigame game) {
        this.plugin = plugin;
        this.game = game;
        var cfg = plugin.getConfigurationManager().game("skygen");
        int rows = Math.max(1, Math.min(6, cfg.getInt("shared.buy.rows", 3)));
        this.sellSlot = cfg.getInt("shared.buy.sell-slot", rows * 9 - 5);
        this.inventory = Bukkit.createInventory(this, rows * 9, TextUtil.parse(cfg.getString("shared.buy.title", "<dark_gray>SkyGen Shop")));
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, ItemFactory.simple(Material.GRAY_STAINED_GLASS_PANE, " ", null));
        }
        int next = 0;
        for (ConfigurationSection s : ConfigUtil.sections(cfg, "shared.buy.items")) {
            int slot = s.getInt("slot", next);
            if (slot < 0 || slot >= inventory.getSize() || slot == sellSlot) {
                continue;
            }
            next = slot + 1;
            ItemStack item = ItemFactory.build(s);
            item.setAmount(Math.max(1, s.getInt("amount", 1)));
            ItemMeta meta = item.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(TextUtil.item(plugin.getMessages().text("skygen.shop-price", "price", TextUtil.formatNumber(s.getDouble("price", 0)))));
            meta.lore(lore);
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
            bySlot.put(slot, s);
        }
        inventory.setItem(sellSlot, ItemFactory.simple(Material.EMERALD, plugin.getMessages().raw("skygen.sell-button"),
                List.of(plugin.getMessages().raw("skygen.sell-button-lore"))));
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot == sellSlot) {
            game.handleGameCommand(player, new String[]{"sell"});
            return;
        }
        ConfigurationSection s = bySlot.get(slot);
        if (s != null) {
            game.buyItem(player, s);
        }
    }
}

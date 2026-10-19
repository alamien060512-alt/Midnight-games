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

/** Item shop. Categories and items are fully defined in games/bedwars.yml -&gt; shop. */
final class ShopMenu extends MenuHolder {

    private final MidnightMinigames plugin;
    private final BedWarsGame game;
    private final List<ConfigurationSection> categories;
    private final Map<Integer, ConfigurationSection> itemBySlot = new HashMap<>();
    private final String dye;
    private int selected;

    ShopMenu(MidnightMinigames plugin, BedWarsGame game, Player player) {
        this.plugin = plugin;
        this.game = game;
        YamlConfiguration cfg = plugin.getConfigurationManager().game("bedwars");
        this.categories = ConfigUtil.sections(cfg, "shop.categories");
        var team = game.getTeam(player.getUniqueId());
        this.dye = team != null && team.getColor() != null ? team.getColor().getDye() : "WHITE";
        String title = cfg.getString("shop.title", "<dark_gray>Item Shop");
        this.inventory = Bukkit.createInventory(this, 54, TextUtil.parse(title));
        render(player);
    }

    private void render(Player player) {
        inventory.clear();
        itemBySlot.clear();
        ItemStack filler = ItemFactory.simple(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 9; i < 18; i++) {
            inventory.setItem(i, filler);
        }
        for (int i = 0; i < categories.size() && i < 9; i++) {
            ConfigurationSection c = categories.get(i);
            ItemStack icon = ItemFactory.build(c);
            if (i == selected) {
                ItemMeta meta = icon.getItemMeta();
                meta.setEnchantmentGlintOverride(true);
                icon.setItemMeta(meta);
            }
            inventory.setItem(c.getInt("slot", i), icon);
        }
        if (selected >= categories.size()) {
            return;
        }
        int auto = 18;
        for (ConfigurationSection entry : ConfigUtil.sections(categories.get(selected), "items")) {
            int slot = entry.getInt("slot", auto++);
            if (slot < 18 || slot >= 54) {
                plugin.getLogger().warning("[config] games/bedwars.yml -> shop item '" + entry.getString("id", "?") + "' has invalid slot " + slot);
                continue;
            }
            inventory.setItem(slot, display(entry));
            itemBySlot.put(slot, entry);
        }
    }

    private ItemStack display(ConfigurationSection entry) {
        ItemStack item = ItemFactory.build(entry, "color", dye);
        ItemMeta meta = item.getItemMeta();
        List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        ConfigurationSection cost = entry.getConfigurationSection("cost");
        if (cost != null) {
            for (String res : cost.getKeys(false)) {
                lore.add(TextUtil.item(plugin.getMessages().text("bedwars.shop-cost", "amount", String.valueOf(cost.getInt(res)),
                        "resource", game.resourceName(res))));
            }
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot >= 0 && slot < 9 && slot < categories.size() + 0 && inventory.getItem(slot) != null) {
            for (int i = 0; i < categories.size(); i++) {
                if (categories.get(i).getInt("slot", i) == slot) {
                    selected = i;
                    render(player);
                    plugin.getEffects().play(player, "menu-click");
                    return;
                }
            }
        }
        ConfigurationSection entry = itemBySlot.get(slot);
        if (entry != null) {
            game.purchase(player, entry);
        }
    }
}

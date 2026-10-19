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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Item shop. Categories and items are defined in games/bedwars.yml -&gt; shop. A "Quick Buy" page shows the
 * player's favourites; shift-click any item to add or remove it (saved per player).
 */
final class ShopMenu extends MenuHolder {

    private static final int QUICK = -1;

    private final MidnightMinigames plugin;
    private final BedWarsGame game;
    private final List<ConfigurationSection> categories;
    private final Map<Integer, ConfigurationSection> itemBySlot = new HashMap<>();
    private final String dye;
    private final boolean quickEnabled;
    private final int quickSlot;
    private final Set<String> favourites = new LinkedHashSet<>();
    private int selected;

    ShopMenu(MidnightMinigames plugin, BedWarsGame game, Player player) {
        this.plugin = plugin;
        this.game = game;
        YamlConfiguration cfg = plugin.getConfigurationManager().game("bedwars");
        this.categories = ConfigUtil.sections(cfg, "shop.categories");
        var team = game.getTeam(player.getUniqueId());
        this.dye = team != null && team.getColor() != null ? team.getColor().getDye() : "WHITE";
        this.quickEnabled = cfg.getBoolean("shop.quick-buy.enabled", true);
        this.quickSlot = cfg.getInt("shop.quick-buy.slot", 8);
        String saved = plugin.getPlayerManager().getProfile(player).getString("bedwars", "quickbuy", null);
        if (saved == null) {
            favourites.addAll(cfg.getStringList("shop.quick-buy.default"));
        } else if (!saved.isEmpty()) {
            favourites.addAll(Arrays.asList(saved.split(",")));
        }
        this.selected = quickEnabled ? QUICK : 0;
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
            inventory.setItem(c.getInt("slot", i), icon(ItemFactory.build(c), i == selected));
        }
        if (quickEnabled) {
            ItemStack star = ItemFactory.simple(Material.NETHER_STAR, plugin.getMessages().text("bedwars.quick-buy-name"),
                    List.of(plugin.getMessages().text("bedwars.quick-buy-lore")));
            inventory.setItem(quickSlot, icon(star, selected == QUICK));
        }
        List<ConfigurationSection> shown = new ArrayList<>();
        if (selected == QUICK) {
            for (ConfigurationSection cat : categories) {
                for (ConfigurationSection entry : ConfigUtil.sections(cat, "items")) {
                    if (favourites.contains(entry.getString("id"))) {
                        shown.add(entry);
                    }
                }
            }
        } else if (selected >= 0 && selected < categories.size()) {
            shown.addAll(ConfigUtil.sections(categories.get(selected), "items"));
        }
        int auto = 18;
        for (ConfigurationSection entry : shown) {
            int slot = selected == QUICK ? auto++ : entry.getInt("slot", auto++);
            if (slot < 18 || slot >= 54) {
                plugin.getLogger().warning("[config] games/bedwars.yml -> shop item '" + entry.getString("id", "?") + "' has invalid slot " + slot);
                continue;
            }
            inventory.setItem(slot, display(entry));
            itemBySlot.put(slot, entry);
        }
    }

    private ItemStack icon(ItemStack item, boolean glow) {
        if (glow) {
            ItemMeta meta = item.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
        return item;
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
        if (quickEnabled) {
            lore.add(TextUtil.item(plugin.getMessages().text(favourites.contains(entry.getString("id"))
                    ? "bedwars.quick-buy-remove" : "bedwars.quick-buy-add")));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (quickEnabled && slot == quickSlot) {
            selected = QUICK;
            render(player);
            plugin.getEffects().play(player, "menu-click");
            return;
        }
        if (slot >= 0 && slot < 9) {
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
        if (entry == null) {
            return;
        }
        if (quickEnabled && event.isShiftClick()) {
            String id = entry.getString("id");
            if (!favourites.remove(id)) {
                favourites.add(id);
            }
            plugin.getPlayerManager().getProfile(player).setString("bedwars", "quickbuy", String.join(",", favourites));
            render(player);
            plugin.getEffects().play(player, "menu-click");
            return;
        }
        game.purchase(player, entry);
    }
}

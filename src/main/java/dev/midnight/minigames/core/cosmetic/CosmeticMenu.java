package dev.midnight.minigames.core.cosmetic;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.cosmetic.CosmeticService.Category;
import dev.midnight.minigames.core.cosmetic.CosmeticService.Item;
import dev.midnight.minigames.core.gui.MenuHolder;
import dev.midnight.minigames.util.ItemFactory;
import dev.midnight.minigames.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** /cosmetic: category overview and one paged shop page per category. Clicking buys (with a confirmation click), equips or unequips. */
public final class CosmeticMenu extends MenuHolder {

    private static final int[] ITEM_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private static final int BACK = 49;
    private static final int PREV = 48;
    private static final int NEXT = 50;
    private static final int UNEQUIP = 45;
    private static final long CONFIRM_MS = 5000L;

    /** Pending purchase confirmations, shared by every open page: player + item key -> expiry. */
    private static final Map<String, Long> CONFIRMS = new HashMap<>();

    private final MidnightMinigames plugin;
    private final CosmeticService svc;
    private final Category category;
    private final int page;
    private final Map<Integer, Item> bySlot = new HashMap<>();
    private final Map<Integer, Category> categoryBySlot = new HashMap<>();

    /** Overview page. */
    public CosmeticMenu(MidnightMinigames plugin, Player player) {
        this(plugin, player, null, 0);
    }

    CosmeticMenu(MidnightMinigames plugin, Player player, Category category, int page) {
        this.plugin = plugin;
        this.svc = plugin.getCosmetics();
        this.category = category;
        this.page = page;
        var y = plugin.getConfigurationManager().cosmetics();
        String title = y.getString("menu.title", "<dark_gray>Cosmetics");
        if (category == null) {
            this.inventory = Bukkit.createInventory(this, 27, TextUtil.parse(title));
            fill(27);
            renderOverview(player);
        } else {
            this.inventory = Bukkit.createInventory(this, 54, TextUtil.parse(title + " <dark_gray>» " + category.name));
            fill(54);
            renderCategory(player);
        }
    }

    private void fill(int size) {
        ItemStack pane = ItemFactory.simple(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < size; i++) {
            inventory.setItem(i, pane);
        }
    }

    private String msg(String key, String... vars) {
        return plugin.getMessages().text(key, vars);
    }

    private ItemStack icon(Material m, String name, List<String> lore, boolean glow) {
        ItemStack it = ItemFactory.simple(m, name, lore);
        if (glow) {
            ItemMeta meta = it.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            it.setItemMeta(meta);
        }
        return it;
    }

    private void renderOverview(Player player) {
        for (Category c : svc.getCategories()) {
            if (c.slot < 0 || c.slot >= 27) {
                continue;
            }
            List<String> lore = new ArrayList<>(c.lore);
            lore.add("");
            lore.add(msg("cosmetic.menu.owned", "owned", String.valueOf(svc.ownedCount(player, c)), "total", String.valueOf(c.items.size())));
            Item active = svc.getActive(player, c.id);
            lore.add(msg("cosmetic.menu.active", "item", active == null ? msg("cosmetic.menu.none") : active.name));
            lore.add("");
            lore.add(msg("cosmetic.menu.open"));
            inventory.setItem(c.slot, icon(c.icon, c.name, lore, active != null));
            categoryBySlot.put(c.slot, c);
        }
        inventory.setItem(22, icon(Material.GOLD_INGOT, msg("cosmetic.menu.balance-title"),
                List.of(msg("cosmetic.menu.balance", "coins", TextUtil.formatNumber(plugin.getEconomy().getBalance(player)))), false));
    }

    private void renderCategory(Player player) {
        int perPage = ITEM_SLOTS.length;
        int pages = Math.max(1, (category.items.size() + perPage - 1) / perPage);
        int start = page * perPage;
        Item active = svc.getActive(player, category.id);
        for (int i = 0; i < perPage && start + i < category.items.size(); i++) {
            Item item = category.items.get(start + i);
            int slot = ITEM_SLOTS[i];
            boolean owned = svc.owns(player, item);
            boolean equipped = active == item;
            List<String> lore = new ArrayList<>(item.lore);
            lore.add("");
            if (equipped) {
                lore.add(msg("cosmetic.menu.equipped"));
            } else if (owned) {
                lore.add(msg("cosmetic.menu.click-equip"));
            } else {
                lore.add(msg("cosmetic.menu.price", "price", TextUtil.formatNumber(item.price)));
                lore.add(isPending(player, item) ? msg("cosmetic.menu.click-confirm") : msg("cosmetic.menu.click-buy"));
            }
            inventory.setItem(slot, icon(item.icon, item.name, lore, equipped));
            bySlot.put(slot, item);
        }
        inventory.setItem(BACK, icon(Material.ARROW, msg("cosmetic.menu.back"), null, false));
        if (page > 0) {
            inventory.setItem(PREV, icon(Material.PAPER, msg("cosmetic.menu.prev"), null, false));
        }
        if (page + 1 < pages) {
            inventory.setItem(NEXT, icon(Material.PAPER, msg("cosmetic.menu.next"), null, false));
        }
        if (active != null) {
            inventory.setItem(UNEQUIP, icon(Material.BARRIER, msg("cosmetic.menu.unequip"), null, false));
        }
        inventory.setItem(53, icon(Material.GOLD_INGOT, msg("cosmetic.menu.balance-title"),
                List.of(msg("cosmetic.menu.balance", "coins", TextUtil.formatNumber(plugin.getEconomy().getBalance(player)))), false));
    }

    private static String confirmKey(Player p, Item item) {
        return p.getUniqueId() + ":" + item.key();
    }

    private static boolean isPending(Player p, Item item) {
        Long until = CONFIRMS.get(confirmKey(p, item));
        return until != null && until > System.currentTimeMillis();
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (category == null) {
            Category c = categoryBySlot.get(slot);
            if (c != null) {
                plugin.getEffects().play(player, "menu-click");
                new CosmeticMenu(plugin, player, c, 0).open(player);
            }
            return;
        }
        if (slot == BACK) {
            new CosmeticMenu(plugin, player).open(player);
            return;
        }
        if (slot == PREV && page > 0) {
            new CosmeticMenu(plugin, player, category, page - 1).open(player);
            return;
        }
        if (slot == NEXT && inventory.getItem(NEXT) != null && inventory.getItem(NEXT).getType() == Material.PAPER) {
            new CosmeticMenu(plugin, player, category, page + 1).open(player);
            return;
        }
        if (slot == UNEQUIP && svc.getActive(player, category.id) != null) {
            svc.unequip(player, category.id);
            plugin.getMessages().send(player, "cosmetic.unequipped", "category", category.name);
            new CosmeticMenu(plugin, player, category, page).open(player);
            return;
        }
        Item item = bySlot.get(slot);
        if (item == null) {
            return;
        }
        if (svc.owns(player, item)) {
            if (svc.getActive(player, category.id) == item) {
                svc.unequip(player, category.id);
                plugin.getMessages().send(player, "cosmetic.unequipped", "category", category.name);
            } else if (svc.equip(player, item)) {
                plugin.getMessages().send(player, "cosmetic.equipped", "item", TextUtil.plain(TextUtil.parse(item.name)));
                plugin.getEffects().play(player, "menu-click");
            }
        } else if (!isPending(player, item)) {
            CONFIRMS.put(confirmKey(player, item), System.currentTimeMillis() + CONFIRM_MS);
        } else {
            CONFIRMS.remove(confirmKey(player, item));
            switch (svc.purchase(player, item)) {
                case OK -> {
                    plugin.getMessages().send(player, "cosmetic.bought", "item", TextUtil.plain(TextUtil.parse(item.name)),
                            "price", TextUtil.formatNumber(item.price));
                    plugin.getEffects().play(player, "shop-buy");
                    svc.equip(player, item);
                }
                case NOT_ENOUGH -> {
                    plugin.getMessages().send(player, "cosmetic.not-enough", "price", TextUtil.formatNumber(item.price),
                            "coins", TextUtil.formatNumber(plugin.getEconomy().getBalance(player)));
                    plugin.getEffects().play(player, "shop-fail");
                }
                case LOADING -> plugin.getMessages().send(player, "cosmetic.loading");
                case ALREADY_OWNED -> plugin.getMessages().send(player, "cosmetic.already-owned");
            }
        }
        new CosmeticMenu(plugin, player, category, page).open(player);
    }
}

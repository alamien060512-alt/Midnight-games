package dev.midnight.minigames.core.rank;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.gui.MenuHolder;
import dev.midnight.minigames.core.rank.RankService.Rank;
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

/** /ranks: every rank with price, perks and state. Clicking buys it (second click confirms). */
public final class RankMenu extends MenuHolder {

    private static final long CONFIRM_MS = 5000L;
    private static final Map<String, Long> CONFIRMS = new HashMap<>();

    private final MidnightMinigames plugin;
    private final RankService svc;
    private final Map<Integer, Rank> bySlot = new HashMap<>();

    public RankMenu(MidnightMinigames plugin, Player player) {
        this.plugin = plugin;
        this.svc = plugin.getRanks();
        List<Rank> ranks = svc.getRanks();
        int rows = ranks.size() > 7 ? 4 : 3;
        this.inventory = Bukkit.createInventory(this, rows * 9,
                TextUtil.parse(plugin.getConfigurationManager().ranks().getString("menu.title", "<dark_gray>Ranks")));
        ItemStack pane = ItemFactory.simple(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, pane);
        }
        Rank current = svc.getRank(player);
        int perRow = Math.min(7, Math.max(1, ranks.size()));
        for (int i = 0; i < ranks.size() && i < 14; i++) {
            Rank r = ranks.get(i);
            int row = 1 + i / 7;
            int inRow = Math.min(7, ranks.size() - (i / 7) * 7);
            int col = 1 + (7 - inRow) / 2 + i % 7;
            int slot = row * 9 + col;
            boolean owned = current == r;
            boolean lower = current != null && current.index > r.index;
            List<String> lore = new ArrayList<>(r.lore);
            lore.add("");
            if (!r.prefix.isEmpty()) {
                lore.add(msg("ranks.menu.prefix", "prefix", r.prefix));
            }
            if (r.multiplier != 1.0) {
                lore.add(msg("ranks.menu.multiplier", "multiplier", String.format(java.util.Locale.ROOT, "%.2f", r.multiplier)));
            }
            lore.add("");
            if (owned) {
                lore.add(msg("ranks.menu.owned"));
            } else if (lower) {
                lore.add(msg("ranks.menu.lower"));
            } else {
                lore.add(msg("ranks.menu.price", "price", TextUtil.formatNumber(svc.costFor(player, r))));
                lore.add(isPending(player, r) ? msg("ranks.menu.click-confirm") : msg("ranks.menu.click-buy"));
            }
            ItemStack item = ItemFactory.simple(r.icon, r.name, lore);
            if (owned) {
                ItemMeta meta = item.getItemMeta();
                meta.setEnchantmentGlintOverride(true);
                item.setItemMeta(meta);
            }
            inventory.setItem(slot, item);
            bySlot.put(slot, r);
        }
        inventory.setItem(inventory.getSize() - 5, ItemFactory.simple(Material.GOLD_INGOT, msg("cosmetic.menu.balance-title"),
                List.of(msg("cosmetic.menu.balance", "coins", TextUtil.formatNumber(plugin.getEconomy().getBalance(player))))));
    }

    private String msg(String key, String... vars) {
        return plugin.getMessages().text(key, vars);
    }

    private static String key(Player p, Rank r) {
        return p.getUniqueId() + ":" + r.id;
    }

    private static boolean isPending(Player p, Rank r) {
        Long until = CONFIRMS.get(key(p, r));
        return until != null && until > System.currentTimeMillis();
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        Rank r = bySlot.get(event.getRawSlot());
        if (r == null) {
            return;
        }
        if (!isPending(player, r)) {
            CONFIRMS.put(key(player, r), System.currentTimeMillis() + CONFIRM_MS);
            new RankMenu(plugin, player).open(player);
            return;
        }
        CONFIRMS.remove(key(player, r));
        switch (svc.purchase(player, r)) {
            case OK -> {
                plugin.getMessages().send(player, "ranks.bought", "rank", TextUtil.plain(TextUtil.parse(r.name)));
                plugin.getEffects().play(player, "win");
            }
            case NOT_ENOUGH -> {
                plugin.getMessages().send(player, "ranks.not-enough", "price", TextUtil.formatNumber(svc.costFor(player, r)),
                        "coins", TextUtil.formatNumber(plugin.getEconomy().getBalance(player)));
                plugin.getEffects().play(player, "shop-fail");
            }
            case ALREADY_OWNED -> plugin.getMessages().send(player, "ranks.already-owned");
            case HAVE_BETTER -> plugin.getMessages().send(player, "ranks.have-better");
            case LOADING -> plugin.getMessages().send(player, "cosmetic.loading");
        }
        new RankMenu(plugin, player).open(player);
    }
}

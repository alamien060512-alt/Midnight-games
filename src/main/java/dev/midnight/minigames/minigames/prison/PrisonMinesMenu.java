package dev.midnight.minigames.minigames.prison;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.gui.MenuHolder;
import dev.midnight.minigames.util.ItemFactory;
import dev.midnight.minigames.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Lists the mines and teleports to the one clicked. */
final class PrisonMinesMenu extends MenuHolder {

    private final MidnightMinigames plugin;
    private final PrisonMinigame prison;
    private final Map<Integer, Mine> bySlot = new HashMap<>();

    PrisonMinesMenu(MidnightMinigames plugin, PrisonMinigame prison, Player player, List<Mine> mines) {
        this.plugin = plugin;
        this.prison = prison;
        YamlConfiguration cfg = plugin.getConfigurationManager().game("prison");
        int rows = Math.max(1, Math.min(6, (mines.size() + 8) / 9));
        this.inventory = Bukkit.createInventory(this, rows * 9, TextUtil.parse(cfg.getString("mines-menu.title", "<dark_gray>Mines")));
        int playerRank = prison.rankOf(player);
        int slot = 0;
        for (Mine m : mines) {
            if (slot >= inventory.getSize()) {
                break;
            }
            boolean unlocked = playerRank >= m.requiredRank;
            ItemStack item = ItemFactory.simple(unlocked ? m.icon : Material.BARRIER,
                    plugin.getMessages().text(unlocked ? "prison.mine-unlocked" : "prison.mine-locked", "mine", m.name), new ArrayList<>(List.of(
                            plugin.getMessages().text("prison.mine-required", "rank", prison.rankName(m.requiredRank)),
                            plugin.getMessages().text("prison.mine-remaining", "percent", String.valueOf(Math.round(m.remainingFraction() * 100))))));
            inventory.setItem(slot, item);
            bySlot.put(slot, m);
            slot++;
        }
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        Mine m = bySlot.get(event.getRawSlot());
        if (m == null) {
            return;
        }
        player.closeInventory();
        prison.teleportToMine(player, m);
    }

}

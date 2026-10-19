package dev.midnight.minigames.minigames.skyblock;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.island.AbstractIslandMinigame;
import dev.midnight.minigames.core.island.Island;
import dev.midnight.minigames.core.placeholder.PlaceholderRegistry;
import dev.midnight.minigames.util.ConfigUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Skyblock: every player gets an island from a template (structure file or layers), can invite members, set a home,
 * delete the island and buy upgrades (island size, member slots). Hub = arena lobby of the "main" skyblock arena.
 */
public final class SkyblockMinigame extends AbstractIslandMinigame {

    public SkyblockMinigame(MidnightMinigames plugin) {
        super(plugin, "skyblock", "<green>Skyblock");
    }

    List<ConfigurationSection> tiers(String upgradeId) {
        return ConfigUtil.sections(plugin.getConfigurationManager().game(getId()).getConfigurationSection("upgrades." + upgradeId), "tiers");
    }

    @Override
    protected int protectionRadius(Island island) {
        List<ConfigurationSection> t = tiers("size");
        if (t.isEmpty()) {
            return super.protectionRadius(island);
        }
        return t.get(Math.min(island.getUpgrade("size"), t.size() - 1)).getInt("radius", 50);
    }

    @Override
    protected int memberLimit(Island island) {
        List<ConfigurationSection> t = tiers("members");
        if (t.isEmpty()) {
            return super.memberLimit(island);
        }
        return t.get(Math.min(island.getUpgrade("members"), t.size() - 1)).getInt("limit", 3);
    }

    @Override
    protected boolean isExtraNeedingNoJoin(String sub) {
        return sub.equals("upgrade") || sub.equals("upgrades");
    }

    @Override
    protected boolean handleExtraCommand(Player player, String sub, String[] args) {
        if (sub.equals("upgrade") || sub.equals("upgrades")) {
            Island is = islands.of(player.getUniqueId());
            if (is == null) {
                plugin.getMessages().send(player, "island.no-island", "command", "/skyblock create");
            } else {
                new SkyblockUpgradeMenu(plugin, this, is, player).open(player);
            }
            return true;
        }
        return false;
    }

    /** Buys the next tier of an upgrade for an island. Only the owner may buy. */
    boolean buy(Player player, Island island, String upgradeId) {
        var m = plugin.getMessages();
        if (!island.getOwner().equals(player.getUniqueId())) {
            m.send(player, "island.owner-only");
            return false;
        }
        List<ConfigurationSection> t = tiers(upgradeId);
        int level = island.getUpgrade(upgradeId);
        if (level + 1 >= t.size()) {
            m.send(player, "island.upgrade-maxed");
            return false;
        }
        double cost = t.get(level + 1).getDouble("cost", 0);
        if (!plugin.getEconomy().withdraw(player, cost)) {
            m.send(player, "island.upgrade-cost", "cost", dev.midnight.minigames.util.TextUtil.formatNumber(cost));
            return false;
        }
        island.getUpgrades().put(upgradeId, level + 1);
        islands.markDirty();
        m.send(player, "island.upgraded", "upgrade", upgradeId, "level", String.valueOf(level + 2));
        plugin.getEffects().play(player, "shop-buy");
        return true;
    }

    @Override
    public Map<String, String> getScoreboardVariables(Player player) {
        Map<String, String> v = super.getScoreboardVariables(player);
        Island is = islands.of(player.getUniqueId());
        v.put("size_level", is == null ? "-" : String.valueOf(is.getUpgrade("size") + 1));
        return v;
    }

    @Override
    public void registerPlaceholders(PlaceholderRegistry registry) {
        // %midnight_skyblock_radius% %midnight_skyblock_members%
        registry.register(getId(), (player, key) -> {
            if (player == null) {
                return "";
            }
            return switch (key) {
                case "radius" -> getScoreboardVariables(player).get("island_radius");
                case "members" -> getScoreboardVariables(player).get("island_members");
                default -> null;
            };
        });
    }

    @Override
    public List<String> validateArena(Arena arena) {
        List<String> problems = new ArrayList<>();
        if (arena.getWorld() == null) {
            problems.add("Island world '" + arena.getWorldName() + "' is not loaded - create it with /minigame world create <name> "
                    + "and /minigame arena setworld skyblock " + arena.getName() + " <name>");
        }
        if (arena.getLobby() == null) {
            problems.add("Hub spawn missing: /minigame arena setlobby skyblock " + arena.getName());
        }
        return problems;
    }
}

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
        return sub.equals("upgrade") || sub.equals("upgrades") || sub.equals("level") || sub.equals("top");
    }

    // ---- island level ----------------------------------------------------------------------------------

    private final java.util.Set<java.util.UUID> calculating = new java.util.HashSet<>();

    /**
     * Counts the blocks inside the island's protection radius (config: level.values) and converts the points to a
     * level. Chunk snapshots are taken on the main thread, counted asynchronously.
     */
    private void calculateLevel(Player player, Island is) {
        var m = plugin.getMessages();
        var cfg = plugin.getConfigurationManager().game("skyblock");
        long cooldownMs = cfg.getLong("level.cooldown-seconds", 60) * 1000L;
        long last = is.getLong("level_time", 0);
        if (System.currentTimeMillis() - last < cooldownMs) {
            m.send(player, "island.level-cooldown", "seconds", String.valueOf((cooldownMs - (System.currentTimeMillis() - last)) / 1000 + 1));
            return;
        }
        org.bukkit.World w = is.getWorld();
        if (w == null || !calculating.add(is.getOwner())) {
            return;
        }
        is.setLong("level_time", System.currentTimeMillis());
        final int radius = protectionRadius(is);
        final int cx = is.getCenterX();
        final int cz = is.getCenterZ();
        final int minY = Math.max(w.getMinHeight(), is.getCenterY() - cfg.getInt("level.y-down", 40));
        final int maxY = Math.min(w.getMaxHeight() - 1, is.getCenterY() + cfg.getInt("level.y-up", 100));
        List<org.bukkit.ChunkSnapshot> snaps = new ArrayList<>();
        for (int x = (cx - radius) >> 4; x <= (cx + radius) >> 4; x++) {
            for (int z = (cz - radius) >> 4; z <= (cz + radius) >> 4; z++) {
                snaps.add(w.getChunkAt(x, z).getChunkSnapshot(false, false, false));
            }
        }
        final java.util.Map<org.bukkit.Material, Double> values = new java.util.HashMap<>();
        var vs = cfg.getConfigurationSection("level.values");
        if (vs != null) {
            for (String k : vs.getKeys(false)) {
                org.bukkit.Material mat = org.bukkit.Material.matchMaterial(k);
                if (mat != null) {
                    values.put(mat, vs.getDouble(k));
                }
            }
        }
        final double perLevel = Math.max(1, cfg.getDouble("level.points-per-level", 100));
        m.send(player, "island.level-calculating");
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            double points = 0;
            for (org.bukkit.ChunkSnapshot s : snaps) {
                int bx = s.getX() << 4;
                int bz = s.getZ() << 4;
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        if (Math.abs(bx + x - cx) > radius || Math.abs(bz + z - cz) > radius) {
                            continue;
                        }
                        for (int y = minY; y <= maxY; y++) {
                            Double v = values.get(s.getBlockType(x, y, z));
                            if (v != null) {
                                points += v;
                            }
                        }
                    }
                }
            }
            final double total = points;
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                calculating.remove(is.getOwner());
                long level = (long) (total / perLevel);
                is.setLong("points", (long) total);
                is.setLong("level", level);
                islands.markDirty();
                Player p = org.bukkit.Bukkit.getPlayer(player.getUniqueId());
                if (p != null) {
                    plugin.getMessages().send(p, "island.level-result", "level", String.valueOf(level), "points", String.valueOf((long) total));
                }
            });
        });
    }

    private void showTop(Player player) {
        List<Island> sorted = new ArrayList<>(islands.all());
        sorted.sort((a, b) -> Long.compare(b.getLong("level", 0), a.getLong("level", 0)));
        plugin.getMessages().send(player, "island.top-header");
        for (int i = 0; i < Math.min(10, sorted.size()); i++) {
            Island is = sorted.get(i);
            plugin.getMessages().send(player, "island.top-entry", "rank", String.valueOf(i + 1), "owner", nameOf(is.getOwner()),
                    "level", String.valueOf(is.getLong("level", 0)));
        }
    }

    @Override
    protected boolean handleExtraCommand(Player player, String sub, String[] args) {
        if (sub.equals("level")) {
            Island own = islands.of(player.getUniqueId());
            if (own == null) {
                plugin.getMessages().send(player, "island.no-island", "command", "/skyblock create");
            } else {
                calculateLevel(player, own);
            }
            return true;
        }
        if (sub.equals("top")) {
            showTop(player);
            return true;
        }
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
        updateBorder(player);
        m.send(player, "island.upgraded", "upgrade", upgradeId, "level", String.valueOf(level + 2));
        plugin.getEffects().play(player, "shop-buy");
        return true;
    }

    @Override
    public Map<String, String> getScoreboardVariables(Player player) {
        Map<String, String> v = super.getScoreboardVariables(player);
        Island is = islands.of(player.getUniqueId());
        v.put("size_level", is == null ? "-" : String.valueOf(is.getUpgrade("size") + 1));
        v.put("island_level", is == null ? "0" : String.valueOf(is.getLong("level", 0)));
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
                case "level" -> getScoreboardVariables(player).get("island_level");
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

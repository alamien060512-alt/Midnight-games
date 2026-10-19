package dev.midnight.minigames.minigames.oneblock;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.island.AbstractIslandMinigame;
import dev.midnight.minigames.core.island.Island;
import dev.midnight.minigames.core.island.IslandTemplate;
import dev.midnight.minigames.core.placeholder.PlaceholderRegistry;
import dev.midnight.minigames.util.ConfigUtil;
import dev.midnight.minigames.util.Cuboid;
import dev.midnight.minigames.util.ItemFactory;
import dev.midnight.minigames.util.LootTable;
import dev.midnight.minigames.util.RegionFiller;
import dev.midnight.minigames.util.TextUtil;
import dev.midnight.minigames.util.WeightedTable;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * OneBlock: one block in the sky that regenerates forever. The block table changes with progression phases
 * (games/oneblock.yml -&gt; phases). Islands, members and protection come from {@link AbstractIslandMinigame}.
 */
public final class OneBlockMinigame extends AbstractIslandMinigame {

    private static final class Phase {
        String name;
        long start;
        WeightedTable<Material> table = new WeightedTable<>();
        List<EntityType> mobs = new ArrayList<>();
        double mobChance;
        double chestChance;
        LootTable chestLoot;
    }

    private final Random random = new Random();
    private final List<Phase> phases = new ArrayList<>();
    private final Map<UUID, Long> resetConfirm = new HashMap<>();

    public OneBlockMinigame(MidnightMinigames plugin) {
        super(plugin, "oneblock", "<yellow>OneBlock");
    }

    @Override
    protected void onIslandsLoaded() {
        phases.clear();
        YamlConfiguration cfg = plugin.getConfigurationManager().game("oneblock");
        LootTable defaultChest = LootTable.load(cfg.getConfigurationSection("chest-loot"));
        for (ConfigurationSection s : ConfigUtil.sections(cfg, "phases")) {
            Phase p = new Phase();
            p.name = s.getString("name", "Phase");
            p.start = s.getLong("start", 0);
            ConfigurationSection blocks = s.getConfigurationSection("blocks");
            if (blocks != null) {
                for (String key : blocks.getKeys(false)) {
                    p.table.add(ItemFactory.material(key, Material.DIRT), blocks.getDouble(key, 1));
                }
            }
            if (p.table.isEmpty()) {
                plugin.getLogger().warning("[config] games/oneblock.yml -> phases: phase '" + p.name + "' has no blocks; using DIRT");
                p.table.add(Material.DIRT, 1);
            }
            for (String mob : s.getStringList("mobs")) {
                NamespacedKey key = NamespacedKey.fromString(mob.toLowerCase(Locale.ROOT));
                EntityType type = key == null ? null : Registry.ENTITY_TYPE.get(key);
                if (type == null) {
                    plugin.getLogger().warning("[config] games/oneblock.yml -> phase '" + p.name + "': unknown mob '" + mob + "'");
                } else {
                    p.mobs.add(type);
                }
            }
            p.mobChance = s.getDouble("mob-chance", 0.0);
            p.chestChance = s.getDouble("chest-chance", 0.0);
            p.chestLoot = s.isConfigurationSection("chest-loot") ? LootTable.load(s.getConfigurationSection("chest-loot")) : defaultChest;
            phases.add(p);
        }
        phases.sort((a, b) -> Long.compare(a.start, b.start));
        if (phases.isEmpty()) {
            plugin.getLogger().severe("[config] games/oneblock.yml -> phases: no phases defined - OneBlock cannot generate blocks");
        }
    }

    private int phaseIndex(long count) {
        int idx = 0;
        for (int i = 0; i < phases.size(); i++) {
            if (phases.get(i).start <= count) {
                idx = i;
            }
        }
        return idx;
    }

    private Block blockOf(Island is) {
        World w = is.getWorld();
        return w == null ? null : w.getBlockAt(is.getCenterX(), is.getCenterY(), is.getCenterZ());
    }

    private void regenerate(Island is) {
        Block b = blockOf(is);
        if (b == null || phases.isEmpty()) {
            return;
        }
        long count = is.getLong("blocks", 0);
        Phase phase = phases.get(phaseIndex(count));
        double r = random.nextDouble();
        if (!phase.mobs.isEmpty() && r < phase.mobChance) {
            b.getWorld().spawnEntity(b.getLocation().add(0.5, 1.0, 0.5), phase.mobs.get(random.nextInt(phase.mobs.size())));
        } else if (phase.chestLoot != null && !phase.chestLoot.isEmpty() && r < phase.mobChance + phase.chestChance) {
            b.setType(Material.CHEST, false);
            if (b.getState() instanceof Chest chest) {
                phase.chestLoot.fill(chest.getBlockInventory(), random);
            }
            return;
        }
        b.setType(phase.table.pick(random), false);
    }

    @Override
    protected void onIslandCreated(Player owner, Island island) {
        island.setLong("blocks", 0);
        regenerate(island);
    }

    @Override
    protected void onEnter(Player player, String modeId) {
        super.onEnter(player, modeId);
        Island is = islands.of(player.getUniqueId());
        if (is != null) {
            Block b = blockOf(is);
            if (b != null && b.getType() == Material.AIR) {
                regenerate(is);
            }
        }
    }

    @Override
    public void onBlockBreak(Player player, BlockBreakEvent e) {
        Island is = islands.at(e.getBlock().getLocation());
        if (is != null && is.isMember(player.getUniqueId()) && e.getBlock().getX() == is.getCenterX()
                && e.getBlock().getY() == is.getCenterY() && e.getBlock().getZ() == is.getCenterZ()) {
            long before = is.getLong("blocks", 0);
            long now = before + 1;
            is.setLong("blocks", now);
            islands.markDirty();
            plugin.getPlayerManager().getProfile(player).increment("oneblock", "broken", 1);
            int oldPhase = phaseIndex(before);
            int newPhase = phaseIndex(now);
            if (newPhase != oldPhase) {
                String name = phases.get(newPhase).name;
                for (UUID id : new ArrayList<>(is.getMembers())) {
                    announcePhase(id, name);
                }
                announcePhase(is.getOwner(), name);
            }
            Bukkit.getScheduler().runTask(plugin, () -> regenerate(is));
            return;
        }
        super.onBlockBreak(player, e);
    }

    private void announcePhase(UUID id, String name) {
        Player p = Bukkit.getPlayer(id);
        if (p != null && inside.contains(id)) {
            plugin.getMessages().title(p, "oneblock-phase", "phase", name);
            plugin.getEffects().play(p, "win");
        }
    }

    @Override
    protected boolean isExtraNeedingNoJoin(String sub) {
        return sub.equals("phase") || sub.equals("progress") || sub.equals("reset");
    }

    @Override
    protected boolean handleExtraCommand(Player player, String sub, String[] args) {
        var m = plugin.getMessages();
        Island is = islands.of(player.getUniqueId());
        switch (sub) {
            case "phase", "progress" -> {
                if (is == null) {
                    m.send(player, "island.no-island", "command", "/oneblock create");
                } else {
                    Map<String, String> v = getScoreboardVariables(player);
                    m.send(player, "oneblock.progress", "phase", v.get("phase"), "blocks", v.get("blocks"), "next", v.get("next_phase_in"));
                }
                return true;
            }
            case "reset" -> {
                if (is == null || !is.getOwner().equals(player.getUniqueId())) {
                    m.send(player, "island.owner-only");
                    return true;
                }
                long now = System.currentTimeMillis();
                Long until = resetConfirm.get(player.getUniqueId());
                if (args.length < 2 || !args[1].equalsIgnoreCase("confirm") || until == null || until < now) {
                    resetConfirm.put(player.getUniqueId(), now + 30_000L);
                    m.send(player, "oneblock.reset-confirm");
                    return true;
                }
                resetConfirm.remove(player.getUniqueId());
                resetIsland(player, is);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** Island reset: progression back to phase one, island area cleared and rebuilt from the template. */
    private void resetIsland(Player player, Island is) {
        World w = is.getWorld();
        if (w == null) {
            plugin.getMessages().send(player, "island.world-missing", "world", is.getWorldName());
            return;
        }
        is.setLong("blocks", 0);
        islands.markDirty();
        YamlConfiguration cfg = plugin.getConfigurationManager().game("oneblock");
        int r = cfg.getInt("reset.clear-radius", 12);
        Cuboid c = new Cuboid(w.getName(), is.getCenterX() - r, is.getCenterY() - cfg.getInt("reset.clear-down", 6), is.getCenterZ() - r,
                is.getCenterX() + r, is.getCenterY() + cfg.getInt("reset.clear-up", 30), is.getCenterZ() + r);
        RegionFiller.fill(plugin, w, c, () -> Material.AIR, cfg.getInt("island.clear-blocks-per-tick", 4000), () -> {
            Location center = is.getCenter();
            if (center == null) {
                return;
            }
            IslandTemplate.paste(plugin, getId(), cfg.getConfigurationSection("template"), center);
            regenerate(is);
            Location home = islands.homeOf(is);
            Player p = Bukkit.getPlayer(player.getUniqueId());
            if (p != null && home != null) {
                p.teleport(home);
                plugin.getMessages().send(p, "oneblock.reset-done");
            }
        });
    }

    @Override
    public Map<String, String> getScoreboardVariables(Player player) {
        Map<String, String> v = super.getScoreboardVariables(player);
        Island is = islands.of(player.getUniqueId());
        if (is == null || phases.isEmpty()) {
            v.put("phase", "-");
            v.put("blocks", "0");
            v.put("next_phase_in", "-");
            return v;
        }
        long count = is.getLong("blocks", 0);
        int idx = phaseIndex(count);
        v.put("phase", TextUtil.plain(TextUtil.parse(phases.get(idx).name)));
        v.put("blocks", String.valueOf(count));
        v.put("next_phase_in", idx + 1 < phases.size() ? String.valueOf(phases.get(idx + 1).start - count) : "-");
        return v;
    }

    @Override
    public void registerPlaceholders(PlaceholderRegistry registry) {
        // %midnight_oneblock_blocks% %midnight_oneblock_phase%
        registry.register(getId(), (player, key) -> {
            if (player == null) {
                return "";
            }
            return switch (key) {
                case "blocks", "phase", "next_phase_in" -> getScoreboardVariables(player).get(key);
                default -> null;
            };
        });
    }

    @Override
    public java.util.List<String> validateArena(Arena arena) {
        List<String> problems = new ArrayList<>();
        if (arena.getWorld() == null) {
            problems.add("Island world '" + arena.getWorldName() + "' is not loaded (/minigame world create <name>, /minigame arena setworld)");
        }
        if (arena.getLobby() == null) {
            problems.add("Hub spawn missing: /minigame arena setlobby oneblock " + arena.getName());
        }
        return problems;
    }
}

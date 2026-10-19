package dev.midnight.minigames.minigames.skywars;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.game.AbstractArenaMinigame;
import dev.midnight.minigames.core.game.GameInstance;
import dev.midnight.minigames.core.game.ModeDefinition;
import dev.midnight.minigames.core.team.TeamColor;
import dev.midnight.minigames.util.Cuboid;
import dev.midnight.minigames.util.LocationUtil;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** SkyWars: loot your island, fight across the void, last team standing wins. */
public final class SkyWarsMinigame extends AbstractArenaMinigame {

    public SkyWarsMinigame(MidnightMinigames plugin) {
        super(plugin, "skywars", "<aqua>SkyWars");
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected GameInstance createInstance(Arena arena, ModeDefinition mode) {
        return new SkyWarsGame(plugin, this, arena, mode);
    }

    @Override
    public List<String> getArenaSubcommands() {
        return List.of("addchest", "scanchests", "clearchests");
    }

    @Override
    public List<String> tabCompleteArena(String sub, Arena arena, String[] args) {
        if (sub.equals("addchest") && args.length == 1) {
            return List.of("island", "mid");
        }
        return List.of();
    }

    @Override
    public boolean handleArenaCommand(CommandSender sender, String sub, Arena arena, String[] args) {
        var m = plugin.getMessages();
        switch (sub) {
            case "addchest" -> {
                if (!(sender instanceof Player p)) {
                    m.send(sender, "general.players-only");
                    return true;
                }
                String tier = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "island";
                if (!tier.equals("island") && !tier.equals("mid")) {
                    m.send(sender, "admin.usage", "usage", "/minigame arena addchest skywars <arena> [island|mid]");
                    return true;
                }
                Block target = p.getTargetBlockExact(6);
                if (target == null || !(target.getState() instanceof Container)) {
                    m.send(sender, "skywars.look-at-chest");
                    return true;
                }
                addChest(arena, tier, target);
                m.send(sender, "skywars.chest-added", "tier", tier, "count", String.valueOf(countChests(arena)));
                return true;
            }
            case "scanchests" -> {
                if (!(sender instanceof Player p)) {
                    m.send(sender, "general.players-only");
                    return true;
                }
                Cuboid sel = plugin.getArenaManager().getSelection(p);
                if (sel == null) {
                    m.send(sender, "admin.no-selection");
                    return true;
                }
                World w = p.getWorld();
                double midRadius = args.length > 0 ? Double.parseDouble(args[0]) : 14.0;
                double cx = (sel.minX + sel.maxX) / 2.0;
                double cz = (sel.minZ + sel.maxZ) / 2.0;
                int found = 0;
                for (int chunkX = sel.minX >> 4; chunkX <= sel.maxX >> 4; chunkX++) {
                    for (int chunkZ = sel.minZ >> 4; chunkZ <= sel.maxZ >> 4; chunkZ++) {
                        Chunk chunk = w.getChunkAt(chunkX, chunkZ);
                        for (BlockState st : chunk.getTileEntities()) {
                            if (st instanceof Container && sel.contains(st.getX(), st.getY(), st.getZ())) {
                                double dx = st.getX() - cx;
                                double dz = st.getZ() - cz;
                                addChest(arena, Math.sqrt(dx * dx + dz * dz) <= midRadius ? "mid" : "island", st.getBlock());
                                found++;
                            }
                        }
                    }
                }
                m.send(sender, "skywars.chests-scanned", "found", String.valueOf(found), "count", String.valueOf(countChests(arena)));
                return true;
            }
            case "clearchests" -> {
                arena.getYaml().set("data.chests", null);
                m.send(sender, "skywars.chests-cleared");
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void addChest(Arena arena, String tier, Block b) {
        String path = "data.chests." + tier;
        List<String> list = new ArrayList<>(arena.getYaml().getStringList(path));
        String s = LocationUtil.serializeBlock(b);
        // remove from the other tier / avoid duplicates
        String other = "data.chests." + (tier.equals("mid") ? "island" : "mid");
        List<String> otherList = new ArrayList<>(arena.getYaml().getStringList(other));
        if (otherList.remove(s)) {
            arena.getYaml().set(other, otherList);
        }
        if (!list.contains(s)) {
            list.add(s);
        }
        arena.getYaml().set(path, list);
    }

    private int countChests(Arena arena) {
        return arena.getYaml().getStringList("data.chests.island").size() + arena.getYaml().getStringList("data.chests.mid").size();
    }

    @Override
    public List<String> validateArena(Arena arena) {
        List<String> problems = new ArrayList<>(super.validateArena(arena));
        ModeDefinition mode = findMode(arena.getMode());
        boolean teamMode = mode != null && mode.getTeamSize() > 1;
        if (teamMode) {
            if (arena.getRawTeamSpawns().size() < 2) {
                problems.add("Team mode needs at least 2 team spawns: /minigame arena setspawn skywars " + arena.getName() + " <red|blue|...>");
            }
            for (String t : arena.getRawTeamSpawns().keySet()) {
                if (TeamColor.fromId(t) == null) {
                    problems.add("Team '" + t + "' is not a standard colour (" + java.util.Arrays.toString(TeamColor.values()) + ")");
                }
            }
        } else if (arena.getSpawns().size() < Math.max(2, arena.getMinPlayers())) {
            problems.add("Needs at least " + Math.max(2, arena.getMinPlayers()) + " spawn points: /minigame arena addspawn skywars " + arena.getName());
        }
        if (countChests(arena) == 0 && !(mode != null && mode.isLucky())) {
            problems.add("No chests registered: /minigame arena addchest or scanchests");
        }
        if (arena.getSpectator() == null) {
            problems.add("Spectator location missing: /minigame arena setspectator");
        }
        return problems;
    }

}

package dev.midnight.minigames.minigames.bedwars;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.game.AbstractArenaMinigame;
import dev.midnight.minigames.core.game.GameInstance;
import dev.midnight.minigames.core.game.ModeDefinition;
import dev.midnight.minigames.core.team.TeamColor;
import dev.midnight.minigames.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * BedWars: protect your bed, collect resources from generators, buy gear, destroy enemy beds.
 * Arena setup commands (all under /minigame arena): setspawn &lt;team&gt;, setbed &lt;team&gt;, addgen &lt;type&gt;,
 * addshop &lt;item|upgrade&gt;, clearshops, cleargens.
 */
public final class BedWarsMinigame extends AbstractArenaMinigame {

    static final List<String> GEN_TYPES = List.of("iron", "gold", "diamond", "emerald");

    public BedWarsMinigame(MidnightMinigames plugin) {
        super(plugin, "bedwars", "<red>BedWars");
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected GameInstance createInstance(Arena arena, ModeDefinition mode) {
        return new BedWarsGame(plugin, this, arena, mode);
    }

    @Override
    public void registerPlaceholders(dev.midnight.minigames.core.placeholder.PlaceholderRegistry registry) {
        // %midnight_bedwars_beds% - beds destroyed by the player
        registry.register(getId(), (player, key) -> {
            if (key.equals("beds")) {
                return player == null ? "0" : String.valueOf(plugin.getPlayerManager().getProfile(player).getLong("stats", "bedwars.beds", 0));
            }
            return null;
        });
    }

    @Override
    public List<String> getArenaSubcommands() {
        return List.of("setbed", "addgen", "cleargens", "addshop", "clearshops");
    }

    @Override
    public List<String> tabCompleteArena(String sub, Arena arena, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        return switch (sub) {
            case "setbed" -> new ArrayList<>(arena.getRawTeamSpawns().keySet());
            case "addgen" -> GEN_TYPES;
            case "addshop" -> List.of("item", "upgrade");
            default -> List.of();
        };
    }

    @Override
    public boolean handleArenaCommand(CommandSender sender, String sub, Arena arena, String[] args) {
        var m = plugin.getMessages();
        if (!getArenaSubcommands().contains(sub)) {
            return false;
        }
        if (sub.equals("cleargens")) {
            arena.getYaml().set("data.generators", null);
            m.send(sender, "bedwars.cleared", "what", "generators");
            return true;
        }
        if (sub.equals("clearshops")) {
            arena.getYaml().set("data.shops", null);
            m.send(sender, "bedwars.cleared", "what", "shops");
            return true;
        }
        if (!(sender instanceof Player p)) {
            m.send(sender, "general.players-only");
            return true;
        }
        switch (sub) {
            case "setbed" -> {
                if (args.length < 1) {
                    m.send(sender, "admin.usage", "usage", "/minigame arena setbed bedwars <arena> <team>  (look at the bed)");
                    return true;
                }
                if (TeamColor.fromId(args[0]) == null) {
                    m.send(sender, "bedwars.bad-team", "team", args[0]);
                    return true;
                }
                Block b = p.getTargetBlockExact(6);
                if (b == null || !Tag.BEDS.isTagged(b.getType())) {
                    m.send(sender, "bedwars.look-at-bed");
                    return true;
                }
                arena.getYaml().set("data.beds." + args[0].toLowerCase(Locale.ROOT), LocationUtil.serializeBlock(b));
                m.send(sender, "bedwars.bed-set", "team", args[0].toLowerCase(Locale.ROOT));
            }
            case "addgen" -> {
                if (args.length < 1 || !GEN_TYPES.contains(args[0].toLowerCase(Locale.ROOT))) {
                    m.send(sender, "admin.usage", "usage", "/minigame arena addgen bedwars <arena> <iron|gold|diamond|emerald>");
                    return true;
                }
                Location l = p.getLocation().getBlock().getLocation();
                List<String> list = new ArrayList<>(arena.getYaml().getStringList("data.generators"));
                list.add(args[0].toLowerCase(Locale.ROOT) + ";" + LocationUtil.serializeBlock(l.getBlock()));
                arena.getYaml().set("data.generators", list);
                m.send(sender, "bedwars.gen-added", "type", args[0].toLowerCase(Locale.ROOT), "count", String.valueOf(list.size()));
            }
            case "addshop" -> {
                String type = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "item";
                if (!type.equals("item") && !type.equals("upgrade")) {
                    m.send(sender, "admin.usage", "usage", "/minigame arena addshop bedwars <arena> <item|upgrade>");
                    return true;
                }
                List<String> list = new ArrayList<>(arena.getYaml().getStringList("data.shops." + type));
                list.add(LocationUtil.serialize(p.getLocation()));
                arena.getYaml().set("data.shops." + type, list);
                m.send(sender, "bedwars.shop-added", "type", type, "count", String.valueOf(list.size()));
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<String> validateArena(Arena arena) {
        List<String> problems = new ArrayList<>(super.validateArena(arena));
        var spawns = arena.getRawTeamSpawns();
        if (spawns.size() < 2) {
            problems.add("Needs at least 2 team spawns: /minigame arena setspawn bedwars " + arena.getName() + " <red|blue|...>");
        }
        for (String team : spawns.keySet()) {
            if (TeamColor.fromId(team) == null) {
                problems.add("Team '" + team + "' is not a standard colour (" + java.util.Arrays.toString(TeamColor.values()) + ")");
            }
            if (arena.getYaml().getString("data.beds." + team) == null) {
                problems.add("Team '" + team + "' has no bed: look at its bed and run /minigame arena setbed bedwars " + arena.getName() + " " + team);
            }
        }
        if (arena.getYaml().getStringList("data.generators").isEmpty()) {
            problems.add("No generators: stand on each spot and run /minigame arena addgen bedwars " + arena.getName() + " <iron|gold|diamond|emerald>");
        }
        if (arena.getYaml().getStringList("data.shops.item").isEmpty()) {
            problems.add("No item shop: /minigame arena addshop bedwars " + arena.getName() + " item");
        }
        if (arena.getYaml().getStringList("data.shops.upgrade").isEmpty()) {
            problems.add("No upgrade shop: /minigame arena addshop bedwars " + arena.getName() + " upgrade");
        }
        if (arena.getSpectator() == null) {
            problems.add("Spectator location missing: /minigame arena setspectator");
        }
        return problems;
    }
}

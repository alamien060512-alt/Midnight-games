package dev.midnight.minigames.commands;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.arena.ArenaManager;
import dev.midnight.minigames.core.game.Minigame;
import dev.midnight.minigames.core.game.ModeDefinition;
import dev.midnight.minigames.core.player.PlayerStats;
import dev.midnight.minigames.core.team.TeamColor;
import dev.midnight.minigames.util.Cuboid;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** /minigame - admin and setup command. */
public final class MinigameCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ROOT = List.of("help", "reload", "arena", "forcestart", "stop", "world",
            "setlobby", "lobby", "pos1", "pos2", "stats", "addcoins");

    /** Generic arena sub commands (modules can add more through Minigame#getArenaSubcommands). */
    private static final List<String> ARENA_SUBS = List.of("create", "delete", "list", "info", "setspawn", "addspawn",
            "clearspawns", "setlobby", "setspectator", "setworld", "setmode", "setplayers", "setcountdown",
            "setgametime", "setvoid", "setreset", "setregion", "enable", "disable", "tp", "validate");

    private final MidnightMinigames plugin;

    public MinigameCommand(MidnightMinigames plugin) {
        this.plugin = plugin;
    }

    private void msg(CommandSender s, String key, String... vars) {
        plugin.getMessages().send(s, key, vars);
    }

    private boolean perm(CommandSender s, String key) {
        if (plugin.getPermissions().has(s, key)) {
            return true;
        }
        msg(s, "general.no-permission");
        return false;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            msg(sender, "admin.help");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        try {
            switch (sub) {
                case "reload" -> {
                    if (perm(sender, "admin-reload")) {
                        plugin.reloadAll();
                        msg(sender, "admin.reloaded");
                    }
                }
                case "arena" -> handleArena(sender, Arrays.copyOfRange(args, 1, args.length));
                case "forcestart" -> {
                    if (perm(sender, "admin-forcestart")) {
                        gameArena(sender, args, 1, (mg, arena) -> {
                            if (mg.start(arena.getName())) {
                                msg(sender, "admin.forcestarted", "arena", arena.getName());
                            } else {
                                msg(sender, "admin.not-running", "arena", arena.getName());
                            }
                        });
                    }
                }
                case "stop" -> {
                    if (perm(sender, "admin-stop")) {
                        gameArena(sender, args, 1, (mg, arena) -> {
                            if (mg.stop(arena.getName())) {
                                msg(sender, "admin.stopped", "arena", arena.getName());
                            } else {
                                msg(sender, "admin.not-running", "arena", arena.getName());
                            }
                        });
                    }
                }
                case "world" -> handleWorld(sender, Arrays.copyOfRange(args, 1, args.length));
                case "setlobby", "lobby" -> {
                    if (!perm(sender, "admin-arena")) {
                        break;
                    }
                    if (sender instanceof Player p) {
                        if (sub.equals("lobby") && args.length == 1) {
                            Location l = plugin.getLobbyService().getMainLobby();
                            if (l == null) {
                                msg(p, "admin.lobby-not-set");
                            } else {
                                p.teleport(l);
                            }
                        } else {
                            plugin.getLobbyService().setMainLobby(p.getLocation());
                            msg(p, "admin.lobby-set");
                        }
                    } else {
                        msg(sender, "general.players-only");
                    }
                }
                case "pos1", "pos2" -> {
                    if (perm(sender, "admin-arena") && requirePlayer(sender)) {
                        Player p = (Player) sender;
                        plugin.getArenaManager().setSelection(p, sub.equals("pos1") ? 0 : 1, p.getLocation().getBlock().getLocation());
                        msg(p, "admin.pos-set", "pos", sub, "x", String.valueOf(p.getLocation().getBlockX()),
                                "y", String.valueOf(p.getLocation().getBlockY()), "z", String.valueOf(p.getLocation().getBlockZ()));
                    }
                }
                case "stats" -> {
                    if (perm(sender, "admin-stats")) {
                        Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : (sender instanceof Player p ? p : null);
                        if (target == null) {
                            msg(sender, "general.player-not-found", "player", args.length > 1 ? args[1] : "?");
                        } else {
                            PlayerStats s = plugin.getPlayerManager().getStats(target);
                            msg(sender, "admin.stats", "player", target.getName(), "wins", String.valueOf(s.getWins()),
                                    "losses", String.valueOf(s.getLosses()), "kills", String.valueOf(s.getKills()),
                                    "deaths", String.valueOf(s.getDeaths()), "coins", String.valueOf(s.getCoins()),
                                    "played", String.valueOf(s.getGamesPlayed()));
                        }
                    }
                }
                case "addcoins" -> {
                    if (perm(sender, "admin-stats")) {
                        if (args.length < 3) {
                            msg(sender, "admin.usage", "usage", "/minigame addcoins <player> <amount>");
                            break;
                        }
                        Player target = Bukkit.getPlayerExact(args[1]);
                        if (target == null) {
                            msg(sender, "general.player-not-found", "player", args[1]);
                            break;
                        }
                        double amount = Double.parseDouble(args[2]);
                        plugin.getPlayerManager().addCoins(target, amount);
                        msg(sender, "admin.coins-added", "player", target.getName(), "amount", args[2]);
                    }
                }
                default -> msg(sender, "admin.help");
            }
        } catch (NumberFormatException ex) {
            msg(sender, "admin.invalid-number", "value", ex.getMessage() == null ? "" : ex.getMessage());
        }
        return true;
    }

    private boolean requirePlayer(CommandSender sender) {
        if (sender instanceof Player) {
            return true;
        }
        msg(sender, "general.players-only");
        return false;
    }

    private interface GameArenaAction {
        void run(Minigame mg, Arena arena);
    }

    /** Resolves "&lt;game&gt; &lt;arena&gt;" at args[offset] with error messages. */
    private void gameArena(CommandSender sender, String[] args, int offset, GameArenaAction action) {
        if (args.length < offset + 2) {
            msg(sender, "admin.usage", "usage", "<game> <arena>");
            return;
        }
        Minigame mg = plugin.getMinigameManager().get(args[offset]);
        if (mg == null) {
            msg(sender, "admin.unknown-game", "game", args[offset]);
            return;
        }
        Arena arena = plugin.getArenaManager().get(mg.getId(), args[offset + 1]);
        if (arena == null) {
            msg(sender, "admin.unknown-arena", "game", mg.getId(), "arena", args[offset + 1]);
            return;
        }
        action.run(mg, arena);
    }

    // ------------------------------------------------------------------------------------------------
    // /minigame arena ...
    // ------------------------------------------------------------------------------------------------

    private void handleArena(CommandSender sender, String[] a) {
        if (!perm(sender, "admin-arena")) {
            return;
        }
        if (a.length == 0) {
            msg(sender, "admin.arena-help");
            return;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        ArenaManager arenas = plugin.getArenaManager();
        switch (sub) {
            case "list" -> {
                List<Arena> list = new ArrayList<>();
                for (Arena ar : arenas.getAll()) {
                    if (a.length < 2 || ar.getGameId().equalsIgnoreCase(a[1])) {
                        list.add(ar);
                    }
                }
                if (list.isEmpty()) {
                    msg(sender, "admin.no-arenas");
                    return;
                }
                msg(sender, "admin.arena-list-header", "count", String.valueOf(list.size()));
                for (Arena ar : list) {
                    String state = plugin.getMinigameManager().get(ar.getGameId()) == null ? "?"
                            : plugin.getMinigameManager().get(ar.getGameId()).getState(ar.getName()).name();
                    msg(sender, "admin.arena-list-entry", "game", ar.getGameId(), "arena", ar.getName(),
                            "mode", ar.getMode(), "world", ar.getWorldName().isEmpty() ? "-" : ar.getWorldName(),
                            "enabled", ar.isEnabled() ? "<green>enabled" : "<red>disabled", "state", state);
                }
            }
            case "create" -> {
                if (a.length < 3) {
                    msg(sender, "admin.usage", "usage", "/minigame arena create <game> <name>");
                    return;
                }
                Minigame mg = plugin.getMinigameManager().get(a[1]);
                if (mg == null) {
                    msg(sender, "admin.unknown-game", "game", a[1]);
                    return;
                }
                if (!ArenaManager.isValidName(a[2])) {
                    msg(sender, "admin.invalid-name");
                    return;
                }
                if (arenas.get(mg.getId(), a[2]) != null) {
                    msg(sender, "admin.arena-exists", "arena", a[2]);
                    return;
                }
                Arena arena = mg.createArena(a[2]);
                if (arena == null) {
                    msg(sender, "admin.arena-create-failed");
                    return;
                }
                if (sender instanceof Player p) {
                    arena.setWorldName(p.getWorld().getName());
                }
                arenas.save(arena);
                msg(sender, "admin.arena-created", "game", mg.getId(), "arena", arena.getName(),
                        "world", arena.getWorldName().isEmpty() ? "-" : arena.getWorldName());
            }
            case "delete" -> {
                if (a.length < 3) {
                    msg(sender, "admin.usage", "usage", "/minigame arena delete <game> <name>");
                    return;
                }
                Minigame mg = plugin.getMinigameManager().get(a[1]);
                if (mg == null) {
                    msg(sender, "admin.unknown-game", "game", a[1]);
                    return;
                }
                if (arenas.get(mg.getId(), a[2]) == null) {
                    msg(sender, "admin.unknown-arena", "game", mg.getId(), "arena", a[2]);
                    return;
                }
                mg.deleteArena(a[2]);
                msg(sender, "admin.arena-deleted", "arena", a[2]);
            }
            default -> {
                gameArena(sender, a, 1, (mg, arena) -> arenaAction(sender, sub, mg, arena, Arrays.copyOfRange(a, 3, a.length)));
            }
        }
    }

    private void arenaAction(CommandSender sender, String sub, Minigame mg, Arena arena, String[] rest) {
        ArenaManager arenas = plugin.getArenaManager();
        Player p = sender instanceof Player pl ? pl : null;
        boolean changed = true;
        switch (sub) {
            case "info" -> {
                changed = false;
                msg(sender, "admin.arena-info", "game", mg.getId(), "arena", arena.getName(), "mode", arena.getMode(),
                        "enabled", String.valueOf(arena.isEnabled()), "world", arena.getWorldName(),
                        "min", String.valueOf(arena.getMinPlayers()), "max", String.valueOf(arena.getMaxPlayers()),
                        "countdown", String.valueOf(arena.getCountdown()), "gametime", String.valueOf(arena.getGameTime()),
                        "lobby", arena.getLobby() != null ? "set" : "<red>missing",
                        "spectator", arena.getSpectator() != null ? "set" : "<red>missing",
                        "spawns", String.valueOf(arena.getSpawns().size()),
                        "teams", String.join(", ", arena.getRawTeamSpawns().keySet()));
            }
            case "validate" -> {
                changed = false;
                List<String> problems = mg.validateArena(arena);
                if (problems.isEmpty()) {
                    msg(sender, "admin.arena-valid", "arena", arena.getName());
                } else {
                    msg(sender, "admin.arena-invalid", "arena", arena.getName());
                    for (String pr : problems) {
                        msg(sender, "admin.problem", "problem", pr);
                    }
                }
            }
            case "setspawn" -> {
                if (!requirePlayer(sender)) {
                    return;
                }
                if (rest.length < 1) {
                    msg(sender, "admin.usage", "usage", "/minigame arena setspawn <game> <arena> <team>");
                    return;
                }
                if (TeamColor.fromId(rest[0]) == null && !rest[0].matches("[A-Za-z0-9_\\-]{1,16}")) {
                    msg(sender, "admin.invalid-name");
                    return;
                }
                arena.setTeamSpawn(rest[0], p.getLocation());
                if (arena.getWorldName().isEmpty()) {
                    arena.setWorldName(p.getWorld().getName());
                }
                msg(sender, "admin.spawn-set", "team", rest[0].toLowerCase(Locale.ROOT));
                if (TeamColor.fromId(rest[0]) == null) {
                    msg(sender, "admin.team-not-standard", "team", rest[0]);
                }
            }
            case "addspawn" -> {
                if (!requirePlayer(sender)) {
                    return;
                }
                arena.addSpawn(p.getLocation());
                msg(sender, "admin.spawn-added", "count", String.valueOf(arena.getSpawns().size()));
            }
            case "clearspawns" -> {
                arena.clearSpawns();
                msg(sender, "admin.spawns-cleared");
            }
            case "setlobby" -> {
                if (!requirePlayer(sender)) {
                    return;
                }
                arena.setLobby(p.getLocation());
                msg(sender, "admin.location-set", "what", "lobby");
            }
            case "setspectator" -> {
                if (!requirePlayer(sender)) {
                    return;
                }
                arena.setSpectator(p.getLocation());
                msg(sender, "admin.location-set", "what", "spectator");
            }
            case "setworld" -> {
                String name = rest.length > 0 ? rest[0] : (p != null ? p.getWorld().getName() : null);
                if (name == null) {
                    msg(sender, "admin.usage", "usage", "/minigame arena setworld <game> <arena> <world>");
                    return;
                }
                World w = plugin.getWorldService().getOrLoad(name);
                if (w == null) {
                    msg(sender, "admin.world-missing", "world", name);
                    return;
                }
                arena.setWorldName(w.getName());
                msg(sender, "admin.world-set", "world", w.getName());
            }
            case "setmode" -> {
                if (rest.length < 1) {
                    msg(sender, "admin.usage", "usage", "/minigame arena setmode <game> <arena> <mode>");
                    return;
                }
                boolean known = false;
                ModeDefinition def = null;
                for (ModeDefinition m : mg.getModes()) {
                    if (m.getId().equalsIgnoreCase(rest[0])) {
                        known = true;
                        def = m;
                    }
                }
                if (!known && !mg.getModes().isEmpty()) {
                    msg(sender, "general.unknown-mode", "mode", rest[0]);
                    return;
                }
                arena.setMode(rest[0].toLowerCase(Locale.ROOT));
                msg(sender, "admin.mode-set", "mode", rest[0]);
                if (def != null && def.getRaw().contains("max-players")) {
                    msg(sender, "admin.mode-hint", "min", String.valueOf(def.getRaw().getInt("min-players", arena.getMinPlayers())),
                            "max", String.valueOf(def.getRaw().getInt("max-players", arena.getMaxPlayers())));
                }
            }
            case "setplayers" -> {
                if (rest.length < 2) {
                    msg(sender, "admin.usage", "usage", "/minigame arena setplayers <game> <arena> <min> <max>");
                    return;
                }
                int min = Integer.parseInt(rest[0]);
                int max = Integer.parseInt(rest[1]);
                if (min < 1 || max < min) {
                    msg(sender, "admin.invalid-number", "value", rest[0] + " " + rest[1]);
                    return;
                }
                arena.setMinPlayers(min);
                arena.setMaxPlayers(max);
                msg(sender, "admin.players-set", "min", String.valueOf(min), "max", String.valueOf(max));
            }
            case "setcountdown" -> {
                if (rest.length < 1) {
                    msg(sender, "admin.usage", "usage", "/minigame arena setcountdown <game> <arena> <seconds>");
                    return;
                }
                arena.setCountdown(Integer.parseInt(rest[0]));
                msg(sender, "admin.value-set", "key", "countdown", "value", rest[0]);
            }
            case "setgametime" -> {
                if (rest.length < 1) {
                    msg(sender, "admin.usage", "usage", "/minigame arena setgametime <game> <arena> <seconds>");
                    return;
                }
                arena.setGameTime(Integer.parseInt(rest[0]));
                msg(sender, "admin.value-set", "key", "game-time", "value", rest[0]);
            }
            case "setvoid" -> {
                int y = rest.length > 0 ? Integer.parseInt(rest[0]) : (p != null ? p.getLocation().getBlockY() : 0);
                arena.getYaml().set("void-y", y);
                msg(sender, "admin.value-set", "key", "void-y", "value", String.valueOf(y));
            }
            case "setreset" -> {
                if (rest.length < 1 || !List.of("blocks", "world-copy", "none").contains(rest[0].toLowerCase(Locale.ROOT))) {
                    msg(sender, "admin.usage", "usage", "/minigame arena setreset <game> <arena> <blocks|world-copy|none>");
                    return;
                }
                arena.getYaml().set("reset-mode", rest[0].toLowerCase(Locale.ROOT));
                msg(sender, "admin.value-set", "key", "reset-mode", "value", rest[0]);
                if (rest[0].equalsIgnoreCase("world-copy")) {
                    msg(sender, "admin.world-copy-hint", "world", arena.getWorldName());
                }
            }
            case "setregion" -> {
                if (!requirePlayer(sender)) {
                    return;
                }
                if (rest.length < 1) {
                    msg(sender, "admin.usage", "usage", "/minigame arena setregion <game> <arena> <key>  (select with /minigame pos1 & pos2)");
                    return;
                }
                Cuboid sel = arenas.getSelection(p);
                if (sel == null) {
                    msg(sender, "admin.no-selection");
                    return;
                }
                arena.setRegion(rest[0], sel);
                if (arena.getWorldName().isEmpty()) {
                    arena.setWorldName(sel.world);
                }
                msg(sender, "admin.region-set", "key", rest[0], "volume", String.valueOf(sel.volume()));
            }
            case "tp" -> {
                changed = false;
                if (!requirePlayer(sender)) {
                    return;
                }
                Location l = arena.getLobby();
                if (l == null) {
                    World w = arena.getWorld();
                    l = w == null ? null : w.getSpawnLocation();
                }
                if (l == null) {
                    msg(sender, "admin.world-missing", "world", arena.getWorldName());
                } else {
                    p.teleport(l);
                }
            }
            case "enable" -> {
                List<String> problems = mg.validateArena(arena);
                if (!problems.isEmpty()) {
                    msg(sender, "admin.arena-invalid", "arena", arena.getName());
                    for (String pr : problems) {
                        msg(sender, "admin.problem", "problem", pr);
                    }
                    return;
                }
                arena.setEnabled(true);
                msg(sender, "admin.arena-enabled", "arena", arena.getName());
            }
            case "disable" -> {
                mg.stop(arena.getName());
                arena.setEnabled(false);
                msg(sender, "admin.arena-disabled", "arena", arena.getName());
            }
            default -> {
                changed = false;
                if (!mg.handleArenaCommand(sender, sub, arena, rest)) {
                    msg(sender, "admin.arena-help");
                } else {
                    changed = true; // module commands usually modify the arena; saving is cheap
                }
            }
        }
        if (changed) {
            arenas.save(arena);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // /minigame world ...
    // ------------------------------------------------------------------------------------------------

    private void handleWorld(CommandSender sender, String[] a) {
        if (!perm(sender, "admin-world")) {
            return;
        }
        if (a.length == 0) {
            msg(sender, "admin.world-help");
            return;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                StringBuilder sb = new StringBuilder();
                for (World w : Bukkit.getWorlds()) {
                    sb.append(sb.length() == 0 ? "" : ", ").append(w.getName());
                }
                msg(sender, "admin.world-list", "worlds", sb.toString());
            }
            case "create" -> {
                if (a.length < 2) {
                    msg(sender, "admin.usage", "usage", "/minigame world create <name>");
                    return;
                }
                if (!a[1].matches("[A-Za-z0-9_\\-]{1,32}")) {
                    msg(sender, "admin.invalid-name");
                    return;
                }
                World w = plugin.getWorldService().createVoid(a[1]);
                msg(sender, w == null ? "admin.world-failed" : "admin.world-created", "world", a[1]);
            }
            case "load" -> {
                if (a.length < 2) {
                    msg(sender, "admin.usage", "usage", "/minigame world load <name>");
                    return;
                }
                World w = plugin.getWorldService().getOrLoad(a[1]);
                if (w == null) {
                    msg(sender, "admin.world-load-failed", "world", a[1], "error", plugin.getWorldService().getLastError());
                } else {
                    msg(sender, "admin.world-loaded", "world", a[1]);
                }
            }
            case "tp" -> {
                if (!requirePlayer(sender) || a.length < 2) {
                    return;
                }
                World w = plugin.getWorldService().getOrLoad(a[1]);
                if (w == null) {
                    msg(sender, "admin.world-missing", "world", a[1]);
                } else {
                    ((Player) sender).teleport(w.getSpawnLocation());
                }
            }
            default -> msg(sender, "admin.world-help");
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Tab completion
    // ------------------------------------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (!plugin.getPermissions().has(sender, "admin-arena") && !plugin.getPermissions().has(sender, "admin-reload")) {
            return out;
        }
        if (args.length == 1) {
            out.addAll(ROOT);
        } else {
            String root = args[0].toLowerCase(Locale.ROOT);
            switch (root) {
                case "forcestart", "stop" -> {
                    if (args.length == 2) {
                        gameIds(out);
                    } else if (args.length == 3) {
                        arenaNames(out, args[1]);
                    }
                }
                case "world" -> {
                    if (args.length == 2) {
                        out.addAll(List.of("list", "create", "load", "tp"));
                    } else if (args.length == 3) {
                        for (World w : Bukkit.getWorlds()) {
                            out.add(w.getName());
                        }
                    }
                }
                case "arena" -> arenaTab(out, args);
                case "stats", "addcoins" -> {
                    if (args.length == 2) {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            out.add(p.getName());
                        }
                    }
                }
                default -> {
                }
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }

    private void arenaTab(List<String> out, String[] args) {
        if (args.length == 2) {
            out.addAll(ARENA_SUBS);
            for (Minigame mg : plugin.getMinigameManager().getAll()) {
                out.addAll(mg.getArenaSubcommands());
            }
            return;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        if (args.length == 3) {
            gameIds(out);
            return;
        }
        if (args.length == 4 && !sub.equals("create") && !sub.equals("list")) {
            arenaNames(out, args[2]);
            return;
        }
        Minigame mg = plugin.getMinigameManager().get(args[2]);
        if (mg == null) {
            return;
        }
        if (args.length == 5) {
            switch (sub) {
                case "setspawn" -> {
                    for (TeamColor c : TeamColor.values()) {
                        out.add(c.name().toLowerCase(Locale.ROOT));
                    }
                }
                case "setmode" -> mg.getModes().forEach(m -> out.add(m.getId()));
                case "setreset" -> out.addAll(List.of("blocks", "world-copy", "none"));
                case "setworld" -> Bukkit.getWorlds().forEach(w -> out.add(w.getName()));
                default -> {
                    Arena arena = plugin.getArenaManager().get(mg.getId(), args[3]);
                    if (arena != null) {
                        out.addAll(mg.tabCompleteArena(sub, arena, Arrays.copyOfRange(args, 4, args.length)));
                    }
                }
            }
        } else if (args.length > 5) {
            Arena arena = plugin.getArenaManager().get(mg.getId(), args[3]);
            if (arena != null) {
                out.addAll(mg.tabCompleteArena(sub, arena, Arrays.copyOfRange(args, 4, args.length)));
            }
        }
    }

    private void gameIds(List<String> out) {
        for (Minigame mg : plugin.getMinigameManager().getAll()) {
            out.add(mg.getId());
        }
    }

    private void arenaNames(List<String> out, String game) {
        for (Arena a : plugin.getArenaManager().getArenas(game)) {
            out.add(a.getName());
        }
    }
}

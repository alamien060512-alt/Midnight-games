package dev.midnight.minigames.minigames.dropper;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.game.AbstractArenaMinigame;
import dev.midnight.minigames.core.game.GameInstance;
import dev.midnight.minigames.core.game.ModeDefinition;
import dev.midnight.minigames.util.Cuboid;
import dev.midnight.minigames.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Dropper: fall through each level without touching anything and land in the finish zone. First to finish all levels wins.
 * Levels are stored per arena (data.levels, "start|finishRegion"); set them with /minigame arena addlevel dropper &lt;arena&gt;.
 */
public final class DropperMinigame extends AbstractArenaMinigame {

    /** One level: where players start and the region that counts as landed. */
    record Level(Location start, Cuboid finish) {
    }

    public DropperMinigame(MidnightMinigames plugin) {
        super(plugin, "dropper", "<aqua>Dropper");
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected GameInstance createInstance(Arena arena, ModeDefinition mode) {
        return new DropperGame(plugin, this, arena, mode);
    }

    static List<Level> levelsOf(Arena arena) {
        List<Level> out = new ArrayList<>();
        for (String raw : arena.getYaml().getStringList("data.levels")) {
            int sep = raw.indexOf('|');
            if (sep < 0) {
                continue;
            }
            Location start = LocationUtil.deserialize(raw.substring(0, sep));
            Cuboid finish = Cuboid.deserialize(raw.substring(sep + 1));
            if (start != null && finish != null) {
                out.add(new Level(start, finish));
            }
        }
        return out;
    }

    @Override
    public List<String> getArenaSubcommands() {
        return List.of("addlevel", "dellevel", "clearlevels", "listlevels");
    }

    @Override
    public boolean handleArenaCommand(CommandSender sender, String sub, Arena arena, String[] args) {
        var m = plugin.getMessages();
        List<String> raw = new ArrayList<>(arena.getYaml().getStringList("data.levels"));
        switch (sub) {
            case "addlevel" -> {
                if (!(sender instanceof Player p)) {
                    m.send(sender, "general.players-only");
                    return true;
                }
                Cuboid sel = plugin.getArenaManager().getSelection(p);
                if (sel == null) {
                    m.send(sender, "dropper.need-selection");
                    return true;
                }
                raw.add(LocationUtil.serialize(p.getLocation()) + "|" + sel.serialize());
                arena.getYaml().set("data.levels", raw);
                if (arena.getWorldName().isEmpty()) {
                    arena.setWorldName(p.getWorld().getName());
                }
                m.send(sender, "dropper.level-added", "number", String.valueOf(raw.size()));
            }
            case "dellevel" -> {
                int n;
                try {
                    n = Integer.parseInt(args.length > 0 ? args[0] : "");
                } catch (NumberFormatException ex) {
                    m.send(sender, "admin.usage", "usage", "/minigame arena dellevel dropper <arena> <number>");
                    return true;
                }
                if (n < 1 || n > raw.size()) {
                    m.send(sender, "dropper.no-such-level", "number", String.valueOf(n));
                    return true;
                }
                raw.remove(n - 1);
                arena.getYaml().set("data.levels", raw);
                m.send(sender, "dropper.level-removed", "number", String.valueOf(n));
            }
            case "clearlevels" -> {
                arena.getYaml().set("data.levels", null);
                m.send(sender, "dropper.levels-cleared");
            }
            case "listlevels" -> {
                m.send(sender, "dropper.levels-list", "count", String.valueOf(raw.size()));
                int i = 1;
                for (Level l : levelsOf(arena)) {
                    sender.sendMessage("  " + i++ + ": start " + l.start().getBlockX() + " " + l.start().getBlockY() + " "
                            + l.start().getBlockZ() + ", finish " + l.finish().serialize());
                }
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
        if (levelsOf(arena).isEmpty()) {
            problems.add("No levels: stand at the top of a level, select its landing zone with /minigame pos1 & pos2, then "
                    + "/minigame arena addlevel dropper " + arena.getName());
        }
        return problems;
    }
}

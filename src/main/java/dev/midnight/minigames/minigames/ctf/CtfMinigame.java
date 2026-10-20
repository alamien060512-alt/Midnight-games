package dev.midnight.minigames.minigames.ctf;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.game.AbstractArenaMinigame;
import dev.midnight.minigames.core.game.GameInstance;
import dev.midnight.minigames.core.game.ModeDefinition;
import dev.midnight.minigames.minigames.teamfight.TeamFightGame;
import dev.midnight.minigames.util.LocationUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Capture the Flag: steal the enemy flag and bring it back to your own base while your flag is safe.
 * Arena setup: red + blue spawn and /minigame arena setflag ctf &lt;arena&gt; &lt;red|blue&gt;, see ARENAS.md.
 */
public final class CtfMinigame extends AbstractArenaMinigame {

    public CtfMinigame(MidnightMinigames plugin) {
        super(plugin, "ctf", "<red>Capture the Flag");
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected GameInstance createInstance(Arena arena, ModeDefinition mode) {
        return new CtfGame(plugin, this, arena, mode);
    }

    @Override
    public List<String> getArenaSubcommands() {
        return List.of("setflag");
    }

    @Override
    public List<String> tabCompleteArena(String sub, Arena arena, String[] args) {
        return sub.equals("setflag") && args.length == 1 ? TeamFightGame.TEAM_IDS : List.of();
    }

    @Override
    public boolean handleArenaCommand(CommandSender sender, String sub, Arena arena, String[] args) {
        if (!sub.equals("setflag")) {
            return false;
        }
        var m = plugin.getMessages();
        if (!(sender instanceof Player p)) {
            m.send(sender, "general.players-only");
            return true;
        }
        if (args.length < 1 || !TeamFightGame.TEAM_IDS.contains(args[0].toLowerCase(Locale.ROOT))) {
            m.send(sender, "admin.usage", "usage", "/minigame arena setflag ctf <arena> <red|blue>  (stand where the flag goes)");
            return true;
        }
        String team = args[0].toLowerCase(Locale.ROOT);
        arena.getYaml().set("data.flags." + team, LocationUtil.serializeBlock(p.getLocation().getBlock()));
        if (arena.getWorldName().isEmpty()) {
            arena.setWorldName(p.getWorld().getName());
        }
        m.send(sender, "ctf.flag-set", "team", team);
        return true;
    }

    @Override
    public List<String> validateArena(Arena arena) {
        List<String> problems = new ArrayList<>(super.validateArena(arena));
        problems.addAll(TeamFightGame.validateTeams(arena));
        for (String t : TeamFightGame.TEAM_IDS) {
            if (LocationUtil.deserialize(arena.getYaml().getString("data.flags." + t)) == null) {
                problems.add("Flag for team '" + t + "' missing: stand on its spot and run /minigame arena setflag ctf "
                        + arena.getName() + " " + t);
            }
        }
        return problems;
    }
}

package dev.midnight.minigames.commands;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.game.Minigame;
import dev.midnight.minigames.core.game.ModeDefinition;
import org.bukkit.Bukkit;
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

/**
 * Generic per-game command (/bw, /sw, /ctf ...). Designed for NPC plugins:
 * <ul>
 *   <li>{@code /bw} opens the BedWars mode menu,</li>
 *   <li>{@code /bw join <mode>} queues the player directly (no GUI),</li>
 *   <li>other arguments are passed to the minigame (e.g. {@code /duels elo}).</li>
 * </ul>
 * NPC plugins that run commands from the console can append the player: {@code /bw join solo %player%}
 * or {@code /bw <player>} (console or midnight.admin.arena only).
 */
public final class GameCommand implements CommandExecutor, TabCompleter {

    private final MidnightMinigames plugin;
    private final String gameId;

    public GameCommand(MidnightMinigames plugin, String gameId) {
        this.plugin = plugin;
        this.gameId = gameId;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] rawArgs) {
        String[] args = rawArgs;
        Player player = sender instanceof Player p ? p : null;
        // "run as console" support: the last argument may name the player to act on
        boolean mayTarget = player == null || plugin.getPermissions().has(sender, "admin-arena");
        if (mayTarget && args.length > 0) {
            Player named = Bukkit.getPlayerExact(args[args.length - 1]);
            if (named != null) {
                player = named;
                args = Arrays.copyOf(args, args.length - 1);
            }
        }
        if (player == null) {
            plugin.getMessages().send(sender, "general.players-only");
            return true;
        }
        Minigame mg = plugin.getMinigameManager().get(gameId);
        if (mg == null || !mg.isEnabled()) {
            plugin.getMessages().send(player, "join.result.disabled", "game", gameId);
            return true;
        }
        if (!plugin.getPermissions().canPlay(player, gameId)) {
            plugin.getMessages().send(player, "join.result.no_permission");
            return true;
        }
        if (args.length >= 1) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("join") || sub.equals("play")) {
                String mode = args.length >= 2 ? args[1] : (mg.getModes().isEmpty() ? null : mg.getModes().get(0).getId());
                if (mode == null) {
                    plugin.getMessages().send(player, "general.unknown-mode", "mode", "?");
                    return true;
                }
                boolean known = false;
                for (ModeDefinition m : mg.getModes()) {
                    known |= m.getId().equalsIgnoreCase(mode);
                }
                if (!known) {
                    plugin.getMessages().send(player, "general.unknown-mode", "mode", mode);
                    return true;
                }
                plugin.getQueueManager().enqueue(player, gameId, mode.toLowerCase(Locale.ROOT));
                return true;
            }
            if (sub.equals("leave")) {
                if (!mg.leave(player)) {
                    plugin.getQueueManager().removePlayer(player.getUniqueId());
                }
                return true;
            }
            if (!sub.equals("open") && !sub.equals("menu") && mg.handleGameCommand(player, args)) {
                return true;
            }
        } else if (mg.handleGameCommand(player, args)) {
            return true;
        }
        plugin.getMenus().openGame(player, gameId);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        Minigame mg = plugin.getMinigameManager().get(gameId);
        if (mg == null) {
            return out;
        }
        if (args.length == 1) {
            out.add("join");
            out.add("leave");
            out.add("open");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            for (ModeDefinition m : mg.getModes()) {
                out.add(m.getId());
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}

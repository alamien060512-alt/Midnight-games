package dev.midnight.minigames.commands;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.game.PlayerContext;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /spectate &lt;player&gt; - watch a running game that supports spectators. */
public final class SpectateCommand implements CommandExecutor, TabCompleter {

    private final MidnightMinigames plugin;

    public SpectateCommand(MidnightMinigames plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "general.players-only");
            return true;
        }
        if (!plugin.getPermissions().has(player, "spectate")) {
            plugin.getMessages().send(player, "general.no-permission");
            return true;
        }
        if (args.length < 1) {
            plugin.getMessages().send(player, "spectate.usage");
            return true;
        }
        if (plugin.getPlayerManager().isInGame(player)) {
            plugin.getMessages().send(player, "join.result.already_in_game");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        PlayerContext ctx = target == null ? null : plugin.getPlayerManager().getContext(target);
        if (target == null || ctx == null) {
            plugin.getMessages().send(player, "spectate.not-playing", "player", args[0]);
            return true;
        }
        if (!ctx.getMinigame().spectate(player, target)) {
            plugin.getMessages().send(player, "spectate.failed");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (plugin.getPlayerManager().isInGame(p) && p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(p.getName());
                }
            }
        }
        return out;
    }
}

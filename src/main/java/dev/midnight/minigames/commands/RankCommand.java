package dev.midnight.minigames.commands;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.rank.RankMenu;
import dev.midnight.minigames.core.rank.RankService;
import dev.midnight.minigames.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** /ranks opens the rank shop, /ranks info shows your rank, /ranks set &lt;player&gt; &lt;rank|none&gt; is for admins. */
public final class RankCommand implements CommandExecutor, TabCompleter {

    private final MidnightMinigames plugin;

    public RankCommand(MidnightMinigames plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        var m = plugin.getMessages();
        String sub = args.length == 0 ? "" : args[0].toLowerCase();
        if (sub.equals("set")) {
            if (!plugin.getPermissions().has(sender, "admin-stats")) {
                m.send(sender, "general.no-permission");
                return true;
            }
            if (args.length < 3) {
                m.send(sender, "admin.usage", "usage", "/ranks set <player> <rank|none>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                m.send(sender, "general.player-not-found", "player", args[1]);
                return true;
            }
            RankService.Rank rank = args[2].equalsIgnoreCase("none") ? null : plugin.getRanks().find(args[2]);
            if (rank == null && !args[2].equalsIgnoreCase("none")) {
                m.send(sender, "ranks.unknown", "rank", args[2]);
                return true;
            }
            plugin.getRanks().set(target, rank);
            m.send(sender, "ranks.set", "player", target.getName(), "rank", rank == null ? "-" : TextUtil.plain(TextUtil.parse(rank.name)));
            return true;
        }
        if (!(sender instanceof Player p)) {
            m.send(sender, "general.players-only");
            return true;
        }
        if (!sender.hasPermission("midnight.ranks")) {
            m.send(sender, "general.no-permission");
            return true;
        }
        if (sub.equals("info")) {
            RankService.Rank r = plugin.getRanks().getRank(p);
            m.send(sender, "ranks.info", "rank", r == null ? "-" : TextUtil.plain(TextUtil.parse(r.name)),
                    "multiplier", String.format(java.util.Locale.ROOT, "%.2f", plugin.getRanks().multiplier(p)));
            return true;
        }
        new RankMenu(plugin, p).open(p);
        plugin.getEffects().play(p, "menu-open");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("info");
            if (plugin.getPermissions().has(sender, "admin-stats")) {
                out.add("set");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            out.add("none");
            plugin.getRanks().getRanks().forEach(r -> out.add(r.id));
        }
        return out;
    }
}

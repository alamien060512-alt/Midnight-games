package dev.midnight.minigames.commands;

import dev.midnight.minigames.MidnightMinigames;
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

/** /party create | invite &lt;player&gt; | accept | leave | disband | kick &lt;player&gt; | list */
public final class PartyCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("create", "invite", "accept", "leave", "disband", "kick", "list");

    private final MidnightMinigames plugin;

    public PartyCommand(MidnightMinigames plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "general.players-only");
            return true;
        }
        if (!plugin.getPermissions().has(player, "party")) {
            plugin.getMessages().send(player, "general.no-permission");
            return true;
        }
        if (args.length == 0) {
            plugin.getMessages().send(player, "party.help");
            return true;
        }
        var parties = plugin.getPartyManager();
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> parties.create(player);
            case "accept" -> parties.accept(player);
            case "leave" -> parties.leave(player);
            case "disband" -> parties.disband(player);
            case "list" -> parties.list(player);
            case "invite" -> {
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                if (target == null) {
                    plugin.getMessages().send(player, "general.player-not-found", "player", args.length > 1 ? args[1] : "?");
                } else {
                    parties.invite(player, target);
                }
            }
            case "kick" -> {
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                if (target == null) {
                    plugin.getMessages().send(player, "general.player-not-found", "player", args.length > 1 ? args[1] : "?");
                } else {
                    parties.kick(player, target);
                }
            }
            default -> {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target != null) {
                    parties.invite(player, target);
                } else {
                    plugin.getMessages().send(player, "party.help");
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(SUBS);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("kick"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                out.add(p.getName());
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}

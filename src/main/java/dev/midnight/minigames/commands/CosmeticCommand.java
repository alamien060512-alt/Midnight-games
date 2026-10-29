package dev.midnight.minigames.commands;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.cosmetic.CosmeticMenu;
import dev.midnight.minigames.core.cosmetic.CosmeticService;
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

/** /cosmetic opens the cosmetic shop. Admins: /cosmetic give|take &lt;player&gt; &lt;category.id&gt;. */
public final class CosmeticCommand implements CommandExecutor, TabCompleter {

    private final MidnightMinigames plugin;

    public CosmeticCommand(MidnightMinigames plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        var m = plugin.getMessages();
        if (args.length == 0 || !(args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take"))) {
            if (!(sender instanceof Player p)) {
                m.send(sender, "general.players-only");
            } else if (!sender.hasPermission("midnight.cosmetic")) {
                m.send(sender, "general.no-permission");
            } else {
                new CosmeticMenu(plugin, p).open(p);
                plugin.getEffects().play(p, "menu-open");
            }
            return true;
        }
        if (!plugin.getPermissions().has(sender, "admin-stats")) {
            m.send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 3) {
            m.send(sender, "admin.usage", "usage", "/cosmetic " + args[0].toLowerCase(Locale.ROOT) + " <player> <category.id>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            m.send(sender, "general.player-not-found", "player", args[1]);
            return true;
        }
        CosmeticService.Item item = plugin.getCosmetics().find(args[2]);
        if (item == null) {
            m.send(sender, "cosmetic.unknown", "item", args[2]);
            return true;
        }
        if (args[0].equalsIgnoreCase("give")) {
            plugin.getCosmetics().grant(target, item);
            m.send(sender, "cosmetic.given", "item", item.key(), "player", target.getName());
        } else {
            plugin.getCosmetics().revoke(target, item);
            m.send(sender, "cosmetic.taken", "item", item.key(), "player", target.getName());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (!plugin.getPermissions().has(sender, "admin-stats")) {
            return out;
        }
        if (args.length == 1) {
            out.addAll(List.of("give", "take"));
        } else if (args.length == 2) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (args.length == 3) {
            for (CosmeticService.Category c : plugin.getCosmetics().getCategories()) {
                for (CosmeticService.Item i : c.items) {
                    out.add(i.key());
                }
            }
        }
        return out;
    }
}

package dev.midnight.minigames.commands;

import dev.midnight.minigames.MidnightMinigames;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /games - opens the main menu (same as the compass). NPC friendly: {@code /games <player>} lets the console
 * (or midnight.admin.arena) open it for someone else.
 */
public final class MenuCommand implements CommandExecutor {

    private final MidnightMinigames plugin;

    public MenuCommand(MidnightMinigames plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        Player target = sender instanceof Player p ? p : null;
        if (args.length > 0 && (target == null || plugin.getPermissions().has(sender, "admin-arena"))) {
            Player named = Bukkit.getPlayerExact(args[args.length - 1]);
            if (named != null) {
                target = named;
            }
        }
        if (target == null) {
            plugin.getMessages().send(sender, "general.players-only");
            return true;
        }
        plugin.getMenus().openMain(target);
        return true;
    }
}

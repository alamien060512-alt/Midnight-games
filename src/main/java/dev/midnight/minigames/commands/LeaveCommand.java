package dev.midnight.minigames.commands;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.game.PlayerContext;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** /leave - leave the queue, the current minigame, or go to the main lobby. */
public final class LeaveCommand implements CommandExecutor {

    private final MidnightMinigames plugin;

    public LeaveCommand(MidnightMinigames plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "general.players-only");
            return true;
        }
        PlayerContext ctx = plugin.getPlayerManager().getContext(player);
        if (ctx != null) {
            ctx.getMinigame().leave(player);
            return true;
        }
        if (plugin.getQueueManager().isQueued(player.getUniqueId())) {
            plugin.getQueueManager().removePlayer(player.getUniqueId());
            return true;
        }
        Location lobby = plugin.getLobbyService().getMainLobby();
        if (lobby != null) {
            player.teleport(lobby);
            plugin.getMessages().send(player, "general.to-lobby");
        } else {
            plugin.getMessages().send(player, "general.not-in-game");
        }
        return true;
    }
}

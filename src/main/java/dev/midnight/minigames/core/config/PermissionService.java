package dev.midnight.minigames.core.config;

import dev.midnight.minigames.MidnightMinigames;
import org.bukkit.command.CommandSender;

/**
 * Resolves logical permission keys (permissions.yml -&gt; nodes) to real permission nodes, so server owners can
 * remap every permission. Holding the configured "admin" node grants every admin-* key.
 */
public final class PermissionService {

    private final MidnightMinigames plugin;

    public PermissionService(MidnightMinigames plugin) {
        this.plugin = plugin;
    }

    public String node(String key) {
        String configured = plugin.getConfigurationManager().permissions().getString("nodes." + key);
        return configured != null ? configured : "midnight." + key.replace('-', '.');
    }

    public boolean has(CommandSender sender, String key) {
        if (sender.hasPermission(node(key))) {
            return true;
        }
        return key.startsWith("admin-") && sender.hasPermission(node("admin"));
    }

    public boolean canPlay(CommandSender sender, String gameId) {
        if (!has(sender, "play")) {
            return false;
        }
        String node = plugin.getConfigurationManager().permissions().getString("nodes.play-game", "midnight.play.{game}")
                .replace("{game}", gameId);
        // Games that never declared a permission node (e.g. added by other plugins) are open to everyone.
        if (org.bukkit.Bukkit.getPluginManager().getPermission(node) == null) {
            return true;
        }
        return sender.hasPermission(node);
    }
}

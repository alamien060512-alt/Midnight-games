package dev.midnight.minigames.core.gui;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.game.Minigame;
import org.bukkit.entity.Player;

/** Opens the configurable compass menus. */
public final class MenuService {

    private final MidnightMinigames plugin;

    public MenuService(MidnightMinigames plugin) {
        this.plugin = plugin;
    }

    public void openMain(Player player) {
        if (!plugin.getPermissions().has(player, "menu")) {
            plugin.getMessages().send(player, "general.no-permission");
            return;
        }
        new MainMenu(plugin).open(player);
        plugin.getEffects().play(player, "menu-open");
    }

    public void openGame(Player player, String gameId) {
        if (!plugin.getPermissions().has(player, "menu")) {
            plugin.getMessages().send(player, "general.no-permission");
            return;
        }
        Minigame mg = plugin.getMinigameManager().get(gameId);
        if (mg == null || !mg.isEnabled()) {
            plugin.getMessages().send(player, "join.result.disabled", "game", gameId);
            return;
        }
        new ModeMenu(plugin, mg).open(player);
        plugin.getEffects().play(player, "menu-open");
    }
}

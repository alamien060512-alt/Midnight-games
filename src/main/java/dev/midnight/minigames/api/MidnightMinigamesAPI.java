package dev.midnight.minigames.api;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.display.TabService;
import dev.midnight.minigames.core.game.Minigame;
import dev.midnight.minigames.core.game.PlayerContext;
import dev.midnight.minigames.core.player.PlayerStats;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Static entry point for other plugins.
 *
 * <pre>
 * Minigame bw = MidnightMinigamesAPI.getMinigame("bedwars");
 * Arena arena = MidnightMinigamesAPI.getArena("bedwars", "solo_1");
 * PlayerStats stats = MidnightMinigamesAPI.getPlayerStats(player);
 * </pre>
 *
 * Events (MinigameJoinEvent, MinigameLeaveEvent, MinigameStartEvent, MinigameEndEvent, PlayerEliminateEvent,
 * ArenaResetEvent) live in {@code dev.midnight.minigames.api.events}. Everything here must be called from the
 * main server thread.
 */
public final class MidnightMinigamesAPI {

    private MidnightMinigamesAPI() {
    }

    private static MidnightMinigames plugin() {
        MidnightMinigames p = MidnightMinigames.getInstance();
        if (p == null) {
            throw new IllegalStateException("MidnightMinigames is not enabled");
        }
        return p;
    }

    public static boolean isAvailable() {
        return MidnightMinigames.getInstance() != null;
    }

    @Nullable
    public static Minigame getMinigame(String id) {
        return plugin().getMinigameManager().get(id);
    }

    public static Collection<Minigame> getMinigames() {
        return plugin().getMinigameManager().getAll();
    }

    /** Registers a custom minigame (see the README section "Adding a minigame"). */
    public static boolean registerMinigame(Minigame minigame) {
        return plugin().getMinigameManager().register(minigame);
    }

    public static void unregisterMinigame(String id) {
        plugin().getMinigameManager().unregister(id);
    }

    @Nullable
    public static Arena getArena(String gameId, String arenaName) {
        return plugin().getArenaManager().get(gameId, arenaName);
    }

    public static Collection<Arena> getArenas(String gameId) {
        return plugin().getArenaManager().getArenas(gameId);
    }

    /** Live stats object of an online player (modifications are saved automatically). */
    public static PlayerStats getPlayerStats(Player player) {
        return plugin().getPlayerManager().getStats(player);
    }

    public static PlayerStats getPlayerStats(UUID uuid) {
        return plugin().getPlayerManager().getProfile(uuid).getStats();
    }

    /** The minigame the player is currently in, or null. */
    @Nullable
    public static Minigame getCurrentMinigame(Player player) {
        return plugin().getPlayerManager().getMinigame(player);
    }

    @Nullable
    public static PlayerContext getContext(Player player) {
        return plugin().getPlayerManager().getContext(player);
    }

    public static boolean isInMinigame(Player player) {
        return plugin().getPlayerManager().isInGame(player);
    }

    /** Joins the player (or their party) to a game mode, queueing if needed. */
    public static void queue(Player player, String gameId, String modeId) {
        plugin().getQueueManager().enqueue(player, gameId, modeId);
    }

    public static void addTabProvider(TabService.TabProvider provider) {
        plugin().getTabService().addProvider(provider);
    }

    public static List<String> getMinigameIds() {
        return plugin().getMinigameManager().getAll().stream().map(Minigame::getId).toList();
    }
}

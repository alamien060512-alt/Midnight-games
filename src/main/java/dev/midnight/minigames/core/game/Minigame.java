package dev.midnight.minigames.core.game;

import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.placeholder.PlaceholderRegistry;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * A minigame module. Implement this (usually by extending {@code AbstractArenaMinigame} or
 * {@code AbstractPersistentMinigame}) and register it with {@code MinigameManager#register}
 * to add a new game without touching the core.
 */
public interface Minigame {

    String getId();

    String getDisplayName();

    boolean isEnabled();

    /** Modes shown in the mode GUI. May be dynamic (e.g. Parkour courses). */
    List<ModeDefinition> getModes();

    /** Called once after registration. Load config, start module services. */
    void enable();

    /** Called on plugin disable / unregister. Must stop every game and release resources. */
    void disable();

    /** Called after /minigame reload so the module can re-read its configuration. */
    default void reload() {
    }

    /** Force-start the game running on the given arena. */
    boolean start(String arenaName);

    /** Force-stop the game running on the given arena. */
    boolean stop(String arenaName);

    JoinResult join(Player player, String modeId);

    /** Joins a party / group together; all players end up in the same game or none do. */
    default JoinResult joinGroup(Collection<Player> players, String modeId) {
        JoinResult last = JoinResult.ERROR;
        for (Player p : players) {
            last = join(p, modeId);
        }
        return last;
    }

    /** Joins as a spectator of the game the target is in. */
    default boolean spectate(Player spectator, Player target) {
        return false;
    }

    boolean leave(Player player);

    @Nullable
    Arena createArena(String name);

    boolean deleteArena(String name);

    /** Called by the core when a player would die (damage is cancelled beforehand). */
    void handlePlayerDeath(Player victim, @Nullable Player killer, EntityDamageEvent.DamageCause cause);

    void handlePlayerQuit(Player player);

    /** Called when a player joins the server (modules may restore state). */
    default void handlePlayerJoin(Player player) {
    }

    /** State of the game on one arena (WAITING when nothing is running). */
    GameState getState(String arenaName);

    /** Aggregate state: highest-activity state across all arenas. */
    GameState getState();

    Collection<Player> getPlayers();

    int getMaxPlayers();

    default boolean supportsSpectators() {
        return false;
    }

    /** Game-specific admin sub commands after /minigame arena (e.g. setbed, addgen). Return true if handled. */
    default boolean handleArenaCommand(CommandSender sender, String sub, Arena arena, String[] args) {
        return false;
    }

    default List<String> getArenaSubcommands() {
        return List.of();
    }

    default List<String> tabCompleteArena(String sub, Arena arena, String[] args) {
        return List.of();
    }

    /** Extra arena validation run before an arena can be enabled. Return human readable problems. */
    default List<String> validateArena(Arena arena) {
        return List.of();
    }

    /** Handles /bw, /duels ... arguments. Return false to fall back to opening the menu. */
    default boolean handleGameCommand(Player player, String[] args) {
        return false;
    }

    default void registerPlaceholders(PlaceholderRegistry registry) {
    }

    /** Whether the arena of this game is user-creatable through /minigame arena create. */
    default boolean usesArenas() {
        return true;
    }
}

package dev.midnight.minigames.core.game;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Map;

/**
 * Something a player can be "inside" (a running arena game or a persistent minigame world).
 * The single {@code CoreListener} routes events to the context of the acting player, so minigames
 * never register their own Bukkit listeners.
 */
public interface PlayerContext {

    Minigame getMinigame();

    /** Arena name or null for contexts that are not arena bound. */
    default String getArenaName() {
        return null;
    }

    default GameState getState() {
        return GameState.IN_GAME;
    }

    /** Players currently in this context (used for placeholders / scoreboards). */
    default int getPlayerCount() {
        return getMinigame().getPlayers().size();
    }

    default int getMaxPlayers() {
        return getMinigame().getMaxPlayers();
    }

    default String getModeName() {
        return "";
    }

    /** Extra scoreboard variables for this player, e.g. kills, team, time. */
    default Map<String, String> getScoreboardVariables(Player player) {
        return Map.of();
    }

    /** Resolves a game specific placeholder; null when unknown. */
    default String resolvePlaceholder(Player player, String key) {
        return null;
    }

    /** MiniMessage prefix/suffix used by the tab list (null = none). */
    default String getTabPrefix(Player player) {
        return null;
    }

    default String getTabSuffix(Player player) {
        return null;
    }

    /** Whether {@link #onMove} should be invoked (only on block changes). */
    default boolean wantsMoveEvents() {
        return false;
    }

    /** Invoked only when the player moved to a different block. May cancel the event (freeze, barriers). */
    default void onMove(Player player, PlayerMoveEvent e) {
    }

    default void onBlockBreak(Player player, BlockBreakEvent e) {
    }

    default void onBlockPlace(Player player, BlockPlaceEvent e) {
    }

    /** Called for every damage event whose victim is a player in this context. */
    default void onDamage(Player victim, EntityDamageEvent e) {
    }

    default void onDamageByEntity(Player victim, EntityDamageByEntityEvent e) {
    }

    /** Called when a player in this context attacks any entity (victim may be a mob or a player outside the game). */
    default void onAttack(Player attacker, EntityDamageByEntityEvent e) {
    }

    /** Where the player respawns after a vanilla death that slipped through; null = world spawn. */
    default Location getRespawnLocation(Player player) {
        return null;
    }

    default void onInteract(Player player, PlayerInteractEvent e) {
    }

    default void onInteractEntity(Player player, PlayerInteractEntityEvent e) {
    }

    default void onDrop(Player player, PlayerDropItemEvent e) {
    }

    default void onPickup(Player player, EntityPickupItemEvent e) {
    }

    default void onFoodChange(Player player, FoodLevelChangeEvent e) {
    }

    default void onRegen(Player player, org.bukkit.event.entity.EntityRegainHealthEvent e) {
    }

    /** Custom item action ("leave", "shop"...). Return true when handled. */
    default boolean onItemAction(Player player, String action) {
        return false;
    }

    default void onExplode(EntityExplodeEvent e) {
    }
}

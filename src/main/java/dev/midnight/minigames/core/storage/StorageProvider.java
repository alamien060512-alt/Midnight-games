package dev.midnight.minigames.core.storage;

import dev.midnight.minigames.core.player.PlayerStats;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous persistence abstraction. Implementations run every call on their own worker thread,
 * so callers on the main thread never block. Futures complete on the worker thread - hop back to the
 * main thread with the scheduler before touching Bukkit.
 */
public interface StorageProvider {

    void init() throws Exception;

    /** Flushes queued writes and closes connections. Blocks; call from onDisable only. */
    void close();

    CompletableFuture<StoredProfile> loadProfile(UUID uuid, String name);

    CompletableFuture<Void> saveProfile(UUID uuid, String name, PlayerStats stats, Map<String, Map<String, String>> data);

    /** Leaderboard over a key/value entry (e.g. namespace "parkour", key "best.course1"). */
    CompletableFuture<List<TopEntry>> topData(String namespace, String key, int limit, boolean ascending);

    /** Leaderboard over a global stat column: wins, losses, kills, deaths, coins, games_played. */
    CompletableFuture<List<TopEntry>> topStat(String column, int limit);

    String getType();
}

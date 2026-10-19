package dev.midnight.minigames.core.storage;

import dev.midnight.minigames.core.player.PlayerStats;

import java.util.Map;

/** Raw data returned by a {@link StorageProvider}. */
public record StoredProfile(PlayerStats stats, Map<String, Map<String, String>> data) {
}

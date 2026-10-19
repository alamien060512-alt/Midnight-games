package dev.midnight.minigames.core.storage;

import java.util.UUID;

/** One row of a leaderboard. */
public record TopEntry(UUID uuid, String name, double value) {
}

package dev.midnight.minigames.core.game;

/** Outcome of a join attempt. Each value maps to a message key {@code join.result.<name>}. */
public enum JoinResult {
    JOINED,
    NO_ARENA,
    FULL,
    IN_PROGRESS,
    DISABLED,
    ALREADY_IN_GAME,
    NO_PERMISSION,
    PARTY_TOO_LARGE,
    CANCELLED,
    ERROR
}

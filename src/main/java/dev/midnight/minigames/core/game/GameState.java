package dev.midnight.minigames.core.game;

/** Lifecycle of an arena-based game. Persistent minigames report IN_GAME while they have players. */
public enum GameState {
    WAITING,
    STARTING,
    IN_GAME,
    ENDING,
    RESETTING;

    /** True while new (non-spectator) players may still join. */
    public boolean isJoinable() {
        return this == WAITING || this == STARTING;
    }
}

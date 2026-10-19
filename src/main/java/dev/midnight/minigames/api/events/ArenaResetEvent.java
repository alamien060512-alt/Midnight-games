package dev.midnight.minigames.api.events;

import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.game.Minigame;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired once an arena finished resetting and is ready for the next game. */
public class ArenaResetEvent extends MinigameEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final boolean success;

    public ArenaResetEvent(Minigame minigame, Arena arena, boolean success) {
        super(minigame, arena);
        this.success = success;
    }

    /** False when the reset failed (for example a missing world template). */
    public boolean isSuccess() {
        return success;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

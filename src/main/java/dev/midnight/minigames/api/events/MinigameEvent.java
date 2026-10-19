package dev.midnight.minigames.api.events;

import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.game.Minigame;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

/** Base class of all MidnightMinigames events. */
public abstract class MinigameEvent extends Event {

    private final Minigame minigame;
    private final Arena arena;

    protected MinigameEvent(Minigame minigame, @Nullable Arena arena) {
        this.minigame = minigame;
        this.arena = arena;
    }

    public Minigame getMinigame() {
        return minigame;
    }

    /** The arena involved, or null for persistent minigames without arenas. */
    @Nullable
    public Arena getArena() {
        return arena;
    }
}

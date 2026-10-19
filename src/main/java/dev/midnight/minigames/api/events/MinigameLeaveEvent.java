package dev.midnight.minigames.api.events;

import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.game.Minigame;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Fired after a player left a minigame (including disconnects). */
public class MinigameLeaveEvent extends MinigameEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final boolean quit;

    public MinigameLeaveEvent(Player player, Minigame minigame, @Nullable Arena arena, boolean quit) {
        super(minigame, arena);
        this.player = player;
        this.quit = quit;
    }

    public Player getPlayer() {
        return player;
    }

    /** True when the player left because they disconnected. */
    public boolean isQuit() {
        return quit;
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

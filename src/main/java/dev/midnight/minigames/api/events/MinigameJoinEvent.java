package dev.midnight.minigames.api.events;

import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.game.Minigame;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Fired before a player joins a minigame. Cancel to deny the join. */
public class MinigameJoinEvent extends MinigameEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final boolean spectator;
    private boolean cancelled;

    public MinigameJoinEvent(Player player, Minigame minigame, @Nullable Arena arena, boolean spectator) {
        super(minigame, arena);
        this.player = player;
        this.spectator = spectator;
    }

    public Player getPlayer() {
        return player;
    }

    /** True when the player joins as a spectator of a running game. */
    public boolean isSpectator() {
        return spectator;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
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

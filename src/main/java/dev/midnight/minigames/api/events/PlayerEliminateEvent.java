package dev.midnight.minigames.api.events;

import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.game.Minigame;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Fired when a player is permanently eliminated from a running game. */
public class PlayerEliminateEvent extends MinigameEvent {

    /** Why a player was eliminated. */
    public enum Reason { KILLED, VOID, QUIT, FELL, TIMEOUT, OTHER }

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Player killer;
    private final Reason reason;

    public PlayerEliminateEvent(Minigame minigame, @Nullable Arena arena, Player player, @Nullable Player killer, Reason reason) {
        super(minigame, arena);
        this.player = player;
        this.killer = killer;
        this.reason = reason;
    }

    public Player getPlayer() {
        return player;
    }

    @Nullable
    public Player getKiller() {
        return killer;
    }

    public Reason getReason() {
        return reason;
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

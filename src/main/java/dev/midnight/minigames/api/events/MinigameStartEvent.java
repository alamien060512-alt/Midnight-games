package dev.midnight.minigames.api.events;

import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.game.Minigame;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Fired when a game moves from STARTING to IN_GAME. */
public class MinigameStartEvent extends MinigameEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final List<Player> players;

    public MinigameStartEvent(Minigame minigame, @Nullable Arena arena, List<Player> players) {
        super(minigame, arena);
        this.players = List.copyOf(players);
    }

    public List<Player> getPlayers() {
        return players;
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

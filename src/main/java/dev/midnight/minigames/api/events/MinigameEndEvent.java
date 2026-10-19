package dev.midnight.minigames.api.events;

import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.game.Minigame;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** Fired when a game reaches ENDING with a result. UUIDs are used because players may be offline. */
public class MinigameEndEvent extends MinigameEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final List<UUID> winners;
    private final List<UUID> participants;

    public MinigameEndEvent(Minigame minigame, @Nullable Arena arena, List<UUID> winners, List<UUID> participants) {
        super(minigame, arena);
        this.winners = List.copyOf(winners);
        this.participants = List.copyOf(participants);
    }

    public List<UUID> getWinners() {
        return winners;
    }

    public List<UUID> getParticipants() {
        return participants;
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

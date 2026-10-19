package dev.midnight.minigames.core.team;

import org.bukkit.Location;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** A team inside one running game: colour, members, spawn and elimination state. */
public final class GameTeam {

    private final String id;
    private final TeamColor color;
    private final Set<UUID> members = new LinkedHashSet<>();
    private Location spawn;
    private boolean eliminated;

    public GameTeam(String id, TeamColor color, Location spawn) {
        this.id = id.toLowerCase();
        this.color = color;
        this.spawn = spawn;
    }

    public String getId() {
        return id;
    }

    /** May be null for teams that are not one of the standard colours. */
    public TeamColor getColor() {
        return color;
    }

    public String getName() {
        return color != null ? color.getDisplayName() : id;
    }

    /** MiniMessage coloured name, e.g. {@code <red>Red}. */
    public String getColoredName() {
        return (color != null ? color.tag() : "<white>") + getName();
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public boolean hasMember(UUID id) {
        return members.contains(id);
    }

    public Location getSpawn() {
        return spawn;
    }

    public void setSpawn(Location spawn) {
        this.spawn = spawn;
    }

    public boolean isEliminated() {
        return eliminated;
    }

    public void setEliminated(boolean eliminated) {
        this.eliminated = eliminated;
    }
}

package dev.midnight.minigames.core.party;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** A group of players that queues together. */
public final class Party {

    private final UUID id = UUID.randomUUID();
    private UUID leader;
    private final Set<UUID> members = new LinkedHashSet<>();

    Party(UUID leader) {
        this.leader = leader;
        this.members.add(leader);
    }

    public UUID getId() {
        return id;
    }

    public UUID getLeader() {
        return leader;
    }

    void setLeader(UUID leader) {
        this.leader = leader;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public boolean isLeader(UUID uuid) {
        return leader.equals(uuid);
    }

    public int size() {
        return members.size();
    }
}

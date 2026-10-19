package dev.midnight.minigames.core.team;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** Distributes players over teams, keeping parties together whenever the team size allows it. */
public final class TeamBalancer {

    private TeamBalancer() {
    }

    /**
     * @param players players to place
     * @param teams   target teams (their current members count towards capacity)
     * @param teamSize maximum intended team size
     * @param partyOf  maps a player to a party id (null when not in a party)
     */
    public static void assign(List<UUID> players, List<GameTeam> teams, int teamSize, Function<UUID, UUID> partyOf) {
        if (teams.isEmpty()) {
            return;
        }
        Map<Object, List<UUID>> groups = new LinkedHashMap<>();
        for (UUID id : players) {
            UUID party = partyOf == null ? null : partyOf.apply(id);
            groups.computeIfAbsent(party != null ? party : id, k -> new ArrayList<>()).add(id);
        }
        List<List<UUID>> ordered = new ArrayList<>(groups.values());
        ordered.sort(Comparator.comparingInt((List<UUID> g) -> g.size()).reversed());

        for (List<UUID> group : ordered) {
            GameTeam best = null;
            for (GameTeam t : teams) {
                int free = teamSize - t.getMembers().size();
                if (free >= group.size() && (best == null || t.getMembers().size() < best.getMembers().size())) {
                    best = t;
                }
            }
            if (best != null) {
                best.getMembers().addAll(group);
                continue;
            }
            for (UUID id : group) { // party does not fit anywhere: split across the emptiest teams
                GameTeam emptiest = teams.get(0);
                for (GameTeam t : teams) {
                    if (t.getMembers().size() < emptiest.getMembers().size()) {
                        emptiest = t;
                    }
                }
                emptiest.getMembers().add(id);
            }
        }
    }
}

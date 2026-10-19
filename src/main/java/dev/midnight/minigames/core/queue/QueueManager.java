package dev.midnight.minigames.core.queue;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.game.JoinResult;
import dev.midnight.minigames.core.game.Minigame;
import dev.midnight.minigames.core.game.ModeDefinition;
import dev.midnight.minigames.core.party.Party;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Entry point for "I want to play X in mode Y". Players (or whole parties) are placed in a game right away
 * when an arena has room; otherwise they wait in a FIFO queue that is processed whenever an arena frees up.
 * No polling task is used - {@link #process(String)} is called by the minigames when capacity appears.
 */
public final class QueueManager {

    private static final class Entry {
        final UUID leader;
        final String gameId;
        final String modeId;
        final Set<UUID> members = new LinkedHashSet<>();

        Entry(UUID leader, String gameId, String modeId, List<Player> group) {
            this.leader = leader;
            this.gameId = gameId;
            this.modeId = modeId;
            for (Player p : group) {
                members.add(p.getUniqueId());
            }
        }
    }

    private final MidnightMinigames plugin;
    private final Map<String, Deque<Entry>> queues = new LinkedHashMap<>();
    private final Map<UUID, Entry> byPlayer = new HashMap<>();

    public QueueManager(MidnightMinigames plugin) {
        this.plugin = plugin;
    }

    public boolean isQueued(UUID id) {
        return byPlayer.containsKey(id);
    }

    public int getQueueSize(String gameId, String modeId) {
        Deque<Entry> d = queues.get(gameId + ":" + modeId);
        if (d == null) {
            return 0;
        }
        int n = 0;
        for (Entry e : d) {
            n += e.members.size();
        }
        return n;
    }

    /** Attempts to join (or queue) a player or their party for a game mode. */
    public void enqueue(Player player, String gameId, String modeId) {
        var m = plugin.getMessages();
        Minigame mg = plugin.getMinigameManager().get(gameId);
        if (mg == null || !mg.isEnabled()) {
            m.send(player, "join.result.disabled", "game", gameId);
            return;
        }
        if (!plugin.getPermissions().canPlay(player, gameId)) {
            m.send(player, "join.result.no_permission");
            return;
        }
        if (plugin.getPlayerManager().isInGame(player)) {
            m.send(player, "join.result.already_in_game");
            return;
        }
        if (isQueued(player.getUniqueId())) {
            m.send(player, "queue.already");
            return;
        }
        Party party = plugin.getPartyManager().getParty(player.getUniqueId());
        if (party != null && !party.isLeader(player.getUniqueId())) {
            m.send(player, "party.only-leader-queues");
            return;
        }
        List<Player> group = new ArrayList<>();
        for (Player p : plugin.getPartyManager().getOnlineGroup(player)) {
            if (!plugin.getPlayerManager().isInGame(p) && !isQueued(p.getUniqueId())) {
                group.add(p);
            } else if (!p.equals(player)) {
                m.send(player, "party.member-busy", "player", p.getName());
            }
        }
        JoinResult result = mg.joinGroup(group, modeId);
        switch (result) {
            case JOINED -> {
            }
            case FULL, IN_PROGRESS -> {
                if (mg.usesArenas()) {
                    Entry e = new Entry(player.getUniqueId(), gameId, modeId, group);
                    queues.computeIfAbsent(gameId + ":" + modeId, k -> new ArrayDeque<>()).addLast(e);
                    for (UUID id : e.members) {
                        byPlayer.put(id, e);
                        Player p = Bukkit.getPlayer(id);
                        if (p != null) {
                            m.send(p, "queue.joined", "game", mg.getDisplayName(), "mode", modeName(mg, modeId));
                        }
                    }
                } else {
                    m.send(player, "join.result." + result.name().toLowerCase());
                }
            }
            default -> m.send(player, "join.result." + result.name().toLowerCase(), "game", mg.getDisplayName());
        }
    }

    private String modeName(Minigame mg, String modeId) {
        for (ModeDefinition d : mg.getModes()) {
            if (d.getId().equalsIgnoreCase(modeId)) {
                return d.getDisplayName();
            }
        }
        return modeId;
    }

    public void removePlayer(UUID id) {
        Entry e = byPlayer.get(id);
        if (e == null) {
            return;
        }
        e.members.remove(id);
        byPlayer.remove(id);
        if (e.members.isEmpty()) {
            Deque<Entry> d = queues.get(e.gameId + ":" + e.modeId);
            if (d != null) {
                d.remove(e);
            }
        }
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            plugin.getMessages().send(p, "queue.left");
        }
    }

    /** Removes the queue entry of a party that was disbanded. */
    public void removeParty(Party party) {
        for (UUID id : new ArrayList<>(party.getMembers())) {
            Entry e = byPlayer.get(id);
            if (e != null) {
                removePlayer(id);
            }
        }
    }

    /** Called when an arena of the game became free / has room. */
    public void process(String gameId) {
        Minigame mg = plugin.getMinigameManager().get(gameId);
        if (mg == null) {
            return;
        }
        for (Map.Entry<String, Deque<Entry>> q : new ArrayList<>(queues.entrySet())) {
            if (!q.getKey().startsWith(gameId + ":")) {
                continue;
            }
            Deque<Entry> deque = q.getValue();
            while (!deque.isEmpty()) {
                Entry e = deque.peekFirst();
                List<Player> group = new ArrayList<>();
                for (UUID id : new ArrayList<>(e.members)) {
                    Player p = Bukkit.getPlayer(id);
                    if (p == null || plugin.getPlayerManager().isInGame(p)) {
                        e.members.remove(id);
                        byPlayer.remove(id);
                    } else {
                        group.add(p);
                    }
                }
                if (group.isEmpty()) {
                    deque.pollFirst();
                    continue;
                }
                JoinResult r = mg.joinGroup(group, e.modeId);
                if (r == JoinResult.JOINED) {
                    deque.pollFirst();
                    for (Player p : group) {
                        byPlayer.remove(p.getUniqueId());
                    }
                } else if (r != JoinResult.FULL && r != JoinResult.IN_PROGRESS) {
                    // not temporary: stop waiting and tell the players why
                    deque.pollFirst();
                    for (Player p : group) {
                        byPlayer.remove(p.getUniqueId());
                        plugin.getMessages().send(p, "join.result." + r.name().toLowerCase(), "game", mg.getDisplayName());
                    }
                } else {
                    break;
                }
            }
        }
    }
}

package dev.midnight.minigames.core.party;

import dev.midnight.minigames.MidnightMinigames;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Basic party system. Parties are in-memory only and disband when the last other member leaves. */
public final class PartyManager {

    private record Invite(Party party, long expiresAt) {
    }

    private final MidnightMinigames plugin;
    private final Map<UUID, Party> byPlayer = new HashMap<>();
    private final Map<UUID, Invite> invites = new HashMap<>();

    public PartyManager(MidnightMinigames plugin) {
        this.plugin = plugin;
    }

    public Party getParty(UUID player) {
        return byPlayer.get(player);
    }

    public UUID getPartyId(UUID player) {
        Party p = byPlayer.get(player);
        return p == null ? null : p.getId();
    }

    private int maxSize() {
        return plugin.getConfigurationManager().config().getInt("party.max-size", 4);
    }

    public void create(Player leader) {
        var m = plugin.getMessages();
        if (byPlayer.containsKey(leader.getUniqueId())) {
            m.send(leader, "party.already-in-party");
            return;
        }
        if (plugin.getPlayerManager().isInGame(leader)) {
            m.send(leader, "party.in-game");
            return;
        }
        byPlayer.put(leader.getUniqueId(), new Party(leader.getUniqueId()));
        m.send(leader, "party.created");
    }

    public void invite(Player leader, Player target) {
        var m = plugin.getMessages();
        Party party = byPlayer.get(leader.getUniqueId());
        if (party == null) {
            create(leader);
            party = byPlayer.get(leader.getUniqueId());
            if (party == null) {
                return;
            }
        }
        if (!party.isLeader(leader.getUniqueId())) {
            m.send(leader, "party.not-leader");
            return;
        }
        if (leader.equals(target)) {
            m.send(leader, "party.invite-self");
            return;
        }
        if (byPlayer.containsKey(target.getUniqueId())) {
            m.send(leader, "party.target-in-party", "player", target.getName());
            return;
        }
        if (party.size() >= maxSize()) {
            m.send(leader, "party.full", "max", String.valueOf(maxSize()));
            return;
        }
        long ttl = plugin.getConfigurationManager().config().getLong("party.invite-expire-seconds", 60) * 1000L;
        invites.put(target.getUniqueId(), new Invite(party, System.currentTimeMillis() + ttl));
        m.send(leader, "party.invite-sent", "player", target.getName());
        m.send(target, "party.invite-received", "player", leader.getName());
    }

    public void accept(Player player) {
        var m = plugin.getMessages();
        Invite inv = invites.remove(player.getUniqueId());
        if (inv == null || inv.expiresAt() < System.currentTimeMillis()) {
            m.send(player, "party.no-invite");
            return;
        }
        Party party = inv.party();
        if (byPlayer.get(party.getLeader()) != party) {
            m.send(player, "party.no-invite");
            return;
        }
        if (byPlayer.containsKey(player.getUniqueId())) {
            m.send(player, "party.already-in-party");
            return;
        }
        if (party.size() >= maxSize()) {
            m.send(player, "party.full", "max", String.valueOf(maxSize()));
            return;
        }
        broadcast(party, "party.member-joined", "player", player.getName());
        party.getMembers().add(player.getUniqueId());
        byPlayer.put(player.getUniqueId(), party);
        m.send(player, "party.joined", "leader", name(party.getLeader()));
    }

    public void leave(Player player) {
        var m = plugin.getMessages();
        Party party = byPlayer.get(player.getUniqueId());
        if (party == null) {
            m.send(player, "party.not-in-party");
            return;
        }
        if (party.isLeader(player.getUniqueId())) {
            disband(player);
            return;
        }
        removeMember(party, player.getUniqueId());
        m.send(player, "party.left");
        broadcast(party, "party.member-left", "player", player.getName());
        dissolveIfAlone(party);
    }

    public void kick(Player leader, Player target) {
        var m = plugin.getMessages();
        Party party = byPlayer.get(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            m.send(leader, "party.not-leader");
            return;
        }
        if (!party.getMembers().contains(target.getUniqueId()) || target.equals(leader)) {
            m.send(leader, "party.target-not-member", "player", target.getName());
            return;
        }
        removeMember(party, target.getUniqueId());
        m.send(target, "party.kicked");
        broadcast(party, "party.member-kicked", "player", target.getName());
        dissolveIfAlone(party);
    }

    public void disband(Player leader) {
        var m = plugin.getMessages();
        Party party = byPlayer.get(leader.getUniqueId());
        if (party == null) {
            m.send(leader, "party.not-in-party");
            return;
        }
        if (!party.isLeader(leader.getUniqueId())) {
            m.send(leader, "party.not-leader");
            return;
        }
        broadcast(party, "party.disbanded");
        for (UUID id : new ArrayList<>(party.getMembers())) {
            byPlayer.remove(id);
        }
        plugin.getQueueManager().removeParty(party);
    }

    public void list(Player player) {
        var m = plugin.getMessages();
        Party party = byPlayer.get(player.getUniqueId());
        if (party == null) {
            m.send(player, "party.not-in-party");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (UUID id : party.getMembers()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(name(id)).append(party.isLeader(id) ? "*" : "");
        }
        m.send(player, "party.list", "members", sb.toString(), "size", String.valueOf(party.size()), "max", String.valueOf(maxSize()));
    }

    /** Online party members of the player (including the player), or just the player when not in a party. */
    public List<Player> getOnlineGroup(Player player) {
        Party party = byPlayer.get(player.getUniqueId());
        List<Player> out = new ArrayList<>();
        if (party == null) {
            out.add(player);
            return out;
        }
        for (UUID id : party.getMembers()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    public void handleQuit(Player player) {
        invites.remove(player.getUniqueId());
        Party party = byPlayer.get(player.getUniqueId());
        if (party == null) {
            return;
        }
        if (party.isLeader(player.getUniqueId())) {
            for (UUID id : new ArrayList<>(party.getMembers())) {
                byPlayer.remove(id);
                Player p = Bukkit.getPlayer(id);
                if (p != null && !p.equals(player)) {
                    plugin.getMessages().send(p, "party.disbanded");
                }
            }
            plugin.getQueueManager().removeParty(party);
        } else {
            removeMember(party, player.getUniqueId());
            broadcast(party, "party.member-left", "player", player.getName());
            dissolveIfAlone(party);
        }
    }

    private void removeMember(Party party, UUID id) {
        party.getMembers().remove(id);
        byPlayer.remove(id);
    }

    private void dissolveIfAlone(Party party) {
        if (party.size() <= 1) {
            for (UUID id : new ArrayList<>(party.getMembers())) {
                byPlayer.remove(id);
            }
            plugin.getQueueManager().removeParty(party);
        }
    }

    private void broadcast(Party party, String key, String... vars) {
        for (UUID id : party.getMembers()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                plugin.getMessages().send(p, key, vars);
            }
        }
    }

    private String name(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            return p.getName();
        }
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? "?" : n;
    }
}

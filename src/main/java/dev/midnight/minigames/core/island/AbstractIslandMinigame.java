package dev.midnight.minigames.core.island;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.game.AbstractPersistentMinigame;
import dev.midnight.minigames.core.game.JoinResult;
import dev.midnight.minigames.util.Cuboid;
import dev.midnight.minigames.util.LocationUtil;
import dev.midnight.minigames.util.PlayerUtil;
import dev.midnight.minigames.util.RegionFiller;
import dev.midnight.minigames.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Container;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Shared behaviour of island based minigames (Skyblock, OneBlock, SkyGen): island creation, home, members,
 * deletion, protection. Subclasses add their own progression mechanics through the hooks.
 */
public abstract class AbstractIslandMinigame extends AbstractPersistentMinigame {

    private record Invite(UUID owner, long expires) {
    }

    protected IslandManager islands;
    private final Map<UUID, Invite> invites = new HashMap<>();
    private final Map<UUID, Long> deleteConfirm = new HashMap<>();

    protected AbstractIslandMinigame(MidnightMinigames plugin, String id, String display) {
        super(plugin, id, display);
    }

    // ---- hooks ------------------------------------------------------------------------------------------

    /** Called once the island manager is loaded (enable and reload). */
    protected void onIslandsLoaded() {
    }

    /** Called after the template was pasted. */
    protected void onIslandCreated(Player owner, Island island) {
    }

    /** Called before the island data is removed. */
    protected void onIslandDeleted(Island island) {
    }

    protected int protectionRadius(Island island) {
        return plugin.getConfigurationManager().game(getId()).getInt("island.protection-radius", 50);
    }

    protected int memberLimit(Island island) {
        return plugin.getConfigurationManager().game(getId()).getInt("island.member-limit", 3);
    }

    /** Game specific sub commands. Return true if handled. */
    protected boolean handleExtraCommand(Player player, String sub, String[] args) {
        return false;
    }

    // ---- lifecycle --------------------------------------------------------------------------------------------

    @Override
    protected void onEnable() {
        islands = new IslandManager(plugin, getId());
        islands.load();
        onIslandsLoaded();
    }

    @Override
    protected void onReload() {
        islands.saveNow();
        islands.load();
        onIslandsLoaded();
    }

    @Override
    protected void onDisable() {
        super.onDisable();
        if (islands != null) {
            islands.saveNow();
        }
    }

    public IslandManager getIslands() {
        return islands;
    }

    @Override
    protected Location resolveEntry(Player player, String modeId) {
        Island is = islands.of(player.getUniqueId());
        if (is != null) {
            Location home = islands.homeOf(is);
            if (home != null) {
                return home;
            }
            plugin.getMessages().send(player, "island.world-missing", "world", is.getWorldName());
        }
        Arena a = primaryArena();
        if (a == null) {
            plugin.getLogger().severe(getId() + " has no enabled arena. Run /minigame arena create " + getId() + " main, "
                    + "set its world and lobby (hub spawn), then enable it.");
            return null;
        }
        Location lobby = a.getLobby();
        if (lobby == null) {
            plugin.getLogger().severe(getId() + " arena " + a.getName() + " has no hub spawn (/minigame arena setlobby).");
        }
        return lobby;
    }

    @Override
    protected void onEnter(Player player, String modeId) {
        player.setGameMode(GameMode.SURVIVAL);
        if (islands.of(player.getUniqueId()) == null) {
            plugin.getMessages().send(player, "island.no-island", "command", "/" + getId() + " create");
        }
    }

    @Override
    protected void onDeathRespawn(Player victim) {
        PlayerUtil.heal(victim);
        Location l = resolveEntry(victim, "");
        if (l != null) {
            victim.teleport(l);
        }
    }

    // ---- protection -------------------------------------------------------------------------------------------------

    protected boolean canBuild(Player p, Location l) {
        if (plugin.getPermissions().has(p, "admin-build")) {
            return true;
        }
        Island is = islands.at(l);
        return is != null && is.isMember(p.getUniqueId()) && is.contains(l, protectionRadius(is));
    }

    @Override
    public void onBlockBreak(Player player, BlockBreakEvent e) {
        if (!canBuild(player, e.getBlock().getLocation())) {
            e.setCancelled(true);
            plugin.getMessages().actionBar(player, "island.cannot-build");
        }
    }

    @Override
    public void onBlockPlace(Player player, BlockPlaceEvent e) {
        if (!canBuild(player, e.getBlock().getLocation())) {
            e.setCancelled(true);
            plugin.getMessages().actionBar(player, "island.cannot-build");
        }
    }

    @Override
    public void onInteract(Player player, PlayerInteractEvent e) {
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && e.getClickedBlock().getState() instanceof Container && !canBuild(player, e.getClickedBlock().getLocation())) {
            e.setCancelled(true);
            plugin.getMessages().actionBar(player, "island.cannot-build");
        }
    }

    // ---- commands -------------------------------------------------------------------------------------------------------

    @Override
    public boolean handleGameCommand(Player player, String[] args) {
        if (args.length == 0) {
            return false;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!inside.contains(player.getUniqueId())) {
            List<String> joinFirst = List.of("create", "home", "info", "delete", "invite", "accept", "kick", "leave", "sethome");
            if (!joinFirst.contains(sub) && !isExtraNeedingNoJoin(sub)) {
                return false;
            }
            if (join(player, "play") != JoinResult.JOINED) {
                return true;
            }
        }
        var m = plugin.getMessages();
        UUID id = player.getUniqueId();
        Island is = islands.of(id);
        switch (sub) {
            case "create" -> createIsland(player);
            case "home", "go" -> {
                if (is == null) {
                    m.send(player, "island.no-island", "command", "/" + getId() + " create");
                } else {
                    Location home = islands.homeOf(is);
                    if (home == null) {
                        m.send(player, "island.world-missing", "world", is.getWorldName());
                    } else {
                        player.teleport(home);
                        m.send(player, "island.teleported");
                    }
                }
            }
            case "sethome" -> {
                if (is == null || !is.contains(player.getLocation(), protectionRadius(is))) {
                    m.send(player, "island.not-on-island");
                } else {
                    is.setHomeRaw(LocationUtil.serialize(player.getLocation()));
                    islands.markDirty();
                    m.send(player, "island.home-set");
                }
            }
            case "delete" -> deleteIsland(player, is, args.length > 1 && args[1].equalsIgnoreCase("confirm"));
            case "invite" -> {
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                if (is == null || !is.getOwner().equals(id)) {
                    m.send(player, "island.owner-only");
                } else if (target == null || target.equals(player)) {
                    m.send(player, "general.player-not-found", "player", args.length > 1 ? args[1] : "?");
                } else if (islands.of(target.getUniqueId()) != null) {
                    m.send(player, "island.target-has-island", "player", target.getName());
                } else if (is.getMembers().size() + 1 >= memberLimit(is)) {
                    m.send(player, "island.member-limit", "limit", String.valueOf(memberLimit(is)));
                } else {
                    invites.put(target.getUniqueId(), new Invite(id, System.currentTimeMillis() + 60_000L));
                    m.send(player, "island.invite-sent", "player", target.getName());
                    m.send(target, "island.invite-received", "player", player.getName(), "command", "/" + getId() + " accept");
                }
            }
            case "accept" -> {
                Invite inv = invites.remove(id);
                Island target = inv == null ? null : islands.getByOwner(inv.owner());
                if (inv == null || inv.expires() < System.currentTimeMillis() || target == null) {
                    m.send(player, "island.no-invite");
                } else if (is != null) {
                    m.send(player, "island.already-have-island");
                } else if (target.getMembers().size() + 1 >= memberLimit(target)) {
                    m.send(player, "island.member-limit", "limit", String.valueOf(memberLimit(target)));
                } else {
                    islands.addMember(target, id);
                    m.send(player, "island.joined", "owner", nameOf(target.getOwner()));
                    Player owner = Bukkit.getPlayer(target.getOwner());
                    if (owner != null) {
                        m.send(owner, "island.member-joined", "player", player.getName());
                    }
                    Location home = islands.homeOf(target);
                    if (home != null) {
                        player.teleport(home);
                    }
                }
            }
            case "leave" -> {
                if (is == null) {
                    m.send(player, "island.no-island", "command", "/" + getId() + " create");
                } else if (is.getOwner().equals(id)) {
                    m.send(player, "island.owner-cannot-leave");
                } else {
                    islands.removeMember(is, id);
                    m.send(player, "island.left");
                    Arena a = primaryArena();
                    if (a != null && a.getLobby() != null) {
                        player.teleport(a.getLobby());
                    }
                }
            }
            case "kick" -> {
                if (is == null || !is.getOwner().equals(id)) {
                    m.send(player, "island.owner-only");
                    break;
                }
                UUID victim = null;
                for (UUID mem : is.getMembers()) {
                    if (args.length > 1 && nameOf(mem).equalsIgnoreCase(args[1])) {
                        victim = mem;
                    }
                }
                if (victim == null) {
                    m.send(player, "island.not-a-member", "player", args.length > 1 ? args[1] : "?");
                } else {
                    islands.removeMember(is, victim);
                    m.send(player, "island.kicked", "player", nameOf(victim));
                    Player vp = Bukkit.getPlayer(victim);
                    Arena a = primaryArena();
                    if (vp != null) {
                        m.send(vp, "island.you-were-kicked");
                        if (inside.contains(victim) && a != null && a.getLobby() != null) {
                            vp.teleport(a.getLobby());
                        }
                    }
                }
            }
            case "info" -> {
                if (is == null) {
                    m.send(player, "island.no-island", "command", "/" + getId() + " create");
                } else {
                    StringBuilder members = new StringBuilder();
                    for (UUID mem : is.getMembers()) {
                        members.append(members.length() == 0 ? "" : ", ").append(nameOf(mem));
                    }
                    m.send(player, "island.info", "owner", nameOf(is.getOwner()),
                            "members", members.length() == 0 ? "-" : members.toString(),
                            "radius", String.valueOf(protectionRadius(is)), "limit", String.valueOf(memberLimit(is)));
                }
            }
            default -> {
                return handleExtraCommand(player, sub, args);
            }
        }
        return true;
    }

    /** Extra sub commands that may be run without being inside the game yet. */
    protected boolean isExtraNeedingNoJoin(String sub) {
        return false;
    }

    protected String nameOf(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            return p.getName();
        }
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? id.toString().substring(0, 8) : n;
    }

    private void createIsland(Player player) {
        var m = plugin.getMessages();
        if (islands.of(player.getUniqueId()) != null) {
            m.send(player, "island.already-have-island");
            return;
        }
        Arena a = primaryArena();
        World w = a == null ? null : a.getWorld();
        if (w == null) {
            m.send(player, "island.world-missing", "world", a == null ? "-" : a.getWorldName());
            return;
        }
        Island is = islands.create(player.getUniqueId(), w.getName());
        Location center = is.getCenter();
        try {
            Location home = IslandTemplate.paste(plugin, getId(),
                    plugin.getConfigurationManager().game(getId()).getConfigurationSection("template"), center);
            is.setHomeRaw(LocationUtil.serialize(home));
            onIslandCreated(player, is);
        } catch (Exception ex) {
            plugin.getLogger().severe("Island creation failed for " + player.getName() + ": " + ex.getMessage());
            ex.printStackTrace();
            islands.delete(is);
            m.send(player, "island.create-failed");
            return;
        }
        islands.markDirty();
        Location home = islands.homeOf(is);
        if (home != null) {
            player.teleport(home);
        }
        m.send(player, "island.created");
        plugin.getEffects().play(player, "win");
        plugin.getScoreboardService().refresh(player);
    }

    private void deleteIsland(Player player, Island is, boolean confirm) {
        var m = plugin.getMessages();
        if (is == null || !is.getOwner().equals(player.getUniqueId())) {
            m.send(player, "island.owner-only");
            return;
        }
        long now = System.currentTimeMillis();
        Long until = deleteConfirm.get(player.getUniqueId());
        if (!confirm || until == null || until < now) {
            deleteConfirm.put(player.getUniqueId(), now + 30_000L);
            m.send(player, "island.delete-confirm", "command", "/" + getId() + " delete confirm");
            return;
        }
        deleteConfirm.remove(player.getUniqueId());
        onIslandDeleted(is);
        Arena hub = primaryArena();
        World w = is.getWorld();
        if (w != null) {
            for (Player p : w.getPlayers()) {
                if (is.isMember(p.getUniqueId()) && inside.contains(p.getUniqueId()) && hub != null && hub.getLobby() != null) {
                    p.teleport(hub.getLobby());
                }
            }
            YamlConfiguration cfg = plugin.getConfigurationManager().game(getId());
            if (cfg.getBoolean("island.clear-on-delete", true)) {
                int r = cfg.getInt("island.clear-radius", 40);
                Cuboid c = new Cuboid(w.getName(), is.getCenterX() - r, is.getCenterY() - cfg.getInt("island.clear-down", 20), is.getCenterZ() - r,
                        is.getCenterX() + r, is.getCenterY() + cfg.getInt("island.clear-up", 40), is.getCenterZ() + r);
                RegionFiller.fill(plugin, w, c, () -> Material.AIR, cfg.getInt("island.clear-blocks-per-tick", 4000), null);
            }
        }
        islands.delete(is);
        m.send(player, "island.deleted");
    }

    @Override
    public java.util.Map<String, String> getScoreboardVariables(Player player) {
        java.util.Map<String, String> v = new HashMap<>();
        Island is = islands.of(player.getUniqueId());
        v.put("game", getDisplayPlain());
        v.put("players", String.valueOf(inside.size()));
        v.put("island_owner", is == null ? "-" : nameOf(is.getOwner()));
        v.put("island_members", is == null ? "0" : String.valueOf(is.getMembers().size() + 1));
        v.put("island_radius", is == null ? "-" : String.valueOf(protectionRadius(is)));
        v.put("balance", TextUtil.formatNumber(plugin.getEconomy().getBalance(player)));
        return v;
    }
}

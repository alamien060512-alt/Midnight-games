package dev.midnight.minigames.core.game;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.api.events.MinigameJoinEvent;
import dev.midnight.minigames.api.events.MinigameLeaveEvent;
import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.util.PlayerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Base class for progression games without rounds (Prison, Skyblock, OneBlock, SkyGen, Parkour).
 * Players "enter" the game world, keep their inventory, and stay until they leave. The minigame itself is the
 * {@link PlayerContext} of everyone inside.
 */
public abstract class AbstractPersistentMinigame extends AbstractMinigame implements PlayerContext {

    protected final Set<UUID> inside = new HashSet<>();

    protected AbstractPersistentMinigame(MidnightMinigames plugin, String id, String defaultDisplayName) {
        super(plugin, id, defaultDisplayName);
    }

    @Override
    public boolean usesArenas() {
        return false;
    }

    @Override
    public Minigame getMinigame() {
        return this;
    }

    /** Where a player enters the game; null makes the join fail with an admin-facing error. */
    protected abstract Location resolveEntry(Player player, String modeId);

    /** Called after the player was teleported in. */
    protected void onEnter(Player player, String modeId) {
    }

    /** Called before the player leaves (save progress, remove temporary items...). */
    protected void onExit(Player player) {
    }

    /** Respawn behaviour after a lethal hit. Default: heal and return to the entry point. */
    protected void onDeathRespawn(Player victim) {
        PlayerUtil.heal(victim);
        Location l = resolveEntry(victim, "");
        if (l != null) {
            victim.teleport(l);
        }
    }

    protected boolean isPvpEnabled() {
        return section() != null && section().getBoolean("pvp", false);
    }

    @Override
    public JoinResult join(Player player, String modeId) {
        if (!isEnabled()) {
            return JoinResult.DISABLED;
        }
        if (inside.contains(player.getUniqueId()) || plugin.getPlayerManager().isInGame(player)) {
            return JoinResult.ALREADY_IN_GAME;
        }
        Location entry;
        try {
            entry = resolveEntry(player, modeId);
        } catch (Exception ex) {
            plugin.getLogger().severe(getId() + ": could not resolve entry point: " + ex.getMessage());
            return JoinResult.ERROR;
        }
        if (entry == null) {
            plugin.getMessages().send(player, "join.result.no_arena", "game", getDisplayName());
            return JoinResult.NO_ARENA;
        }
        MinigameJoinEvent ev = new MinigameJoinEvent(player, this, primaryArena(), false);
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) {
            return JoinResult.CANCELLED;
        }
        inside.add(player.getUniqueId());
        plugin.getPlayerManager().setContext(player, this);
        player.teleport(entry);
        onEnter(player, modeId);
        plugin.getMessages().send(player, "game.entered", "game", getDisplayName());
        plugin.getScoreboardService().refresh(player);
        return JoinResult.JOINED;
    }

    /** The arena that represents this game's world setup (usually named "main"), may be null. */
    @Nullable
    protected Arena primaryArena() {
        for (Arena a : plugin.getArenaManager().getArenas(getId())) {
            if (a.isEnabled()) {
                return a;
            }
        }
        return null;
    }

    @Override
    public boolean leave(Player player) {
        return leave(player, false);
    }

    protected boolean leave(Player player, boolean quit) {
        if (!inside.remove(player.getUniqueId())) {
            return false;
        }
        try {
            onExit(player);
        } catch (Exception ex) {
            plugin.getLogger().warning(getId() + ": error while exiting " + player.getName() + ": " + ex.getMessage());
        }
        plugin.getPlayerManager().clearContext(player);
        plugin.getScoreboardService().remove(player);
        if (!quit) {
            plugin.getLobbyService().afterLeaveGame(player);
            plugin.getScoreboardService().refresh(player);
        }
        Bukkit.getPluginManager().callEvent(new MinigameLeaveEvent(player, this, primaryArena(), quit));
        return true;
    }

    @Override
    public void handlePlayerQuit(Player player) {
        leave(player, true);
    }

    @Override
    public void handlePlayerDeath(Player victim, @Nullable Player killer, EntityDamageEvent.DamageCause cause) {
        if (killer != null && inside.contains(killer.getUniqueId())) {
            plugin.getPlayerManager().addKill(killer, getId());
        }
        plugin.getPlayerManager().addDeath(victim, getId());
        onDeathRespawn(victim);
    }

    @Override
    public void onDamageByEntity(Player victim, EntityDamageByEntityEvent e) {
        if (!isPvpEnabled() && (e.getDamager() instanceof Player
                || (e.getDamager() instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Player))) {
            e.setCancelled(true);
        }
    }

    @Override
    protected void onDisable() {
        for (UUID id : new ArrayList<>(inside)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                try {
                    onExit(p);
                } catch (Exception ignored) {
                }
                plugin.getPlayerManager().clearContext(p);
            }
        }
        inside.clear();
    }

    @Override
    public boolean start(String arenaName) {
        return false;
    }

    @Override
    public boolean stop(String arenaName) {
        return false;
    }

    @Override
    public GameState getState(String arenaName) {
        return getState();
    }

    @Override
    public GameState getState() {
        return inside.isEmpty() ? GameState.WAITING : GameState.IN_GAME;
    }

    @Override
    public Collection<Player> getPlayers() {
        List<Player> out = new ArrayList<>();
        for (UUID id : inside) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    @Override
    public int getPlayerCount() {
        return inside.size();
    }

    @Override
    public int getMaxPlayers() {
        return Bukkit.getMaxPlayers();
    }

    @Override
    public boolean onItemAction(Player player, String action) {
        if (action.equals("leave")) {
            leave(player);
            return true;
        }
        return false;
    }
}

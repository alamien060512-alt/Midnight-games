package dev.midnight.minigames.core.game;

import dev.midnight.minigames.MidnightMinigames;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/**
 * Tracks every running {@link GameInstance} and drives them from ONE repeating task (once per second).
 * Individual games never schedule their own repeating timers for countdowns or generators.
 */
public final class GameManager {

    private final MidnightMinigames plugin;
    private final Set<GameInstance> instances = new LinkedHashSet<>();
    private BukkitTask ticker;

    public GameManager(MidnightMinigames plugin) {
        this.plugin = plugin;
    }

    public void start() {
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (ticker != null) {
            ticker.cancel();
        }
    }

    public void register(GameInstance instance) {
        instances.add(instance);
    }

    public void unregister(GameInstance instance) {
        instances.remove(instance);
    }

    private void tick() {
        if (instances.isEmpty()) {
            return;
        }
        for (GameInstance g : new ArrayList<>(instances)) {
            try {
                g.tick();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Game " + g.getMinigame().getId() + "/" + g.getArenaName()
                        + " crashed and is being stopped: " + ex.getMessage(), ex);
                try {
                    g.crashStop();
                } catch (Exception inner) {
                    plugin.getLogger().log(Level.SEVERE, "Could not cleanly stop crashed game: " + inner.getMessage(), inner);
                    unregister(g);
                }
            }
        }
    }

    public GameInstance getInstance(Player player) {
        PlayerContext ctx = plugin.getPlayerManager().getContext(player);
        return ctx instanceof GameInstance gi ? gi : null;
    }

    public List<GameInstance> instancesIn(World world) {
        List<GameInstance> out = new ArrayList<>();
        for (GameInstance g : instances) {
            if (g.getArena().getWorldName().equals(world.getName())) {
                out.add(g);
            }
        }
        return out;
    }

    /** Running games whose arena contains / is close to the location (several arenas may share one world). */
    public List<GameInstance> nearInstances(org.bukkit.Location l) {
        List<GameInstance> out = new ArrayList<>();
        if (instances.isEmpty() || l.getWorld() == null) {
            return out;
        }
        for (GameInstance g : instances) {
            if (g.getState() == GameState.IN_GAME && g.isNear(l)) {
                out.add(g);
            }
        }
        return out;
    }

    public Set<GameInstance> getInstances() {
        return instances;
    }

    /** Stops every game immediately (plugin disable). Players are returned to the lobby. */
    public void stopAll() {
        for (GameInstance g : new ArrayList<>(instances)) {
            try {
                g.shutdown();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Error while shutting down a game: " + ex.getMessage(), ex);
            }
        }
        instances.clear();
    }
}

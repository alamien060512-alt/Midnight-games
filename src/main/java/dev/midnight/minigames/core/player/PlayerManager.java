package dev.midnight.minigames.core.player;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.game.Minigame;
import dev.midnight.minigames.core.game.PlayerContext;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Player profiles (stats + data, loaded/saved asynchronously) and the "which game is this player in" mapping. */
public final class PlayerManager {

    private final MidnightMinigames plugin;
    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerContext> contexts = new HashMap<>();
    private BukkitTask autosave;

    public PlayerManager(MidnightMinigames plugin) {
        this.plugin = plugin;
    }

    public void start() {
        long minutes = Math.max(1, plugin.getConfigurationManager().config().getLong("database.autosave-minutes", 5));
        autosave = Bukkit.getScheduler().runTaskTimer(plugin, this::saveDirty, minutes * 1200L, minutes * 1200L);
    }

    public void stop() {
        if (autosave != null) {
            autosave.cancel();
        }
    }

    // ---- profiles --------------------------------------------------------------------------------

    /** Never null; for players that are still loading the returned profile has {@code isLoaded() == false}. */
    public PlayerProfile getProfile(UUID uuid) {
        return profiles.computeIfAbsent(uuid, id -> {
            Player p = Bukkit.getPlayer(id);
            return new PlayerProfile(id, p != null ? p.getName() : "Unknown");
        });
    }

    public PlayerProfile getProfile(Player player) {
        return getProfile(player.getUniqueId());
    }

    public PlayerStats getStats(Player player) {
        return getProfile(player).getStats();
    }

    public void handleJoin(Player player) {
        PlayerProfile profile = getProfile(player);
        profile.setName(player.getName());
        final UUID id = player.getUniqueId();
        plugin.getStorage().loadProfile(id, player.getName()).whenComplete((stored, ex) -> {
            if (ex != null) {
                plugin.getLogger().log(Level.SEVERE, "Could not load stats for " + player.getName()
                        + " - stats will not be saved this session: " + ex.getMessage());
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                PlayerProfile current = profiles.get(id);
                if (current != null) {
                    current.applyLoaded(stored.stats(), stored.data());
                }
            });
        });
    }

    public void handleQuit(Player player) {
        PlayerProfile profile = profiles.remove(player.getUniqueId());
        contexts.remove(player.getUniqueId());
        if (profile != null) {
            save(profile);
        }
    }

    private void save(PlayerProfile profile) {
        if (!profile.isLoaded() || !profile.isDirty()) {
            return;
        }
        profile.clearDirty();
        plugin.getStorage().saveProfile(profile.getUuid(), profile.getName(), profile.getStats().copy(), profile.snapshotData());
    }

    public void saveDirty() {
        for (PlayerProfile p : profiles.values()) {
            save(p);
        }
    }

    // ---- contexts ----------------------------------------------------------------------------------

    public PlayerContext getContext(UUID id) {
        return contexts.get(id);
    }

    public PlayerContext getContext(Player p) {
        return contexts.get(p.getUniqueId());
    }

    public void setContext(Player p, PlayerContext ctx) {
        contexts.put(p.getUniqueId(), ctx);
        plugin.getTabService().update(p);
    }

    public void clearContext(Player p) {
        contexts.remove(p.getUniqueId());
        plugin.getTabService().update(p);
    }

    public boolean isInGame(Player p) {
        return contexts.containsKey(p.getUniqueId());
    }

    public Minigame getMinigame(Player p) {
        PlayerContext c = contexts.get(p.getUniqueId());
        return c == null ? null : c.getMinigame();
    }

    public int countInGames() {
        return contexts.size();
    }

    // ---- stat helpers ------------------------------------------------------------------------------

    public void addWin(Player p, String gameId) {
        PlayerProfile pr = getProfile(p);
        pr.getStats().setWins(pr.getStats().getWins() + 1);
        pr.increment("stats", gameId + ".wins", 1);
    }

    public void addLoss(Player p, String gameId) {
        PlayerProfile pr = getProfile(p);
        pr.getStats().setLosses(pr.getStats().getLosses() + 1);
        pr.increment("stats", gameId + ".losses", 1);
    }

    public void addKill(Player p, String gameId) {
        PlayerProfile pr = getProfile(p);
        pr.getStats().setKills(pr.getStats().getKills() + 1);
        pr.increment("stats", gameId + ".kills", 1);
    }

    public void addDeath(Player p, String gameId) {
        PlayerProfile pr = getProfile(p);
        pr.getStats().setDeaths(pr.getStats().getDeaths() + 1);
        pr.increment("stats", gameId + ".deaths", 1);
    }

    public void addGamePlayed(Player p, String gameId) {
        PlayerProfile pr = getProfile(p);
        pr.getStats().setGamesPlayed(pr.getStats().getGamesPlayed() + 1);
        pr.increment("stats", gameId + ".played", 1);
    }

    public void addCoins(Player p, double amount) {
        PlayerProfile pr = getProfile(p);
        pr.getStats().setCoins(pr.getStats().getCoins() + amount);
        pr.markDirty();
    }

    public double getGameStat(Player p, String gameId, String key) {
        return getProfile(p).getLong("stats", gameId + "." + key, 0);
    }

    /** Queues a save for every loaded profile; the storage close() afterwards drains the queue. */
    public void saveAllOnShutdown() {
        for (PlayerProfile p : profiles.values()) {
            save(p);
        }
    }
}

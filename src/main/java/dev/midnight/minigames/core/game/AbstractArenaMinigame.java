package dev.midnight.minigames.core.game;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.arena.Arena;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Base class for round based minigames (BedWars, SkyWars, Spleef, Duels...). Maintains one
 * {@link GameInstance} per active arena and picks arenas for joining players.
 */
public abstract class AbstractArenaMinigame extends AbstractMinigame {

    protected final Map<String, GameInstance> instances = new LinkedHashMap<>();
    private final Set<String> warnedArenas = new HashSet<>();
    private final Random random = new Random();

    protected AbstractArenaMinigame(MidnightMinigames plugin, String id, String defaultDisplayName) {
        super(plugin, id, defaultDisplayName);
    }

    /** Creates the game logic object for an arena. Called only when players actually want to play. */
    protected abstract GameInstance createInstance(Arena arena, ModeDefinition mode);

    /** Whether an arena can host the given mode. Duels overrides this since kits are not tied to arenas. */
    protected boolean arenaMatchesMode(Arena arena, ModeDefinition mode) {
        return arena.getMode().equalsIgnoreCase(mode.getId());
    }

    @Override
    public boolean supportsSpectators() {
        return section() == null || section().getBoolean("spectators", true);
    }

    // ---- joining -------------------------------------------------------------------------------------

    @Override
    public JoinResult join(Player player, String modeId) {
        return joinGroup(List.of(player), modeId);
    }

    @Override
    public JoinResult joinGroup(Collection<Player> group, String modeId) {
        if (!isEnabled()) {
            return JoinResult.DISABLED;
        }
        ModeDefinition mode = findMode(modeId);
        if (mode == null) {
            return JoinResult.ERROR;
        }
        int n = group.size();
        GameInstance inst = findJoinable(mode, n);
        if (inst == null) {
            inst = openNewInstance(mode, n);
            if (inst == null) {
                if (anyArenaFor(mode, n)) {
                    return JoinResult.FULL;          // arenas exist but are all busy -> the queue handles it
                }
                return anyArenaFor(mode, 1) ? JoinResult.PARTY_TOO_LARGE : JoinResult.NO_ARENA;
            }
        }
        JoinResult last = JoinResult.JOINED;
        for (Player p : group) {
            JoinResult r = inst.join(p);
            if (r != JoinResult.JOINED) {
                last = r;
                break;
            }
        }
        return last;
    }

    private GameInstance findJoinable(ModeDefinition mode, int count) {
        GameInstance best = null;
        for (GameInstance gi : instances.values()) {
            if (gi.getModeId().equalsIgnoreCase(mode.getId()) && gi.hasRoomFor(count)) {
                if (best == null || gi.getPlayerCount() > best.getPlayerCount()) {
                    best = gi;
                }
            }
        }
        return best;
    }

    /** An arena that is enabled, matches the mode, can hold the group and has a loadable world and a lobby. */
    private boolean isUsable(Arena a, ModeDefinition mode, int count) {
        if (!a.isEnabled() || !arenaMatchesMode(a, mode) || a.getMaxPlayers() < count) {
            return false;
        }
        if (a.getWorld() == null && plugin.getWorldService().getOrLoad(a.getWorldName()) == null) {
            if (warnedArenas.add(a.getName())) {
                plugin.getLogger().severe("Arena " + getId() + "/" + a.getName() + " cannot be used: world '"
                        + a.getWorldName() + "' does not exist or is not loaded. Disable the arena or fix the world.");
            }
            return false;
        }
        if (a.getLobby() == null) {
            if (warnedArenas.add(a.getName())) {
                plugin.getLogger().severe("Arena " + getId() + "/" + a.getName() + " cannot be used: lobby location "
                        + "is missing or its world is not loaded (/minigame arena setlobby).");
            }
            return false;
        }
        return true;
    }

    private boolean anyArenaFor(ModeDefinition mode, int count) {
        for (Arena a : plugin.getArenaManager().getArenas(getId())) {
            if (isUsable(a, mode, count)) {
                return true;
            }
        }
        return false;
    }

    /** Joins the group into a brand new instance (never an existing lobby). Used for direct duel challenges. */
    protected JoinResult joinNewInstance(Collection<Player> group, ModeDefinition mode) {
        GameInstance inst = openNewInstance(mode, group.size());
        if (inst == null) {
            return JoinResult.NO_ARENA;
        }
        JoinResult last = JoinResult.JOINED;
        for (Player p : group) {
            last = inst.join(p);
            if (last != JoinResult.JOINED) {
                break;
            }
        }
        return last;
    }

    private GameInstance openNewInstance(ModeDefinition mode, int count) {
        List<Arena> candidates = new ArrayList<>();
        for (Arena a : plugin.getArenaManager().getArenas(getId())) {
            if (!instances.containsKey(key(a.getName())) && isUsable(a, mode, count)) {
                candidates.add(a);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        Arena chosen = candidates.get(random.nextInt(candidates.size()));
        try {
            GameInstance gi = createInstance(chosen, mode);
            instances.put(key(chosen.getName()), gi);
            return gi;
        } catch (Exception ex) {
            plugin.getLogger().severe("Could not create game instance for " + getId() + "/" + chosen.getName() + ": " + ex.getMessage());
            ex.printStackTrace();
            return null;
        }
    }

    protected static String key(String arenaName) {
        return arenaName.toLowerCase(Locale.ROOT);
    }

    void removeInstance(GameInstance gi) {
        instances.values().removeIf(v -> v == gi);
    }

    public GameInstance getInstance(String arenaName) {
        return instances.get(key(arenaName));
    }

    public Collection<GameInstance> getInstances() {
        return new ArrayList<>(instances.values());
    }

    @Override
    public boolean spectate(Player spectator, Player target) {
        PlayerContext ctx = plugin.getPlayerManager().getContext(target);
        return ctx instanceof GameInstance gi && gi.getMinigame() == this && gi.addSpectator(spectator, target);
    }

    @Override
    public boolean leave(Player player) {
        PlayerContext ctx = plugin.getPlayerManager().getContext(player);
        if (ctx instanceof GameInstance gi && gi.getMinigame() == this) {
            gi.leave(player, false);
            return true;
        }
        return false;
    }

    // ---- lifecycle -----------------------------------------------------------------------------------

    @Override
    public boolean start(String arenaName) {
        GameInstance gi = instances.get(key(arenaName));
        return gi != null && gi.forceStart();
    }

    @Override
    public boolean stop(String arenaName) {
        GameInstance gi = instances.get(key(arenaName));
        return gi != null && gi.forceStop();
    }

    @Override
    protected void onDisable() {
        for (GameInstance gi : new ArrayList<>(instances.values())) {
            gi.shutdown();
            plugin.getGameManager().unregister(gi);
        }
        instances.clear();
    }

    @Override
    public void handlePlayerDeath(Player victim, @Nullable Player killer, EntityDamageEvent.DamageCause cause) {
        PlayerContext ctx = plugin.getPlayerManager().getContext(victim);
        if (ctx instanceof GameInstance gi && gi.getMinigame() == this) {
            gi.handleDeath(victim, killer, cause);
        }
    }

    @Override
    public void handlePlayerQuit(Player player) {
        PlayerContext ctx = plugin.getPlayerManager().getContext(player);
        if (ctx instanceof GameInstance gi && gi.getMinigame() == this) {
            gi.leave(player, true);
        }
    }

    // ---- state ---------------------------------------------------------------------------------------

    @Override
    public GameState getState(String arenaName) {
        GameInstance gi = instances.get(key(arenaName));
        return gi == null ? GameState.WAITING : gi.getState();
    }

    @Override
    public GameState getState() {
        GameState best = GameState.WAITING;
        for (GameInstance gi : instances.values()) {
            GameState s = gi.getState();
            if (s == GameState.IN_GAME) {
                return s;
            }
            if (s == GameState.STARTING || (s == GameState.ENDING && best == GameState.WAITING)) {
                best = s;
            }
        }
        return best;
    }

    @Override
    public Collection<Player> getPlayers() {
        List<Player> out = new ArrayList<>();
        for (GameInstance gi : instances.values()) {
            out.addAll(gi.getOnlineOccupants());
        }
        return out;
    }

    /** Number of players (not spectators) currently in games of the given mode. */
    public int countPlayers(String modeId) {
        int n = 0;
        for (GameInstance gi : instances.values()) {
            if (modeId == null || gi.getModeId().equalsIgnoreCase(modeId)) {
                n += gi.getPlayerCount();
            }
        }
        return n;
    }

    @Override
    public int getMaxPlayers() {
        int max = 0;
        for (Arena a : plugin.getArenaManager().getArenas(getId())) {
            if (a.isEnabled()) {
                max = Math.max(max, a.getMaxPlayers());
            }
        }
        return max;
    }

    @Override
    public List<String> validateArena(Arena arena) {
        List<String> problems = new ArrayList<>(arena.validateBasics());
        if (findMode(arena.getMode()) == null && !modes.isEmpty()) {
            StringBuilder ids = new StringBuilder();
            for (ModeDefinition m : modes) {
                ids.append(ids.length() == 0 ? "" : ", ").append(m.getId());
            }
            problems.add("Unknown mode '" + arena.getMode() + "' (available: " + ids + ") - /minigame arena setmode");
        }
        return problems;
    }
}

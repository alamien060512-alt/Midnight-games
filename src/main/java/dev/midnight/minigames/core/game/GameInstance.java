package dev.midnight.minigames.core.game;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.api.events.ArenaResetEvent;
import dev.midnight.minigames.api.events.MinigameEndEvent;
import dev.midnight.minigames.api.events.MinigameJoinEvent;
import dev.midnight.minigames.api.events.MinigameLeaveEvent;
import dev.midnight.minigames.api.events.MinigameStartEvent;
import dev.midnight.minigames.api.events.PlayerEliminateEvent;
import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.team.GameTeam;
import dev.midnight.minigames.core.world.BlockTracker;
import dev.midnight.minigames.util.PlayerUtil;
import dev.midnight.minigames.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One running session of a round based minigame on a single arena. Handles the whole lifecycle
 * (WAITING -> STARTING -> IN_GAME -> ENDING -> RESETTING), player state snapshots, spectators, teams,
 * stats and rewards. Subclasses implement the actual game rules through the protected hooks.
 */
public abstract class GameInstance implements PlayerContext {

    protected final MidnightMinigames plugin;
    protected final AbstractArenaMinigame minigame;
    protected final Arena arena;
    protected final ModeDefinition mode;

    protected GameState state = GameState.WAITING;
    /** Active participants: everyone waiting in the lobby, then everyone still alive in the game. */
    protected final Set<UUID> players = new LinkedHashSet<>();
    protected final Set<UUID> spectators = new LinkedHashSet<>();
    /** Everyone that was alive when the game started (used for stats and results). */
    protected final Set<UUID> participants = new LinkedHashSet<>();
    protected final Map<String, GameTeam> teams = new LinkedHashMap<>();
    protected final Map<UUID, GameTeam> teamByPlayer = new HashMap<>();
    protected final Map<UUID, Integer> kills = new HashMap<>();
    protected final BlockTracker blocks = new BlockTracker();

    private final Map<UUID, PlayerSnapshot> snapshots = new HashMap<>();
    private final Map<UUID, UUID> lastAttacker = new HashMap<>();
    private final Map<UUID, Long> lastAttackTime = new HashMap<>();

    protected int countdown;
    protected int elapsed;
    protected int endTimer;
    private boolean disposed;
    private boolean registered;
    /** Set when an admin stopped the game: no stats, rewards or result titles are recorded. */
    private boolean aborted;

    protected GameInstance(MidnightMinigames plugin, AbstractArenaMinigame minigame, Arena arena, ModeDefinition mode) {
        this.plugin = plugin;
        this.minigame = minigame;
        this.arena = arena;
        this.mode = mode;
        plugin.getGameManager().register(this);
        registered = true;
    }

    // ------------------------------------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------------------------------------

    @Override
    public AbstractArenaMinigame getMinigame() {
        return minigame;
    }

    public Arena getArena() {
        return arena;
    }

    @Override
    public String getArenaName() {
        return arena.getName();
    }

    public ModeDefinition getMode() {
        return mode;
    }

    public String getModeId() {
        return mode.getId();
    }

    @Override
    public GameState getState() {
        return state;
    }

    @Override
    public String getModeName() {
        return TextUtil.plain(TextUtil.parse(mode.getDisplayName()));
    }

    @Override
    public int getPlayerCount() {
        return players.size();
    }

    @Override
    public int getMaxPlayers() {
        return arena.getMaxPlayers();
    }

    public int getElapsed() {
        return elapsed;
    }

    public int getTimeLeft() {
        int limit = arena.getGameTime();
        return limit <= 0 ? -1 : Math.max(0, limit - elapsed);
    }

    public Set<UUID> getPlayerIds() {
        return players;
    }

    public Set<UUID> getSpectatorIds() {
        return spectators;
    }

    public boolean isPlaying(Player p) {
        return players.contains(p.getUniqueId());
    }

    public boolean isSpectator(Player p) {
        return spectators.contains(p.getUniqueId());
    }

    /**
     * Whether a location belongs to this arena. Uses the optional region "bounds" (/minigame arena setregion ... bounds);
     * without it, any player of this game within 120 blocks counts. Needed when several arenas share one world.
     */
    public boolean isNear(Location l) {
        if (l.getWorld() == null || !l.getWorld().getName().equals(arena.getWorldName())) {
            return false;
        }
        dev.midnight.minigames.util.Cuboid bounds = arena.getRegion("bounds");
        if (bounds != null) {
            return bounds.contains(l);
        }
        for (Player p : getOnlineOccupants()) {
            if (p.getWorld() == l.getWorld() && p.getLocation().distanceSquared(l) <= 120.0 * 120.0) {
                return true;
            }
        }
        return false;
    }

    /** Places a block programmatically (bridge egg, towers, cages): only into air, tracked so the arena resets. */
    protected boolean placeTracked(org.bukkit.block.Block b, org.bukkit.Material m) {
        if (!b.getType().isAir()) {
            return false;
        }
        blocks.record(b);
        b.setType(m, false);
        blocks.markPlaced(b);
        return true;
    }

    /** Remembers the original state of a block changed by something other than a player click (liquids, TNT, fire...). */
    public void trackChange(org.bukkit.block.Block block) {
        if (state == GameState.IN_GAME) {
            blocks.record(block);
        }
    }

    /** Removes dropped items, arrows and primed TNT left inside the arena bounds (only when bounds are set). */
    private void clearLooseEntities() {
        dev.midnight.minigames.util.Cuboid b = arena.getRegion("bounds");
        World w = world();
        if (b == null || w == null) {
            return;
        }
        for (int cx = b.minX >> 4; cx <= b.maxX >> 4; cx++) {
            for (int cz = b.minZ >> 4; cz <= b.maxZ >> 4; cz++) {
                if (!w.isChunkLoaded(cx, cz)) {
                    continue;
                }
                for (Entity en : w.getChunkAt(cx, cz).getEntities()) {
                    if ((en instanceof org.bukkit.entity.Item || en instanceof Projectile
                            || en instanceof org.bukkit.entity.TNTPrimed || en instanceof org.bukkit.entity.FallingBlock)
                            && b.contains(en.getLocation())) {
                        en.remove();
                    }
                }
            }
        }
    }

    public boolean hasRoomFor(int count) {
        return state.isJoinable() && players.size() + count <= arena.getMaxPlayers();
    }

    public boolean isEmpty() {
        return players.isEmpty() && spectators.isEmpty();
    }

    public GameTeam getTeam(UUID id) {
        return teamByPlayer.get(id);
    }

    public List<Player> getOnlinePlayers() {
        return online(players);
    }

    public List<Player> getOnlineOccupants() {
        List<Player> out = new ArrayList<>(online(players));
        out.addAll(online(spectators));
        return out;
    }

    protected List<Player> online(Collection<UUID> ids) {
        List<Player> out = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    protected World world() {
        return arena.getWorld();
    }

    protected String gameConfigString(String path, String def) {
        return plugin.getConfigurationManager().game(minigame.getId()).getString(path, def);
    }

    // ------------------------------------------------------------------------------------------------
    // Messaging helpers
    // ------------------------------------------------------------------------------------------------

    protected void broadcast(String key, String... vars) {
        plugin.getMessages().sendAll(getOnlineOccupants(), key, vars);
    }

    protected void broadcastTeam(GameTeam team, String key, String... vars) {
        plugin.getMessages().sendAll(online(team.getMembers()), key, vars);
    }

    protected void sound(String key) {
        plugin.getEffects().playAll(getOnlineOccupants(), key);
    }

    // ------------------------------------------------------------------------------------------------
    // Joining and leaving
    // ------------------------------------------------------------------------------------------------

    public JoinResult join(Player player) {
        if (disposed || !state.isJoinable()) {
            return JoinResult.IN_PROGRESS;
        }
        UUID id = player.getUniqueId();
        if (players.contains(id) || spectators.contains(id)) {
            return JoinResult.ALREADY_IN_GAME;
        }
        if (players.size() >= arena.getMaxPlayers()) {
            return JoinResult.FULL;
        }
        MinigameJoinEvent event = new MinigameJoinEvent(player, minigame, arena, false);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return JoinResult.CANCELLED;
        }
        snapshots.put(id, PlayerSnapshot.of(player));
        players.add(id);
        plugin.getPlayerManager().setContext(player, this);
        preparePlayerForLobby(player);
        broadcast("game.player-joined", "player", player.getName(),
                "current", String.valueOf(players.size()), "max", String.valueOf(arena.getMaxPlayers()));
        plugin.getEffects().play(player, "join");
        updateLobbyState();
        plugin.getScoreboardService().refresh(player);
        return JoinResult.JOINED;
    }

    /** Joins a running game as spectator. Only valid when the minigame supports spectators. */
    public boolean addSpectator(Player player, @Nullable Player target) {
        if (disposed || state != GameState.IN_GAME || !minigame.supportsSpectators()) {
            return false;
        }
        UUID id = player.getUniqueId();
        if (players.contains(id) || spectators.contains(id)) {
            return false;
        }
        MinigameJoinEvent event = new MinigameJoinEvent(player, minigame, arena, true);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        snapshots.put(id, PlayerSnapshot.of(player));
        plugin.getPlayerManager().setContext(player, this);
        makeSpectator(player);
        if (target != null && target.isOnline()) {
            player.teleport(target.getLocation());
        }
        plugin.getMessages().send(player, "game.now-spectating", "arena", arena.getName());
        plugin.getScoreboardService().refresh(player);
        return true;
    }

    protected void preparePlayerForLobby(Player player) {
        PlayerUtil.reset(player, GameMode.ADVENTURE);
        Location lobby = arena.getLobby();
        if (lobby != null) {
            player.teleport(lobby);
        } else {
            plugin.getLogger().severe("Arena " + minigame.getId() + "/" + arena.getName() + " has no lobby location!");
        }
        ItemStack leave = plugin.getLobbyService().createLeaveItem();
        if (leave != null) {
            player.getInventory().setItem(plugin.getLobbyService().leaveItemSlot(), leave);
        }
        onLobbyJoin(player);
    }

    /** Hook: give lobby items such as a kit selector or team chooser. */
    protected void onLobbyJoin(Player player) {
    }

    /** Leaves the game (voluntarily or by disconnecting). */
    public void leave(Player player, boolean quit) {
        UUID id = player.getUniqueId();
        boolean playing = players.contains(id);
        boolean spectating = spectators.contains(id);
        if (!playing && !spectating) {
            return;
        }
        if (playing && state == GameState.IN_GAME) {
            onPlayerLeftDuringGame(player);
            @Nullable Player killer = recentAttacker(player);
            eliminate(player, killer, PlayerEliminateEvent.Reason.QUIT, false);
        } else if (playing && state.isJoinable()) {
            broadcast("game.player-left", "player", player.getName(),
                    "current", String.valueOf(players.size() - 1), "max", String.valueOf(arena.getMaxPlayers()));
        }
        removePlayer(player, quit);
        if (disposed) {
            return;
        }
        if (state.isJoinable()) {
            updateLobbyState();
            if (isEmpty()) {
                dispose();
            }
        } else if (state == GameState.IN_GAME) {
            if (players.isEmpty() && spectators.isEmpty()) {
                beginReset();
            } else {
                checkWin();
            }
        } else if (state == GameState.ENDING && isEmpty()) {
            beginReset();
        }
    }

    /** Hook: a participant disconnects/leaves mid-game (drop items, etc.). */
    protected void onPlayerLeftDuringGame(Player player) {
    }

    private void removePlayer(Player player, boolean quit) {
        UUID id = player.getUniqueId();
        players.remove(id);
        spectators.remove(id);
        lastAttacker.remove(id);
        lastAttackTime.remove(id);
        plugin.getPlayerManager().clearContext(player);
        plugin.getScoreboardService().remove(player);
        PlayerSnapshot snap = snapshots.remove(id);
        try {
            onPlayerRemoved(player);
        } catch (Exception ex) {
            plugin.getLogger().warning("Error in onPlayerRemoved: " + ex.getMessage());
        }
        if (snap != null) {
            snap.restore(player);
            if (!plugin.getConfigurationManager().config().getBoolean("lobby.teleport-on-leave", true)
                    || plugin.getLobbyService().getMainLobby() == null) {
                player.teleport(snap.location());
            }
        } else {
            PlayerUtil.reset(player, GameMode.SURVIVAL);
        }
        plugin.getLobbyService().afterLeaveGame(player);
        Bukkit.getPluginManager().callEvent(new MinigameLeaveEvent(player, minigame, arena, quit));
        plugin.getScoreboardService().refresh(player);
    }

    /** Hook invoked while a player is being detached from this instance (before state restore). */
    protected void onPlayerRemoved(Player player) {
    }

    // ------------------------------------------------------------------------------------------------
    // Lobby / countdown
    // ------------------------------------------------------------------------------------------------

    protected void updateLobbyState() {
        int n = players.size();
        if (state == GameState.WAITING && n >= arena.getMinPlayers()) {
            state = GameState.STARTING;
            countdown = arena.getCountdown();
        } else if (state == GameState.STARTING) {
            if (n < arena.getMinPlayers()) {
                state = GameState.WAITING;
                broadcast("game.countdown-cancelled");
            } else if (n >= arena.getMaxPlayers()) {
                int full = plugin.getConfigurationManager().config().getInt("defaults.full-countdown", 10);
                if (countdown > full) {
                    countdown = full;
                }
            }
        }
    }

    /** Admin/forcestart: skips the remaining countdown. */
    public boolean forceStart() {
        if (!state.isJoinable() || players.isEmpty()) {
            return false;
        }
        startGame();
        return true;
    }

    /** Admin/stop: ends the game without winners and resets the arena. */
    public boolean forceStop() {
        if (disposed || state == GameState.RESETTING) {
            return false;
        }
        if (state == GameState.IN_GAME) {
            aborted = true;
            endGame(List.of());
        }
        beginReset();
        return true;
    }

    /** Called by the game manager when {@link #tick()} threw an exception. */
    public void crashStop() {
        for (Player p : getOnlineOccupants()) {
            plugin.getMessages().send(p, "game.crashed");
        }
        if (state != GameState.RESETTING) {
            beginReset();
        } else {
            dispose();
        }
    }

    /** Called when the plugin disables. Synchronous, no scheduler usage. */
    public void shutdown() {
        for (Player p : getOnlineOccupants()) {
            removePlayer(p, false);
        }
        try {
            onCleanup();
        } catch (Exception ex) {
            plugin.getLogger().warning("Cleanup error during shutdown: " + ex.getMessage());
        }
        blocks.restoreNow(world());
        disposed = true;
        state = GameState.RESETTING;
    }

    // ------------------------------------------------------------------------------------------------
    // Tick (called once per second by the GameManager)
    // ------------------------------------------------------------------------------------------------

    public void tick() {
        if (disposed) {
            return;
        }
        switch (state) {
            case STARTING -> {
                if (countdown > 0) {
                    announceCountdown(countdown);
                    countdown--;
                }
                if (countdown <= 0) {
                    startGame();
                }
            }
            case IN_GAME -> {
                elapsed++;
                onTick();
                int limit = arena.getGameTime();
                if (state == GameState.IN_GAME && limit > 0 && elapsed >= limit) {
                    onTimeLimit();
                }
            }
            case ENDING -> {
                if (--endTimer <= 0) {
                    beginReset();
                }
            }
            default -> {
            }
        }
    }

    private void announceCountdown(int seconds) {
        if (seconds <= 5 || seconds == 10 || seconds == 15 || seconds == 20 || seconds == 30 || seconds == 60) {
            broadcast("game.countdown", "seconds", String.valueOf(seconds));
            if (seconds <= 5) {
                plugin.getMessages().titleAll(getOnlineOccupants(), "countdown", "seconds", String.valueOf(seconds));
            }
            sound("countdown");
        }
    }

    /** Hook: per-second game logic. */
    protected void onTick() {
    }

    /** Hook: game-time limit reached. Default: end without winner. */
    protected void onTimeLimit() {
        broadcast("game.time-up");
        endGame(List.of());
    }

    // ------------------------------------------------------------------------------------------------
    // Start / end / reset
    // ------------------------------------------------------------------------------------------------

    protected void startGame() {
        if (state == GameState.IN_GAME || disposed) {
            return;
        }
        state = GameState.IN_GAME;
        elapsed = 0;
        participants.clear();
        participants.addAll(players);
        for (Player p : getOnlinePlayers()) {
            PlayerUtil.reset(p, GameMode.SURVIVAL);
        }
        try {
            onGameStart();
        } catch (Exception ex) {
            plugin.getLogger().severe("Game " + minigame.getId() + "/" + arena.getName() + " failed to start: " + ex.getMessage());
            ex.printStackTrace();
            broadcast("game.crashed");
            beginReset();
            return;
        }
        Bukkit.getPluginManager().callEvent(new MinigameStartEvent(minigame, arena, getOnlinePlayers()));
        broadcast("game.started");
        plugin.getMessages().titleAll(getOnlinePlayers(), "start");
        sound("start");
        for (Player p : getOnlineOccupants()) {
            plugin.getScoreboardService().refresh(p);
            plugin.getTabService().update(p);
        }
    }

    /** Hook: teleport players, assign teams, give kits. Throwing here aborts the game safely. */
    protected abstract void onGameStart();

    /** Ends the game. Winners get win stats, everyone else participating gets a loss. */
    protected void endGame(Collection<UUID> winners) {
        if (state != GameState.IN_GAME) {
            return;
        }
        state = GameState.ENDING;
        endTimer = Math.max(1, plugin.getConfigurationManager().config().getInt("games." + minigame.getId() + ".end-delay",
                plugin.getConfigurationManager().config().getInt("defaults.end-delay", 8)));
        List<UUID> won = new ArrayList<>(winners);
        StringBuilder names = new StringBuilder();
        for (UUID id : won) {
            Player p = Bukkit.getPlayer(id);
            String n = p != null ? p.getName() : Bukkit.getOfflinePlayer(id).getName();
            if (n != null) {
                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(n);
            }
        }
        if (aborted) {
            broadcast("game.aborted");
        } else {
            for (UUID id : participants) {
                Player p = Bukkit.getPlayer(id);
                if (p == null) {
                    continue;
                }
                plugin.getPlayerManager().addGamePlayed(p, minigame.getId());
                if (won.contains(id)) {
                    plugin.getPlayerManager().addWin(p, minigame.getId());
                    reward(p, "win");
                    plugin.getMessages().title(p, "win");
                    plugin.getEffects().play(p, "win");
                    plugin.getEffects().firework(p.getLocation());
                } else if (won.isEmpty()) {
                    plugin.getMessages().title(p, "draw"); // a draw is neither a win nor a loss
                } else {
                    plugin.getPlayerManager().addLoss(p, minigame.getId());
                    reward(p, "loss");
                    plugin.getMessages().title(p, "lose");
                    plugin.getEffects().play(p, "lose");
                }
            }
            if (won.isEmpty()) {
                broadcast("game.draw");
            } else {
                broadcast("game.winner", "winners", names.toString());
            }
        }
        Bukkit.getPluginManager().callEvent(new MinigameEndEvent(minigame, arena, won, new ArrayList<>(participants)));
        try {
            if (!aborted) {
                onGameEnd(won);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Error in onGameEnd: " + ex.getMessage());
        }
        for (Player p : getOnlineOccupants()) {
            plugin.getScoreboardService().refresh(p);
        }
    }

    /** Hook: after results were announced. */
    protected void onGameEnd(List<UUID> winners) {
    }

    protected void reward(Player p, String type) {
        double amount = plugin.getConfigurationManager().config().getDouble("games." + minigame.getId() + ".rewards." + type, 0);
        if (amount > 0) {
            plugin.getPlayerManager().addCoins(p, amount);
            plugin.getMessages().send(p, "game.reward." + type, "coins", TextUtil.formatNumber(amount));
        }
    }

    protected void beginReset() {
        if (state == GameState.RESETTING || disposed) {
            return;
        }
        state = GameState.RESETTING;
        for (Player p : getOnlineOccupants()) {
            try {
                removePlayer(p, false);
            } catch (Exception ex) {
                plugin.getLogger().warning("Error removing " + p.getName() + " during reset: " + ex.getMessage());
            }
        }
        players.clear();
        spectators.clear();
        try {
            onCleanup();
            clearLooseEntities();
        } catch (Exception ex) {
            plugin.getLogger().warning("Cleanup error: " + ex.getMessage());
        }
        resetWorld(ok -> finishReset(ok));
    }

    /** Hook: remove spawned entities (shops, drops), cancel own state. Runs before the world reset. */
    protected void onCleanup() {
    }

    /** Hook: game specific world restoration (e.g. refill a spleef floor). Must call {@code done} exactly once. */
    protected void onResetWorld(Runnable done) {
        done.run();
    }

    private void resetWorld(java.util.function.Consumer<Boolean> done) {
        String resetMode = arena.getYaml().getString("reset-mode", "blocks").toLowerCase();
        World w = world();
        Runnable afterBlocks = () -> {
            try {
                onResetWorld(() -> done.accept(true));
            } catch (Exception ex) {
                plugin.getLogger().severe("Arena reset hook failed for " + arena.getName() + ": " + ex.getMessage());
                done.accept(false);
            }
        };
        switch (resetMode) {
            case "world-copy" -> plugin.getWorldService().resetFromTemplate(arena.getWorldName(), ok -> {
                if (!ok) {
                    plugin.getLogger().severe("World-copy reset of arena " + arena.getName() + " failed. Check "
                            + "worlds.world-copy-reset.enabled in config.yml and the world-templates/" + arena.getWorldName() + " folder.");
                }
                blocks.clear();
                done.accept(ok);
            });
            case "none" -> {
                blocks.clear();
                afterBlocks.run();
            }
            default -> blocks.restore(plugin, w, plugin.getConfigurationManager().config().getInt("worlds.restore-blocks-per-tick", 2000), afterBlocks);
        }
    }

    private void finishReset(boolean ok) {
        state = GameState.RESETTING;
        dispose();
        Bukkit.getPluginManager().callEvent(new ArenaResetEvent(minigame, arena, ok));
    }

    /** Detaches this instance from the minigame and game manager without touching the world. */
    protected void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (registered) {
            plugin.getGameManager().unregister(this);
            registered = false;
        }
        minigame.removeInstance(this);
        if (state != GameState.RESETTING) {
            state = GameState.RESETTING;
        }
        if (!isEmpty()) {
            for (Player p : getOnlineOccupants()) {
                removePlayer(p, false);
            }
        }
        try {
            onCleanup();
        } catch (Exception ex) {
            plugin.getLogger().warning("Cleanup error: " + ex.getMessage());
        }
        plugin.getQueueManager().process(minigame.getId());
    }

    // ------------------------------------------------------------------------------------------------
    // Eliminations, spectators, win detection
    // ------------------------------------------------------------------------------------------------

    protected void makeSpectator(Player p) {
        UUID id = p.getUniqueId();
        players.remove(id);
        spectators.add(id);
        PlayerUtil.reset(p, GameMode.SPECTATOR);
        Location l = arena.getSpectator();
        if (l != null) {
            p.teleport(l);
        }
        plugin.getTabService().update(p);
    }

    /** Permanently removes a player from the active set. */
    protected void eliminate(Player player, @Nullable Player killer, PlayerEliminateEvent.Reason reason, boolean spectate) {
        UUID id = player.getUniqueId();
        if (!players.contains(id)) {
            return;
        }
        players.remove(id);
        plugin.getPlayerManager().addDeath(player, minigame.getId());
        if (killer != null && !killer.equals(player)) {
            kills.merge(killer.getUniqueId(), 1, Integer::sum);
            plugin.getPlayerManager().addKill(killer, minigame.getId());
            reward(killer, "kill");
            plugin.getEffects().play(killer, "kill");
        }
        Bukkit.getPluginManager().callEvent(new PlayerEliminateEvent(minigame, arena, player, killer, reason));
        String key = switch (reason) {
            case KILLED -> killer != null ? "game.death.killed" : "game.death.generic";
            case VOID -> killer != null ? "game.death.void-killed" : "game.death.void";
            case QUIT -> "game.death.quit";
            default -> "game.death.generic";
        };
        broadcast(key, "player", coloredName(player), "killer", killer == null ? "" : coloredName(killer));
        plugin.getEffects().lightning(player.getLocation());
        if (spectate) {
            makeSpectator(player);
            plugin.getMessages().title(player, "eliminated");
            plugin.getEffects().play(player, "eliminated");
        }
        try {
            onEliminated(player, killer, reason);
        } catch (Exception ex) {
            plugin.getLogger().warning("Error in onEliminated: " + ex.getMessage());
        }
        GameTeam team = teamByPlayer.get(id);
        if (team != null && !team.isEliminated() && aliveCount(team) == 0) {
            team.setEliminated(true);
            onTeamEliminated(team);
        }
        for (Player p : getOnlineOccupants()) {
            plugin.getScoreboardService().refresh(p);
        }
        if (spectate) {
            checkWin();
        }
    }

    protected void onEliminated(Player player, @Nullable Player killer, PlayerEliminateEvent.Reason reason) {
    }

    protected void onTeamEliminated(GameTeam team) {
        broadcast("game.team-eliminated", "team", team.getColoredName());
    }

    protected int aliveCount(GameTeam team) {
        int n = 0;
        for (UUID id : team.getMembers()) {
            if (players.contains(id)) {
                n++;
            }
        }
        return n;
    }

    protected List<GameTeam> aliveTeams() {
        List<GameTeam> out = new ArrayList<>();
        for (GameTeam t : teams.values()) {
            if (aliveCount(t) > 0) {
                out.add(t);
            }
        }
        return out;
    }

    protected String coloredName(Player p) {
        GameTeam t = teamByPlayer.get(p.getUniqueId());
        return (t != null && t.getColor() != null ? t.getColor().tag() : "<white>") + p.getName() + "<reset>";
    }

    /** Default win rule: one team (or one player in free-for-all) left standing. */
    protected void checkWin() {
        if (state != GameState.IN_GAME) {
            return;
        }
        if (!teams.isEmpty()) {
            List<GameTeam> alive = aliveTeams();
            if (alive.size() <= 1) {
                endGame(alive.isEmpty() ? List.of() : new ArrayList<>(alive.get(0).getMembers()));
            }
        } else if (players.size() <= 1) {
            endGame(new ArrayList<>(players));
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Combat tracking
    // ------------------------------------------------------------------------------------------------

    protected void recordAttack(Player victim, Player attacker) {
        lastAttacker.put(victim.getUniqueId(), attacker.getUniqueId());
        lastAttackTime.put(victim.getUniqueId(), System.currentTimeMillis());
    }

    /** The player that damaged {@code victim} within the last 10 seconds, if still in this game. */
    @Nullable
    protected Player recentAttacker(Player victim) {
        UUID a = lastAttacker.get(victim.getUniqueId());
        Long t = lastAttackTime.get(victim.getUniqueId());
        if (a == null || t == null || System.currentTimeMillis() - t > 10_000L) {
            return null;
        }
        Player p = Bukkit.getPlayer(a);
        return p != null && players.contains(a) ? p : null;
    }

    public void handleDeath(Player victim, @Nullable Player killer, EntityDamageEvent.DamageCause cause) {
        if (state != GameState.IN_GAME || !players.contains(victim.getUniqueId())) {
            return;
        }
        Player credited = killer != null ? killer : recentAttacker(victim);
        onPlayerDeath(victim, credited, cause);
    }

    /** Hook: default behaviour is final elimination. BedWars overrides this for respawns. */
    protected void onPlayerDeath(Player victim, @Nullable Player killer, EntityDamageEvent.DamageCause cause) {
        boolean voidDeath = cause == EntityDamageEvent.DamageCause.VOID;
        eliminate(victim, killer, voidDeath ? PlayerEliminateEvent.Reason.VOID
                : killer != null ? PlayerEliminateEvent.Reason.KILLED : PlayerEliminateEvent.Reason.OTHER, true);
    }

    // ------------------------------------------------------------------------------------------------
    // Scoreboard variables
    // ------------------------------------------------------------------------------------------------

    @Override
    public Map<String, String> getScoreboardVariables(Player player) {
        Map<String, String> v = new HashMap<>();
        v.put("game", minigame.getDisplayPlain());
        v.put("arena", arena.getName());
        v.put("mode", getModeName());
        v.put("state", plugin.getMessages().text("state." + state.name().toLowerCase()));
        v.put("players", String.valueOf(players.size()));
        v.put("max", String.valueOf(arena.getMaxPlayers()));
        v.put("min", String.valueOf(arena.getMinPlayers()));
        v.put("countdown", String.valueOf(Math.max(0, countdown)));
        v.put("countdown_line", state == GameState.STARTING
                ? plugin.getMessages().text("game.scoreboard-countdown", "seconds", String.valueOf(Math.max(0, countdown))) : "");
        v.put("time", TextUtil.formatTime(elapsed));
        int left = getTimeLeft();
        v.put("timeleft", left < 0 ? "-" : TextUtil.formatTime(left));
        v.put("kills", String.valueOf(kills.getOrDefault(player.getUniqueId(), 0)));
        GameTeam t = teamByPlayer.get(player.getUniqueId());
        v.put("team", t == null ? "-" : t.getName());
        v.put("team_colored", t == null ? "-" : t.getColoredName());
        v.put("alive", String.valueOf(players.size()));
        v.put("teams_alive", String.valueOf(aliveTeams().size()));
        return v;
    }

    // ------------------------------------------------------------------------------------------------
    // Default event behaviour (games call super and then add their rules)
    // ------------------------------------------------------------------------------------------------

    private boolean frozenOrLobby(Player p) {
        return state != GameState.IN_GAME || spectators.contains(p.getUniqueId());
    }

    @Override
    public void onBlockBreak(Player player, BlockBreakEvent e) {
        if (frozenOrLobby(player)) {
            e.setCancelled(true);
            explainBlocked(player);
        }
    }

    @Override
    public void onBlockPlace(Player player, BlockPlaceEvent e) {
        if (frozenOrLobby(player)) {
            e.setCancelled(true);
            explainBlocked(player);
        }
    }

    private void explainBlocked(Player player) {
        plugin.getMessages().actionBar(player, spectators.contains(player.getUniqueId())
                ? "game.build-blocked-spectator" : "game.build-blocked-not-started", "state", state.name());
    }

    /** Whether players may damage each other right now. */
    protected boolean isPvpEnabled() {
        return true;
    }

    protected boolean fallDamageEnabled() {
        return true;
    }

    @Override
    public void onDamage(Player victim, EntityDamageEvent e) {
        if (frozenOrLobby(victim)) {
            e.setCancelled(true);
            return;
        }
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && !fallDamageEnabled()) {
            e.setCancelled(true);
        }
    }

    @Override
    public void onDamageByEntity(Player victim, EntityDamageByEntityEvent e) {
        Player attacker = resolveAttacker(e.getDamager());
        if (attacker == null) {
            return;
        }
        if (attacker.equals(victim)) {
            return;
        }
        if (!isPvpEnabled() || frozenOrLobby(attacker) || !players.contains(attacker.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        GameTeam a = teamByPlayer.get(attacker.getUniqueId());
        GameTeam b = teamByPlayer.get(victim.getUniqueId());
        if (a != null && a == b) {
            e.setCancelled(true);
            return;
        }
        recordAttack(victim, attacker);
    }

    @Override
    public void onAttack(Player attacker, EntityDamageByEntityEvent e) {
        if (spectators.contains(attacker.getUniqueId()) || state != GameState.IN_GAME) {
            e.setCancelled(true);
        }
    }

    protected Player resolveAttacker(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }

    @Override
    public void onInteract(Player player, PlayerInteractEvent e) {
        if (state != GameState.IN_GAME || spectators.contains(player.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @Override
    public void onDrop(Player player, PlayerDropItemEvent e) {
        if (frozenOrLobby(player)) {
            e.setCancelled(true);
        }
    }

    @Override
    public void onPickup(Player player, EntityPickupItemEvent e) {
        if (frozenOrLobby(player)) {
            e.setCancelled(true);
        }
    }

    @Override
    public void onFoodChange(Player player, FoodLevelChangeEvent e) {
        if (state != GameState.IN_GAME) {
            e.setCancelled(true);
        }
    }

    @Override
    public boolean onItemAction(Player player, String action) {
        if (action.equals("leave")) {
            minigame.leave(player);
            return true;
        }
        return false;
    }

    @Override
    public String getTabPrefix(Player player) {
        GameTeam t = teamByPlayer.get(player.getUniqueId());
        if (t == null || t.getColor() == null) {
            return null;
        }
        return t.getColor().tag() + "[" + t.getName().charAt(0) + "] ";
    }

    @Override
    public Location getRespawnLocation(Player player) {
        Location l = arena.getSpectator();
        return l != null ? l : arena.getLobby();
    }
}

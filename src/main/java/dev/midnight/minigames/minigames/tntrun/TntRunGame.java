package dev.midnight.minigames.minigames.tntrun;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.api.events.PlayerEliminateEvent;
import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.game.AbstractArenaMinigame;
import dev.midnight.minigames.core.game.GameInstance;
import dev.midnight.minigames.core.game.GameState;
import dev.midnight.minigames.core.game.ModeDefinition;
import dev.midnight.minigames.util.Cuboid;
import dev.midnight.minigames.util.ItemFactory;
import dev.midnight.minigames.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * One TNT Run round. Blocks under a player's feet are queued and removed after a short delay (together with the block
 * below them, which gives the classic sand-over-TNT look). Everything removed is tracked so the arena resets itself.
 */
final class TntRunGame extends GameInstance {

    private static final long TASK_PERIOD = 2L;

    private final Random random = new Random();
    private final Cuboid floor;
    private final int removeDelayTicks;
    private final boolean removeBelow;
    private final int fallDepth;
    private final int graceSeconds;
    private final int doubleJumps;
    private final Set<Material> keep = EnumSet.noneOf(Material.class);
    /** Packed block position -> value of {@link #clock} when it disappears. */
    private final Map<Long, Long> pending = new HashMap<>();
    /** Counts game ticks (the removal task runs every 2 ticks), so lag stretches the delay instead of skipping it. */
    private long clock;
    private final Map<UUID, Integer> jumpsLeft = new HashMap<>();
    private BukkitTask task;

    TntRunGame(MidnightMinigames plugin, AbstractArenaMinigame minigame, Arena arena, ModeDefinition mode) {
        super(plugin, minigame, arena, mode);
        var cfg = plugin.getConfigurationManager().game("tntrun");
        this.floor = arena.getRegion("floor");
        this.removeDelayTicks = Math.max(0, cfg.getInt("remove-delay-ticks", 10));
        this.removeBelow = cfg.getBoolean("remove-block-below", true);
        this.fallDepth = Math.max(1, cfg.getInt("fall-depth", 2));
        this.graceSeconds = Math.max(0, cfg.getInt("grace-seconds", 3));
        this.doubleJumps = Math.max(0, mode.getRaw().getInt("double-jumps", cfg.getInt("double-jumps", 3)));
        for (String name : cfg.getStringList("keep-blocks")) {
            keep.add(ItemFactory.material(name, Material.BEDROCK));
        }
    }

    @Override
    protected void onGameStart() {
        if (floor == null) {
            throw new IllegalStateException("arena has no 'floor' region");
        }
        List<Location> spawns = arena.getSpawns();
        int i = 0;
        for (Player p : getOnlinePlayers()) {
            p.teleport(spawns.isEmpty() ? randomTop() : spawns.get(i++ % spawns.size()));
            if (doubleJumps > 0) {
                jumpsLeft.put(p.getUniqueId(), doubleJumps);
                p.setAllowFlight(true);
            }
            plugin.getScoreboardService().refresh(p);
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::processPending, TASK_PERIOD, TASK_PERIOD);
    }

    /** A random spot on the highest layer of the floor. */
    private Location randomTop() {
        World w = world();
        int x = floor.minX + random.nextInt(floor.maxX - floor.minX + 1);
        int z = floor.minZ + random.nextInt(floor.maxZ - floor.minZ + 1);
        int y = floor.maxY;
        while (y > floor.minY && w.getBlockAt(x, y, z).getType().isAir()) {
            y--;
        }
        return new Location(w, x + 0.5, y + 1, z + 0.5);
    }

    // ---- block removal -------------------------------------------------------------------------------------------

    private void queueUnder(Location l) {
        if (graceSeconds > 0 && elapsed < graceSeconds) {
            return;
        }
        World w = l.getWorld();
        double r = 0.3;
        int y = (int) Math.floor(l.getY() - 0.1);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int bx = (int) Math.floor(l.getX() + sx * r);
                int bz = (int) Math.floor(l.getZ() + sz * r);
                if (floor.contains(bx, y, bz) && !w.getBlockAt(bx, y, bz).getType().isAir()) {
                    pending.putIfAbsent(LocationUtil.pack(bx, y, bz), clock + removeDelayTicks);
                }
            }
        }
    }

    private void processPending() {
        clock += TASK_PERIOD;
        if (state != GameState.IN_GAME || pending.isEmpty()) {
            return;
        }
        World w = world();
        long now = clock;
        for (Iterator<Map.Entry<Long, Long>> it = pending.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Long> e = it.next();
            if (e.getValue() > now) {
                continue;
            }
            it.remove();
            long k = e.getKey();
            int x = LocationUtil.unpackX(k);
            int y = LocationUtil.unpackY(k);
            int z = LocationUtil.unpackZ(k);
            vanish(w.getBlockAt(x, y, z));
            if (removeBelow && floor.contains(x, y - 1, z)) {
                vanish(w.getBlockAt(x, y - 1, z));
            }
        }
    }

    private void vanish(Block b) {
        Material m = b.getType();
        if (m.isAir() || keep.contains(m)) {
            return;
        }
        blocks.record(b);
        b.setType(Material.AIR, false);
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        pending.clear();
    }

    // ---- movement and elimination ------------------------------------------------------------------------------

    @Override
    public boolean wantsMoveEvents() {
        return state == GameState.IN_GAME;
    }

    @Override
    public void onMove(Player player, PlayerMoveEvent e) {
        if (!players.contains(player.getUniqueId())) {
            return;
        }
        Location to = e.getTo();
        if (to.getY() < floor.minY - fallDepth || to.getY() < arena.getVoidY()) {
            eliminate(player, null, PlayerEliminateEvent.Reason.VOID, true);
            return;
        }
        queueUnder(to);
    }

    @Override
    protected void onTick() {
        for (Player p : getOnlinePlayers()) { // players standing still on the edge of a block
            if (players.contains(p.getUniqueId())) {
                queueUnder(p.getLocation());
            }
        }
    }

    @Override
    protected void onEliminated(Player player, Player killer, PlayerEliminateEvent.Reason reason) {
        jumpsLeft.remove(player.getUniqueId());
        if (arena.getSpectator() == null && floor != null && world() != null) {
            player.teleport(floor.center(world()).add(0, 6, 0));
        }
    }

    @Override
    protected void onPlayerDeath(Player victim, Player killer, EntityDamageEvent.DamageCause cause) {
        eliminate(victim, null, PlayerEliminateEvent.Reason.OTHER, true);
    }

    @Override
    public void onToggleFlight(Player player, PlayerToggleFlightEvent e) {
        UUID id = player.getUniqueId();
        if (state != GameState.IN_GAME || !players.contains(id)) {
            return;
        }
        e.setCancelled(true);
        player.setFlying(false);
        int left = jumpsLeft.getOrDefault(id, 0);
        if (left <= 0) {
            player.setAllowFlight(false);
            return;
        }
        jumpsLeft.put(id, --left);
        player.setVelocity(player.getLocation().getDirection().setY(0).normalize().multiply(0.35).setY(0.85));
        plugin.getMessages().actionBar(player, "tntrun.double-jump", "left", String.valueOf(left));
        if (left <= 0) {
            player.setAllowFlight(false);
        }
    }

    // ---- rules -------------------------------------------------------------------------------------------------------

    @Override
    public void onDamage(Player victim, EntityDamageEvent e) {
        e.setCancelled(true);
    }

    @Override
    public void onBlockBreak(Player player, BlockBreakEvent e) {
        e.setCancelled(true);
    }

    @Override
    public void onBlockPlace(Player player, BlockPlaceEvent e) {
        e.setCancelled(true);
    }

    @Override
    protected boolean isPvpEnabled() {
        return false;
    }

    @Override
    protected void onPlayerRemoved(Player player) {
        jumpsLeft.remove(player.getUniqueId());
        player.setAllowFlight(false);
    }

    @Override
    protected void onGameEnd(List<UUID> winners) {
        stopTask();
    }

    @Override
    protected void onCleanup() {
        stopTask();
    }

    @Override
    public Map<String, String> getScoreboardVariables(Player player) {
        Map<String, String> v = super.getScoreboardVariables(player);
        v.put("jumps", String.valueOf(jumpsLeft.getOrDefault(player.getUniqueId(), 0)));
        return v;
    }

    /** The floor is restored by the block tracker; nothing else to do here. */
    @Override
    protected void onResetWorld(Runnable done) {
        done.run();
    }
}

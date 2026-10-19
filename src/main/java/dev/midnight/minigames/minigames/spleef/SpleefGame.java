package dev.midnight.minigames.minigames.spleef;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.api.events.PlayerEliminateEvent;
import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.game.AbstractArenaMinigame;
import dev.midnight.minigames.core.game.GameInstance;
import dev.midnight.minigames.core.game.ModeDefinition;
import dev.midnight.minigames.util.Cuboid;
import dev.midnight.minigames.util.ItemFactory;
import dev.midnight.minigames.util.PlayerUtil;
import dev.midnight.minigames.util.RegionFiller;
import dev.midnight.minigames.util.WeightedTable;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** One Spleef match: several rounds on a refilled floor. */
final class SpleefGame extends GameInstance {

    private final Random random = new Random();
    private final Cuboid floor;
    private final int totalRounds;
    private final int intermissionSeconds;
    private final int fallDepth;
    private final int snowballs;

    private int round;
    private boolean roundActive;
    private int intermission = -1;
    private boolean filling;
    private final Set<UUID> roundDead = new HashSet<>();
    private final Map<UUID, Integer> scores = new HashMap<>();

    SpleefGame(MidnightMinigames plugin, AbstractArenaMinigame minigame, Arena arena, ModeDefinition mode) {
        super(plugin, minigame, arena, mode);
        YamlConfiguration cfg = plugin.getConfigurationManager().game("spleef");
        this.floor = arena.getRegion("floor");
        this.totalRounds = Math.max(1, mode.getRaw().getInt("rounds", cfg.getInt("rounds", 3)));
        this.intermissionSeconds = Math.max(1, cfg.getInt("round-intermission", 5));
        this.fallDepth = Math.max(1, cfg.getInt("floor.fall-depth", 3));
        this.snowballs = Math.max(0, mode.getRaw().getInt("snowballs", cfg.getInt("snowballs", 0)));
    }

    private WeightedTable<Material> floorTable() {
        WeightedTable<Material> table = new WeightedTable<>();
        ConfigurationSection blocks = plugin.getConfigurationManager().game("spleef").getConfigurationSection("floor.blocks");
        if (blocks != null) {
            for (String key : blocks.getKeys(false)) {
                Material m = ItemFactory.material(key, Material.SNOW_BLOCK);
                table.add(m, blocks.getDouble(key, 1));
            }
        }
        if (table.isEmpty()) {
            table.add(Material.SNOW_BLOCK, 1);
        }
        return table;
    }

    @Override
    protected void onGameStart() {
        if (floor == null) {
            throw new IllegalStateException("arena has no 'floor' region");
        }
        round = 0;
        scores.clear();
        startRound();
    }

    private void startRound() {
        round++;
        roundActive = false;
        intermission = -1;
        roundDead.clear();
        World w = world();
        filling = true;
        WeightedTable<Material> table = floorTable();
        RegionFiller.fill(plugin, w, floor, () -> table.pick(random),
                plugin.getConfigurationManager().config().getInt("worlds.restore-blocks-per-tick", 2000), () -> {
                    filling = false;
                    if (state != dev.midnight.minigames.core.game.GameState.IN_GAME) {
                        return;
                    }
                    beginRoundPlay();
                });
    }

    private void beginRoundPlay() {
        List<Location> spawns = arena.getSpawns();
        int i = 0;
        ItemStack tool = shovel();
        for (Player p : getOnlinePlayers()) {
            PlayerUtil.reset(p, GameMode.SURVIVAL);
            Location spawn = spawns.isEmpty() ? randomOnFloor() : spawns.get(i++ % spawns.size());
            p.teleport(spawn);
            p.getInventory().addItem(tool.clone());
            if (snowballs > 0) {
                p.getInventory().addItem(new ItemStack(Material.SNOWBALL, snowballs));
            }
            plugin.getScoreboardService().refresh(p);
        }
        roundActive = true;
        broadcast("spleef.round-start", "round", String.valueOf(round), "rounds", String.valueOf(totalRounds));
        sound("start");
    }

    private Location randomOnFloor() {
        double x = floor.minX + 0.5 + random.nextInt(floor.maxX - floor.minX + 1);
        double z = floor.minZ + 0.5 + random.nextInt(floor.maxZ - floor.minZ + 1);
        return new Location(world(), x, floor.maxY + 1, z);
    }

    private ItemStack shovel() {
        ConfigurationSection s = plugin.getConfigurationManager().game("spleef").getConfigurationSection("tool");
        if (s != null) {
            return ItemFactory.build(s);
        }
        return new ItemStack(Material.IRON_SHOVEL);
    }

    private int aliveInRound() {
        int n = 0;
        for (UUID id : players) {
            if (!roundDead.contains(id)) {
                n++;
            }
        }
        return n;
    }

    @Override
    protected void onTick() {
        if (intermission > 0 && --intermission == 0) {
            startRound();
        }
    }

    @Override
    public boolean wantsMoveEvents() {
        return state == dev.midnight.minigames.core.game.GameState.IN_GAME;
    }

    @Override
    public void onMove(Player player, PlayerMoveEvent e) {
        if (!roundActive || !players.contains(player.getUniqueId()) || roundDead.contains(player.getUniqueId())) {
            return;
        }
        Location to = e.getTo();
        if (to.getBlockY() < floor.minY - fallDepth || isLiquid(to)) {
            roundEliminate(player);
        }
    }

    private boolean isLiquid(Location l) {
        Material m = l.getBlock().getType();
        return m == Material.LAVA || m == Material.WATER;
    }

    private void roundEliminate(Player p) {
        if (!roundDead.add(p.getUniqueId())) {
            return;
        }
        broadcast("spleef.out", "player", p.getName(), "alive", String.valueOf(aliveInRound()));
        PlayerUtil.reset(p, GameMode.SPECTATOR);
        Location spec = arena.getSpectator();
        p.teleport(spec != null ? spec : randomOnFloor().add(0, 8, 0));
        plugin.getEffects().play(p, "eliminated");
        plugin.getScoreboardService().refresh(p);
        checkRoundEnd();
    }

    private void checkRoundEnd() {
        if (!roundActive || state != dev.midnight.minigames.core.game.GameState.IN_GAME) {
            return;
        }
        if (aliveInRound() > 1) {
            return;
        }
        roundActive = false;
        UUID winner = null;
        for (UUID id : players) {
            if (!roundDead.contains(id)) {
                winner = id;
            }
        }
        if (winner != null) {
            scores.merge(winner, 1, Integer::sum);
            Player wp = Bukkit.getPlayer(winner);
            broadcast("spleef.round-win", "player", wp == null ? "?" : wp.getName(),
                    "score", String.valueOf(scores.get(winner)));
        }
        if (round >= totalRounds) {
            finishMatch();
        } else {
            intermission = intermissionSeconds;
            broadcast("spleef.intermission", "seconds", String.valueOf(intermissionSeconds));
        }
    }

    private void finishMatch() {
        int best = 0;
        for (UUID id : players) {
            best = Math.max(best, scores.getOrDefault(id, 0));
        }
        List<UUID> winners = new ArrayList<>();
        if (best > 0) {
            for (UUID id : players) {
                if (scores.getOrDefault(id, 0) == best) {
                    winners.add(id);
                }
            }
        }
        endGame(winners);
    }

    @Override
    protected void checkWin() {
        if (state != dev.midnight.minigames.core.game.GameState.IN_GAME) {
            return;
        }
        if (players.size() <= 1) {
            endGame(new ArrayList<>(players));
            return;
        }
        checkRoundEnd();
    }

    @Override
    protected void onPlayerDeath(Player victim, Player killer, EntityDamageEvent.DamageCause cause) {
        roundEliminate(victim);
    }

    @Override
    protected void onEliminated(Player player, Player killer, PlayerEliminateEvent.Reason reason) {
        roundDead.remove(player.getUniqueId());
    }

    @Override
    public void onDamage(Player victim, EntityDamageEvent e) {
        e.setCancelled(true); // nobody gets hurt in Spleef - falling out is handled by position checks
    }

    @Override
    public void onBlockBreak(Player player, BlockBreakEvent e) {
        if (!roundActive || !players.contains(player.getUniqueId()) || roundDead.contains(player.getUniqueId())
                || !floor.contains(e.getBlock().getLocation())) {
            e.setCancelled(true);
            return;
        }
        e.setDropItems(false);
        e.setExpToDrop(0);
    }

    /** Snowball spleef: a snowball that hits the floor removes the block it hit. */
    @Override
    public void onProjectileHit(Player shooter, org.bukkit.event.entity.ProjectileHitEvent e) {
        if (!roundActive || !(e.getEntity() instanceof org.bukkit.entity.Snowball) || e.getHitBlock() == null) {
            return;
        }
        if (players.contains(shooter.getUniqueId()) && !roundDead.contains(shooter.getUniqueId())
                && floor.contains(e.getHitBlock().getLocation())) {
            e.getHitBlock().setType(Material.AIR, false);
            e.getEntity().remove();
        }
    }

    @Override
    public void onBlockPlace(Player player, BlockPlaceEvent e) {
        e.setCancelled(true);
    }

    @Override
    public java.util.Map<String, String> getScoreboardVariables(Player player) {
        java.util.Map<String, String> v = super.getScoreboardVariables(player);
        v.put("round", String.valueOf(Math.max(1, round)));
        v.put("rounds", String.valueOf(totalRounds));
        v.put("score", String.valueOf(scores.getOrDefault(player.getUniqueId(), 0)));
        v.put("alive", String.valueOf(aliveInRound()));
        return v;
    }

    @Override
    protected void onResetWorld(Runnable done) {
        if (floor == null || world() == null) {
            done.run();
            return;
        }
        WeightedTable<Material> table = floorTable();
        RegionFiller.fill(plugin, world(), floor, () -> table.pick(random),
                plugin.getConfigurationManager().config().getInt("worlds.restore-blocks-per-tick", 2000), done);
    }

}

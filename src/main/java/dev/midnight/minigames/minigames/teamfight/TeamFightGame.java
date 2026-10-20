package dev.midnight.minigames.minigames.teamfight;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.game.AbstractArenaMinigame;
import dev.midnight.minigames.core.game.GameInstance;
import dev.midnight.minigames.core.game.GameState;
import dev.midnight.minigames.core.game.ModeDefinition;
import dev.midnight.minigames.core.team.GameTeam;
import dev.midnight.minigames.core.team.TeamBalancer;
import dev.midnight.minigames.core.team.TeamColor;
import dev.midnight.minigames.util.ConfigUtil;
import dev.midnight.minigames.util.Cuboid;
import dev.midnight.minigames.util.ItemFactory;
import dev.midnight.minigames.util.PlayerUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared base of the two-team fighting games (The Bridge, Capture the Flag): red against blue, kit from config,
 * respawns at the team spawn, first team to reach {@code score-to-win} wins (highest score when time runs out).
 * Games add their own scoring through {@link #addScore} and the hooks.
 */
public abstract class TeamFightGame extends GameInstance {

    public static final List<String> TEAM_IDS = List.of("red", "blue");

    protected final YamlConfiguration cfg;
    protected final int scoreToWin;
    private final int respawnDelay;
    private final int invulnerableSeconds;
    protected final Map<String, Integer> scores = new LinkedHashMap<>();
    private final Map<UUID, Integer> respawning = new HashMap<>();
    private final Map<UUID, Integer> invulnerableUntil = new HashMap<>();
    /** While true players cannot leave their block (round countdown). */
    protected boolean frozen;

    protected TeamFightGame(MidnightMinigames plugin, AbstractArenaMinigame minigame, Arena arena, ModeDefinition mode) {
        super(plugin, minigame, arena, mode);
        this.cfg = plugin.getConfigurationManager().game(minigame.getId());
        this.scoreToWin = Math.max(1, mode.getRaw().getInt("score-to-win", cfg.getInt("score-to-win", 3)));
        this.respawnDelay = Math.max(0, cfg.getInt("respawn-delay", 0));
        this.invulnerableSeconds = Math.max(0, cfg.getInt("invulnerable-seconds", 3));
    }

    /** Problems that stop an arena from being enabled (shared by Bridge and CTF). */
    public static List<String> validateTeams(Arena arena) {
        List<String> problems = new ArrayList<>();
        for (String t : TEAM_IDS) {
            if (arena.getTeamSpawn(t) == null) {
                problems.add("Missing spawn for team '" + t + "': stand there and run /minigame arena setspawn " + arena.getGameId()
                        + " " + arena.getName() + " " + t);
            }
        }
        return problems;
    }

    // ---- teams and kit ------------------------------------------------------------------------------------

    protected void assignTeams() {
        Map<String, GameTeam> all = new LinkedHashMap<>();
        for (String id : TEAM_IDS) {
            Location spawn = arena.getTeamSpawn(id);
            if (spawn == null) {
                throw new IllegalStateException("arena has no '" + id + "' spawn");
            }
            all.put(id, new GameTeam(id, TeamColor.fromId(id), spawn));
        }
        List<UUID> ids = new ArrayList<>();
        for (Player p : getOnlinePlayers()) {
            ids.add(p.getUniqueId());
        }
        TeamBalancer.assign(ids, new ArrayList<>(all.values()), mode.getTeamSize(), plugin.getPartyManager()::getPartyId);
        for (GameTeam t : all.values()) {
            teams.put(t.getId(), t);
            scores.put(t.getId(), 0);
            for (UUID id : t.getMembers()) {
                teamByPlayer.put(id, t);
            }
        }
    }

    protected GameTeam other(GameTeam t) {
        for (GameTeam o : teams.values()) {
            if (o != t) {
                return o;
            }
        }
        return t;
    }

    /** Resets a player, puts them at their team spawn and hands out the kit. */
    protected void spawnPlayer(Player p) {
        GameTeam team = teamByPlayer.get(p.getUniqueId());
        if (team == null) {
            return;
        }
        PlayerUtil.reset(p, GameMode.SURVIVAL);
        p.teleport(team.getSpawn());
        giveKit(p);
    }

    protected void giveKit(Player p) {
        GameTeam team = teamByPlayer.get(p.getUniqueId());
        var inv = p.getInventory();
        if (team != null && cfg.getBoolean("kit.leather-armor", true)) {
            inv.setHelmet(coloredArmor(Material.LEATHER_HELMET, team));
            inv.setChestplate(coloredArmor(Material.LEATHER_CHESTPLATE, team));
            inv.setLeggings(coloredArmor(Material.LEATHER_LEGGINGS, team));
            inv.setBoots(coloredArmor(Material.LEATHER_BOOTS, team));
        }
        for (ConfigurationSection s : ConfigUtil.sections(cfg, "kit.items")) {
            ItemStack item = ItemFactory.build(s);
            String teamMaterial = s.getString("team-material");
            if (teamMaterial != null && team != null) {
                Material m = team.getColor().material(teamMaterial);
                if (m != null) {
                    item.setType(m);
                }
            }
            int slot = s.getInt("slot", -1);
            if (slot >= 0 && slot < 36) {
                inv.setItem(slot, item);
            } else if (slot == 40) {
                inv.setItemInOffHand(item);
            } else {
                inv.addItem(item);
            }
        }
    }

    /** Puts the normal team helmet back (or nothing when the kit has no leather armour). */
    protected void restoreHelmet(Player p) {
        GameTeam team = teamByPlayer.get(p.getUniqueId());
        boolean armor = team != null && cfg.getBoolean("kit.leather-armor", true);
        p.getInventory().setHelmet(armor ? coloredArmor(Material.LEATHER_HELMET, team) : null);
    }

    protected ItemStack coloredArmor(Material m, GameTeam team) {
        ItemStack item = new ItemStack(m);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(team.getColor().getArmorColor());
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    // ---- scoring and winning ---------------------------------------------------------------------------------

    /** Adds a point and ends the game when the target is reached. Returns true if the game ended. */
    protected boolean addScore(GameTeam team) {
        int now = scores.merge(team.getId(), 1, Integer::sum);
        for (Player p : getOnlineOccupants()) {
            plugin.getScoreboardService().refresh(p);
        }
        if (now >= scoreToWin) {
            endGame(new ArrayList<>(team.getMembers()));
            return true;
        }
        return false;
    }

    protected String scoreLine() {
        StringBuilder sb = new StringBuilder();
        for (GameTeam t : teams.values()) {
            sb.append(sb.length() == 0 ? "" : "<dark_gray> - ").append(t.getColor().tag()).append(scores.getOrDefault(t.getId(), 0));
        }
        return sb.toString();
    }

    @Override
    protected void onTimeLimit() {
        GameTeam best = null;
        boolean tie = false;
        for (GameTeam t : teams.values()) {
            if (best == null || scores.get(t.getId()) > scores.get(best.getId())) {
                best = t;
                tie = false;
            } else if (scores.get(t.getId()).equals(scores.get(best.getId()))) {
                tie = true;
            }
        }
        broadcast("game.time-up");
        endGame(best == null || tie ? List.of() : new ArrayList<>(best.getMembers()));
    }

    @Override
    protected void checkWin() {
        if (state != GameState.IN_GAME || teams.isEmpty()) {
            return;
        }
        List<GameTeam> alive = aliveTeams();
        if (alive.size() <= 1) {
            endGame(alive.isEmpty() ? List.of() : new ArrayList<>(alive.get(0).getMembers()));
        }
    }

    // ---- deaths and respawns -----------------------------------------------------------------------------------

    /** Hook: a player died (before the respawn is scheduled). */
    protected void onDeath(Player victim, @Nullable Player killer) {
    }

    @Override
    protected void onPlayerDeath(Player victim, @Nullable Player killer, EntityDamageEvent.DamageCause cause) {
        UUID id = victim.getUniqueId();
        if (respawning.containsKey(id) || !players.contains(id)) {
            return;
        }
        plugin.getPlayerManager().addDeath(victim, minigame.getId());
        if (killer != null && !killer.equals(victim)) {
            kills.merge(killer.getUniqueId(), 1, Integer::sum);
            plugin.getPlayerManager().addKill(killer, minigame.getId());
            reward(killer, "kill");
            plugin.getEffects().play(killer, "kill");
        }
        boolean voidDeath = cause == EntityDamageEvent.DamageCause.VOID;
        String key = voidDeath ? (killer != null ? "game.death.void-killed" : "game.death.void")
                : (killer != null ? "game.death.killed" : "game.death.generic");
        broadcast(key, "player", coloredName(victim), "killer", killer == null ? "" : coloredName(killer));
        onDeath(victim, killer);
        if (respawnDelay <= 0) {
            respawn(victim);
        } else {
            PlayerUtil.reset(victim, GameMode.SPECTATOR);
            Location spec = arena.getSpectator();
            GameTeam team = teamByPlayer.get(id);
            victim.teleport(spec != null ? spec : team != null ? team.getSpawn() : victim.getLocation());
            respawning.put(id, respawnDelay);
            plugin.getMessages().title(victim, "respawning", "seconds", String.valueOf(respawnDelay));
            plugin.getEffects().play(victim, "eliminated");
        }
    }

    protected void respawn(Player p) {
        if (!players.contains(p.getUniqueId())) {
            return;
        }
        respawning.remove(p.getUniqueId());
        spawnPlayer(p);
        if (invulnerableSeconds > 0) {
            invulnerableUntil.put(p.getUniqueId(), elapsed + invulnerableSeconds);
        }
        plugin.getEffects().play(p, "respawn");
    }

    protected boolean isRespawning(Player p) {
        return respawning.containsKey(p.getUniqueId());
    }

    /** Hook: runs once per second while the game is running (after respawn handling). */
    protected void onSecond() {
    }

    @Override
    protected void onTick() {
        if (!respawning.isEmpty()) {
            for (UUID id : new ArrayList<>(respawning.keySet())) {
                int left = respawning.get(id) - 1;
                Player p = org.bukkit.Bukkit.getPlayer(id);
                if (p == null || !players.contains(id)) {
                    respawning.remove(id);
                } else if (left <= 0) {
                    respawn(p);
                } else {
                    respawning.put(id, left);
                }
            }
        }
        onSecond();
    }

    @Override
    public void onDamage(Player victim, EntityDamageEvent e) {
        super.onDamage(victim, e);
        if (e.isCancelled()) {
            return;
        }
        Integer until = invulnerableUntil.get(victim.getUniqueId());
        if (until != null && elapsed < until) {
            e.setCancelled(true);
        }
    }

    @Override
    protected void onPlayerRemoved(Player player) {
        respawning.remove(player.getUniqueId());
        invulnerableUntil.remove(player.getUniqueId());
        player.setWalkSpeed(0.2f);
    }

    @Override
    protected void onPlayerLeftDuringGame(Player player) {
        respawning.remove(player.getUniqueId());
        player.getInventory().clear();
    }

    // ---- movement ----------------------------------------------------------------------------------------------------

    @Override
    public boolean wantsMoveEvents() {
        return state == GameState.IN_GAME;
    }

    /** Hook for subclasses: a living player moved to another block. */
    protected void onGameMove(Player player, PlayerMoveEvent e) {
    }

    @Override
    public void onMove(Player player, PlayerMoveEvent e) {
        if (!players.contains(player.getUniqueId()) || isRespawning(player)) {
            return;
        }
        Location from = e.getFrom();
        Location to = e.getTo();
        if (frozen && (from.getX() != to.getX() || from.getZ() != to.getZ())) {
            e.setTo(new Location(from.getWorld(), from.getX(), to.getY(), from.getZ(), to.getYaw(), to.getPitch()));
            return;
        }
        if (to.getY() < arena.getVoidY()) {
            onPlayerDeath(player, recentAttacker(player), EntityDamageEvent.DamageCause.VOID);
            return;
        }
        onGameMove(player, e);
    }

    // ---- building ---------------------------------------------------------------------------------------------------------

    protected boolean buildingEnabled() {
        return cfg.getBoolean("building.enabled", false);
    }

    /** Whether a player may place a block here (bounds, spawn protection). Subclasses add protected regions. */
    protected boolean canPlaceAt(Block b) {
        Cuboid bounds = arena.getRegion("bounds");
        if (bounds != null && !bounds.contains(b.getLocation())) {
            return false;
        }
        double radius = cfg.getDouble("building.spawn-protection-radius", 3.0);
        if (radius > 0) {
            Location c = b.getLocation().add(0.5, 0.5, 0.5);
            for (GameTeam t : teams.values()) {
                if (t.getSpawn().getWorld() == c.getWorld() && t.getSpawn().distanceSquared(c) <= radius * radius) {
                    return false;
                }
            }
        }
        int maxY = cfg.getInt("building.max-y", 0);
        return maxY <= 0 || b.getY() <= maxY;
    }

    @Override
    public void onBlockPlace(Player player, BlockPlaceEvent e) {
        super.onBlockPlace(player, e);
        if (e.isCancelled()) {
            return;
        }
        if (!buildingEnabled() || !players.contains(player.getUniqueId()) || !canPlaceAt(e.getBlockPlaced())) {
            e.setCancelled(true);
            plugin.getMessages().actionBar(player, "teamfight.cannot-build");
            return;
        }
        blocks.recordPlaced(e);
    }

    @Override
    public void onBlockBreak(Player player, BlockBreakEvent e) {
        super.onBlockBreak(player, e);
        if (e.isCancelled()) {
            return;
        }
        Block b = e.getBlock();
        if (!blocks.isPlaced(b)) {
            e.setCancelled(true);
            plugin.getMessages().actionBar(player, "teamfight.cannot-break");
            return;
        }
        e.setDropItems(false);
        e.setExpToDrop(0);
        blocks.unmarkPlaced(b);
    }

    @Override
    public void onDrop(Player player, org.bukkit.event.player.PlayerDropItemEvent e) {
        e.setCancelled(true);
    }

    @Override
    public void onPickup(Player player, org.bukkit.event.entity.EntityPickupItemEvent e) {
        e.setCancelled(true);
    }

    // ---- scoreboard ------------------------------------------------------------------------------------------------------

    @Override
    public Map<String, String> getScoreboardVariables(Player player) {
        Map<String, String> v = super.getScoreboardVariables(player);
        for (String id : TEAM_IDS) {
            v.put("score_" + id, String.valueOf(scores.getOrDefault(id, 0)));
        }
        v.put("scores", scoreLine());
        v.put("to_win", String.valueOf(scoreToWin));
        return v;
    }
}

package dev.midnight.minigames.minigames.ctf;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.game.AbstractArenaMinigame;
import dev.midnight.minigames.core.game.ModeDefinition;
import dev.midnight.minigames.core.team.GameTeam;
import dev.midnight.minigames.minigames.teamfight.TeamFightGame;
import dev.midnight.minigames.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One Capture the Flag match. Flags are banner blocks at the team bases. A player touching the enemy flag carries it
 * (glowing, banner on the head); touching an own dropped flag returns it; walking the stolen flag into the own base
 * scores while the own flag is at home. A killed carrier drops the flag, which returns on its own after a while.
 */
final class CtfGame extends TeamFightGame {

    private enum FlagState { HOME, CARRIED, DROPPED }

    private static final class Flag {
        final GameTeam team;
        final Location home;
        FlagState state = FlagState.HOME;
        UUID carrier;
        Location dropped;
        int returnIn;

        Flag(GameTeam team, Location home) {
            this.team = team;
            this.home = home;
        }

        Location position() {
            return state == FlagState.DROPPED && dropped != null ? dropped : home;
        }
    }

    private final Map<String, Flag> flags = new HashMap<>();
    private final double touchRadius;
    private final int returnSeconds;
    private final boolean requireHome;
    private final int slownessAmplifier;

    CtfGame(MidnightMinigames plugin, AbstractArenaMinigame minigame, Arena arena, ModeDefinition mode) {
        super(plugin, minigame, arena, mode);
        this.touchRadius = Math.max(0.5, cfg.getDouble("flag.touch-radius", 1.5));
        this.returnSeconds = Math.max(1, cfg.getInt("flag.return-seconds", 20));
        this.requireHome = cfg.getBoolean("flag.require-own-flag-home", true);
        this.slownessAmplifier = cfg.getInt("flag.carrier-slowness", 0) - 1;
    }

    @Override
    protected void onGameStart() {
        assignTeams();
        for (GameTeam t : teams.values()) {
            Location home = LocationUtil.deserialize(arena.getYaml().getString("data.flags." + t.getId()));
            if (home == null) {
                throw new IllegalStateException("team " + t.getId() + " has no flag location (/minigame arena setflag)");
            }
            Flag f = new Flag(t, home.getBlock().getLocation());
            flags.put(t.getId(), f);
            placeBanner(f, f.home);
        }
        for (Player p : getOnlinePlayers()) {
            spawnPlayer(p);
        }
    }

    // ---- banner blocks ---------------------------------------------------------------------------------------------

    private Material bannerMaterial(GameTeam t) {
        Material m = t.getColor().material("BANNER");
        return m == null ? Material.WHITE_BANNER : m;
    }

    private void placeBanner(Flag f, Location at) {
        Block b = at.getBlock();
        blocks.record(b);
        b.setType(bannerMaterial(f.team), false);
    }

    private void removeBanner(Location at) {
        Block b = at.getBlock();
        if (b.getType().name().endsWith("_BANNER")) {
            blocks.record(b);
            b.setType(Material.AIR, false);
        }
    }

    // ---- flag logic ----------------------------------------------------------------------------------------------------

    private @Nullable Flag carriedBy(Player p) {
        for (Flag f : flags.values()) {
            if (f.state == FlagState.CARRIED && p.getUniqueId().equals(f.carrier)) {
                return f;
            }
        }
        return null;
    }

    private boolean near(Location pl, Location flagLoc) {
        if (pl.getWorld() != flagLoc.getWorld()) {
            return false;
        }
        double dx = pl.getX() - (flagLoc.getBlockX() + 0.5);
        double dz = pl.getZ() - (flagLoc.getBlockZ() + 0.5);
        double dy = pl.getY() - flagLoc.getBlockY();
        return dx * dx + dz * dz <= touchRadius * touchRadius && dy > -1.5 && dy < 2.5;
    }

    /** {@code at} is where the player is (or is moving to; inside a move event getLocation() is still the old spot). */
    private void checkContact(Player p, Location at) {
        GameTeam mine = teamByPlayer.get(p.getUniqueId());
        if (mine == null || !players.contains(p.getUniqueId()) || isRespawning(p)) {
            return;
        }
        Flag own = flags.get(mine.getId());
        Flag enemy = flags.get(other(mine).getId());
        if (own == null || enemy == null) {
            return;
        }
        if (own.state == FlagState.DROPPED && near(at, own.position())) {
            returnFlag(own, p);
            return;
        }
        Flag carried = carriedBy(p);
        if (carried != null) {
            if (near(at, own.home) && (!requireHome || own.state == FlagState.HOME)) {
                capture(p, mine, carried);
            }
            return;
        }
        if (enemy.state != FlagState.CARRIED && near(at, enemy.position())) {
            pickUp(p, enemy);
        }
    }

    private void pickUp(Player p, Flag f) {
        if (f.state == FlagState.DROPPED && f.dropped != null) {
            removeBanner(f.dropped);
        } else {
            removeBanner(f.home);
        }
        f.state = FlagState.CARRIED;
        f.carrier = p.getUniqueId();
        f.dropped = null;
        p.getInventory().setHelmet(new ItemStack(bannerMaterial(f.team)));
        p.setGlowing(true);
        if (slownessAmplifier >= 0) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, slownessAmplifier, false, false));
        }
        broadcast("ctf.picked-up", "player", coloredName(p), "team", f.team.getColoredName());
        plugin.getEffects().play(p, "kill");
    }

    private void releaseCarrier(Flag f) {
        Player carrier = f.carrier == null ? null : org.bukkit.Bukkit.getPlayer(f.carrier);
        f.carrier = null;
        if (carrier != null) {
            carrier.setGlowing(false);
            if (slownessAmplifier >= 0) {
                carrier.removePotionEffect(PotionEffectType.SLOWNESS);
            }
            if (players.contains(carrier.getUniqueId())) {
                restoreHelmet(carrier);
            }
        }
    }

    private void drop(Flag f, Location where) {
        releaseCarrier(f);
        Block b = where.getBlock();
        for (int i = 0; i < 6 && !b.getType().isAir(); i++) {
            b = b.getRelative(0, 1, 0);
        }
        f.state = FlagState.DROPPED;
        f.dropped = b.getLocation();
        f.returnIn = returnSeconds;
        placeBanner(f, f.dropped);
        broadcast("ctf.dropped", "team", f.team.getColoredName());
    }

    private void returnFlag(Flag f, @Nullable Player by) {
        if (f.dropped != null) {
            removeBanner(f.dropped);
        }
        f.state = FlagState.HOME;
        f.dropped = null;
        f.carrier = null;
        placeBanner(f, f.home);
        if (by != null) {
            broadcast("ctf.returned-by", "player", coloredName(by), "team", f.team.getColoredName());
            plugin.getEffects().play(by, "kill");
        } else {
            broadcast("ctf.returned", "team", f.team.getColoredName());
        }
    }

    private void capture(Player p, GameTeam scoringTeam, Flag stolen) {
        releaseCarrier(stolen);
        stolen.state = FlagState.HOME;
        stolen.dropped = null;
        placeBanner(stolen, stolen.home);
        broadcast("ctf.captured", "player", coloredName(p), "team", scoringTeam.getColoredName(),
                "enemy", stolen.team.getColoredName());
        plugin.getEffects().firework(p.getLocation());
        sound("win");
        addScore(scoringTeam);
    }

    // ---- events ----------------------------------------------------------------------------------------------------------

    @Override
    protected void onGameMove(Player player, PlayerMoveEvent e) {
        checkContact(player, e.getTo());
    }

    @Override
    protected void onDeath(Player victim, @Nullable Player killer) {
        Flag f = carriedBy(victim);
        if (f != null) {
            drop(f, victim.getLocation());
        }
    }

    @Override
    protected void onPlayerLeftDuringGame(Player player) {
        Flag f = carriedBy(player);
        if (f != null) {
            drop(f, player.getLocation());
        }
        super.onPlayerLeftDuringGame(player);
    }

    @Override
    protected void onSecond() {
        for (Flag f : flags.values()) {
            if (f.state == FlagState.DROPPED && --f.returnIn <= 0) {
                returnFlag(f, null);
            }
            World w = f.position().getWorld();
            if (w != null && f.state != FlagState.CARRIED) {
                w.spawnParticle(Particle.END_ROD, f.position().clone().add(0.5, 2.0, 0.5), 3, 0.1, 0.4, 0.1, 0.01);
            }
        }
        for (Player p : getOnlinePlayers()) { // catches players standing still next to a flag
            checkContact(p, p.getLocation());
        }
    }

    @Override
    protected void onCleanup() {
        for (Flag f : flags.values()) {
            releaseCarrier(f);
        }
    }

    @Override
    public Map<String, String> getScoreboardVariables(Player player) {
        Map<String, String> v = super.getScoreboardVariables(player);
        for (String id : TEAM_IDS) {
            Flag f = flags.get(id);
            v.put("flag_" + id, f == null ? "-" : plugin.getMessages().text("ctf.status-" + f.state.name().toLowerCase()));
        }
        Flag carried = carriedBy(player);
        v.put("carrying", carried == null ? plugin.getMessages().text("ctf.carry-no") : plugin.getMessages().text("ctf.carry-yes"));
        return v;
    }
}

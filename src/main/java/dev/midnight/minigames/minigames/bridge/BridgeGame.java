package dev.midnight.minigames.minigames.bridge;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.game.AbstractArenaMinigame;
import dev.midnight.minigames.core.game.ModeDefinition;
import dev.midnight.minigames.core.team.GameTeam;
import dev.midnight.minigames.minigames.teamfight.TeamFightGame;
import dev.midnight.minigames.util.Cuboid;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Map;

/** One Bridge match. The goal region named goal_&lt;team&gt; belongs to that team; the other team scores by entering it. */
final class BridgeGame extends TeamFightGame {

    private final int countdownSeconds;
    private int freezeLeft;
    private boolean scoring;

    BridgeGame(MidnightMinigames plugin, AbstractArenaMinigame minigame, Arena arena, ModeDefinition mode) {
        super(plugin, minigame, arena, mode);
        this.countdownSeconds = Math.max(0, cfg.getInt("round-countdown", 5));
    }

    @Override
    protected void onGameStart() {
        for (String t : TEAM_IDS) {
            if (arena.getRegion("goal_" + t) == null) {
                throw new IllegalStateException("arena has no 'goal_" + t + "' region");
            }
        }
        assignTeams();
        startRound();
    }

    /** Puts everyone back on their spawn with a fresh kit and a short countdown. Placed blocks stay. */
    private void startRound() {
        for (Player p : getOnlinePlayers()) {
            if (players.contains(p.getUniqueId())) {
                spawnPlayer(p);
            }
        }
        freezeLeft = countdownSeconds;
        frozen = freezeLeft > 0;
        scoring = false;
    }

    @Override
    protected void onSecond() {
        if (freezeLeft > 0) {
            freezeLeft--;
            if (freezeLeft > 0) {
                for (Player p : getOnlinePlayers()) {
                    plugin.getMessages().title(p, "countdown", "seconds", String.valueOf(freezeLeft));
                }
            } else {
                frozen = false;
                sound("start");
                for (Player p : getOnlinePlayers()) {
                    plugin.getMessages().title(p, "start");
                }
            }
        }
    }

    @Override
    protected void onGameMove(Player player, PlayerMoveEvent e) {
        if (scoring || frozen) {
            return;
        }
        GameTeam mine = teamByPlayer.get(player.getUniqueId());
        if (mine == null) {
            return;
        }
        Cuboid enemyGoal = arena.getRegion("goal_" + other(mine).getId());
        if (enemyGoal != null && enemyGoal.contains(e.getTo())) {
            score(player, mine);
        }
    }

    private void score(Player scorer, GameTeam team) {
        scoring = true;
        broadcast("bridge.goal", "player", coloredName(scorer), "team", team.getColoredName());
        plugin.getEffects().firework(scorer.getLocation());
        sound("win");
        if (addScore(team)) {
            return;
        }
        startRound();
    }

    @Override
    protected boolean canPlaceAt(Block b) {
        for (String t : TEAM_IDS) {
            Cuboid goal = arena.getRegion("goal_" + t);
            if (goal != null && goal.contains(b.getLocation())) {
                return false;
            }
        }
        return super.canPlaceAt(b);
    }

    @Override
    protected boolean buildingEnabled() {
        return cfg.getBoolean("building.enabled", true);
    }

    @Override
    public Map<String, String> getScoreboardVariables(Player player) {
        Map<String, String> v = super.getScoreboardVariables(player);
        v.put("goals", String.valueOf(scores.getOrDefault(teamIdOf(player), 0)));
        return v;
    }

    private String teamIdOf(Player p) {
        GameTeam t = teamByPlayer.get(p.getUniqueId());
        return t == null ? "" : t.getId();
    }
}

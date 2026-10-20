package dev.midnight.minigames.minigames.bridge;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.game.AbstractArenaMinigame;
import dev.midnight.minigames.core.game.GameInstance;
import dev.midnight.minigames.core.game.ModeDefinition;
import dev.midnight.minigames.minigames.teamfight.TeamFightGame;

import java.util.ArrayList;
import java.util.List;

/**
 * The Bridge: two teams, two goals. Cross the bridge and jump into the enemy goal to score; first to the target wins.
 * Arena setup: red + blue spawn, regions goal_red / goal_blue (the goal each team defends), see ARENAS.md.
 */
public final class BridgeMinigame extends AbstractArenaMinigame {

    public BridgeMinigame(MidnightMinigames plugin) {
        super(plugin, "bridge", "<gold>The Bridge");
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected GameInstance createInstance(Arena arena, ModeDefinition mode) {
        return new BridgeGame(plugin, this, arena, mode);
    }

    @Override
    public List<String> validateArena(Arena arena) {
        List<String> problems = new ArrayList<>(super.validateArena(arena));
        problems.addAll(TeamFightGame.validateTeams(arena));
        for (String t : TeamFightGame.TEAM_IDS) {
            if (arena.getRegion("goal_" + t) == null) {
                problems.add("Goal region for team '" + t + "' missing: /minigame pos1, pos2, then /minigame arena setregion bridge "
                        + arena.getName() + " goal_" + t + "  (the goal that team defends)");
            }
        }
        return problems;
    }
}

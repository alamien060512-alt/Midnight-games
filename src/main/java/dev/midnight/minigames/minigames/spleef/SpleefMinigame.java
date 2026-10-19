package dev.midnight.minigames.minigames.spleef;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.game.AbstractArenaMinigame;
import dev.midnight.minigames.core.game.GameInstance;
import dev.midnight.minigames.core.game.ModeDefinition;

import java.util.ArrayList;
import java.util.List;

/** Spleef: break the floor under your opponents. Last player standing wins the round; most rounds wins the match. */
public final class SpleefMinigame extends AbstractArenaMinigame {

    public SpleefMinigame(MidnightMinigames plugin) {
        super(plugin, "spleef", "<aqua>Spleef");
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected GameInstance createInstance(Arena arena, ModeDefinition mode) {
        return new SpleefGame(plugin, this, arena, mode);
    }

    @Override
    public List<String> validateArena(Arena arena) {
        List<String> problems = new ArrayList<>(super.validateArena(arena));
        if (arena.getRegion("floor") == null) {
            problems.add("Floor region missing: select it with /minigame pos1 & pos2, then /minigame arena setregion spleef "
                    + arena.getName() + " floor");
        }
        return problems;
    }
}

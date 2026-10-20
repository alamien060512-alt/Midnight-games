package dev.midnight.minigames.minigames.tntrun;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.game.AbstractArenaMinigame;
import dev.midnight.minigames.core.game.GameInstance;
import dev.midnight.minigames.core.game.ModeDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * TNT Run: the blocks vanish shortly after you step on them. Last player standing wins.
 * Arena setup: the floor region (all layers) as region "floor", spawns or random spawn on the top layer.
 */
public final class TntRunMinigame extends AbstractArenaMinigame {

    public TntRunMinigame(MidnightMinigames plugin) {
        super(plugin, "tntrun", "<red>TNT Run");
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected GameInstance createInstance(Arena arena, ModeDefinition mode) {
        return new TntRunGame(plugin, this, arena, mode);
    }

    @Override
    public List<String> validateArena(Arena arena) {
        List<String> problems = new ArrayList<>(super.validateArena(arena));
        if (arena.getRegion("floor") == null) {
            problems.add("Floor region missing: select every layer with /minigame pos1 & pos2, then /minigame arena setregion tntrun "
                    + arena.getName() + " floor");
        }
        return problems;
    }
}

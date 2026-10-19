package dev.midnight.minigames.minigames.onevone;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.minigames.duels.DuelsMinigame;

/**
 * Simple 1v1 queue built on the duel system. Modes are defined in config.yml -&gt; games.1v1.modes and point to a kit
 * of games/duels.yml (or "random"). Arenas are stored under arenas/1v1/.
 */
public final class OneVOneMinigame extends DuelsMinigame {

    public OneVOneMinigame(MidnightMinigames plugin) {
        super(plugin, "1v1", "<green>1v1", false);
    }
}

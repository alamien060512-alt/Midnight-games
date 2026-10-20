package dev.midnight.minigames.minigames.dropper;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.game.AbstractArenaMinigame;
import dev.midnight.minigames.core.game.GameInstance;
import dev.midnight.minigames.core.game.GameState;
import dev.midnight.minigames.core.game.ModeDefinition;
import dev.midnight.minigames.util.PlayerUtil;
import dev.midnight.minigames.util.TextUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One Dropper race. Touching a block on the way down sends the player back to the start of the level. */
final class DropperGame extends GameInstance {

    private final List<DropperMinigame.Level> levels;
    private final Map<UUID, Integer> levelOf = new HashMap<>();
    private final Map<UUID, Integer> fails = new HashMap<>();
    /** Players that already left the start platform; collisions only count from then on. */
    private final Set<UUID> falling = new HashSet<>();
    private final double armDepth;
    private final double hitbox;
    private final boolean voidResets;

    DropperGame(MidnightMinigames plugin, AbstractArenaMinigame minigame, Arena arena, ModeDefinition mode) {
        super(plugin, minigame, arena, mode);
        var cfg = plugin.getConfigurationManager().game("dropper");
        this.levels = DropperMinigame.levelsOf(arena);
        this.armDepth = Math.max(0.5, cfg.getDouble("arm-depth", 1.5));
        this.hitbox = Math.max(0.2, cfg.getDouble("hitbox", 0.31));
        this.voidResets = cfg.getBoolean("void-resets", true);
    }

    @Override
    protected void onGameStart() {
        if (levels.isEmpty()) {
            throw new IllegalStateException("arena has no levels (/minigame arena addlevel)");
        }
        for (Player p : getOnlinePlayers()) {
            levelOf.put(p.getUniqueId(), 0);
            fails.put(p.getUniqueId(), 0);
            toStart(p, false);
        }
    }

    private void toStart(Player p, boolean failed) {
        int lvl = levelOf.getOrDefault(p.getUniqueId(), 0);
        PlayerUtil.reset(p, GameMode.ADVENTURE);
        p.teleport(levels.get(lvl).start());
        falling.remove(p.getUniqueId());
        if (failed) {
            fails.merge(p.getUniqueId(), 1, Integer::sum);
            plugin.getMessages().actionBar(p, "dropper.failed");
            plugin.getEffects().play(p, "shop-fail");
        }
    }

    @Override
    public boolean wantsMoveEvents() {
        return state == GameState.IN_GAME;
    }

    @Override
    public void onMove(Player player, PlayerMoveEvent e) {
        UUID id = player.getUniqueId();
        if (!players.contains(id)) {
            return;
        }
        int lvl = levelOf.getOrDefault(id, 0);
        DropperMinigame.Level level = levels.get(lvl);
        Location to = e.getTo();
        if (level.finish().contains(to)) {
            completeLevel(player, lvl);
            return;
        }
        if (voidResets && to.getY() < arena.getVoidY()) {
            toStart(player, true);
            return;
        }
        if (!falling.contains(id)) {
            if (to.getY() < level.start().getY() - armDepth) {
                falling.add(id);
            }
            return;
        }
        if (touchesBlock(player, to)) {
            toStart(player, true);
        }
    }

    /** Whether the player's body overlaps a solid block (feet, waist and head height, all four corners). */
    private boolean touchesBlock(Player p, Location at) {
        World w = at.getWorld();
        double[] heights = {0.05, 0.9, 1.7};
        for (double h : heights) {
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    if (w.getBlockAt((int) Math.floor(at.getX() + sx * hitbox), (int) Math.floor(at.getY() + h),
                            (int) Math.floor(at.getZ() + sz * hitbox)).getType().isSolid()) {
                        return true;
                    }
                }
            }
        }
        return p.isOnGround();
    }

    private void completeLevel(Player p, int lvl) {
        UUID id = p.getUniqueId();
        int next = lvl + 1;
        levelOf.put(id, next);
        falling.remove(id);
        if (next >= levels.size()) {
            broadcast("dropper.finished", "player", p.getName(), "time", TextUtil.formatTime(elapsed),
                    "fails", String.valueOf(fails.getOrDefault(id, 0)));
            plugin.getEffects().firework(p.getLocation());
            endGame(List.of(id));
            return;
        }
        plugin.getEffects().play(p, "kill");
        plugin.getMessages().actionBar(p, "dropper.level-done", "level", String.valueOf(next), "levels", String.valueOf(levels.size()));
        PlayerUtil.reset(p, GameMode.ADVENTURE);
        p.teleport(levels.get(next).start());
        plugin.getScoreboardService().refresh(p);
    }

    @Override
    protected void onTimeLimit() {
        UUID best = null;
        boolean tie = false;
        for (UUID id : players) {
            if (best == null || better(id, best)) {
                best = id;
                tie = false;
            } else if (!better(best, id)) {
                tie = true;
            }
        }
        broadcast("game.time-up");
        endGame(best == null || tie ? List.of() : List.of(best));
    }

    /** Further along wins; equal levels are decided by fewer fails. */
    private boolean better(UUID a, UUID b) {
        int la = levelOf.getOrDefault(a, 0);
        int lb = levelOf.getOrDefault(b, 0);
        if (la != lb) {
            return la > lb;
        }
        return fails.getOrDefault(a, 0) < fails.getOrDefault(b, 0);
    }

    @Override
    protected void checkWin() {
        if (state == GameState.IN_GAME && players.isEmpty()) {
            endGame(new ArrayList<>());
        }
    }

    @Override
    protected void onPlayerDeath(Player victim, Player killer, EntityDamageEvent.DamageCause cause) {
        toStart(victim, true);
    }

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
    public java.util.Map<String, String> getScoreboardVariables(Player player) {
        java.util.Map<String, String> v = super.getScoreboardVariables(player);
        int lvl = levelOf.getOrDefault(player.getUniqueId(), 0);
        v.put("level", String.valueOf(Math.min(lvl + 1, levels.size())));
        v.put("levels", String.valueOf(levels.size()));
        v.put("fails", String.valueOf(fails.getOrDefault(player.getUniqueId(), 0)));
        int leader = 0;
        for (UUID id : players) {
            leader = Math.max(leader, levelOf.getOrDefault(id, 0));
        }
        v.put("leader_level", String.valueOf(Math.min(leader + 1, levels.size())));
        return v;
    }

    @Override
    protected void onPlayerRemoved(Player player) {
        levelOf.remove(player.getUniqueId());
        falling.remove(player.getUniqueId());
    }
}

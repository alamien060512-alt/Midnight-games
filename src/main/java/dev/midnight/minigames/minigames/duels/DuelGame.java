package dev.midnight.minigames.minigames.duels;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.game.AbstractArenaMinigame;
import dev.midnight.minigames.core.game.GameInstance;
import dev.midnight.minigames.core.game.GameState;
import dev.midnight.minigames.core.game.ModeDefinition;
import dev.midnight.minigames.core.team.GameTeam;
import dev.midnight.minigames.core.team.TeamColor;
import dev.midnight.minigames.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A single duel with one kit. */
final class DuelGame extends GameInstance {

    private final Kit kit;
    private final Map<UUID, Integer> hits = new HashMap<>();

    DuelGame(MidnightMinigames plugin, AbstractArenaMinigame minigame, Arena arena, ModeDefinition mode, Kit kit) {
        super(plugin, minigame, arena, mode);
        this.kit = kit;
    }

    @Override
    public String getModeName() {
        return TextUtil.plain(TextUtil.parse(kit.getDisplayName()));
    }

    @Override
    protected void onLobbyJoin(Player player) {
        plugin.getMessages().send(player, "duels.kit-info", "kit", kit.getDisplayName());
    }

    @Override
    protected void onGameStart() {
        List<Player> list = getOnlinePlayers();
        String[] ids = {"red", "blue"};
        for (int i = 0; i < list.size() && i < 2; i++) {
            Location spawn = arena.getTeamSpawn(ids[i]);
            if (spawn == null) {
                throw new IllegalStateException("arena has no '" + ids[i] + "' spawn");
            }
            GameTeam t = new GameTeam(ids[i], TeamColor.fromId(ids[i]), spawn);
            t.getMembers().add(list.get(i).getUniqueId());
            teams.put(ids[i], t);
            teamByPlayer.put(list.get(i).getUniqueId(), t);
            Player p = list.get(i);
            p.teleport(spawn);
            kit.apply(p);
            plugin.getTabService().update(p);
        }
        for (Player p : list) {
            Player opp = opponentOf(p);
            plugin.getMessages().send(p, "duels.start", "kit", kit.getDisplayName(), "opponent", opp == null ? "-" : opp.getName());
        }
    }

    private Player opponentOf(Player p) {
        for (UUID id : players) {
            if (!id.equals(p.getUniqueId())) {
                return Bukkit.getPlayer(id);
            }
        }
        return null;
    }

    @Override
    public boolean wantsMoveEvents() {
        return state == GameState.IN_GAME;
    }

    @Override
    public void onMove(Player player, PlayerMoveEvent e) {
        if (!players.contains(player.getUniqueId())) {
            return;
        }
        if (e.getTo().getY() < arena.getVoidY()
                || (kit.liquidLoses() && (e.getTo().getBlock().getType() == Material.WATER || e.getTo().getBlock().getType() == Material.LAVA))) {
            handleDeath(player, opponentOf(player), EntityDamageEvent.DamageCause.VOID);
        }
    }

    @Override
    public void onDamageByEntity(Player victim, EntityDamageByEntityEvent e) {
        super.onDamageByEntity(victim, e);
        if (e.isCancelled()) {
            return;
        }
        Player attacker = resolveAttacker(e.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        if (!kit.damage()) {
            e.setDamage(0); // knockback stays, health does not change
        }
        int max = kit.maxHits();
        if (max > 0) {
            int n = hits.merge(attacker.getUniqueId(), 1, Integer::sum);
            if (n >= max) {
                endGame(List.of(attacker.getUniqueId()));
            }
        }
    }

    @Override
    public void onRegen(Player player, EntityRegainHealthEvent e) {
        if (!kit.naturalRegen() && (e.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED
                || e.getRegainReason() == EntityRegainHealthEvent.RegainReason.REGEN)) {
            e.setCancelled(true);
        }
    }

    @Override
    public void onFoodChange(Player player, FoodLevelChangeEvent e) {
        super.onFoodChange(player, e);
        if (!kit.hunger() && !e.isCancelled()) {
            e.setCancelled(true);
        }
    }

    @Override
    public void onBlockPlace(Player player, BlockPlaceEvent e) {
        super.onBlockPlace(player, e);
        if (e.isCancelled()) {
            return;
        }
        if (!kit.allowBuild()) {
            e.setCancelled(true);
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
        if (!kit.allowBuild() || !blocks.isPlaced(e.getBlock())) {
            e.setCancelled(true);
        }
    }

    private final Map<UUID, Integer> eloDelta = new HashMap<>();

    /** Standard ELO between the two duellists. Only when both are still online (otherwise the result is not rated). */
    private void rate(List<UUID> winners) {
        var cfg = plugin.getConfigurationManager().game("duels");
        if (!cfg.getBoolean("ranked.enabled", true) || winners.size() != 1 || participants.size() != 2) {
            return;
        }
        UUID winId = winners.get(0);
        UUID loseId = null;
        for (UUID id : participants) {
            if (!id.equals(winId)) {
                loseId = id;
            }
        }
        Player w = Bukkit.getPlayer(winId);
        Player l = loseId == null ? null : Bukkit.getPlayer(loseId);
        if (w == null || l == null) {
            return;
        }
        int start = cfg.getInt("ranked.starting", 1000);
        double k = cfg.getDouble("ranked.k-factor", 32);
        var wp = plugin.getPlayerManager().getProfile(w);
        var lp = plugin.getPlayerManager().getProfile(l);
        long rw = wp.getLong("elo", kit.getId(), start);
        long rl = lp.getLong("elo", kit.getId(), start);
        double expected = 1.0 / (1.0 + Math.pow(10, (rl - rw) / 400.0));
        int delta = (int) Math.max(1, Math.round(k * (1.0 - expected)));
        long nw = rw + delta;
        long nl = Math.max(0, rl - delta);
        wp.setLong("elo", kit.getId(), nw);
        lp.setLong("elo", kit.getId(), nl);
        eloDelta.put(winId, delta);
        eloDelta.put(loseId, -delta);
        plugin.getMessages().send(w, "duels.elo-change", "kit", kit.getDisplayName(), "old", String.valueOf(rw), "new", String.valueOf(nw), "delta", "+" + delta);
        plugin.getMessages().send(l, "duels.elo-change", "kit", kit.getDisplayName(), "old", String.valueOf(rl), "new", String.valueOf(nl), "delta", "-" + delta);
    }

    @Override
    protected void onGameEnd(List<UUID> winners) {
        rate(winners);
        for (UUID id : participants) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                plugin.getPlayerManager().getProfile(p).increment("stats", minigame.getId() + "." + kit.getId() + (winners.contains(id) ? ".wins" : ".losses"), 1);
            }
        }
    }

    @Override
    public Map<String, String> getScoreboardVariables(Player player) {
        Map<String, String> v = super.getScoreboardVariables(player);
        Player opp = opponentOf(player);
        v.put("kit", TextUtil.plain(TextUtil.parse(kit.getDisplayName())));
        v.put("opponent", opp == null ? "-" : opp.getName());
        v.put("hits", String.valueOf(hits.getOrDefault(player.getUniqueId(), 0)));
        v.put("opponent_hits", opp == null ? "0" : String.valueOf(hits.getOrDefault(opp.getUniqueId(), 0)));
        v.put("max_hits", String.valueOf(kit.maxHits()));
        v.put("elo", String.valueOf(plugin.getPlayerManager().getProfile(player).getLong("elo", kit.getId(),
                plugin.getConfigurationManager().game("duels").getInt("ranked.starting", 1000))));
        return v;
    }
}

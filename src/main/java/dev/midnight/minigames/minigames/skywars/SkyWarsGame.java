package dev.midnight.minigames.minigames.skywars;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.arena.Arena;
import dev.midnight.minigames.core.game.AbstractArenaMinigame;
import dev.midnight.minigames.core.game.GameInstance;
import dev.midnight.minigames.core.game.ModeDefinition;
import dev.midnight.minigames.core.team.GameTeam;
import dev.midnight.minigames.core.team.TeamBalancer;
import dev.midnight.minigames.core.team.TeamColor;
import dev.midnight.minigames.util.LocationUtil;
import dev.midnight.minigames.util.LootTable;
import dev.midnight.minigames.util.LuckyBlocks;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** One SkyWars match. */
final class SkyWarsGame extends GameInstance {

    private final Random random = new Random();
    private final YamlConfiguration cfg;
    private final Map<Long, String> chestTier = new HashMap<>();
    private final Set<Long> filled = new HashSet<>();
    private final Set<Long> luckyBlocks = new HashSet<>();
    private final Map<String, LootTable> loot = new HashMap<>();
    private LootTable luckyTable;
    private Material luckyMaterial;
    private final int freezeSeconds;
    private final int graceSeconds;
    private final List<Integer> refillTimes;
    private int nextRefill = 0;
    private final boolean lucky;

    SkyWarsGame(MidnightMinigames plugin, AbstractArenaMinigame minigame, Arena arena, ModeDefinition mode) {
        super(plugin, minigame, arena, mode);
        this.cfg = plugin.getConfigurationManager().game("skywars");
        this.freezeSeconds = cfg.getInt("freeze-seconds", 5);
        this.graceSeconds = cfg.getInt("grace-seconds", 10);
        this.refillTimes = cfg.getIntegerList("refill-times");
        this.lucky = mode.isLucky();
    }

    @Override
    protected void onGameStart() {
        loot.put("island", LootTable.load(cfg.getConfigurationSection("loot.island")));
        loot.put("mid", LootTable.load(cfg.getConfigurationSection("loot.mid")));
        luckyTable = LuckyBlocks.table(cfg);
        luckyMaterial = LuckyBlocks.material(cfg);
        chestTier.clear();
        filled.clear();
        luckyBlocks.clear();
        nextRefill = 0;
        for (String tier : List.of("island", "mid")) {
            for (String raw : arena.getYaml().getStringList("data.chests." + tier)) {
                Location l = LocationUtil.deserialize(raw);
                if (l != null) {
                    chestTier.put(LocationUtil.pack(l.getBlockX(), l.getBlockY(), l.getBlockZ()), tier);
                }
            }
        }
        if (lucky) {
            for (long key : chestTier.keySet()) {
                Block b = world().getBlockAt(LocationUtil.unpackX(key), LocationUtil.unpackY(key), LocationUtil.unpackZ(key));
                blocks.record(b);
                if (b.getState() instanceof Container c) {
                    c.getInventory().clear();
                }
                b.setType(luckyMaterial, false);
                luckyBlocks.add(key);
            }
        }

        List<Player> online = getOnlinePlayers();
        if (mode.getTeamSize() > 1) {
            List<GameTeam> list = new ArrayList<>();
            for (Map.Entry<String, String> e : arena.getRawTeamSpawns().entrySet()) {
                Location spawn = LocationUtil.deserialize(e.getValue());
                if (spawn == null) {
                    continue;
                }
                GameTeam t = new GameTeam(e.getKey(), TeamColor.fromId(e.getKey()), spawn);
                teams.put(t.getId(), t);
                list.add(t);
            }
            List<UUID> ids = new ArrayList<>();
            online.forEach(p -> ids.add(p.getUniqueId()));
            TeamBalancer.assign(ids, list, mode.getTeamSize(), plugin.getPartyManager()::getPartyId);
            teams.values().removeIf(t -> t.getMembers().isEmpty());
            for (GameTeam t : teams.values()) {
                for (UUID id : t.getMembers()) {
                    teamByPlayer.put(id, t);
                }
            }
            for (Player p : online) {
                GameTeam t = teamByPlayer.get(p.getUniqueId());
                if (t != null) {
                    p.teleport(t.getSpawn());
                    plugin.getMessages().send(p, "game.team-assigned", "team", t.getColoredName());
                }
            }
        } else {
            List<Location> spawns = arena.getSpawns();
            if (spawns.isEmpty()) {
                throw new IllegalStateException("arena has no spawn points");
            }
            int i = 0;
            for (Player p : online) {
                p.teleport(spawns.get(i++ % spawns.size()));
            }
        }
        for (Player p : online) {
            plugin.getTabService().update(p);
        }
    }

    @Override
    protected void onTick() {
        if (nextRefill < refillTimes.size() && elapsed >= refillTimes.get(nextRefill)) {
            nextRefill++;
            filled.clear();
            broadcast("skywars.refill");
            sound("refill");
        }
        if (elapsed == freezeSeconds + 1 && freezeSeconds > 0) {
            broadcast("skywars.go");
        }
    }

    private boolean frozen() {
        return elapsed < freezeSeconds;
    }

    @Override
    protected boolean isPvpEnabled() {
        return elapsed >= freezeSeconds + graceSeconds;
    }

    @Override
    protected boolean fallDamageEnabled() {
        return elapsed >= freezeSeconds + 3;
    }

    @Override
    public boolean wantsMoveEvents() {
        return state == dev.midnight.minigames.core.game.GameState.IN_GAME;
    }

    @Override
    public void onMove(Player player, PlayerMoveEvent e) {
        if (!players.contains(player.getUniqueId())) {
            return;
        }
        if (frozen()) {
            Location from = e.getFrom();
            Location to = e.getTo();
            if (from.getBlockX() != to.getBlockX() || from.getBlockZ() != to.getBlockZ()) {
                e.setTo(from.clone().setDirection(to.getDirection()));
            }
            return;
        }
        if (e.getTo().getY() < arena.getVoidY()) {
            handleDeath(player, null, EntityDamageEvent.DamageCause.VOID);
        }
    }

    @Override
    public void onDamage(Player victim, EntityDamageEvent e) {
        super.onDamage(victim, e);
        if (!e.isCancelled() && frozen()) {
            e.setCancelled(true);
        }
    }

    @Override
    protected void onPlayerDeath(Player victim, Player killer, EntityDamageEvent.DamageCause cause) {
        dropInventory(victim);
        super.onPlayerDeath(victim, killer, cause);
    }

    @Override
    protected void onPlayerLeftDuringGame(Player player) {
        dropInventory(player);
    }

    private void dropInventory(Player p) {
        Location at = p.getLocation();
        if (at.getWorld() == null) {
            return;
        }
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                at.getWorld().dropItemNaturally(at, item);
            }
        }
        p.getInventory().clear();
    }

    // ---- chests ---------------------------------------------------------------------------------------

    @Override
    public void onInteract(Player player, PlayerInteractEvent e) {
        super.onInteract(player, e);
        if (e.isCancelled() || e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) {
            return;
        }
        Block b = e.getClickedBlock();
        if (!(b.getState() instanceof Container container)) {
            return;
        }
        long key = LocationUtil.pack(b);
        String tier = chestTier.get(key);
        if (tier == null || filled.contains(key)) {
            // double chest: the other half may be the registered one
            if (tier == null && container instanceof Chest chest && chest.getInventory().getHolder() instanceof DoubleChest dc) {
                for (InventoryHolder side : new InventoryHolder[]{dc.getLeftSide(), dc.getRightSide()}) {
                    if (side instanceof Chest c) {
                        long k = LocationUtil.pack(c.getBlock());
                        if (chestTier.containsKey(k) && !filled.contains(k)) {
                            fill(container.getInventory(), chestTier.get(k));
                            markFilled(container.getInventory());
                            filled.add(k);
                            return;
                        }
                    }
                }
            }
            return;
        }
        fill(container.getInventory(), tier);
        markFilled(container.getInventory());
        filled.add(key);
    }

    private void markFilled(Inventory inv) {
        if (inv.getHolder() instanceof DoubleChest dc) {
            for (InventoryHolder side : new InventoryHolder[]{dc.getLeftSide(), dc.getRightSide()}) {
                if (side instanceof Chest c) {
                    filled.add(LocationUtil.pack(c.getBlock()));
                }
            }
        }
    }

    private void fill(Inventory inv, String tier) {
        LootTable table = loot.get(tier);
        if (table == null || table.isEmpty()) {
            return;
        }
        table.fill(inv, random);
    }

    // ---- building -------------------------------------------------------------------------------------------

    @Override
    public void onBlockBreak(Player player, BlockBreakEvent e) {
        super.onBlockBreak(player, e);
        if (e.isCancelled()) {
            return;
        }
        blocks.record(e.getBlock());
        long key = LocationUtil.pack(e.getBlock());
        if (luckyBlocks.remove(key)) {
            e.setDropItems(false);
            LuckyBlocks.dropLoot(e.getBlock().getLocation(), luckyTable, random);
        }
    }

    @Override
    public void onBlockPlace(Player player, BlockPlaceEvent e) {
        super.onBlockPlace(player, e);
        if (!e.isCancelled()) {
            blocks.markPlaced(e.getBlock());
            blocks.record(e.getBlockReplacedState().getBlock());
        }
    }

    @Override
    public Map<String, String> getScoreboardVariables(Player player) {
        Map<String, String> v = super.getScoreboardVariables(player);
        v.put("refill", nextRefill < refillTimes.size() ? dev.midnight.minigames.util.TextUtil.formatTime(refillTimes.get(nextRefill) - elapsed) : "-");
        return v;
    }
}

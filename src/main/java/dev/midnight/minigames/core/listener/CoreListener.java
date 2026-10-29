package dev.midnight.minigames.core.listener;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.core.game.GameInstance;
import dev.midnight.minigames.core.game.Minigame;
import dev.midnight.minigames.core.game.PlayerContext;
import dev.midnight.minigames.core.gui.MenuHolder;
import dev.midnight.minigames.util.ItemFactory;
import dev.midnight.minigames.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;

/**
 * The ONLY Bukkit listener of the plugin. Every event is routed (with a single map lookup) to the
 * {@link PlayerContext} of the acting player, so minigames never register listeners of their own and
 * players outside of games cost next to nothing.
 */
public final class CoreListener implements Listener {

    private final MidnightMinigames plugin;

    public CoreListener(MidnightMinigames plugin) {
        this.plugin = plugin;
    }

    private PlayerContext ctx(Player p) {
        return plugin.getPlayerManager().getContext(p.getUniqueId());
    }

    private boolean isLobbyWorld(World w) {
        Location l = plugin.getLobbyService().getMainLobby();
        return l != null && l.getWorld() != null && l.getWorld().equals(w);
    }

    // ---- join / quit -----------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.getPlayerManager().handleJoin(p);
        plugin.getLobbyService().handleJoin(p);
        plugin.getScoreboardService().refresh(p);
        plugin.getTabService().update(p);
        plugin.getTabService().sendHeaderFooter(p);
        for (Minigame mg : plugin.getMinigameManager().getAll()) {
            if (mg.isEnabled()) {
                mg.handlePlayerJoin(p);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        PlayerContext c = ctx(p);
        if (c != null) {
            try {
                c.getMinigame().handlePlayerQuit(p);
            } catch (Exception ex) {
                plugin.getLogger().warning("Error handling quit of " + p.getName() + ": " + ex.getMessage());
            }
        }
        plugin.getQueueManager().removePlayer(p.getUniqueId());
        plugin.getPartyManager().handleQuit(p);
        plugin.getScoreboardService().remove(p);
        plugin.getPlayerManager().handleQuit(p);
    }

    // ---- chat (rank prefixes) --------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(io.papermc.paper.event.player.AsyncChatEvent e) {
        if (plugin.getRanks() != null && plugin.getRanks().isChatEnabled()) {
            e.renderer(io.papermc.paper.chat.ChatRenderer.viewerUnaware((source, name, message) -> plugin.getRanks().formatChat(source, message)));
        }
    }

    // ---- combat / death ----------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        Entity victimEntity = e.getEntity();
        if (e instanceof EntityDamageByEntityEvent byEntity) {
            Player attacker = attackerOf(byEntity.getDamager());
            if (attacker != null) {
                PlayerContext ac = ctx(attacker);
                if (ac != null) {
                    ac.onAttack(attacker, byEntity);
                    if (e.isCancelled()) {
                        return;
                    }
                }
            }
        }
        if (!(victimEntity instanceof Player victim)) {
            return;
        }
        PlayerContext c = ctx(victim);
        if (c == null) {
            if (plugin.getConfigurationManager().config().getBoolean("lobby.disable-damage", true) && isLobbyWorld(victim.getWorld())) {
                e.setCancelled(true);
            }
            return;
        }
        c.onDamage(victim, e);
        if (e instanceof EntityDamageByEntityEvent byEntity && !e.isCancelled()) {
            c.onDamageByEntity(victim, byEntity);
        }
        if (e.isCancelled()) {
            return;
        }
        if (victim.getHealth() - e.getFinalDamage() <= 0.0) {
            e.setCancelled(true);
            Player killer = null;
            if (e instanceof EntityDamageByEntityEvent byEntity) {
                killer = attackerOf(byEntity.getDamager());
            }
            if (killer == victim) {
                killer = null;
            }
            c.getMinigame().handlePlayerDeath(victim, killer, e.getCause());
        }
    }

    private Player attackerOf(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }

    /** Safety net for deaths that bypass damage events (/kill, setHealth(0)). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        PlayerContext c = ctx(p);
        if (c == null) {
            return;
        }
        e.deathMessage(null);
        e.setKeepInventory(true);
        e.setKeepLevel(true);
        e.getDrops().clear();
        e.setDroppedExp(0);
        final Player killer = p.getKiller();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline() && p.isDead()) {
                p.spigot().respawn();
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                PlayerContext now = ctx(p);
                if (p.isOnline() && now != null) {
                    now.getMinigame().handlePlayerDeath(p, killer, EntityDamageEvent.DamageCause.CUSTOM);
                }
            });
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        PlayerContext c = ctx(e.getPlayer());
        if (c != null) {
            Location l = c.getRespawnLocation(e.getPlayer());
            if (l != null) {
                e.setRespawnLocation(l);
            }
        }
    }

    // ---- blocks ---------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        PlayerContext c = ctx(p);
        if (c != null) {
            c.onBlockBreak(p, e);
        } else if (protectedLobby(p, e.getBlock().getWorld())) {
            e.setCancelled(true);
            plugin.getMessages().actionBar(p, "general.lobby-protected");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        PlayerContext c = ctx(p);
        if (c != null) {
            c.onBlockPlace(p, e);
        } else if (protectedLobby(p, e.getBlock().getWorld())) {
            e.setCancelled(true);
            plugin.getMessages().actionBar(p, "general.lobby-protected");
        }
    }

    private boolean protectedLobby(Player p, World w) {
        return plugin.getConfigurationManager().config().getBoolean("lobby.protect-blocks", true)
                && isLobbyWorld(w) && !plugin.getPermissions().has(p, "admin-build");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        World w = e.getLocation().getWorld();
        if (w == null || plugin.getGameManager().getInstances().isEmpty()) {
            return;
        }
        for (GameInstance gi : plugin.getGameManager().instancesIn(w)) {
            if (gi.isNear(e.getLocation())) {
                for (org.bukkit.block.Block b : e.blockList()) {
                    gi.trackChange(b); // remember the originals before the game filters the list
                }
            }
            gi.onExplode(e);
        }
    }

    // ---- block changes that are not caused by a direct click: remembered so the arena resets completely --------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucket(org.bukkit.event.player.PlayerBucketEmptyEvent e) {
        for (GameInstance gi : plugin.getGameManager().nearInstances(e.getBlock().getLocation())) {
            gi.trackChange(e.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(org.bukkit.event.player.PlayerBucketFillEvent e) {
        for (GameInstance gi : plugin.getGameManager().nearInstances(e.getBlock().getLocation())) {
            gi.trackChange(e.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlow(org.bukkit.event.block.BlockFromToEvent e) {
        if (plugin.getGameManager().getInstances().isEmpty()) {
            return;
        }
        for (GameInstance gi : plugin.getGameManager().nearInstances(e.getToBlock().getLocation())) {
            gi.trackChange(e.getToBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(org.bukkit.event.block.BlockBurnEvent e) {
        for (GameInstance gi : plugin.getGameManager().nearInstances(e.getBlock().getLocation())) {
            gi.trackChange(e.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIgnite(org.bukkit.event.block.BlockIgniteEvent e) {
        for (GameInstance gi : plugin.getGameManager().nearInstances(e.getBlock().getLocation())) {
            gi.trackChange(e.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(org.bukkit.event.block.BlockExplodeEvent e) {
        for (GameInstance gi : plugin.getGameManager().nearInstances(e.getBlock().getLocation())) {
            for (org.bukkit.block.Block b : e.blockList()) {
                gi.trackChange(b);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(org.bukkit.event.entity.EntityChangeBlockEvent e) {
        if (e.getEntity() instanceof Player) {
            return;
        }
        for (GameInstance gi : plugin.getGameManager().nearInstances(e.getBlock().getLocation())) {
            gi.trackChange(e.getBlock());
        }
    }

    // ---- interaction ------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Action a = e.getAction();
        if ((a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) && e.getHand() == EquipmentSlot.HAND) {
            ItemStack item = e.getItem();
            if (item != null && item.hasItemMeta()) {
                String action = ItemFactory.readTag(item, Keys.ACTION);
                if (action != null) {
                    e.setCancelled(true);
                    if (action.equals("open_menu")) {
                        plugin.getMenus().openMain(p);
                        return;
                    }
                    PlayerContext c = ctx(p);
                    if (c != null) {
                        c.onItemAction(p, action);
                    }
                    return;
                }
            }
        }
        PlayerContext c = ctx(p);
        if (c != null) {
            c.onInteract(p, e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        PlayerContext c = ctx(e.getPlayer());
        if (c != null) {
            c.onInteractEntity(e.getPlayer(), e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ())) {
            return;
        }
        PlayerContext c = ctx(e.getPlayer());
        if (c instanceof GameInstance gi && to.getY() < from.getY() && gi.rescueFromVoid(e.getPlayer(), to)) {
            return;
        }
        if (c != null && c.wantsMoveEvents()) {
            c.onMove(e.getPlayer(), e);
        }
    }

    /** No lava/water outside the buildable area (placed blocks are covered by the block place hook). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketBuild(org.bukkit.event.player.PlayerBucketEmptyEvent e) {
        if (ctx(e.getPlayer()) instanceof GameInstance gi && !gi.isBuildAllowedAt(e.getBlock().getLocation())) {
            e.setCancelled(true);
            plugin.getMessages().actionBar(e.getPlayer(), "game.build-outside");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onToggleFlight(org.bukkit.event.player.PlayerToggleFlightEvent e) {
        PlayerContext c = ctx(e.getPlayer());
        if (c != null) {
            c.onToggleFlight(e.getPlayer(), e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        PlayerContext c = ctx(e.getPlayer());
        if (c != null) {
            c.onDrop(e.getPlayer(), e);
        } else if (ItemFactory.readTag(e.getItemDrop().getItemStack(), Keys.ACTION) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p) {
            PlayerContext c = ctx(p);
            if (c != null) {
                c.onPickup(p, e);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p) {
            PlayerContext c = ctx(p);
            if (c != null) {
                c.onFoodChange(p, e);
            } else if (isLobbyWorld(p.getWorld()) && plugin.getConfigurationManager().config().getBoolean("lobby.disable-hunger", true)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRegen(org.bukkit.event.entity.EntityRegainHealthEvent e) {
        if (e.getEntity() instanceof Player p) {
            PlayerContext c = ctx(p);
            if (c != null) {
                c.onRegen(p, e);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLaunch(org.bukkit.event.entity.ProjectileLaunchEvent e) {
        if (e.getEntity().getShooter() instanceof Player p) {
            PlayerContext c = ctx(p);
            if (c != null) {
                c.onProjectileLaunch(p, e);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileHit(org.bukkit.event.entity.ProjectileHitEvent e) {
        if (e.getEntity().getShooter() instanceof Player p) {
            PlayerContext c = ctx(p);
            if (c != null) {
                c.onProjectileHit(p, e);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(org.bukkit.event.player.PlayerItemConsumeEvent e) {
        PlayerContext c = ctx(e.getPlayer());
        if (c != null) {
            c.onConsume(e.getPlayer(), e);
        }
    }

    // ---- inventories (menus) ------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        if (e.getView().getTopInventory().getHolder() instanceof MenuHolder holder) {
            e.setCancelled(true);
            if (e.getClickedInventory() == e.getView().getTopInventory()) {
                holder.onClick(p, e);
            } else {
                holder.onBottomClick(p, e);
            }
            return;
        }
        PlayerContext inv = ctx(p);
        if (inv != null) {
            inv.onInventoryClick(p, e);
            if (e.isCancelled()) {
                return;
            }
        }
        // Lobby items (compass / leave item) can never be moved around.
        ItemStack cur = e.getCurrentItem();
        if (cur != null && cur.hasItemMeta() && ItemFactory.readTag(cur, Keys.ACTION) != null
                && p.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            e.setCancelled(true);
            return;
        }
        if (e.getWhoClicked() instanceof Player p) {
            PlayerContext c = ctx(p);
            if (c != null) {
                c.onInventoryDrag(p, e);
            }
        }
    }

    /** No crafting inside games (items come from the shop). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(org.bukkit.event.inventory.CraftItemEvent e) {
        if (e.getWhoClicked() instanceof Player p && ctx(p) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(org.bukkit.event.inventory.PrepareItemCraftEvent e) {
        for (org.bukkit.entity.HumanEntity h : e.getViewers()) {
            if (h instanceof Player p && ctx(p) != null) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    /** Sleeping in a bed would also set the respawn point, so beds do nothing for players inside a game. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBedEnter(org.bukkit.event.player.PlayerBedEnterEvent e) {
        if (ctx(e.getPlayer()) != null) {
            e.setCancelled(true);
        }
    }

    // ---- commands -------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        PlayerContext c = ctx(p);
        if (!(c instanceof GameInstance) || plugin.getPermissions().has(p, "admin")) {
            return;
        }
        List<String> blocked = plugin.getConfigurationManager().config().getStringList("restrictions.blocked-commands");
        if (blocked.isEmpty()) {
            return;
        }
        String msg = e.getMessage().toLowerCase(Locale.ROOT);
        String first = msg.split(" ")[0];
        for (String b : blocked) {
            if (first.equals(b.toLowerCase(Locale.ROOT).startsWith("/") ? b.toLowerCase(Locale.ROOT) : "/" + b.toLowerCase(Locale.ROOT))) {
                e.setCancelled(true);
                plugin.getMessages().send(p, "game.command-blocked");
                return;
            }
        }
    }
}

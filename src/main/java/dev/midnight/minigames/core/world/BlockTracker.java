package dev.midnight.minigames.core.world;

import dev.midnight.minigames.util.LocationUtil;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Remembers the original state of every block a game changes so the arena can be restored
 * without unloading or deleting its world. Restoring is spread over several ticks.
 * Note: tile entity contents (chest inventories, signs) are not restored.
 */
public final class BlockTracker {

    private final Map<Long, BlockData> original = new LinkedHashMap<>();
    private final Set<Long> placed = new HashSet<>();

    /** Call BEFORE a block is modified. Only the first recorded state per position is kept. */
    public void record(Block block) {
        original.putIfAbsent(LocationUtil.pack(block), block.getBlockData());
    }

    /**
     * Records a block placement. The ORIGINAL data is taken from the replaced state (what was there before), because
     * by the time the event fires the world already contains the new block. Multi-block placements
     * (beds, doors, tall plants) are handled too.
     */
    public void recordPlaced(org.bukkit.event.block.BlockPlaceEvent e) {
        if (e instanceof org.bukkit.event.block.BlockMultiPlaceEvent multi) {
            for (org.bukkit.block.BlockState st : multi.getReplacedBlockStates()) {
                recordReplaced(st);
            }
        } else {
            recordReplaced(e.getBlockReplacedState());
        }
    }

    private void recordReplaced(org.bukkit.block.BlockState replaced) {
        long key = LocationUtil.pack(replaced.getX(), replaced.getY(), replaced.getZ());
        original.putIfAbsent(key, replaced.getBlockData());
        placed.add(key);
    }

    public void markPlaced(Block block) {
        record(block);
        placed.add(LocationUtil.pack(block));
    }

    public boolean isPlaced(Block block) {
        return placed.contains(LocationUtil.pack(block));
    }

    public void unmarkPlaced(Block block) {
        placed.remove(LocationUtil.pack(block));
    }

    public int size() {
        return original.size();
    }

    public void clear() {
        original.clear();
        placed.clear();
    }

    /** Restores everything, {@code perTick} blocks per tick, then runs {@code done} (main thread). */
    public void restore(Plugin plugin, World world, int perTick, Runnable done) {
        if (original.isEmpty() || world == null) {
            clear();
            if (done != null) {
                done.run();
            }
            return;
        }
        final List<Map.Entry<Long, BlockData>> snapshot = new ArrayList<>(original.entrySet());
        clear();
        final int budget = Math.max(200, perTick);
        new BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                int end = Math.min(snapshot.size(), index + budget);
                for (; index < end; index++) {
                    Map.Entry<Long, BlockData> e = snapshot.get(index);
                    long k = e.getKey();
                    world.getBlockAt(LocationUtil.unpackX(k), LocationUtil.unpackY(k), LocationUtil.unpackZ(k))
                            .setBlockData(e.getValue(), false);
                }
                if (index >= snapshot.size()) {
                    cancel();
                    if (done != null) {
                        done.run();
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Synchronous restore for plugin shutdown, when no new scheduler tasks can be created. */
    public void restoreNow(World world) {
        if (world != null) {
            for (Map.Entry<Long, BlockData> e : original.entrySet()) {
                long k = e.getKey();
                world.getBlockAt(LocationUtil.unpackX(k), LocationUtil.unpackY(k), LocationUtil.unpackZ(k))
                        .setBlockData(e.getValue(), false);
            }
        }
        clear();
    }
}

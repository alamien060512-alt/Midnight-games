package dev.midnight.minigames.core.island;

import dev.midnight.minigames.MidnightMinigames;
import dev.midnight.minigames.util.ConfigUtil;
import dev.midnight.minigames.util.ItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;

/**
 * Builds the starting island. Two sources, in this order:
 * <ol>
 *   <li>a vanilla structure file {@code plugins/MidnightMinigames/templates/<game>/island.nbt}
 *       (save one in-game with a structure block; this is the "schematic" support, no WorldEdit needed),</li>
 *   <li>the {@code template.layers} defined in games/&lt;game&gt;.yml.</li>
 * </ol>
 */
public final class IslandTemplate {

    private IslandTemplate() {
    }

    /** Pastes the template centred on {@code center}. Returns the suggested home location. */
    public static Location paste(MidnightMinigames plugin, String gameId, ConfigurationSection cfg, Location center) {
        World w = center.getWorld();
        boolean pasted = false;
        if (cfg != null && cfg.getBoolean("use-structure-file", true)) {
            File f = new File(plugin.getDataFolder(), "templates/" + gameId + "/" + cfg.getString("structure-file", "island.nbt"));
            if (f.isFile()) {
                try {
                    Structure st = Bukkit.getStructureManager().loadStructure(f);
                    BlockVector size = st.getSize();
                    int yOffset = cfg.getInt("structure-y-offset", size.getBlockY() / 2);
                    Location corner = center.clone().add(-(size.getBlockX() / 2), -yOffset, -(size.getBlockZ() / 2));
                    st.place(corner, false, StructureRotation.NONE, Mirror.NONE, 0, 1.0f, new Random());
                    pasted = true;
                } catch (IOException | RuntimeException ex) {
                    plugin.getLogger().severe("Could not paste structure template " + f.getName() + " for " + gameId + ": "
                            + ex.getMessage() + " - falling back to layers.");
                }
            }
        }
        if (!pasted && cfg != null) {
            for (ConfigurationSection layer : ConfigUtil.sections(cfg, "layers")) {
                Material m = ItemFactory.material(layer.getString("material", "DIRT"), Material.DIRT);
                int dy = layer.getInt("dy", 0);
                int r = Math.max(0, layer.getInt("radius", 2));
                boolean circle = layer.getString("shape", "square").equalsIgnoreCase("circle");
                for (int x = -r; x <= r; x++) {
                    for (int z = -r; z <= r; z++) {
                        if (circle && x * x + z * z > r * r + r) {
                            continue;
                        }
                        w.getBlockAt(center.getBlockX() + x, center.getBlockY() + dy, center.getBlockZ() + z).setType(m, false);
                    }
                }
            }
        }
        ConfigurationSection chest = cfg == null ? null : cfg.getConfigurationSection("chest");
        if (chest != null && chest.getBoolean("enabled", true)) {
            Block b = w.getBlockAt(center.getBlockX() + chest.getInt("dx", 2), center.getBlockY() + chest.getInt("dy", 1),
                    center.getBlockZ() + chest.getInt("dz", 0));
            b.setType(Material.CHEST, false);
            if (b.getState() instanceof Chest c) {
                for (ConfigurationSection item : ConfigUtil.sections(chest, "items")) {
                    c.getInventory().addItem(ItemFactory.build(item));
                }
            }
        }
        List<Integer> off = cfg == null ? List.of() : cfg.getIntegerList("spawn-offset");
        int ox = off.size() > 0 ? off.get(0) : 0;
        int oy = off.size() > 1 ? off.get(1) : 2;
        int oz = off.size() > 2 ? off.get(2) : 0;
        return center.clone().add(ox, oy, oz);
    }
}

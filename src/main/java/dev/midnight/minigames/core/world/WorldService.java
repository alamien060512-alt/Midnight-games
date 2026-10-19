package dev.midnight.minigames.core.world;

import dev.midnight.minigames.MidnightMinigames;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Loads, creates and (only when explicitly enabled in config.yml) resets arena worlds.
 * Worlds are never deleted unless {@code worlds.world-copy-reset.enabled} is true AND the arena uses
 * {@code reset-mode: world-copy} AND a template folder exists in {@code world-templates/<world>}.
 */
public final class WorldService {

    private final MidnightMinigames plugin;

    public WorldService(MidnightMinigames plugin) {
        this.plugin = plugin;
    }

    public File worldFolder(String name) {
        return new File(Bukkit.getWorldContainer(), name);
    }

    /**
     * True when a world of that name exists on disk: either as a classic folder in the server directory
     * (Paper migrates it automatically when it is loaded) or already inside the Paper 26.x layout
     * {@code <level>/dimensions/minecraft/<name>}.
     */
    public boolean worldExistsOnDisk(String name) {
        if (worldFolder(name).isDirectory()) {
            return true;
        }
        if (!Bukkit.getWorlds().isEmpty()) {
            File level = worldFolder(Bukkit.getWorlds().get(0).getName());
            return new File(level, "dimensions/minecraft/" + name).isDirectory()
                    || new File(level, "dimensions/minecraft/" + name.toLowerCase(java.util.Locale.ROOT)).isDirectory();
        }
        return false;
    }

    /** Returns the loaded world, loading it from disk when allowed. Null when it does not exist. */
    public World getOrLoad(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        World w = Bukkit.getWorld(name);
        if (w != null) {
            return w;
        }
        if (plugin.getConfigurationManager().config().getBoolean("worlds.auto-load", true)
                && worldExistsOnDisk(name)) {
            return load(name);
        }
        return null;
    }

    public World load(String name) {
        if (!worldExistsOnDisk(name)) {
            return null;
        }
        try {
            return new WorldCreator(name).createWorld();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load world '" + name + "': " + ex.getMessage(), ex);
            return null;
        }
    }

    /** Creates an empty (void) world. Used by Skyblock/OneBlock/SkyGen and /minigame world create. */
    public World createVoid(String name) {
        World existing = Bukkit.getWorld(name);
        if (existing != null) {
            return existing;
        }
        try {
            WorldCreator creator = new WorldCreator(name);
            creator.generator(new ChunkGenerator() {
            });
            creator.generateStructures(false);
            World w = creator.createWorld();
            if (w != null) {
                w.setSpawnLocation(0, 80, 0);
            }
            return w;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create void world '" + name + "': " + ex.getMessage(), ex);
            return null;
        }
    }

    public boolean isWorldCopyResetEnabled() {
        return plugin.getConfigurationManager().config().getBoolean("worlds.world-copy-reset.enabled", false);
    }

    public boolean hasTemplate(String worldName) {
        return new File(plugin.getDataFolder(), "world-templates/" + worldName).isDirectory();
    }

    /**
     * Replaces the world folder with a fresh copy of the template. All players must already be out of the world.
     * File work happens asynchronously; world unload/load on the main thread. {@code done} receives success.
     */
    public void resetFromTemplate(String worldName, Consumer<Boolean> done) {
        if (!isWorldCopyResetEnabled() || !hasTemplate(worldName)) {
            done.accept(false);
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            if (!world.getPlayers().isEmpty()) {
                plugin.getLogger().warning("World reset of '" + worldName + "' aborted: players are still inside.");
                done.accept(false);
                return;
            }
            if (!Bukkit.unloadWorld(world, false)) {
                plugin.getLogger().warning("World reset of '" + worldName + "' failed: world could not be unloaded.");
                done.accept(false);
                return;
            }
        }
        final Path target = worldFolder(worldName).toPath();
        final Path template = new File(plugin.getDataFolder(), "world-templates/" + worldName).toPath();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean ok = true;
            try {
                deleteRecursively(target);
                copyRecursively(template, target);
            } catch (IOException ex) {
                ok = false;
                plugin.getLogger().log(Level.SEVERE, "World copy reset failed for '" + worldName + "': " + ex.getMessage(), ex);
            }
            final boolean copied = ok;
            Bukkit.getScheduler().runTask(plugin, () -> {
                World reloaded = load(worldName);
                done.accept(copied && reloaded != null);
            });
        });
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void copyRecursively(Path from, Path to) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.getFileName().toString().equals("uid.dat") || file.getFileName().toString().equals("session.lock")) {
                    return FileVisitResult.CONTINUE;
                }
                Files.copy(file, to.resolve(from.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public Plugin getPlugin() {
        return plugin;
    }
}

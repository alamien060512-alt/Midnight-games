package dev.midnight.minigames.core.display;

import dev.midnight.minigames.MidnightMinigames;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Configurable sounds (config.yml -&gt; sounds) and visual effects (config.yml -&gt; effects). */
public final class EffectService {

    private record SoundDef(String key, float volume, float pitch) {
    }

    private final MidnightMinigames plugin;
    private final Map<String, SoundDef> sounds = new HashMap<>();
    private boolean soundsEnabled = true;

    public EffectService(MidnightMinigames plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        sounds.clear();
        ConfigurationSection s = plugin.getConfigurationManager().config().getConfigurationSection("sounds");
        if (s == null) {
            return;
        }
        soundsEnabled = s.getBoolean("enabled", true);
        for (String name : s.getKeys(false)) {
            ConfigurationSection d = s.getConfigurationSection(name);
            if (d == null) {
                continue;
            }
            String key = d.getString("sound", "");
            if (key.isEmpty()) {
                continue;
            }
            NamespacedKey nk = NamespacedKey.fromString(key.toLowerCase());
            if (nk == null || Registry.SOUNDS.get(nk) == null) {
                plugin.getLogger().warning("[config] config.yml -> sounds." + name + ".sound: unknown sound '" + key + "'");
                continue;
            }
            sounds.put(name, new SoundDef(nk.toString(), (float) d.getDouble("volume", 1.0), (float) d.getDouble("pitch", 1.0)));
        }
    }

    public void play(Player p, String key) {
        if (!soundsEnabled) {
            return;
        }
        SoundDef d = sounds.get(key);
        if (d != null && p != null) {
            p.playSound(p.getLocation(), d.key(), d.volume(), d.pitch());
        }
    }

    public void playAll(Collection<Player> players, String key) {
        if (!soundsEnabled || players.isEmpty()) {
            return;
        }
        SoundDef d = sounds.get(key);
        if (d == null) {
            return;
        }
        for (Player p : players) {
            p.playSound(p.getLocation(), d.key(), d.volume(), d.pitch());
        }
    }

    public void playAt(Location l, String key) {
        SoundDef d = sounds.get(key);
        if (soundsEnabled && d != null && l.getWorld() != null) {
            l.getWorld().playSound(l, d.key(), d.volume(), d.pitch());
        }
    }

    /** Win fireworks; controlled by effects.win-fireworks. */
    public void firework(Location l) {
        ConfigurationSection s = plugin.getConfigurationManager().config().getConfigurationSection("effects.win-fireworks");
        if (s == null || !s.getBoolean("enabled", true) || l.getWorld() == null) {
            return;
        }
        int count = Math.max(1, Math.min(8, s.getInt("count", 2)));
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            Location at = l.clone().add(r.nextDouble(-2, 2), 0.5, r.nextDouble(-2, 2));
            Firework fw = l.getWorld().spawn(at, Firework.class);
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(Color.fromRGB(r.nextInt(0xFFFFFF)), Color.fromRGB(r.nextInt(0xFFFFFF)))
                    .withFlicker().build());
            meta.setPower(0);
            fw.setFireworkMeta(meta);
            fw.detonate();
        }
    }

    /** Cosmetic lightning on elimination; controlled by effects.elimination-lightning. */
    public void lightning(Location l) {
        if (l.getWorld() != null && plugin.getConfigurationManager().config().getBoolean("effects.elimination-lightning", false)) {
            l.getWorld().strikeLightningEffect(l);
        }
    }
}

package dev.midnight.minigames.core.placeholder;

import dev.midnight.minigames.MidnightMinigames;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/** PlaceholderAPI bridge. Only ever loaded when PlaceholderAPI is installed (see {@link PlaceholderHook}). */
final class MidnightExpansion extends PlaceholderExpansion {

    private final MidnightMinigames plugin;

    MidnightExpansion(MidnightMinigames plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "midnight";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Midnight";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offline, @NotNull String params) {
        return plugin.getPlaceholderService().resolve(offline == null ? null : offline.getPlayer(), params);
    }
}

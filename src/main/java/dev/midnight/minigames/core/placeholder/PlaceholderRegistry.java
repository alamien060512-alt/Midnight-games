package dev.midnight.minigames.core.placeholder;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * Lets minigames (and other plugins) expose game specific placeholders:
 * {@code %midnight_<gameId>_<key>%}. Works without PlaceholderAPI too (see {@code PlaceholderService#resolve}).
 */
public final class PlaceholderRegistry {

    private final Map<String, BiFunction<Player, String, String>> handlers = new ConcurrentHashMap<>();

    /** The handler receives the player (may be null for offline requests) and the key after "<gameId>_". */
    public void register(String gameId, BiFunction<Player, String, String> handler) {
        handlers.put(gameId.toLowerCase(), handler);
    }

    public void unregister(String gameId) {
        handlers.remove(gameId.toLowerCase());
    }

    /** Params are "<gameId>_<key>". Returns null when no handler matches. */
    public String resolve(Player player, String params) {
        int i = params.indexOf('_');
        if (i <= 0) {
            return null;
        }
        BiFunction<Player, String, String> h = handlers.get(params.substring(0, i).toLowerCase());
        return h == null ? null : h.apply(player, params.substring(i + 1));
    }
}

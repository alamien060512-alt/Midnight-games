package dev.midnight.minigames.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Text helpers: MiniMessage parsing with legacy '&' colour code support and simple placeholders. */
public final class TextUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Map<Character, String> LEGACY = Map.ofEntries(
            Map.entry('0', "<black>"), Map.entry('1', "<dark_blue>"), Map.entry('2', "<dark_green>"),
            Map.entry('3', "<dark_aqua>"), Map.entry('4', "<dark_red>"), Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"), Map.entry('7', "<gray>"), Map.entry('8', "<dark_gray>"),
            Map.entry('9', "<blue>"), Map.entry('a', "<green>"), Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"), Map.entry('d', "<light_purple>"), Map.entry('e', "<yellow>"),
            Map.entry('f', "<white>"), Map.entry('k', "<obfuscated>"), Map.entry('l', "<bold>"),
            Map.entry('m', "<strikethrough>"), Map.entry('n', "<underlined>"), Map.entry('o', "<italic>"),
            Map.entry('r', "<reset>"));

    private TextUtil() {
    }

    /** Converts '&' colour codes into MiniMessage tags. */
    public static String legacyToMini(String input) {
        if (input == null || input.indexOf('&') < 0) {
            return input == null ? "" : input;
        }
        Matcher m = HEX.matcher(input);
        String s = m.replaceAll("<#$1>");
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&' && i + 1 < s.length()) {
                String tag = LEGACY.get(Character.toLowerCase(s.charAt(i + 1)));
                if (tag != null) {
                    out.append(tag);
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    public static Component parse(String input) {
        return MM.deserialize(legacyToMini(input));
    }

    /** Parses text for item names/lore: italics are disabled unless explicitly requested. */
    public static Component item(String input) {
        return parse(input).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> items(List<String> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String l : lines) {
            out.add(item(l));
        }
        return out;
    }

    public static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    /** Replaces {key} placeholders. Pairs are key, value, key, value... */
    public static String replace(String input, String... pairs) {
        if (input == null) {
            return "";
        }
        String out = input;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            out = out.replace("{" + pairs[i] + "}", pairs[i + 1] == null ? "" : pairs[i + 1]);
        }
        return out;
    }

    public static String replace(String input, Map<String, String> vars) {
        if (input == null) {
            return "";
        }
        String out = input;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    public static String formatTime(long seconds) {
        if (seconds < 0) {
            seconds = 0;
        }
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s);
    }

    /** Formats milliseconds as mm:ss.SSS. */
    public static String formatMillis(long ms) {
        long total = Math.max(0, ms);
        long m = total / 60000;
        long s = (total % 60000) / 1000;
        long milli = total % 1000;
        return String.format("%02d:%02d.%03d", m, s, milli);
    }

    public static String formatNumber(double value) {
        if (value >= 1_000_000_000) {
            return String.format("%.2fB", value / 1_000_000_000);
        }
        if (value >= 1_000_000) {
            return String.format("%.2fM", value / 1_000_000);
        }
        if (value >= 10_000) {
            return String.format("%.1fK", value / 1_000);
        }
        return value == Math.floor(value) ? String.valueOf((long) value) : String.format("%.2f", value);
    }
}

package dev.midnight.minigames.core.team;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;

import java.util.Locale;

/** Standard team colours with matching chat colour, dye prefix (for WOOL/BED/GLASS...) and leather armour colour. */
public enum TeamColor {
    RED("Red", NamedTextColor.RED, "RED", Color.fromRGB(0xB02E26)),
    BLUE("Blue", NamedTextColor.BLUE, "BLUE", Color.fromRGB(0x3C44AA)),
    GREEN("Green", NamedTextColor.GREEN, "LIME", Color.fromRGB(0x80C71F)),
    YELLOW("Yellow", NamedTextColor.YELLOW, "YELLOW", Color.fromRGB(0xFED83D)),
    AQUA("Aqua", NamedTextColor.AQUA, "LIGHT_BLUE", Color.fromRGB(0x3AB3DA)),
    WHITE("White", NamedTextColor.WHITE, "WHITE", Color.fromRGB(0xF9FFFE)),
    PINK("Pink", NamedTextColor.LIGHT_PURPLE, "PINK", Color.fromRGB(0xF38BAA)),
    GRAY("Gray", NamedTextColor.GRAY, "GRAY", Color.fromRGB(0x474F52)),
    ORANGE("Orange", NamedTextColor.GOLD, "ORANGE", Color.fromRGB(0xF9801D)),
    PURPLE("Purple", NamedTextColor.DARK_PURPLE, "PURPLE", Color.fromRGB(0x8932B8)),
    CYAN("Cyan", NamedTextColor.DARK_AQUA, "CYAN", Color.fromRGB(0x169C9C)),
    LIME("Lime", NamedTextColor.GREEN, "LIME", Color.fromRGB(0x80C71F)),
    BLACK("Black", NamedTextColor.DARK_GRAY, "BLACK", Color.fromRGB(0x1D1D21));

    private final String displayName;
    private final NamedTextColor textColor;
    private final String dye;
    private final Color armorColor;

    TeamColor(String displayName, NamedTextColor textColor, String dye, Color armorColor) {
        this.displayName = displayName;
        this.textColor = textColor;
        this.dye = dye;
        this.armorColor = armorColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public NamedTextColor getTextColor() {
        return textColor;
    }

    public Color getArmorColor() {
        return armorColor;
    }

    public String getDye() {
        return dye;
    }

    /** E.g. {@code material("WOOL")} returns RED_WOOL, material("BED") returns RED_BED. */
    public Material material(String suffix) {
        return Material.matchMaterial(dye + "_" + suffix);
    }

    /** MiniMessage tag for this colour, e.g. {@code <red>}. */
    public String tag() {
        return "<" + textColor.toString() + ">";
    }

    public static TeamColor fromId(String id) {
        if (id == null) {
            return null;
        }
        try {
            return valueOf(id.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}

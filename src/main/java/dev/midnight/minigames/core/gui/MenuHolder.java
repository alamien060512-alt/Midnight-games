package dev.midnight.minigames.core.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Base class of every plugin menu. Menus are identified by their InventoryHolder - never by title -
 * and the core listener cancels all clicks/drags in them, so items can never be taken.
 */
public abstract class MenuHolder implements InventoryHolder {

    protected Inventory inventory;

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    /** Called for every click in the TOP inventory (already cancelled). */
    public abstract void onClick(Player player, InventoryClickEvent event);

    /** Called for clicks in the bottom (player) inventory while this menu is open (already cancelled). */
    public void onBottomClick(Player player, InventoryClickEvent event) {
    }
}

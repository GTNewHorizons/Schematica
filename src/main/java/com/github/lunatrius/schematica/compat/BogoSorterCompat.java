package com.github.lunatrius.schematica.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import com.github.lunatrius.schematica.reference.Reference;

/**
 * Inventory Bogo Sorter (in GTNH 2.9.0) makes {@code Slot.canTakeStack} return false on the client unless its static
 * flag {@code ShortcutHandler.SetCanTakeStack} is set, which it sets around its own inventory actions. Without it a
 * client-side {@code windowClick} is not applied locally (only by the server, a moment later), so the printer never
 * sees the item it just swapped into the hotbar. Raise the flag the same way for the printer's own clicks.
 * <p>
 * Reflection only: no compile-time dependency, and a no-op when Bogo Sorter is absent or changes its internals.
 */
public final class BogoSorterCompat {

    private static final Field FLAG = findFlag();

    private BogoSorterCompat() {}

    private static Field findFlag() {
        try {
            final Field f = Class.forName("com.cleanroommc.bogosorter.ShortcutHandler")
                .getField("SetCanTakeStack");
            if (f.getType() == boolean.class && Modifier.isStatic(f.getModifiers())) {
                Reference.logger.info("Inventory Bogo Sorter found: printer slot swaps will be applied client-side.");
                return f;
            }
        } catch (ClassNotFoundException ignored) {
            // Bogo Sorter not installed.
        } catch (Throwable t) {
            Reference.logger.warn("Inventory Bogo Sorter present but its slot flag is not usable", t);
        }
        return null;
    }

    /** Sets the flag to true; returns the previous value to hand back to {@link #end(boolean)}. */
    public static boolean begin() {
        if (FLAG == null) {
            return false;
        }
        try {
            final boolean previous = FLAG.getBoolean(null);
            FLAG.setBoolean(null, true);
            return previous;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void end(boolean previous) {
        if (FLAG == null) {
            return;
        }
        try {
            FLAG.setBoolean(null, previous);
        } catch (Throwable ignored) {}
    }
}

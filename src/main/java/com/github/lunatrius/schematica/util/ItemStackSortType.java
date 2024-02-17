package com.github.lunatrius.schematica.util;

import java.util.Comparator;
import java.util.List;

import com.github.lunatrius.schematica.client.util.BlockList;
import com.github.lunatrius.schematica.reference.Reference;

public enum ItemStackSortType {

    NAME_ASC("name", "\u2191", (wrappedItemStackA, wrappedItemStackB) -> {
        final String nameA = wrappedItemStackA.getItemStackDisplayName();
        final String nameB = wrappedItemStackB.getItemStackDisplayName();

        return nameA.compareTo(nameB);
    }),
    NAME_DESC("name", "\u2193", (wrappedItemStackA, wrappedItemStackB) -> {
        final String nameA = wrappedItemStackA.getItemStackDisplayName();
        final String nameB = wrappedItemStackB.getItemStackDisplayName();

        return nameB.compareTo(nameA);
    }),
    SIZE_ASC("amount", "\u2191", Comparator.comparingInt(wrappedItemStackA -> wrappedItemStackA.total)),
    SIZE_DESC("amount", "\u2193",
            (wrappedItemStackA, wrappedItemStackB) -> wrappedItemStackB.total - wrappedItemStackA.total);

    private final Comparator<BlockList.WrappedItemStack> comparator;

    public final String label;
    public final String glyph;

    ItemStackSortType(final String label, final String glyph, final Comparator<BlockList.WrappedItemStack> comparator) {
        this.label = label;
        this.glyph = glyph;
        this.comparator = comparator;
    }

    public void sort(final List<BlockList.WrappedItemStack> blockList) {
        try {
            blockList.sort(this.comparator);
        } catch (final Exception e) {
            Reference.logger.error("Could not sort the block list!", e);
        }
    }

    public ItemStackSortType next() {
        final ItemStackSortType[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static ItemStackSortType fromString(final String name) {
        try {
            return valueOf(name);
        } catch (final Exception ignored) {}

        return NAME_ASC;
    }
}

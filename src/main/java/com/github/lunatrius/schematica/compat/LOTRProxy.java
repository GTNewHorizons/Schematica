package com.github.lunatrius.schematica.compat;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

import lotr.common.LOTRMod;

public class LOTRProxy implements ILOTRPresent {

    /**
     * Blacklist for lotr mod blocks that don't work with the printer
     */
    @Override
    public Boolean isBlackListed(Block block, ItemStack itemStack) {
        // Handles the chiseled sections
        if (itemStack.getItem() == LOTRMod.chisel || itemStack.getItem() == LOTRMod.chiselIthildin) {
            return true;
        }

        return false;
    }
}

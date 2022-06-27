package com.github.lunatrius.schematica.compat;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

public class NoLOTRProxy implements ILOTRPresent {
    @Override
    public Boolean isBlackListed(Block block, ItemStack itemStack) {
        return false;
    }
}

package com.github.lunatrius.schematica.compat;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

/**
 * Interface for handling compatibility with the lotr mod, based of this post by diesieben07: <a href="https://forums.minecraftforge.net/topic/63802-how-to-add-compatibility-with-other-mods/">...</a>
 */
public interface ILOTRPresent {
    /**
     * Extended blacklist for the printer
     * @return true if blacklisted for printer placement
     */
    Boolean isBlackListed(Block block, ItemStack itemStack);
    // TODO:fix printer functionality with weapon racks/ armour stands/ plates etc.

}

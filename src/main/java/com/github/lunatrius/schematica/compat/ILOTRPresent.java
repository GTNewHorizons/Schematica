package com.github.lunatrius.schematica.compat;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

/**
 * Interface for handling compatibility with other mods, based of this post by diesieben07: <a href="https://forums.minecraftforge.net/topic/63802-how-to-add-compatibility-with-other-mods/">...</a>
 */
public interface ILOTRPresent {
    /**
     * Extended blacklist for the printer
     * @return true if blacklisted for printer placement
     */
    Boolean isBlackListed(Block block, ItemStack itemStack);
    /*
        TODO:
        fix rendering of tile entities (fix chat spam):
    [16:15:45] [Client thread/ERROR]: ########## GL ERROR ##########
    [16:15:45] [Client thread/ERROR]: @ Post render
    [16:15:45] [Client thread/ERROR]: 1283: Stack overflow
    [16:15:45] [Client thread/ERROR] [Schematica]: Failed to render a tile entity!
    java.lang.NullPointerException

        fix printer functionality with weapon racks/ armour stands/ plates etc.
    */
}

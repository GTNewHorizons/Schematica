package com.github.lunatrius.schematica.nbt;

import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;

import com.github.lunatrius.schematica.api.ISchematic;
import com.gtnewhorizons.postea.utility.BlockInfo;
import com.gtnewhorizons.postea.utility.TransformerRegistry;

public final class LegacyGregTechOre {

    private static final String TILE_ID = "GT_TileEntity_Ores";

    private LegacyGregTechOre() {}

    public static boolean replace(ISchematic schematic, NBTTagCompound tag) {
        for (var converter : TransformerRegistry.getTileEntityToNormalBlockTransformerFunction(TILE_ID)) {
            // GregTech's ore converter needs only the material, host rock and natural flag from the tile tag.
            final BlockInfo ore = converter.apply(tag, null, null);
            if (ore != null && ore.block != Blocks.air && ore.tileTransformer == null) {
                return schematic
                    .setBlock(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z"), ore.block, ore.metadata);
            }
        }
        return false;
    }
}

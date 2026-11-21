package com.github.lunatrius.schematica.client.compat;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import crazypants.enderio.machine.painter.BlockPaintedSlab;
import crazypants.enderio.machine.painter.PainterUtil;
import crazypants.enderio.machine.painter.TileEntityPaintedBlock;

public final class EnderIOSlabSupport {

    private EnderIOSlabSupport() {}

    public static boolean isPaintedSlab(Block block) {
        return block instanceof BlockPaintedSlab;
    }

    public static ItemStack getItem(TileEntity tile) {
        if (tile instanceof TileEntityPaintedBlock painted) {
            return BlockPaintedSlab
                .createItemStackForSourceBlock(painted.getSourceBlock(), painted.getSourceBlockMetadata());
        }
        return null;
    }

    public static boolean itemsMatch(ItemStack actual, ItemStack expected) {
        return PainterUtil.isMetadataEquivelent(actual, expected);
    }

    public static boolean matchesPaint(TileEntity actual, TileEntity expected) {
        return actual instanceof TileEntityPaintedBlock placed && expected instanceof TileEntityPaintedBlock saved
            && placed.getSourceBlock() == saved.getSourceBlock()
            && placed.getSourceBlockMetadata() == saved.getSourceBlockMetadata();
    }
}

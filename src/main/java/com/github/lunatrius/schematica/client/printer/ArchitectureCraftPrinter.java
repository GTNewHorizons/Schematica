package com.github.lunatrius.schematica.client.printer;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.lunatrius.schematica.client.compat.ArchitectureCraftSupport;

final class ArchitectureCraftPrinter {

    private ArchitectureCraftPrinter() {}

    static boolean place(PrinterInteraction interaction, int x, int y, int z, TileEntity tile, ItemStack item) {
        if (!interaction.canSelectItem(item)) {
            return false;
        }
        for (ForgeDirection support : interaction.findSupports(x, y, z)) {
            for (boolean sneaking : new boolean[] { true, false }) {
                // A standing click on the support could activate it instead of placing the shape.
                final int clickedX = x + (sneaking ? support.offsetX : 0);
                final int clickedY = y + (sneaking ? support.offsetY : 0);
                final int clickedZ = z + (sneaking ? support.offsetZ : 0);
                if (!PrinterInteraction
                    .canClickForPlacement(interaction.world.getBlock(clickedX, clickedY, clickedZ), item, sneaking)) {
                    continue;
                }
                final Vec3 hit = ArchitectureCraftSupport
                    .findHit(interaction.player, interaction.world, tile, item, x, y, z, support, sneaking);
                if (hit != null) {
                    return interaction.selectItem(item) && interaction.withSneaking(
                        sneaking,
                        () -> interaction.place(
                            interaction.player.getHeldItem(),
                            clickedX,
                            clickedY,
                            clickedZ,
                            support.getOpposite()
                                .ordinal(),
                            sneaking ? hit : hit.addVector(-support.offsetX, -support.offsetY, -support.offsetZ)));
                }
            }
        }
        return false;
    }
}

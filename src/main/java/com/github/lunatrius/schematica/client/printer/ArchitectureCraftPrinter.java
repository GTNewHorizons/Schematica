package com.github.lunatrius.schematica.client.printer;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.lunatrius.schematica.client.compat.ArchitectureCraftSupport;
import com.github.lunatrius.schematica.handler.ConfigurationHandler;

final class ArchitectureCraftPrinter {

    private ArchitectureCraftPrinter() {}

    static boolean place(PrinterInteraction interaction, int x, int y, int z, TileEntity tile, ItemStack item) {
        if (!interaction.canSelectItem(item)) {
            return false;
        }
        final ForgeDirection[] supports = interaction.findSupports(x, y, z);
        final boolean hasSupport = supports.length > 0;
        if (!hasSupport && ConfigurationHandler.placeAdjacent) {
            return false;
        }
        if (placeAgainstSupports(interaction, x, y, z, tile, item, supports, false)) {
            return true;
        }
        return ArchitectureCraftSupport.isTurnableCapital(tile)
            && interaction.canSelectItem(ArchitectureCraftSupport.getHammer())
            && placeAgainstSupports(interaction, x, y, z, tile, item, supports, true);
    }

    static boolean rotateCapital(PrinterInteraction interaction, int x, int y, int z) {
        return interaction.selectItem(ArchitectureCraftSupport.getHammer()) && interaction.withSneaking(
            false,
            () -> interaction.useBlockOnServer(
                interaction.player.getHeldItem(),
                x,
                y,
                z,
                ForgeDirection.UP.ordinal(),
                Vec3.createVectorHelper(x + 0.5, y + 1, z + 0.5)));
    }

    private static boolean placeAgainstSupports(PrinterInteraction interaction, int x, int y, int z, TileEntity tile,
        ItemStack item, ForgeDirection[] supports, boolean allowHammerTurn) {
        final boolean hasSupport = supports.length > 0;
        for (ForgeDirection support : hasSupport ? supports : ForgeDirection.VALID_DIRECTIONS) {
            for (boolean sneaking : new boolean[] { true, false }) {
                // A standing click on the support could activate it instead of placing the shape.
                final boolean clickSupport = hasSupport && sneaking;
                final int clickedX = x + (clickSupport ? support.offsetX : 0);
                final int clickedY = y + (clickSupport ? support.offsetY : 0);
                final int clickedZ = z + (clickSupport ? support.offsetZ : 0);
                if (!PrinterInteraction
                    .canClickForPlacement(interaction.world.getBlock(clickedX, clickedY, clickedZ), item, sneaking)) {
                    continue;
                }
                final Vec3 hit = ArchitectureCraftSupport.findHit(
                    interaction.player,
                    interaction.world,
                    tile,
                    item,
                    x,
                    y,
                    z,
                    support,
                    sneaking,
                    allowHammerTurn);
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
                            clickSupport ? hit : hit.addVector(-support.offsetX, -support.offsetY, -support.offsetZ)));
                }
            }
        }
        return false;
    }
}

package com.github.lunatrius.schematica.client.printer;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.lunatrius.schematica.client.compat.ArchitectureCraftSupport;
import com.github.lunatrius.schematica.handler.ConfigurationHandler;

final class ArchitectureCraftPrinter {

    private static final long TURN_ACK_TIMEOUT = TimeUnit.SECONDS.toNanos(5);
    // An outstanding click belongs to the live tile, even when the schematic is refreshed.
    private final Map<TileEntity, PendingTurn> pendingTurns = new WeakHashMap<>();

    void beginPass(World world) {
        final long now = System.nanoTime();
        this.pendingTurns.entrySet()
            .removeIf(entry -> {
                final TileEntity tile = entry.getKey();
                final PendingTurn pending = entry.getValue();
                return tile.getWorldObj() != world || !world.blockExists(tile.xCoord, tile.yCoord, tile.zCoord)
                    || world.getTileEntity(tile.xCoord, tile.yCoord, tile.zCoord) != tile
                    || ArchitectureCraftSupport.getTurn(tile) != pending.turn
                    || now - pending.sentAt >= TURN_ACK_TIMEOUT;
            });
    }

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

    boolean rotateCapital(PrinterInteraction interaction, TileEntity tile, int x, int y, int z) {
        if (this.pendingTurns.containsKey(tile) || !interaction.selectItem(ArchitectureCraftSupport.getHammer())) {
            return false;
        }
        final int previousTurn = ArchitectureCraftSupport.getTurn(tile);
        final boolean sent = interaction.withSneaking(
            false,
            () -> interaction.useBlockOnServer(
                interaction.player.getHeldItem(),
                x,
                y,
                z,
                ForgeDirection.NORTH.ordinal(),
                Vec3.createVectorHelper(x + 0.5, y + 0.5, z)));
        if (sent) {
            // A turn is relative. Wait for a tile update before sending another click.
            this.pendingTurns.put(tile, new PendingTurn(previousTurn, System.nanoTime()));
        }
        return sent;
    }

    private static final class PendingTurn {

        private final int turn;
        private final long sentAt;

        private PendingTurn(int turn, long sentAt) {
            this.turn = turn;
            this.sentAt = sentAt;
        }
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

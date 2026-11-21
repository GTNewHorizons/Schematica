package com.github.lunatrius.schematica.client.printer;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;

import com.github.lunatrius.schematica.client.compat.MicroblockSupport;
import com.github.lunatrius.schematica.handler.ConfigurationHandler;

final class MicroblockPrinter {

    private final Map<ChunkPosition, PendingPlacement> pendingPlacements = new HashMap<>();

    void reset() {
        this.pendingPlacements.clear();
    }

    boolean isWaiting(World world, int localX, int localY, int localZ, int x, int y, int z) {
        if (this.pendingPlacements.isEmpty()) {
            return false;
        }
        final ChunkPosition cell = new ChunkPosition(localX, localY, localZ);
        final PendingPlacement pending = this.pendingPlacements.get(cell);
        if (pending == null) {
            return false;
        }
        if (!pending.click.targets(x, y, z) || pending.click.isConfirmed(world.getTileEntity(x, y, z))) {
            this.pendingPlacements.remove(cell);
            return false;
        }
        pending.remainingDelay -= Math.max(1, ConfigurationHandler.placeDelay);
        if (pending.remainingDelay <= 0) {
            this.pendingPlacements.remove(cell);
        }
        return true;
    }

    boolean place(PrinterInteraction interaction, TileEntity tile, int localX, int localY, int localZ, int x, int y,
        int z) {
        final MicroblockSupport.Click click = MicroblockSupport
            .findPlacement(interaction.player, tile, x, y, z, interaction::canSelectItem);
        if (click == null || !interaction.selectItem(click.item)) {
            return false;
        }
        final MovingObjectPosition hit = click.hit;
        // ItemMicroPart re-traces the clicked block from the player's aim on both sides.
        if (!interaction.withSneaking(
            click.sneaking,
            () -> interaction.withLook(
                click.yaw,
                click.pitch,
                () -> interaction.place(
                    interaction.player.getCurrentEquippedItem(),
                    hit.blockX,
                    hit.blockY,
                    hit.blockZ,
                    hit.sideHit,
                    hit.hitVec)))) {
            return false;
        }
        this.pendingPlacements.put(new ChunkPosition(localX, localY, localZ), new PendingPlacement(click));
        return true;
    }

    private static final class PendingPlacement {

        private final MicroblockSupport.Click click;
        private int remainingDelay = Math.max(1, ConfigurationHandler.timeout);

        private PendingPlacement(MicroblockSupport.Click click) {
            this.click = click;
        }
    }
}

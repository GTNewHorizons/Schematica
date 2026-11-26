package com.github.lunatrius.schematica.client.printer;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;

import com.github.lunatrius.schematica.client.compat.MicroblockSupport;

final class MicroblockPrinter {

    private static final long ACK_TIMEOUT = TimeUnit.SECONDS.toNanos(5);

    private final Map<ChunkPosition, PendingPlacement> pendingPlacements = new HashMap<>();
    private WeakReference<World> pendingWorld = new WeakReference<>(null);

    void beginPass(World world) {
        if (this.pendingWorld.get() != world) {
            this.pendingPlacements.clear();
            this.pendingWorld = new WeakReference<>(world);
        } else if (!this.pendingPlacements.isEmpty()) {
            final long now = System.nanoTime();
            this.pendingPlacements.values()
                .removeIf(pending -> now - pending.sentAt >= ACK_TIMEOUT);
        }
    }

    boolean isWaiting(World world, int x, int y, int z) {
        if (this.pendingPlacements.isEmpty()) {
            return false;
        }
        final ChunkPosition cell = new ChunkPosition(x, y, z);
        final PendingPlacement pending = this.pendingPlacements.get(cell);
        if (pending == null) {
            return false;
        }
        if (world.blockExists(x, y, z) && pending.click.isConfirmed(world.getTileEntity(x, y, z))) {
            this.pendingPlacements.remove(cell);
            return false;
        }
        return true;
    }

    boolean place(PrinterInteraction interaction, TileEntity tile, int x, int y, int z) {
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
        // ForgeMultipart expands on the server. Keep the click pending across schematic moves and refreshes.
        this.pendingPlacements.put(new ChunkPosition(x, y, z), new PendingPlacement(click));
        return true;
    }

    private static final class PendingPlacement {

        private final MicroblockSupport.Click click;
        private final long sentAt = System.nanoTime();

        private PendingPlacement(MicroblockSupport.Click click) {
            this.click = click;
        }
    }
}

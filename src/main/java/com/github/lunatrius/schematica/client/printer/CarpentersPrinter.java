package com.github.lunatrius.schematica.client.printer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.lunatrius.schematica.client.compat.CarpentersBlocksSupport;
import com.github.lunatrius.schematica.client.compat.CarpentersHammerSupport;
import com.github.lunatrius.schematica.client.world.SchematicWorld;
import com.github.lunatrius.schematica.handler.ConfigurationHandler;

final class CarpentersPrinter {

    private static final float[] STAIR_HIT_OFFSETS = { 0.5f, 0.125f, 0.875f, 0.25f, 0.75f };

    private final Map<ChunkPosition, PendingClick> pendingClicks = new HashMap<>();
    private long currentPass;

    void reset() {
        this.pendingClicks.clear();
    }

    void beginPass(World world) {
        final long pass = ++this.currentPass;
        this.pendingClicks.values()
            .removeIf(click -> click.allowsNextClick(world, pass));
    }

    boolean isWaiting(int x, int y, int z) {
        return !this.pendingClicks.isEmpty() && this.pendingClicks.containsKey(new ChunkPosition(x, y, z));
    }

    boolean configureBlock(PrinterInteraction interaction, SchematicWorld schematic, Block block, TileEntity target,
        int x, int y, int z) {
        final World world = interaction.world;
        final TileEntity placed = world.getTileEntity(x, y, z);
        final CarpentersHammerSupport.Adjustment adjustment = CarpentersHammerSupport
            .findAdjustment(block, schematic, target, placed);
        if (adjustment != null && applyHammer(interaction, adjustment, x, y, z)) {
            final PendingClick click = new PendingClick(world, this.currentPass, () -> adjustment.isConfirmed(world));
            for (TileEntity piece : adjustment.affected) {
                this.pendingClicks.put(new ChunkPosition(piece.xCoord, piece.yCoord, piece.zCoord), click);
            }
            return true;
        }

        final CarpentersBlocksSupport.Interaction attachment = CarpentersBlocksSupport
            .findAttachment(block, target, placed, interaction.player, interaction::canSelectItem);
        if (attachment == null || !interaction.selectItem(attachment.item)) {
            return false;
        }
        if (!useFace(interaction, x, y, z, attachment.face, attachment.sneaking)) {
            return false;
        }
        this.pendingClicks.put(
            new ChunkPosition(x, y, z),
            new PendingClick(world, this.currentPass, () -> attachment.isConfirmed(world.getTileEntity(x, y, z))));
        return true;
    }

    private static boolean applyHammer(PrinterInteraction interaction, CarpentersHammerSupport.Adjustment adjustment,
        int x, int y, int z) {
        final ItemStack hammer = findHammer(interaction);
        if (hammer == null || !interaction.selectItem(hammer)
            || !CarpentersHammerSupport.canUseHammer(interaction.player, interaction.world)) {
            return false;
        }
        if (adjustment.click != CarpentersHammerSupport.Click.LEFT) {
            return useFace(
                interaction,
                x,
                y,
                z,
                adjustment.face,
                adjustment.click == CarpentersHammerSupport.Click.SNEAK_RIGHT);
        }
        return interaction.withSneaking(
            false,
            () -> CarpentersBlocksSupport.isCoverable(interaction.world.getBlock(x, y, z))
                && interaction.tapWithoutMining(x, y, z, adjustment.face));
    }

    private static boolean useFace(PrinterInteraction interaction, int x, int y, int z, int side, boolean sneaking) {
        final ForgeDirection face = ForgeDirection.getOrientation(side);
        final Vec3 hit = Vec3.createVectorHelper(
            x + 0.5 + face.offsetX * 0.5,
            y + 0.5 + face.offsetY * 0.5,
            z + 0.5 + face.offsetZ * 0.5);
        final boolean used = interaction
            .withSneaking(sneaking, () -> interaction.useBlock(interaction.player.getHeldItem(), x, y, z, side, hit));
        // Carpenter's Blocks cancels its sneak interaction after dispatching a separate activation packet.
        return used || sneaking;
    }

    static boolean place(PrinterInteraction interaction, SchematicWorld schematic, Block block, int localX, int localY,
        int localZ, ItemStack item, BooleanSupplier placeNormally) {
        final int x = schematic.position.x + localX;
        final int y = schematic.position.y + localY;
        final int z = schematic.position.z + localZ;
        if (CarpentersBlocksSupport.isGarageDoor(block)) {
            final Float yaw = CarpentersBlocksSupport
                .findGarageDoorYaw(block, schematic, interaction.world, localX, localY, localZ);
            return yaw != null && placeGarageDoor(interaction, x, y, z, block, item, yaw);
        }
        if (CarpentersBlocksSupport.isStairs(block)) {
            final TileEntity tile = schematic.getTileEntity(localX, localY, localZ);
            return placeStairs(interaction, x, y, z, block, tile, item)
                || (findHammer(interaction) != null && placeNormally.getAsBoolean());
        }
        return placeNormally.getAsBoolean();
    }

    private static ItemStack findHammer(PrinterInteraction interaction) {
        return CarpentersHammerSupport
            .findHammer(interaction.player.inventory.mainInventory, interaction::canSelectItem);
    }

    private static boolean placeGarageDoor(PrinterInteraction interaction, int x, int y, int z, Block block,
        ItemStack item, float yaw) {
        if (PrinterInteraction.isBlacklisted(block, item) || !interaction.selectItem(item)) {
            return false;
        }
        return interaction.withSneaking(
            true,
            () -> interaction.withLook(
                yaw,
                interaction.player.rotationPitch,
                () -> interaction.place(
                    interaction.player.getHeldItem(),
                    x,
                    y + 1,
                    z,
                    ForgeDirection.DOWN.ordinal(),
                    Vec3.createVectorHelper(x + 0.5, y + 1, z + 0.5))));
    }

    private static boolean placeStairs(PrinterInteraction interaction, int x, int y, int z, Block block,
        TileEntity tile, ItemStack item) {
        if (PrinterInteraction.isBlacklisted(block, item) || !interaction.canSelectItem(item)) {
            return false;
        }
        final ForgeDirection[] supports = interaction.findSupports(x, y, z);
        final boolean hasSupport = supports.length > 0;
        if (!hasSupport && ConfigurationHandler.placeAdjacent) {
            return false;
        }
        for (ForgeDirection support : hasSupport ? supports : ForgeDirection.VALID_DIRECTIONS) {
            final ForgeDirection face = support.getOpposite();
            final int clickedX = hasSupport ? x + support.offsetX : x;
            final int clickedY = hasSupport ? y + support.offsetY : y;
            final int clickedZ = hasSupport ? z + support.offsetZ : z;
            for (float across : STAIR_HIT_OFFSETS) {
                for (float height : STAIR_HIT_OFFSETS) {
                    final float hitX = face.offsetX == 0 ? across : (1 + face.offsetX) * 0.5f;
                    final float hitY = face.offsetY == 0 ? height : (1 + face.offsetY) * 0.5f;
                    final float hitZ = face.offsetZ == 0 ? across : (1 + face.offsetZ) * 0.5f;
                    if (CarpentersBlocksSupport.matchesStairPlacement(
                        tile,
                        block,
                        interaction.world,
                        x,
                        y,
                        z,
                        face.ordinal(),
                        hitX,
                        hitY,
                        hitZ,
                        interaction.player)) {
                        final Vec3 hit = Vec3.createVectorHelper(clickedX + hitX, clickedY + hitY, clickedZ + hitZ);
                        return interaction.withSneaking(
                            true,
                            () -> interaction.selectItem(item) && interaction.place(
                                interaction.player.getHeldItem(),
                                clickedX,
                                clickedY,
                                clickedZ,
                                face.ordinal(),
                                hit));
                    }
                }
            }
        }
        return false;
    }

    private static final class PendingClick {

        private final World world;
        private final long retryPass;
        private final BooleanSupplier confirmed;
        private long lastChecked = Long.MIN_VALUE;
        private boolean ready;

        private PendingClick(World world, long pass, BooleanSupplier confirmed) {
            final int delay = Math.max(1, ConfigurationHandler.placeDelay);
            final int timeout = Math.max(20, ConfigurationHandler.timeout);
            this.world = world;
            // Keep the original retry interval in printer passes for clicks without a server update.
            this.retryPass = pass + 2 + (timeout - 1) / delay;
            this.confirmed = confirmed;
        }

        private boolean allowsNextClick(World world, long pass) {
            if (world != this.world || pass >= this.retryPass) {
                return true;
            }
            // Connected panels share a click. Check all of them once per printer pass.
            if (pass != this.lastChecked) {
                this.lastChecked = pass;
                this.ready = this.confirmed.getAsBoolean();
            }
            return this.ready;
        }
    }
}

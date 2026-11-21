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
        if (!CarpentersBlocksSupport.canConfigure(placed, interaction.player)) {
            return false;
        }
        final CarpentersHammerSupport.Adjustment adjustment = CarpentersHammerSupport
            .findAdjustment(block, schematic, target, placed);
        if (adjustment != null && adjustment.affected.stream()
            .allMatch(piece -> CarpentersBlocksSupport.canConfigure(piece, interaction.player))
            && applyHammer(interaction, adjustment, x, y, z)) {
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
        return useFace(
            interaction,
            x,
            y,
            z,
            adjustment.face,
            adjustment.click == CarpentersHammerSupport.Click.SNEAK_RIGHT);
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
        final TileEntity tile = schematic.getTileEntity(localX, localY, localZ);
        if (CarpentersBlocksSupport.isStairs(block)) {
            return placeStairs(interaction, x, y, z, block, tile, item);
        }
        final ForgeDirection face = CarpentersBlocksSupport.getAttachmentDirection(block, tile);
        if (face != null) {
            return placeSidedBlock(interaction, x, y, z, block, item, face);
        }
        if (CarpentersBlocksSupport.isSideWedge(block, tile, item)) {
            return placeSideWedge(interaction, x, y, z, block, item, tile);
        }
        final Float wedgeYaw = CarpentersBlocksSupport.getWedgeYaw(block, tile, item);
        if (wedgeYaw != null) {
            final ForgeDirection verticalFace = CarpentersBlocksSupport.getWedgeFace(tile);
            final ForgeDirection wallFace = CarpentersBlocksSupport.getWedgeWallFace(tile);
            if (canUseSupport(interaction, x, y, z, verticalFace, item)) {
                return placeFromFace(interaction, x, y, z, block, item, verticalFace, true, 0.5, 0.5, wedgeYaw);
            }
            if (canUseSupport(interaction, x, y, z, wallFace, item)) {
                final double hitY = verticalFace == ForgeDirection.UP ? 0.25 : 0.75;
                return placeFromFace(interaction, x, y, z, block, item, wallFace, true, 0.5, hitY, wedgeYaw);
            }
            if (!ConfigurationHandler.placeAdjacent) {
                return placeFromFace(interaction, x, y, z, block, item, verticalFace, false, 0.5, 0.5, wedgeYaw);
            }
            final ForgeDirection opposite = verticalFace.getOpposite();
            return canUseSupport(interaction, x, y, z, opposite, item)
                && CarpentersBlocksSupport.canReshapeOppositeWedge(tile)
                && findHammer(interaction) != null
                && placeFromFace(interaction, x, y, z, block, item, opposite, true, 0.5, 0.5, wedgeYaw);
        }
        return placeNormally.getAsBoolean();
    }

    private static boolean placeSideWedge(PrinterInteraction interaction, int x, int y, int z, Block block,
        ItemStack item, TileEntity tile) {
        final float yaw = interaction.player.rotationYaw;
        ForgeDirection directFace = ForgeDirection.UNKNOWN;
        for (ForgeDirection face : ForgeDirection.VALID_DIRECTIONS) {
            final double across = CarpentersBlocksSupport.getSideWedgeHit(tile, face);
            if (across == 0) {
                continue;
            }
            if (canUseSupport(interaction, x, y, z, face, item)) {
                return placeFromFace(interaction, x, y, z, block, item, face, true, across, 0.5, yaw);
            }
            directFace = face;
        }
        return !ConfigurationHandler.placeAdjacent && directFace != ForgeDirection.UNKNOWN
            && placeFromFace(
                interaction,
                x,
                y,
                z,
                block,
                item,
                directFace,
                false,
                CarpentersBlocksSupport.getSideWedgeHit(tile, directFace),
                0.5,
                yaw);
    }

    private static boolean placeSidedBlock(PrinterInteraction interaction, int x, int y, int z, Block block,
        ItemStack item, ForgeDirection face) {
        if (face == ForgeDirection.UNKNOWN) {
            return false;
        }
        if (CarpentersBlocksSupport.isFreestandingLadder(block, face)) {
            final float yaw = face == ForgeDirection.UP ? 90 : 0;
            for (ForgeDirection vertical : new ForgeDirection[] { ForgeDirection.UP, ForgeDirection.DOWN }) {
                if (block.canPlaceBlockOnSide(interaction.world, x, y, z, vertical.ordinal())
                    && canUseSupport(interaction, x, y, z, vertical, item)) {
                    return placeFromFace(interaction, x, y, z, block, item, vertical, true, 0.5, 0.5, yaw);
                }
            }
            return false;
        }
        return block.canPlaceBlockOnSide(interaction.world, x, y, z, face.ordinal())
            && canUseSupport(interaction, x, y, z, face, item)
            && placeFromFace(interaction, x, y, z, block, item, face, true, 0.5, 0.5, interaction.player.rotationYaw);
    }

    private static boolean canUseSupport(PrinterInteraction interaction, int x, int y, int z, ForgeDirection face,
        ItemStack item) {
        final int sx = x - face.offsetX;
        final int sy = y - face.offsetY;
        final int sz = z - face.offsetZ;
        final Block support = interaction.world.getBlock(sx, sy, sz);
        return !support.isAir(interaction.world, sx, sy, sz) && !support.isReplaceable(interaction.world, sx, sy, sz)
            && PrinterInteraction.canClickForPlacement(support, item, true);
    }

    private static boolean placeFromFace(PrinterInteraction interaction, int x, int y, int z, Block block,
        ItemStack item, ForgeDirection face, boolean adjacent, double across, double hitY, float yaw) {
        if (PrinterInteraction.isBlacklisted(block, item) || !interaction.selectItem(item)) {
            return false;
        }
        final int sx = adjacent ? x - face.offsetX : x;
        final int sy = adjacent ? y - face.offsetY : y;
        final int sz = adjacent ? z - face.offsetZ : z;
        final Vec3 hit = Vec3.createVectorHelper(
            sx + (face.offsetX == 0 ? across : (1 + face.offsetX) * 0.5),
            sy + hitY + face.offsetY * 0.5,
            sz + (face.offsetZ == 0 ? across : (1 + face.offsetZ) * 0.5));
        final BooleanSupplier click = () -> interaction.withSneaking(
            true,
            () -> interaction.place(interaction.player.getHeldItem(), sx, sy, sz, face.ordinal(), hit));
        return yaw == interaction.player.rotationYaw ? click.getAsBoolean()
            : interaction.withLook(yaw, interaction.player.rotationPitch, click);
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
        final float originalYaw = interaction.player.rotationYaw;
        final float pitch = interaction.player.rotationPitch;
        final float[] yaws = { originalYaw, 0, 90, 180, 270 };
        for (boolean allowReshape : new boolean[] { false, true }) {
            if (allowReshape && findHammer(interaction) == null) {
                return false;
            }
            for (float yaw : yaws) {
                for (ForgeDirection support : hasSupport ? supports : ForgeDirection.VALID_DIRECTIONS) {
                    final ForgeDirection face = support.getOpposite();
                    final int clickedX = hasSupport ? x + support.offsetX : x;
                    final int clickedY = hasSupport ? y + support.offsetY : y;
                    final int clickedZ = hasSupport ? z + support.offsetZ : z;
                    if (!PrinterInteraction
                        .canClickForPlacement(interaction.world.getBlock(clickedX, clickedY, clickedZ), item, true)) {
                        continue;
                    }
                    final Vec3 hit = findStairHit(interaction.world, x, y, z, block, tile, yaw, allowReshape, face);
                    if (hit == null) {
                        continue;
                    }
                    final BooleanSupplier click = () -> interaction.withSneaking(
                        true,
                        () -> interaction.place(
                            interaction.player.getHeldItem(),
                            clickedX,
                            clickedY,
                            clickedZ,
                            face.ordinal(),
                            hit.addVector(clickedX, clickedY, clickedZ)));
                    return interaction.selectItem(item)
                        && (yaw == originalYaw ? click.getAsBoolean() : interaction.withLook(yaw, pitch, click));
                }
            }
        }
        return false;
    }

    private static Vec3 findStairHit(World world, int x, int y, int z, Block block, TileEntity tile, float yaw,
        boolean allowReshape, ForgeDirection face) {
        for (float across : STAIR_HIT_OFFSETS) {
            for (float height : STAIR_HIT_OFFSETS) {
                final float hitX = face.offsetX == 0 ? across : (1 + face.offsetX) * 0.5f;
                final float hitY = face.offsetY == 0 ? height : (1 + face.offsetY) * 0.5f;
                final float hitZ = face.offsetZ == 0 ? across : (1 + face.offsetZ) * 0.5f;
                if (CarpentersBlocksSupport.matchesStairPlacement(
                    tile,
                    block,
                    world,
                    x,
                    y,
                    z,
                    face.ordinal(),
                    hitX,
                    hitY,
                    hitZ,
                    yaw,
                    allowReshape)) {
                    return Vec3.createVectorHelper(hitX, hitY, hitZ);
                }
            }
        }
        return null;
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

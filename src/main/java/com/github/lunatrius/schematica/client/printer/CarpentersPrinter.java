package com.github.lunatrius.schematica.client.printer;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

import net.minecraft.block.Block;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
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

    private static final float[] SHAPE_HIT_OFFSETS = { 0.5f, 0.125f, 0.875f, 0.25f, 0.75f };

    private final Map<ChunkPosition, PendingClick> pendingClicks = new HashMap<>();
    private final Set<ChunkPosition> deferredGarageHosts = new HashSet<>();
    private WeakReference<World> garageWorld = new WeakReference<>(null);
    private PendingClick pendingGarageStock;
    private ChunkPosition pendingGarageHost;
    private long currentPass;

    void reset() {
        this.pendingClicks.clear();
        this.deferredGarageHosts.clear();
    }

    void beginPass(World world) {
        final long pass = ++this.currentPass;
        this.pendingClicks.values()
            .removeIf(click -> click.allowsNextClick(world, pass));
        if (this.garageWorld.get() != world) {
            this.deferredGarageHosts.clear();
            this.garageWorld = new WeakReference<>(world);
        }
        if (this.pendingGarageStock != null && this.pendingGarageStock.allowsNextClick(world, pass)) {
            if (this.pendingGarageStock.world.get() == world && pass >= this.pendingGarageStock.retryPass) {
                // Let the current scan try the other garage hosts before retrying this one.
                this.deferredGarageHosts.add(this.pendingGarageHost);
            }
            this.pendingGarageStock = null;
            this.pendingGarageHost = null;
        }
    }

    void finishPass() {
        if (this.pendingGarageStock == null) {
            this.deferredGarageHosts.clear();
        }
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

    boolean place(PrinterInteraction interaction, SchematicWorld schematic, Block block, int localX, int localY,
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
        if (CarpentersBlocksSupport.isStairs(block) || CarpentersBlocksSupport.isSlope(block)) {
            return placeShape(interaction, x, y, z, block, tile, item);
        }
        final ForgeDirection face = CarpentersBlocksSupport.getAttachmentDirection(block, tile);
        if (face != null) {
            return placeSidedBlock(interaction, x, y, z, block, item, face);
        }
        return placeNormally.getAsBoolean();
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

    private boolean placeGarageDoor(PrinterInteraction interaction, int x, int y, int z, Block block, ItemStack item,
        float yaw) {
        final World world = interaction.world;
        final boolean consumesItem = !interaction.player.capabilities.isCreativeMode;
        final ChunkPosition host = new ChunkPosition(x, y, z);
        if ((consumesItem && (this.pendingGarageStock != null || this.deferredGarageHosts.contains(host)))
            || PrinterInteraction.isBlacklisted(block, item)
            || !PrinterInteraction.canClickForPlacement(world.getBlock(x, y + 1, z), item, true)
            || !interaction.selectItem(item)) {
            return false;
        }
        final InventoryPlayer inventory = interaction.player.inventory;
        final int slot = inventory.currentItem;
        final ItemStack held = inventory.getCurrentItem();
        final int count = held.stackSize;
        // Client prediction creates the whole column, but a rejected server click only corrects the host cell.
        final boolean sent = interaction.withSneaking(
            true,
            () -> interaction.withLook(
                yaw,
                interaction.player.rotationPitch,
                () -> interaction.useBlockOnServer(
                    interaction.player.getHeldItem(),
                    x,
                    y + 1,
                    z,
                    ForgeDirection.DOWN.ordinal(),
                    Vec3.createVectorHelper(x + 0.5, y + 1, z + 0.5))));
        if (sent) {
            this.pendingClicks.put(
                host,
                new PendingClick(
                    world,
                    this.currentPass,
                    () -> world.blockExists(x, y, z) && world.getBlock(x, y, z) == block));
            if (consumesItem) {
                // A block update can arrive before the consumed item is removed from the selected slot.
                final WeakReference<InventoryPlayer> inventoryRef = new WeakReference<>(inventory);
                this.pendingGarageHost = host;
                this.pendingGarageStock = new PendingClick(world, this.currentPass, () -> {
                    final InventoryPlayer current = inventoryRef.get();
                    final ItemStack stack = current == null ? null : current.getStackInSlot(slot);
                    return stack == null || !stack.isItemEqual(item) || stack.stackSize < count;
                });
            }
        }
        return sent;
    }

    private static boolean placeShape(PrinterInteraction interaction, int x, int y, int z, Block block, TileEntity tile,
        ItemStack item) {
        if (PrinterInteraction.isBlacklisted(block, item)) {
            return false;
        }
        final ItemStack wedge = CarpentersBlocksSupport.isSlope(block) ? CarpentersBlocksSupport.getWedgeItem(item)
            : item;
        final boolean usePreferred = interaction.canSelectItem(item);
        final boolean useWedge = !wedge.isItemEqual(item) && interaction.canSelectItem(wedge);
        if (!usePreferred && !useWedge) {
            return false;
        }
        final ItemStack[] items = usePreferred ? (useWedge ? new ItemStack[] { item, wedge } : new ItemStack[] { item })
            : new ItemStack[] { wedge };
        final ForgeDirection[] supports = interaction.findSupports(x, y, z);
        if (supports.length == 0 && ConfigurationHandler.placeAdjacent) {
            return false;
        }
        final float originalYaw = interaction.player.rotationYaw;
        final float[] yaws = { originalYaw, 0, 90, 180, 270 };
        final boolean[] clickModes = ConfigurationHandler.placeAdjacent ? new boolean[] { true }
            : new boolean[] { true, false };
        for (boolean allowReshape : new boolean[] { false, true }) {
            if (allowReshape && findHammer(interaction) == null) {
                return false;
            }
            for (ItemStack candidate : items) {
                final BooleanSupplier click = findShapeClick(
                    interaction,
                    x,
                    y,
                    z,
                    block,
                    tile,
                    candidate,
                    supports,
                    yaws,
                    clickModes,
                    allowReshape);
                if (click != null) {
                    return interaction.selectItem(candidate) && click.getAsBoolean();
                }
            }
        }
        return false;
    }

    private static BooleanSupplier findShapeClick(PrinterInteraction interaction, int x, int y, int z, Block block,
        TileEntity tile, ItemStack item, ForgeDirection[] supports, float[] yaws, boolean[] clickModes,
        boolean allowReshape) {
        final float originalYaw = interaction.player.rotationYaw;
        final float pitch = interaction.player.rotationPitch;
        for (boolean adjacent : clickModes) {
            final ForgeDirection[] candidates = adjacent ? supports : ForgeDirection.VALID_DIRECTIONS;
            for (float yaw : yaws) {
                for (ForgeDirection support : candidates) {
                    final ForgeDirection face = support.getOpposite();
                    final int clickedX = adjacent ? x + support.offsetX : x;
                    final int clickedY = adjacent ? y + support.offsetY : y;
                    final int clickedZ = adjacent ? z + support.offsetZ : z;
                    final Block clicked = interaction.world.getBlock(clickedX, clickedY, clickedZ);
                    if (!PrinterInteraction.canClickForPlacement(clicked, item, true)
                        || (!adjacent && clicked == Blocks.snow_layer
                            && face != ForgeDirection.UP
                            && (interaction.world.getBlockMetadata(clickedX, clickedY, clickedZ) & 7) == 0)) {
                        continue;
                    }
                    final Vec3 hit = findShapeHit(
                        interaction.world,
                        x,
                        y,
                        z,
                        block,
                        tile,
                        item,
                        yaw,
                        allowReshape,
                        face);
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
                    return yaw == originalYaw ? click : () -> interaction.withLook(yaw, pitch, click);
                }
            }
        }
        return null;
    }

    private static Vec3 findShapeHit(World world, int x, int y, int z, Block block, TileEntity tile, ItemStack item,
        float yaw, boolean allowReshape, ForgeDirection face) {
        for (float across : SHAPE_HIT_OFFSETS) {
            for (float height : SHAPE_HIT_OFFSETS) {
                final float hitX = face.offsetX == 0 ? across : (1 + face.offsetX) * 0.5f;
                final float hitY = face.offsetY == 0 ? height : (1 + face.offsetY) * 0.5f;
                final float hitZ = face.offsetZ == 0 ? across : (1 + face.offsetZ) * 0.5f;
                final boolean matches = CarpentersBlocksSupport.isSlope(block)
                    ? CarpentersBlocksSupport
                        .matchesSlopePlacement(tile, item, face, hitX, hitY, hitZ, yaw, allowReshape)
                    : CarpentersBlocksSupport.matchesStairPlacement(
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
                        allowReshape);
                if (matches) {
                    return Vec3.createVectorHelper(hitX, hitY, hitZ);
                }
            }
        }
        return null;
    }

    private static final class PendingClick {

        private final WeakReference<World> world;
        private final long retryPass;
        private final BooleanSupplier confirmed;
        private long lastChecked = Long.MIN_VALUE;
        private boolean ready;

        private PendingClick(World world, long pass, BooleanSupplier confirmed) {
            final int delay = Math.max(1, ConfigurationHandler.placeDelay);
            final int timeout = Math.max(20, ConfigurationHandler.timeout);
            this.world = new WeakReference<>(world);
            // Keep the original retry interval in printer passes for clicks without a server update.
            this.retryPass = pass + 2 + (timeout - 1) / delay;
            this.confirmed = confirmed;
        }

        private boolean allowsNextClick(World world, long pass) {
            if (world != this.world.get() || pass >= this.retryPass) {
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

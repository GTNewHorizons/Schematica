package com.github.lunatrius.schematica.client.printer;

import net.minecraft.block.Block;
import net.minecraft.block.BlockPistonBase;
import net.minecraft.block.BlockSlab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemSlab;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.lunatrius.core.util.vector.Vector3i;
import com.github.lunatrius.schematica.client.compat.ArchitectureCraftSupport;
import com.github.lunatrius.schematica.client.compat.CarpentersBlocksSupport;
import com.github.lunatrius.schematica.client.compat.MicroblockSupport;
import com.github.lunatrius.schematica.client.printer.registry.PlacementData;
import com.github.lunatrius.schematica.client.printer.registry.PlacementRegistry;
import com.github.lunatrius.schematica.client.util.BlockToItemStack;
import com.github.lunatrius.schematica.client.world.SchematicWorld;
import com.github.lunatrius.schematica.handler.ConfigurationHandler;
import com.github.lunatrius.schematica.proxy.ClientProxy;
import com.github.lunatrius.schematica.reference.Mods;
import com.github.lunatrius.schematica.reference.Reference;

import cpw.mods.fml.common.registry.FMLControlledNamespacedRegistry;
import cpw.mods.fml.common.registry.GameData;

public class SchematicPrinter {

    public static final SchematicPrinter INSTANCE = new SchematicPrinter();
    public static final FMLControlledNamespacedRegistry<Block> BLOCK_REGISTRY = GameData.getBlockRegistry();
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final MicroblockPrinter microblocks = new MicroblockPrinter();
    private final CarpentersPrinter carpenters = new CarpentersPrinter();
    private final ArchitectureCraftPrinter architectureCraft = new ArchitectureCraftPrinter();

    private boolean isEnabled;
    private boolean isPrinting;
    private SchematicWorld schematic;
    private byte[][][] retryDelay;

    public boolean isEnabled() {
        return this.isEnabled;
    }

    public void setEnabled(boolean isEnabled) {
        this.isEnabled = isEnabled;
    }

    public boolean togglePrinting() {
        this.isPrinting = !this.isPrinting;
        return this.isPrinting;
    }

    public boolean isPrinting() {
        return this.isPrinting;
    }

    public void setPrinting(boolean isPrinting) {
        this.isPrinting = isPrinting;
    }

    public SchematicWorld getSchematic() {
        return this.schematic;
    }

    public void setSchematic(SchematicWorld schematic) {
        this.isPrinting = false;
        this.schematic = schematic;
        if (schematic == null) {
            this.carpenters.reset();
        }
        refresh();
    }

    public void refresh() {
        if (this.schematic != null) {
            this.retryDelay = new byte[this.schematic.getWidth()][this.schematic.getHeight()][this.schematic
                .getLength()];
        } else {
            this.retryDelay = null;
        }
    }

    public boolean print() {
        final EntityClientPlayerMP player = this.minecraft.thePlayer;
        final World world = this.minecraft.theWorld;
        final PrinterInteraction interaction = new PrinterInteraction(world, player, this.minecraft.playerController);
        this.microblocks.beginPass(world);
        this.carpenters.beginPass(world);
        this.architectureCraft.beginPass(world);

        final Vector3i trans = ClientProxy.playerPosition.clone()
            .sub(this.schematic.position.x, this.schematic.position.y, this.schematic.position.z)
            .toVector3i();
        final int minX = Math.max(0, trans.x - 3);
        final int maxX = Math.min(this.schematic.getWidth(), trans.x + 4);
        final int minY = Math.max(0, trans.y - 3);
        final int maxY = Math.min(this.schematic.getHeight(), trans.y + 4);
        final int minZ = Math.max(0, trans.z - 3);
        final int maxZ = Math.min(this.schematic.getLength(), trans.z + 4);

        final int slot = player.inventory.currentItem;
        final boolean wasSneaking = player.isSneaking();
        interaction.syncSneaking(true);
        try {
            final boolean isRenderingLayer = this.schematic.isRenderingLayer;
            final int renderingLayer = this.schematic.renderingLayer;
            for (int y = minY; y < maxY; y++) {
                if (isRenderingLayer && renderingLayer != y) {
                    continue;
                }
                for (int x = minX; x < maxX; x++) {
                    for (int z = minZ; z < maxZ; z++) {
                        if (printCell(interaction, x, y, z)) {
                            return true;
                        }
                    }
                }
            }
            this.carpenters.finishPass();
            return true;
        } catch (Exception e) {
            Reference.logger.error("Could not place block!", e);
            return false;
        } finally {
            player.inventory.currentItem = slot;
            interaction.syncSneaking(wasSneaking);
        }
    }

    // Returns true when placement should end the current pass.
    private boolean printCell(PrinterInteraction interaction, int x, int y, int z) {
        final World world = interaction.world;
        final EntityClientPlayerMP player = interaction.player;
        final int worldX = this.schematic.position.x + x;
        final int worldY = this.schematic.position.y + y;
        final int worldZ = this.schematic.position.z + z;

        if (this.carpenters.isWaiting(worldX, worldY, worldZ)
            || this.microblocks.isWaiting(world, worldX, worldY, worldZ)) {
            return false;
        }
        if (this.retryDelay[x][y][z] > 0) {
            this.retryDelay[x][y][z] -= Math.max(1, ConfigurationHandler.placeDelay);
            return false;
        }

        final Block block = this.schematic.getBlock(x, y, z);
        final Block placedBlock = world.getBlock(worldX, worldY, worldZ);
        final int metadata = this.schematic.getBlockMetadata(x, y, z);
        final int placedMetadata = world.getBlockMetadata(worldX, worldY, worldZ);

        if (Mods.FORGE_MICROBLOCK && MicroblockSupport.isMultipartBlock(block)) {
            final TileEntity tile = this.schematic.getTileEntity(x, y, z);
            if (MicroblockSupport.hasMicroblocks(tile)) {
                return this.microblocks.place(interaction, tile, worldX, worldY, worldZ)
                    && !ConfigurationHandler.placeInstantly;
            }
        }

        final boolean carpenterBlock = Mods.CARPENTERS_BLOCKS && CarpentersBlocksSupport.isCoverable(block);
        if (carpenterBlock && block == placedBlock) {
            return this.carpenters.configureBlock(
                interaction,
                this.schematic,
                block,
                this.schematic.getTileEntity(x, y, z),
                worldX,
                worldY,
                worldZ) && !ConfigurationHandler.placeInstantly;
        }

        final boolean architectureShape = Mods.ARCHITECTURE_CRAFT && ArchitectureCraftSupport.isShapeBlock(block);
        final TileEntity architectureTile = architectureShape ? this.schematic.getTileEntity(x, y, z) : null;
        final TileEntity placedArchitectureTile = architectureShape && block == placedBlock
            ? world.getTileEntity(worldX, worldY, worldZ)
            : null;
        if (architectureShape && ArchitectureCraftSupport.needsHammerTurn(placedArchitectureTile, architectureTile)) {
            if (this.architectureCraft.rotateCapital(interaction, placedArchitectureTile, worldX, worldY, worldZ)) {
                this.retryDelay[x][y][z] = (byte) ConfigurationHandler.timeout;
                return !ConfigurationHandler.placeInstantly;
            }
            return false;
        }
        if (block == placedBlock && BlockToItemStack.matchesMetadata(block, placedMetadata, metadata)
            && BlockToItemStack.matchesPaint(block, world, worldX, worldY, worldZ, this.schematic, x, y, z)
            && (!architectureShape
                || ArchitectureCraftSupport.matches(placedArchitectureTile, architectureTile, block))) {
            return false;
        }

        // Native slab merges cannot recreate the seamless double-stone-slab variant.
        if (block == Blocks.double_stone_slab && (metadata & 8) != 0) {
            return false;
        }

        if (block instanceof BlockSlab && block.isOpaqueCube()
            && placedBlock instanceof BlockSlab
            && !placedBlock.isOpaqueCube()) {
            final ItemStack slab = BlockToItemStack.getItemStack(player, block, this.schematic, x, y, z);
            if (slab != null && slab.getItem() instanceof ItemSlab
                && BlockToItemStack.matchesHalfSlab(world, worldX, worldY, worldZ, slab)
                && BlockToItemStack.matchesPaint(block, world, worldX, worldY, worldZ, this.schematic, x, y, z)) {
                if (interaction.selectItem(slab)
                    && mergeSlab(interaction, worldX, worldY, worldZ, player.getHeldItem())) {
                    this.retryDelay[x][y][z] = (byte) ConfigurationHandler.timeout;
                    return !ConfigurationHandler.placeInstantly;
                }
                return false;
            }
        }

        if (ConfigurationHandler.destroyBlocks && !world.isAirBlock(worldX, worldY, worldZ)
            && this.minecraft.playerController.isInCreativeMode()) {
            this.minecraft.playerController.clickBlock(worldX, worldY, worldZ, 0);

            this.retryDelay[x][y][z] = (byte) ConfigurationHandler.timeout;

            return !ConfigurationHandler.destroyInstantly;
        }

        if (this.schematic.isAirBlock(x, y, z)) {
            return false;
        }

        if (!placedBlock.isReplaceable(world, worldX, worldY, worldZ)) {
            return false;
        }

        final ItemStack itemStack = BlockToItemStack.getItemStack(player, block, this.schematic, x, y, z);
        if (itemStack == null || itemStack.getItem() == null) {
            Reference.logger.debug("{} is missing a mapping!", BLOCK_REGISTRY.getNameForObject(block));
            return false;
        }

        final boolean success;
        if (architectureShape) {
            success = ArchitectureCraftPrinter.place(interaction, worldX, worldY, worldZ, architectureTile, itemStack);
        } else if (carpenterBlock) {
            success = this.carpenters.place(
                interaction,
                this.schematic,
                block,
                x,
                y,
                z,
                itemStack,
                () -> placeVanillaBlock(interaction, worldX, worldY, worldZ, block, metadata, itemStack));
        } else {
            success = placeVanillaBlock(interaction, worldX, worldY, worldZ, block, metadata, itemStack);
        }
        if (success) {
            this.retryDelay[x][y][z] = (byte) ConfigurationHandler.timeout;

            if (!ConfigurationHandler.placeInstantly) {
                return true;
            }
        }

        return false;
    }

    private boolean placeVanillaBlock(PrinterInteraction interaction, int x, int y, int z, Block block, int metadata,
        ItemStack itemStack) {
        final World world = interaction.world;
        if (PrinterInteraction.isBlacklisted(block, itemStack)) {
            return false;
        }

        final PlacementData data = PlacementRegistry.INSTANCE.getPlacementData(block, itemStack);

        if (!isValidOrientation(interaction.player, x, y, z, data, metadata)) {
            return false;
        }

        final ForgeDirection[] solidSides = interaction.findSupports(x, y, z);
        final ForgeDirection[] validSides = data == null ? solidSides : data.getValidDirections(solidSides, metadata);
        final boolean slab = block instanceof BlockSlab && itemStack.getItem() instanceof ItemSlab;
        final boolean halfSlab = slab && !block.isOpaqueCube();
        ForgeDirection direction = ForgeDirection.UNKNOWN;
        for (ForgeDirection support : validSides) {
            if ((!halfSlab || placesSlabHalf(metadata, support)) && PrinterInteraction.canClickForPlacement(
                world.getBlock(x + support.offsetX, y + support.offsetY, z + support.offsetZ),
                itemStack,
                true) && (!slab || !wouldMergeSupportingSlab(world, x, y, z, support, itemStack))) {
                direction = support;
                break;
            }
        }

        final boolean adjacent = direction != ForgeDirection.UNKNOWN;
        if (!adjacent) {
            final boolean needsMappedSupport = data != null && data.type == PlacementData.PlacementType.BLOCK
                && !data.mapping.isEmpty()
                && !data.allowsDirectClick();
            if ((ConfigurationHandler.placeAdjacent && solidSides.length == 0)
                || (needsMappedSupport && validSides.length == 0)
                || !(itemStack.getItem() instanceof ItemBlock)) {
                return false;
            }
            final ForgeDirection[] valid = needsMappedSupport ? validSides
                : data == null ? ForgeDirection.VALID_DIRECTIONS
                    : data.getValidDirections(ForgeDirection.VALID_DIRECTIONS, metadata);
            // ItemBlock forces an upward click when replacing a single layer of snow.
            final boolean snowForcesUp = world.getBlock(x, y, z) == Blocks.snow_layer
                && (world.getBlockMetadata(x, y, z) & 7) == 0;
            for (ForgeDirection side : valid) {
                // ItemSlab checks the block across the clicked face before placing into the clicked cell.
                if ((!snowForcesUp || side == ForgeDirection.DOWN) && (!halfSlab || placesSlabHalf(metadata, side))
                    && (!slab || !BlockToItemStack
                        .matchesHalfSlab(world, x - side.offsetX, y - side.offsetY, z - side.offsetZ, itemStack))) {
                    direction = side;
                    break;
                }
            }
            if (direction == ForgeDirection.UNKNOWN) {
                return false;
            }
        }

        final float offsetY = data == null || data.maskOffset == 0 ? 0.5f : data.getOffsetFromMetadata(metadata);
        final int extraClicks = data == null ? 0 : data.getExtraClicks(block, metadata);
        return interaction.selectBlockItem(itemStack)
            && placeAgainstSupport(interaction, x, y, z, direction, adjacent, offsetY, extraClicks);
    }

    static boolean placesSlabHalf(int metadata, ForgeDirection support) {
        return support.offsetY == 0
            || ((metadata & 8) != 0 ? support == ForgeDirection.UP : support == ForgeDirection.DOWN);
    }

    private boolean wouldMergeSupportingSlab(World world, int x, int y, int z, ForgeDirection support, ItemStack slab) {
        if (support.offsetY == 0 || !BlockToItemStack.matchesHalfSlab(world, x, y + support.offsetY, z, slab)) {
            return false;
        }
        final boolean top = (world.getBlockMetadata(x, y + support.offsetY, z) & 8) != 0;
        return top ? support == ForgeDirection.UP : support == ForgeDirection.DOWN;
    }

    private boolean mergeSlab(PrinterInteraction interaction, int x, int y, int z, ItemStack slab) {
        final boolean top = (interaction.world.getBlockMetadata(x, y, z) & 8) != 0;
        return interaction.place(
            slab,
            x,
            y,
            z,
            top ? ForgeDirection.DOWN.ordinal() : ForgeDirection.UP.ordinal(),
            Vec3.createVectorHelper(x + 0.5, y + 0.5, z + 0.5));
    }

    private boolean isValidOrientation(EntityPlayer player, int x, int y, int z, PlacementData data, int metadata) {
        if (data != null) {
            switch (data.type) {
                case BLOCK: {
                    return true;
                }

                case PLAYER: {
                    Integer integer = data.mapping.get(ClientProxy.orientation);
                    if (integer != null) {
                        return integer == (metadata & data.maskMeta);
                    }
                    break;
                }

                case PISTON: {
                    Integer integer = data.mapping.get(ClientProxy.orientation);
                    if (integer != null) {
                        return BlockPistonBase.determineOrientation(null, x, y, z, player)
                            == BlockPistonBase.getPistonOrientation(metadata);
                    }
                    break;
                }
            }
            return false;
        }

        return true;
    }

    private boolean placeAgainstSupport(PrinterInteraction interaction, int x, int y, int z, ForgeDirection direction,
        boolean adjacent, float offsetY, int extraClicks) {
        final EntityClientPlayerMP player = interaction.player;
        final ItemStack itemStack = player.getCurrentEquippedItem();

        if (!this.minecraft.playerController.isInCreativeMode() && itemStack != null
            && !(itemStack.getItem() instanceof ItemSlab)
            && itemStack.stackSize <= extraClicks) {
            return false;
        }

        final int clickedX = adjacent ? x + direction.offsetX : x;
        final int clickedY = adjacent ? y + direction.offsetY : y;
        final int clickedZ = adjacent ? z + direction.offsetZ : z;
        final ForgeDirection face = direction.getOpposite();
        final int side = face.ordinal();
        final Vec3 hit = Vec3.createVectorHelper(
            clickedX + 0.5 + face.offsetX * 0.5,
            clickedY + (face.offsetY == 0 ? offsetY : (1 + face.offsetY) * 0.5),
            clickedZ + 0.5 + face.offsetZ * 0.5);

        final boolean success = interaction.withSneaking(true, () -> {
            boolean placed = interaction.place(itemStack, clickedX, clickedY, clickedZ, side, hit);
            if (placed && extraClicks > 0 && itemStack.getItem() instanceof ItemSlab) {
                // The existing-half path can finish on a later pass when the remaining slab is in another slot.
                if (itemStack.stackSize > 0
                    && BlockToItemStack.matchesHalfSlab(interaction.world, x, y, z, itemStack)) {
                    mergeSlab(interaction, x, y, z, itemStack);
                }
                return true;
            }
            for (int i = 0; placed && i < extraClicks; i++) {
                placed = interaction.place(itemStack, clickedX, clickedY, clickedZ, side, hit);
            }
            return placed;
        });

        if (itemStack != null && itemStack.stackSize == 0 && success) {
            player.inventory.mainInventory[player.inventory.currentItem] = null;
        }

        return success;
    }
}

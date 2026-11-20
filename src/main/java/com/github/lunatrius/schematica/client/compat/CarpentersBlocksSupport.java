package com.github.lunatrius.schematica.client.compat;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDirectional;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.carpentersblocks.block.BlockCarpentersBlock;
import com.carpentersblocks.block.BlockCarpentersGarageDoor;
import com.carpentersblocks.block.BlockCarpentersSlope;
import com.carpentersblocks.block.BlockCarpentersStairs;
import com.carpentersblocks.block.BlockCoverable;
import com.carpentersblocks.data.GarageDoor;
import com.carpentersblocks.data.Stairs;
import com.carpentersblocks.tileentity.TEBase;
import com.carpentersblocks.util.BlockProperties;
import com.carpentersblocks.util.protection.PlayerPermissions;
import com.carpentersblocks.util.registry.FeatureRegistry;
import com.github.lunatrius.schematica.client.util.BlockToItemStack;
import com.github.lunatrius.schematica.client.world.SchematicWorld;
import com.github.lunatrius.schematica.reference.Mods;

public final class CarpentersBlocksSupport {

    private static final int BASE = 6;
    private static final byte[][] SURFACE_ATTRIBUTES = { TEBase.ATTR_COVER, TEBase.ATTR_DYE, TEBase.ATTR_OVERLAY };
    private static final int[] TOP_BOTTOM_FACING = { 0, 3, 1, 2 };

    private CarpentersBlocksSupport() {}

    public static boolean isCoverable(Block block) {
        return block instanceof BlockCoverable;
    }

    public static boolean isStairs(Block block) {
        return block instanceof BlockCarpentersStairs;
    }

    public static boolean isGarageDoor(Block block) {
        return block instanceof BlockCarpentersGarageDoor;
    }

    public static boolean isGarageDoorHost(TileEntity tile) {
        return tile instanceof TEBase carpenter && GarageDoor.INSTANCE.isHost(carpenter);
    }

    public static boolean canConfigure(TileEntity tile, EntityPlayer player) {
        // A rejected edit can fall through to normal activation. Remote clients only know the synced owner.
        return tile instanceof TEBase carpenter && PlayerPermissions.hasElevatedPermission(carpenter, player, true);
    }

    public static boolean passesThroughMicroblockClick(Block block) {
        return block instanceof BlockCarpentersBlock || isStairs(block);
    }

    public static boolean allowsPlacementClick(Block clicked, ItemStack stack, boolean sneaking) {
        if (!isCoverable(clicked)) {
            return true;
        }
        if (sneaking) {
            return stack.getItem() instanceof ItemBlock && !BlockProperties.isOverlay(stack);
        }
        return Mods.FORGE_MICROBLOCK && MicroblockSupport.isMicroblockItem(stack)
            && passesThroughMicroblockClick(clicked);
    }

    public static void addMaterials(TileEntity target, TileEntity placed, BiConsumer<ItemStack, Boolean> add) {
        if (!(target instanceof TEBase expected)) {
            return;
        }
        final TEBase actual = placed instanceof TEBase carpenter ? carpenter : null;
        for (byte[] attributes : SURFACE_ATTRIBUTES) {
            for (byte attribute : attributes) {
                addMaterial(expected, actual, attribute, add);
            }
        }
        addMaterial(expected, actual, TEBase.ATTR_ILLUMINATOR, add);
    }

    private static void addMaterial(TEBase expected, TEBase actual, byte attribute,
        BiConsumer<ItemStack, Boolean> add) {
        final ItemStack stack = expected.getAttribute(attribute);
        if (stack != null) {
            add.accept(ingredient(stack, attribute), matches(stack, actual, attribute));
        }
    }

    private static boolean matches(ItemStack expected, TEBase actual, byte attribute) {
        final ItemStack placed = actual == null ? null : actual.getAttribute(attribute);
        return expected == null ? placed == null : placed != null && BlockToItemStack.matches(placed, expected);
    }

    private static ItemStack ingredient(ItemStack stack, byte attribute) {
        final ItemStack ingredient = stack.copy();
        ingredient.stackSize = 1;
        if (attribute <= TEBase.ATTR_COVER[BASE]) {
            final Block block = BlockProperties.toBlock(stack);
            final int damage = stack.getItemDamage();
            if (BlockProperties.blockRotates(stack) || block instanceof BlockDirectional) {
                ingredient.setItemDamage(block.damageDropped(damage));
            }
        }
        return ingredient;
    }

    public static boolean matches(Block block, TileEntity target, TileEntity placed) {
        if (!(target instanceof TEBase expected)) {
            return placed == null;
        }
        if (!(placed instanceof TEBase actual) || !matchesShape(block, expected, actual)
            || !CarpentersHammerSupport.matchesSettings(block, expected, actual)) {
            return false;
        }
        for (byte[] attributes : SURFACE_ATTRIBUTES) {
            for (byte attribute : attributes) {
                if (!matches(expected.getAttribute(attribute), actual, attribute)) {
                    return false;
                }
            }
        }
        return matches(expected.getAttribute(TEBase.ATTR_ILLUMINATOR), actual, TEBase.ATTR_ILLUMINATOR);
    }

    private static boolean matchesShape(Block block, TEBase expected, TEBase actual) {
        if (isGarageDoor(block)) {
            return GarageDoor.INSTANCE.getDirection(actual) == GarageDoor.INSTANCE.getDirection(expected)
                && GarageDoor.INSTANCE.isHost(actual) == GarageDoor.INSTANCE.isHost(expected);
        }
        return !(block instanceof BlockCarpentersBlock || block instanceof BlockCarpentersSlope || isStairs(block))
            || actual.getData() == expected.getData();
    }

    public static Float findGarageDoorYaw(Block block, SchematicWorld schematic, World world, int x, int y, int z) {
        final GarageDoor door = GarageDoor.INSTANCE;
        if (!(schematic.getTileEntity(x, y, z) instanceof TEBase host) || !door.isHost(host)) {
            return null;
        }
        final ForgeDirection direction = door.getDirection(host);
        if (direction.offsetY != 0 || direction == ForgeDirection.UNKNOWN) {
            return null;
        }
        final int wx = schematic.position.x + x;
        final int wy = schematic.position.y + y;
        final int wz = schematic.position.z + z;
        if (!block.canPlaceBlockOnSide(world, wx, wy, wz, ForgeDirection.DOWN.ordinal())) {
            return null;
        }

        // A single use creates every panel below the host until the first non-replaceable block.
        int panelY = y - 1;
        int worldY = wy - 1;
        while (worldY >= 0 && world.getBlock(wx, worldY, wz)
            .isReplaceable(world, wx, worldY, wz)) {
            if (schematic.getBlock(x, panelY, z) != block
                || !(schematic.getTileEntity(x, panelY, z) instanceof TEBase panel)
                || door.isHost(panel)
                || door.getDirection(panel) != direction) {
                return null;
            }
            panelY--;
            worldY--;
        }
        if (schematic.getBlock(x, panelY, z) == block && (world.getBlock(wx, worldY, wz) != block
            || !(world.getTileEntity(wx, worldY, wz) instanceof TEBase panel)
            || door.getDirection(panel) != direction)) {
            return null;
        }
        return Direction.facingToDirection[direction.ordinal()] * 90.0f;
    }

    public static boolean matchesStairPlacement(TileEntity tile, Block block, World world, int x, int y, int z,
        int side, float hitX, float hitY, float hitZ, EntityPlayer player) {
        if (!(tile instanceof TEBase expected)) {
            return false;
        }
        final int metadata = block.onBlockPlaced(world, x, y, z, side, hitX, hitY, hitZ, 0);
        final int facing = MathHelper.floor_double(player.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
        final int data = metadata < 12 ? metadata
            : (metadata == 12 ? Stairs.ID_NORMAL_NEG_N : Stairs.ID_NORMAL_POS_N) + TOP_BOTTOM_FACING[facing];
        return data == expected.getData();
    }

    public static Interaction findAttachment(Block block, TileEntity target, TileEntity placed, EntityPlayer player,
        Predicate<ItemStack> available) {
        if (!(target instanceof TEBase expected) || !(placed instanceof TEBase actual)
            || !matchesShape(block, expected, actual)
            || (isGarageDoor(block) && GarageDoor.INSTANCE.isOpen(actual))) {
            return null;
        }
        final boolean hasBaseCover = actual.hasAttribute(TEBase.ATTR_COVER[BASE]);
        final int bareFace = uncoveredFace(actual);
        if (bareFace != -1) {
            final Interaction decoration = findDecoration(expected, actual, BASE, bareFace, available);
            if (decoration != null) {
                return decoration;
            }
        }
        final boolean needsBaseDecoration = (FeatureRegistry.enableDyeColors
            && isMissing(expected, actual, TEBase.ATTR_DYE[BASE]))
            || (FeatureRegistry.enableOverlays && isMissing(expected, actual, TEBase.ATTR_OVERLAY[BASE]));
        for (int side = BASE; side >= 0; side--) {
            final byte attribute = TEBase.ATTR_COVER[side];
            final ItemStack cover = expected.getAttribute(attribute);
            // Leave a bare face available for base decorations, even if their items are currently missing.
            if (side == bareFace && needsBaseDecoration) {
                continue;
            }
            if (FeatureRegistry.enableCovers && cover != null
                && !actual.hasAttribute(attribute)
                && (side == BASE ? bareFace != -1 : hasBaseCover)) {
                final ItemStack item = ingredient(cover, attribute);
                if (available.test(item) && BlockProperties.isCover(item)
                    && matchesCoverDamage(cover, item, actual, player)) {
                    return new Interaction(item, side == BASE ? bareFace : side, false, attribute, cover);
                }
            }
        }
        if (FeatureRegistry.enableIllumination) {
            final Interaction illuminator = missing(expected, actual, TEBase.ATTR_ILLUMINATOR, 1, available);
            if (illuminator != null) {
                return illuminator;
            }
        }
        for (int face = 0; face < BASE; face++) {
            if (actual.hasAttribute(TEBase.ATTR_COVER[face])) {
                final Interaction decoration = findDecoration(expected, actual, face, face, available);
                if (decoration != null) {
                    return decoration;
                }
            }
        }
        return null;
    }

    private static Interaction findDecoration(TEBase expected, TEBase actual, int side, int face,
        Predicate<ItemStack> available) {
        final Interaction dye = FeatureRegistry.enableDyeColors
            ? missing(expected, actual, TEBase.ATTR_DYE[side], face, available)
            : null;
        return dye != null || !FeatureRegistry.enableOverlays ? dye
            : missing(expected, actual, TEBase.ATTR_OVERLAY[side], face, available);
    }

    private static boolean matchesCoverDamage(ItemStack cover, ItemStack item, TEBase tile, EntityPlayer player) {
        final Block block = BlockProperties.toBlock(item);
        int damage = block instanceof BlockDirectional
            ? MathHelper.floor_double(player.rotationYaw * 4.0F / 360.0F + 2.5D) & 3
            : item.getItemDamage();
        if (BlockProperties.blockRotates(item)) {
            final int facing = MathHelper.floor_double(player.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
            final int side = player.rotationPitch < -45 ? 0
                : player.rotationPitch > 45 ? 1 : Direction.directionToFacing[Direction.rotateOpposite[facing]];
            damage = block
                .onBlockPlaced(player.worldObj, tile.xCoord, tile.yCoord, tile.zCoord, side, 0.5f, 0.5f, 0.5f, damage);
        }
        return damage == cover.getItemDamage();
    }

    private static int uncoveredFace(TEBase tile) {
        for (int side = 0; side < BASE; side++) {
            if (!tile.hasAttribute(TEBase.ATTR_COVER[side])) {
                return side;
            }
        }
        return -1;
    }

    private static Interaction missing(TEBase expected, TEBase actual, byte attribute, int face,
        Predicate<ItemStack> available) {
        final ItemStack stack = expected.getAttribute(attribute);
        return stack != null && !actual.hasAttribute(attribute) && available.test(stack)
            ? new Interaction(stack, face, true, attribute, stack)
            : null;
    }

    private static boolean isMissing(TEBase expected, TEBase actual, byte attribute) {
        return expected.hasAttribute(attribute) && !actual.hasAttribute(attribute);
    }

    public static final class Interaction {

        public final ItemStack item;
        public final int face;
        public final boolean sneaking;
        private final byte attribute;
        private final ItemStack expected;

        private Interaction(ItemStack item, int face, boolean sneaking, byte attribute, ItemStack expected) {
            this.item = item;
            this.face = face;
            this.sneaking = sneaking;
            this.attribute = attribute;
            this.expected = expected;
        }

        public boolean isConfirmed(TileEntity tile) {
            return tile instanceof TEBase carpenter && matches(this.expected, carpenter, this.attribute);
        }
    }
}

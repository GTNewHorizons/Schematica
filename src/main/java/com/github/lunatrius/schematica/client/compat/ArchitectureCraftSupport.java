package com.github.lunatrius.schematica.client.compat;

import net.minecraft.block.Block;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import gcewing.architecture.common.block.BlockShape;
import gcewing.architecture.common.shape.Shape;
import gcewing.architecture.common.shape.ShapeItem;
import gcewing.architecture.common.shape.ShapeSymmetry;
import gcewing.architecture.common.tile.TileArchitecture;
import gcewing.architecture.common.tile.TileShape;
import gcewing.architecture.compat.BlockCompatUtils;
import gcewing.architecture.compat.BlockPos;
import gcewing.architecture.compat.IBlockState;
import gcewing.architecture.compat.Trans3;
import gcewing.architecture.compat.Vector3;
import gcewing.architecture.util.Utils;

public final class ArchitectureCraftSupport {

    private static final double[] HIT_OFFSETS = { 0.5, 0.125, 0.875 };

    private ArchitectureCraftSupport() {}

    public static boolean isShapeBlock(Block block) {
        return block instanceof BlockShape;
    }

    public static boolean isShapeItem(ItemStack stack) {
        return stack.getItem() instanceof ShapeItem;
    }

    public static String getMaterialName(ItemStack stack) {
        final NBTTagCompound tag = stack.getTagCompound();
        final Block base = tag == null ? null : Block.getBlockFromName(tag.getString("BaseName"));
        return base == null ? "" : Utils.displayNameOfBlock(base, tag.getInteger("BaseData"));
    }

    public static ItemStack getSecondaryItem(TileEntity tile) {
        if (tile instanceof TileShape shape && shape.shape != null && shape.secondaryBlockState != null) {
            return shape.shape.kind.newSecondaryMaterialStack(shape.secondaryBlockState);
        }
        return null;
    }

    public static ItemStack getItem(TileEntity tile, Block block) {
        if (tile instanceof TileShape shape && shape.shape != null) {
            return TileArchitecture.blockStackWithTileEntity(block, 1, shape);
        }
        return null;
    }

    public static boolean itemsMatch(ItemStack stack, ItemStack target) {
        return sameShape(stack, target) && stack.getTagCompound()
            .getInteger("Disconnected")
            == target.getTagCompound()
                .getInteger("Disconnected");
    }

    private static boolean sameShape(ItemStack stack, ItemStack target) {
        final NBTTagCompound tag = stack.getTagCompound();
        final NBTTagCompound expected = target.getTagCompound();
        return tag != null && expected != null
            && tag.hasKey("Shape")
            && expected.hasKey("Shape")
            && tag.getInteger("Shape") == expected.getInteger("Shape")
            && tag.getInteger("BaseData") == expected.getInteger("BaseData")
            && tag.getString("BaseName")
                .equals(expected.getString("BaseName"));
    }

    public static boolean matches(TileEntity placed, TileEntity target, Block block) {
        if (!(placed instanceof TileShape actual) || !(target instanceof TileShape expected)) {
            return false;
        }
        final ItemStack actualItem = getItem(actual, block);
        final ItemStack expectedItem = getItem(expected, block);
        return actualItem != null && expectedItem != null
            && sameShape(actualItem, expectedItem)
            && sameOrientation(actual, expected);
    }

    public static boolean matchesPreview(TileEntity placed, TileEntity target, Block block) {
        return placed instanceof TileShape actual && target instanceof TileShape expected
            && matches(actual, expected, block)
            && actual.disabledConnections == expected.disabledConnections
            && sameMaterial(actual.secondaryBlockState, expected.secondaryBlockState);
    }

    private static boolean sameMaterial(IBlockState actual, IBlockState expected) {
        return actual == expected || (actual != null && expected != null
            && actual.getBlock() == expected.getBlock()
            && BlockCompatUtils.getMetaFromBlockState(actual) == BlockCompatUtils.getMetaFromBlockState(expected));
    }

    private static boolean sameOrientation(TileShape actual, TileShape expected) {
        return actual.side == expected.side && actual.getOffsetX() == expected.getOffsetX()
            && (actual.turn == expected.turn || expected.shape.symmetry == ShapeSymmetry.Quadrilateral);
    }

    public static Vec3 findHit(EntityClientPlayerMP player, World world, TileEntity target, ItemStack stack, int x,
        int y, int z, ForgeDirection support, boolean sneaking) {
        if (!(target instanceof TileShape expected) || expected.shape == null) {
            return null;
        }

        final boolean originalSneak = player.movementInput.sneak;
        player.movementInput.sneak = sneaking;
        try {
            final ForgeDirection face = support.getOpposite();
            final BlockPos neighbor = new BlockPos(x + support.offsetX, y + support.offsetY, z + support.offsetZ);
            final TileEntity neighborTile = world.getTileEntity(neighbor.x, neighbor.y, neighbor.z);
            final TileShape candidate = new TileShape();
            candidate.xCoord = x;
            candidate.yCoord = y;
            candidate.zCoord = z;
            candidate.setWorldObj(world);
            candidate.readFromItemStack(stack);
            for (double u : HIT_OFFSETS) {
                for (double v : HIT_OFFSETS) {
                    final double hitX = support.offsetX == 0 ? u : (1 + face.offsetX) / 2.0;
                    final double hitY = support.offsetY == 0 ? (support.offsetX == 0 ? v : u)
                        : (1 + face.offsetY) / 2.0;
                    final double hitZ = support.offsetZ == 0 ? v : (1 + face.offsetZ) / 2.0;
                    final Vector3 relative = new Vector3(
                        hitX - face.offsetX - 0.5,
                        hitY - face.offsetY - 0.5,
                        hitZ - face.offsetZ - 0.5);
                    candidate.setSide(0);
                    candidate.setTurn(0);
                    candidate.setOffsetX(0);
                    candidate.shape.orientOnPlacement(
                        player,
                        candidate,
                        neighbor,
                        BlockCompatUtils.getWorldBlockState(world, neighbor),
                        neighborTile,
                        EnumFacing.values()[face.ordinal()],
                        relative);
                    if (sameOrientation(candidate, expected)) {
                        return Vec3.createVectorHelper(neighbor.x + hitX, neighbor.y + hitY, neighbor.z + hitZ);
                    }
                }
            }
            return null;
        } finally {
            player.movementInput.sneak = originalSneak;
        }
    }

    public static void transform(TileEntity tile, ForgeDirection axis, boolean mirror) {
        if (!(tile instanceof TileShape shape) || shape.shape == null
            || shape.side < 0
            || shape.side >= 6
            || shape.turn < 0
            || shape.turn >= 4) {
            return;
        }

        Trans3 orientation = Trans3.sideTurn(shape.side, shape.turn);
        int mirrorTurn = 0;
        if (mirror) {
            // The reflection is represented by a proper rotation and a reflection across the shape's local X axis.
            switch (shape.shape) {
                case RoofOverhangGableLH -> shape.shape = Shape.RoofOverhangGableRH;
                case RoofOverhangGableRH -> shape.shape = Shape.RoofOverhangGableLH;
                case RoofOverhangGableEndLH -> shape.shape = Shape.RoofOverhangGableEndRH;
                case RoofOverhangGableEndRH -> shape.shape = Shape.RoofOverhangGableEndLH;
                case CorniceLH -> shape.shape = Shape.CorniceRH;
                case CorniceRH -> shape.shape = Shape.CorniceLH;
                case CorniceEndLH -> shape.shape = Shape.CorniceEndRH;
                case CorniceEndRH -> shape.shape = Shape.CorniceEndLH;
                default -> {}
            }
            shape.setOffsetX(-shape.getOffsetX());
            // Placement symmetry differs from the mirrored geometry of these models.
            mirrorTurn = switch (shape.shape) {
                case AngledRoofRidge, DoricTriglyphCorner -> 1;
                case DoubleRoofTile, IonicCapital, DoricCapital, CorinthianCapital -> 2;
                default -> shape.shape.symmetry == ShapeSymmetry.Unilateral ? 1 : 0;
            };
        }

        final Vector3 up = transform(orientation.v(0, 1, 0), axis, mirror);
        final Vector3 forward = transform(orientation.v(0, 0, 1), axis, mirror);
        for (int side = 0; side < 6; side++) {
            for (int turn = 0; turn < 4; turn++) {
                final Trans3 candidate = Trans3.sideTurn(side, turn);
                if (candidate.v(0, 1, 0)
                    .dot(up) > 0.99
                    && candidate.v(0, 0, 1)
                        .dot(forward) > 0.99) {
                    shape.setSide(side);
                    shape.setTurn((turn + mirrorTurn) & 3);
                }
            }
        }

        int connections = shape.disabledConnections & ~0x3f;
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            if ((shape.disabledConnections & 1 << direction.ordinal()) == 0) {
                continue;
            }
            final Vector3 transformed = transform(
                new Vector3(direction.offsetX, direction.offsetY, direction.offsetZ),
                axis,
                mirror);
            for (ForgeDirection face : ForgeDirection.VALID_DIRECTIONS) {
                if (transformed.dot(face.offsetX, face.offsetY, face.offsetZ) > 0.99) {
                    connections |= 1 << face.ordinal();
                }
            }
        }
        shape.disabledConnections = connections;
    }

    private static Vector3 transform(Vector3 vector, ForgeDirection axis, boolean mirror) {
        return switch (axis) {
            case EAST -> mirror ? new Vector3(-vector.x, vector.y, vector.z)
                : new Vector3(vector.x, vector.z, -vector.y);
            case UP -> mirror ? new Vector3(vector.x, -vector.y, vector.z) : new Vector3(-vector.z, vector.y, vector.x);
            case SOUTH -> mirror ? new Vector3(vector.x, vector.y, -vector.z)
                : new Vector3(vector.y, -vector.x, vector.z);
            default -> vector;
        };
    }
}

package com.github.lunatrius.schematica.compat.architecturecraft;

import net.minecraft.block.Block;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameData;

public class ArchitectureCraftHelper {

    private static final String MOD_ID = "ArchitectureCraft";
    private static Boolean loaded = null;

    public static boolean isLoaded() {
        if (loaded == null) {
            loaded = Loader.isModLoaded(MOD_ID);
        }
        return loaded;
    }

    public static boolean isShapeBlock(Block block) {
        if (!isLoaded()) return false;
        String name = GameData.getBlockRegistry()
            .getNameForObject(block);
        return "ArchitectureCraft:shape".equals(name) || "ArchitectureCraft:shapeSE".equals(name);
    }

    /**
     * Compare two AC shape item stacks by their shape-defining NBT tags (Shape ID, base material).
     * Both stacks must already pass isItemEqual (same Item + damage).
     */
    public static boolean areShapeItemStacksEqual(ItemStack a, ItemStack b) {
        NBTTagCompound tagA = a.getTagCompound();
        NBTTagCompound tagB = b.getTagCompound();

        if (tagA == null && tagB == null) return true;
        if (tagA == null || tagB == null) return false;

        if (tagA.getInteger("Shape") != tagB.getInteger("Shape")) return false;

        String baseNameA = tagA.getString("BaseName");
        String baseNameB = tagB.getString("BaseName");
        if (!baseNameA.equals(baseNameB)) return false;

        if (tagA.getInteger("BaseData") != tagB.getInteger("BaseData")) return false;

        return true;
    }

    /**
     * Read orientation (side, turn) from a tile entity's NBT.
     * Returns a 2-element byte array: [side, turn].
     */
    public static byte[] getOrientationFromTE(TileEntity te) {
        NBTTagCompound nbt = new NBTTagCompound();
        te.writeToNBT(nbt);
        return new byte[] { nbt.getByte("side"), nbt.getByte("turn") };
    }

    /**
     * Count items in inventory that match the given AC shape item stack by NBT (Shape, BaseName, BaseData).
     */
    public static int countShapeInInventory(IInventory inventory, ItemStack target) {
        int count = 0;
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack slot = inventory.getStackInSlot(i);
            if (slot != null && slot.isItemEqual(target) && areShapeItemStacksEqual(target, slot)) {
                count += slot.stackSize;
            }
        }
        return count;
    }

    /**
     * Update side/turn in a tile entity's NBT via writeToNBT/readFromNBT round-trip.
     */
    public static void setOrientationOnTE(TileEntity te, byte side, byte turn) {
        NBTTagCompound nbt = new NBTTagCompound();
        te.writeToNBT(nbt);
        nbt.setByte("side", side);
        nbt.setByte("turn", turn);
        te.readFromNBT(nbt);
    }

    // @formatter:off
    // Rotation lookup tables: sideMap[oldSide] = newSide, turnDelta[oldSide] = delta
    // newTurn = (oldTurn + turnDelta) % 4
    // Derived from Matrix3.sideRotations and turnRotations in ArchitectureCraft.

    // Rotate 90 CW around Y (ForgeDirection.UP)
    private static final int[] ROTATE_Y_SIDE  = { 0, 1, 5, 4, 2, 3 };
    private static final int[] ROTATE_Y_DELTA = { 3, 1, 0, 0, 0, 0 };

    // Rotate 90 CW around X (ForgeDirection.EAST)
    private static final int[] ROTATE_X_SIDE  = { 3, 2, 0, 1, 4, 5 };
    private static final int[] ROTATE_X_DELTA = { 2, 0, 0, 2, 3, 1 };

    // Rotate 90 CW around Z (ForgeDirection.SOUTH)
    private static final int[] ROTATE_Z_SIDE  = { 4, 5, 2, 3, 1, 0 };
    private static final int[] ROTATE_Z_DELTA = { 3, 3, 3, 1, 3, 3 };

    // Flip lookup tables: sideMap[oldSide] = newSide
    // Turn formulas vary per flip axis and sometimes per side.

    // Flip X (ForgeDirection.EAST): WEST<->EAST
    private static final int[] FLIP_X_SIDE = { 0, 1, 2, 3, 5, 4 };

    // Flip Y (ForgeDirection.UP): DOWN<->UP
    private static final int[] FLIP_Y_SIDE = { 1, 0, 2, 3, 4, 5 };

    // Flip Z (ForgeDirection.SOUTH): NORTH<->SOUTH
    private static final int[] FLIP_Z_SIDE = { 0, 1, 3, 2, 4, 5 };
    // @formatter:on

    /**
     * Transform AC orientation for a schematic rotation (90 CW around the given axis).
     * Returns [newSide, newTurn].
     */
    public static byte[] transformOrientationRotate(byte side, byte turn, ForgeDirection axis) {
        if (side < 0 || side > 5 || turn < 0 || turn > 3) return new byte[] { side, turn };
        final int[] sideMap;
        final int[] turnDelta;
        switch (axis) {
            case UP:
                sideMap = ROTATE_Y_SIDE;
                turnDelta = ROTATE_Y_DELTA;
                break;
            case EAST:
                sideMap = ROTATE_X_SIDE;
                turnDelta = ROTATE_X_DELTA;
                break;
            case SOUTH:
                sideMap = ROTATE_Z_SIDE;
                turnDelta = ROTATE_Z_DELTA;
                break;
            default:
                return new byte[] { side, turn };
        }
        return new byte[] { (byte) sideMap[side], (byte) ((turn + turnDelta[side]) % 4) };
    }

    /**
     * Transform AC orientation for a schematic flip (mirror along the given axis).
     * Exact for bilaterally symmetric shapes; best-fit approximation for asymmetric shapes.
     * Returns [newSide, newTurn].
     */
    public static byte[] transformOrientationFlip(byte side, byte turn, ForgeDirection axis) {
        if (side < 0 || side > 5 || turn < 0 || turn > 3) return new byte[] { side, turn };
        final int newSide;
        final int newTurn;
        switch (axis) {
            case EAST: // Flip X
                newSide = FLIP_X_SIDE[side];
                newTurn = (4 - turn) % 4;
                break;
            case UP: // Flip Y
                newSide = FLIP_Y_SIDE[side];
                newTurn = (6 - turn) % 4;
                break;
            case SOUTH: // Flip Z
                newSide = FLIP_Z_SIDE[side];
                if (side <= 1) {
                    newTurn = (6 - turn) % 4;
                } else {
                    newTurn = (4 - turn) % 4;
                }
                break;
            default:
                return new byte[] { side, turn };
        }
        return new byte[] { (byte) newSide, (byte) newTurn };
    }

    /**
     * Fix block orientation via ArchitectureCraft's DataChannel.
     */
    public static void sendOrientationUpdate(int x, int y, int z, byte side, byte turn) {
        ArchitectureCraftBridge.sendOrientationUpdate(x, y, z, side, turn);
    }
}

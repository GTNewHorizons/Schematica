package com.github.lunatrius.schematica.client.util;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.github.lunatrius.schematica.client.compat.ArchitectureCraftSupport;
import com.github.lunatrius.schematica.client.compat.CarpentersBlocksSupport;
import com.github.lunatrius.schematica.client.compat.EnderIOSlabSupport;
import com.github.lunatrius.schematica.client.compat.MicroblockSupport;
import com.github.lunatrius.schematica.reference.Mods;
import com.github.lunatrius.schematica.reference.Reference;

public class BlockToItemStack {

    public static ItemStack getItemStack(final EntityPlayer player, final Block block, final World world, final int x,
        final int y, final int z) {
        try {
            if (Mods.CARPENTERS_BLOCKS && CarpentersBlocksSupport.isSlope(block)) {
                return CarpentersBlocksSupport.getSlopeItem(block, world.getTileEntity(x, y, z));
            }
            if (Mods.ENDER_IO && EnderIOSlabSupport.isPaintedSlab(block)) {
                return EnderIOSlabSupport.getItem(world.getTileEntity(x, y, z));
            }
            if (Mods.ARCHITECTURE_CRAFT && ArchitectureCraftSupport.isShapeBlock(block)) {
                final ItemStack shape = ArchitectureCraftSupport.getItem(world.getTileEntity(x, y, z), block);
                if (shape != null) {
                    return shape;
                }
            }
            final ItemStack itemStack = block.getPickBlock(
                new MovingObjectPosition(x, y, z, 1, Vec3.createVectorHelper(x + 0.5, y + 1.0, z + 0.5)),
                world,
                x,
                y,
                z,
                player);
            if (itemStack != null && itemStack.getItem() != null) {
                // Ignore facing damage when a non-subtyped item places the same metadata as its default stack.
                if (itemStack.getItemDamage() != 0 && itemStack.getItem() instanceof ItemBlock
                    && !itemStack.getHasSubtypes()
                    && itemStack.getItem()
                        .getMetadata(itemStack.getItemDamage())
                        == itemStack.getItem()
                            .getMetadata(0)) {
                    final ItemStack normalized = itemStack.copy();
                    normalized.setItemDamage(0);
                    return normalized;
                }
                return itemStack;
            }
        } catch (final Exception e) {
            Reference.logger.debug("Could not get the pick block for: {}", block, e);
        }

        return null;
    }

    public static boolean matches(ItemStack stack, ItemStack target) {
        if (!stack.isItemEqual(target)) {
            return false;
        }
        if (Mods.FORGE_MICROBLOCK && MicroblockSupport.isMicroblockItem(target)) {
            return MicroblockSupport.itemsMatch(stack, target);
        }
        if (Mods.ARCHITECTURE_CRAFT && ArchitectureCraftSupport.isShapeItem(target)) {
            return ArchitectureCraftSupport.itemsMatch(stack, target);
        }
        if (Mods.ENDER_IO && EnderIOSlabSupport.isPaintedSlab(Block.getBlockFromItem(target.getItem()))) {
            return EnderIOSlabSupport.itemsMatch(stack, target);
        }
        return ItemStack.areItemStackTagsEqual(stack, target)
            || (hasOnlyCosmeticTags(stack) && hasOnlyCosmeticTags(target));
    }

    private static boolean hasOnlyCosmeticTags(ItemStack stack) {
        final NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return true;
        }
        // The vanilla anvil writes RepairCost when renaming an item.
        final int cosmeticKeys = (tag.hasKey("display", 10) ? 1 : 0) + (tag.hasKey("RepairCost", 3) ? 1 : 0);
        return tag.func_150296_c()
            .size() == cosmeticKeys;
    }

    public static boolean matchesMetadata(Block block, int actual, int saved) {
        return actual == saved || (block.getClass()
            .getName()
            .equals("gregtech.common.blocks.GTBlockOre") && block.damageDropped(actual) == block.damageDropped(saved));
    }

    public static boolean matchesPaint(Block block, IBlockAccess world, int x, int y, int z, IBlockAccess schematic,
        int localX, int localY, int localZ) {
        return !Mods.ENDER_IO || !EnderIOSlabSupport.isPaintedSlab(block)
            || EnderIOSlabSupport
                .matchesPaint(world.getTileEntity(x, y, z), schematic.getTileEntity(localX, localY, localZ));
    }

    public static boolean matchesHalfSlab(World world, int x, int y, int z, ItemStack slab) {
        final Block block = world.getBlock(x, y, z);
        return block == Block.getBlockFromItem(slab.getItem()) && !block.isOpaqueCube()
            && (world.getBlockMetadata(x, y, z) & 7) == slab.getItemDamage();
    }
}

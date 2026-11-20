package com.github.lunatrius.schematica.client.util;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import com.github.lunatrius.schematica.client.compat.ArchitectureCraftSupport;
import com.github.lunatrius.schematica.client.compat.MicroblockSupport;
import com.github.lunatrius.schematica.reference.Mods;
import com.github.lunatrius.schematica.reference.Reference;

public class BlockToItemStack {

    public static ItemStack getItemStack(final EntityPlayer player, final Block block, final World world, final int x,
        final int y, final int z) {
        try {
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
                if (itemStack.getItem() instanceof ItemBlock && !itemStack.getHasSubtypes()
                    && itemStack.getItem()
                        .getMetadata(itemStack.getItemDamage())
                        == itemStack.getItem()
                            .getMetadata(0)) {
                    itemStack.setItemDamage(0);
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
}

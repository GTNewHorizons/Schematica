package com.github.lunatrius.schematica.client.util;

import java.util.ArrayList;
import java.util.List;

import net.blay09.mods.cookingforblockheads.utils.DyeUtils;
import net.minecraft.block.Block;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import com.github.lunatrius.schematica.client.compat.ArchitectureCraftSupport;
import com.github.lunatrius.schematica.client.compat.CarpentersBlocksSupport;
import com.github.lunatrius.schematica.client.compat.MicroblockSupport;
import com.github.lunatrius.schematica.client.world.SchematicWorld;
import com.github.lunatrius.schematica.nbt.NBTHelper;
import com.github.lunatrius.schematica.reference.Mods;
import com.github.lunatrius.schematica.reference.Reference;

import cpw.mods.fml.common.registry.GameRegistry;

public class BlockList {

    public List<WrappedItemStack> getList(final EntityPlayer player, final SchematicWorld world,
        final WorldClient mcWorld) {
        final List<WrappedItemStack> blockList = new ArrayList<>();

        if (world == null) {
            return blockList;
        }

        for (int y = 0; y < world.getHeight(); y++) {
            for (int x = 0; x < world.getWidth(); x++) {
                for (int z = 0; z < world.getLength(); z++) {
                    if (world.isRenderingLayer && y != world.renderingLayer) {
                        continue;
                    }

                    final Block block = world.getBlock(x, y, z);

                    if (block == Blocks.air || world.isAirBlock(x, y, z)) {
                        continue;
                    }

                    final int wx = world.position.x + x;
                    final int wy = world.position.y + y;
                    final int wz = world.position.z + z;
                    if (Mods.FORGE_MICROBLOCK && block.hasTileEntity(world.getBlockMetadata(x, y, z))
                        && MicroblockSupport.addMaterials(
                            world.getTileEntity(x, y, z),
                            mcWorld.getTileEntity(wx, wy, wz),
                            (stack, placed) -> addMaterial(blockList, stack, stack.stackSize, placed))) {
                        continue;
                    }
                    final Block mcBlock = mcWorld.getBlock(wx, wy, wz);
                    final boolean carpenter = Mods.CARPENTERS_BLOCKS && CarpentersBlocksSupport.isCoverable(block);
                    if (carpenter) {
                        CarpentersBlocksSupport.addMaterials(
                            world.getTileEntity(x, y, z),
                            block == mcBlock ? mcWorld.getTileEntity(wx, wy, wz) : null,
                            (material, placed) -> addMaterial(blockList, material, 1, placed));
                    }
                    final ItemStack stack = BlockToItemStack.getItemStack(player, block, world, x, y, z);

                    if (stack == null || stack.getItem() == null) {
                        Reference.logger.debug("Could not find the item for: {}", block);
                        continue;
                    }

                    final ItemStack placed = block == mcBlock && (carpenter || BlockToItemStack
                        .matchesMetadata(block, mcWorld.getBlockMetadata(wx, wy, wz), world.getBlockMetadata(x, y, z)))
                            ? BlockToItemStack.getItemStack(player, mcBlock, mcWorld, wx, wy, wz)
                            : null;
                    addMaterial(blockList, stack, 1, placed != null && BlockToItemStack.matches(placed, stack));
                    if (Mods.ARCHITECTURE_CRAFT && ArchitectureCraftSupport.isShapeBlock(block)) {
                        final ItemStack secondary = ArchitectureCraftSupport
                            .getSecondaryItem(world.getTileEntity(x, y, z));
                        if (secondary != null) {
                            final ItemStack actual = block == mcBlock
                                ? ArchitectureCraftSupport.getSecondaryItem(mcWorld.getTileEntity(wx, wy, wz))
                                : null;
                            addMaterial(
                                blockList,
                                secondary,
                                secondary.stackSize,
                                actual != null && BlockToItemStack.matches(actual, secondary));
                        }
                    }
                    if (Mods.COOKING_FOR_BLOCKHEADS && block.hasTileEntity(world.getBlockMetadata(x, y, z))) {
                        final GameRegistry.UniqueIdentifier name = GameRegistry.findUniqueIdentifierFor(block);
                        if (name != null && Mods.COOKING_FOR_BLOCKHEADS_ID.equals(name.modId)) {
                            final int color = kitchenColor(world.getTileEntity(x, y, z));
                            final boolean pairedFridge = "fridge".equals(name.name) && !world.isRenderingLayer
                                && y > 0
                                && world.getBlock(x, y - 1, z) == block
                                && kitchenColor(world.getTileEntity(x, y - 1, z)) == color;
                            if (color > 0 && color <= 15 && !pairedFridge) {
                                final boolean dyed = block == mcBlock
                                    && kitchenColor(mcWorld.getTileEntity(wx, wy, wz)) == color;
                                final WrappedItemStack dye = addMaterial(
                                    blockList,
                                    new ItemStack(Items.dye, 1, 15 - color),
                                    1,
                                    dyed);
                                if (!dyed) {
                                    dye.kitchenDyesNeeded++;
                                }
                            }
                        }
                    }
                }
            }
        }

        for (WrappedItemStack wrappedItemStack : blockList) {
            final ItemStack target = wrappedItemStack.itemStack;
            if (player.capabilities.isCreativeMode) {
                wrappedItemStack.inventory = -1;
            } else {
                for (ItemStack stack : player.inventory.mainInventory) {
                    if (stack != null && BlockToItemStack.matches(stack, target)) {
                        wrappedItemStack.inventory += stack.stackSize;
                    }
                }
            }
        }

        if (Mods.COOKING_FOR_BLOCKHEADS && !player.capabilities.isCreativeMode) {
            addKitchenDyeSubstitutes(blockList, player.inventory.mainInventory);
        }
        return blockList;
    }

    private void addKitchenDyeSubstitutes(List<WrappedItemStack> materials, ItemStack[] inventory) {
        for (WrappedItemStack dye : materials) {
            if (dye.kitchenDyesNeeded == 0) {
                continue;
            }
            final int damage = dye.itemStack.getItemDamage();
            int available = 0;
            for (ItemStack stack : inventory) {
                if (stack != null && !BlockToItemStack.matches(stack, dye.itemStack)
                    && DyeUtils.rawDyeDamageFromStack(stack) == damage) {
                    available += stack.stackSize;
                }
            }
            // Reserve dyes that a different row needs as exact ingredients before counting substitutes.
            for (WrappedItemStack material : materials) {
                if (material != dye && DyeUtils.rawDyeDamageFromStack(material.itemStack) == damage) {
                    available -= Math.min(material.inventory, material.total - material.placed);
                }
            }
            dye.inventory += Math.min(Math.max(available, 0), dye.kitchenDyesNeeded);
        }
    }

    private int kitchenColor(TileEntity tile) {
        return tile == null ? 0
            : NBTHelper.writeTileEntityToCompound(tile)
                .getByte("Color");
    }

    private WrappedItemStack addMaterial(List<WrappedItemStack> blockList, ItemStack stack, int count, boolean placed) {
        final WrappedItemStack entry = findOrCreateWrappedItemStackFor(blockList, stack);
        if (placed) {
            entry.placed += count;
        }
        entry.total += count;
        return entry;
    }

    private WrappedItemStack findOrCreateWrappedItemStackFor(final List<WrappedItemStack> blockList,
        final ItemStack itemStack) {
        for (final WrappedItemStack wrappedItemStack : blockList) {
            if (BlockToItemStack.matches(wrappedItemStack.itemStack, itemStack)) {
                return wrappedItemStack;
            }
        }

        final WrappedItemStack wrappedItemStack = new WrappedItemStack(itemStack.copy());
        wrappedItemStack.itemStack.stackSize = 1;
        blockList.add(wrappedItemStack);
        return wrappedItemStack;
    }

    public static class WrappedItemStack {

        public ItemStack itemStack;
        public int placed;
        public int total;
        public int inventory;
        private int kitchenDyesNeeded;

        public WrappedItemStack(final ItemStack itemStack) {
            this(itemStack, 0, 0);
        }

        public WrappedItemStack(final ItemStack itemStack, final int placed, final int total) {
            this.itemStack = itemStack;
            this.placed = placed;
            this.total = total;
        }

        public String getItemStackDisplayName() {
            final String name = this.itemStack.getDisplayName();
            if (Mods.ARCHITECTURE_CRAFT && ArchitectureCraftSupport.isShapeItem(this.itemStack)) {
                final String material = ArchitectureCraftSupport.getMaterialName(this.itemStack);
                if (!material.isEmpty()) {
                    return name + " (" + material + ")";
                }
            }
            return name;
        }

        public String getFormattedAmount() {
            final char color = this.placed < this.total ? 'c' : 'a';
            return String.format(
                "\u00a7%c%s\u00a7r/%s",
                color,
                getFormattedStackAmount(itemStack, this.placed),
                getFormattedStackAmount(itemStack, this.total));
        }

        public String getFormattedAmountRequired(final String reqstr, final String avastr) {
            final int need = this.total - this.inventory - this.placed;
            if (this.inventory != -1 && need > 0) {
                return String.format("\u00a7c%s:%s", reqstr, getFormattedStackAmount(itemStack, need));
            } else {
                return String.format("\u00a7a%s", avastr);
            }
        }

        private static String getFormattedStackAmount(final ItemStack itemStack, final int amount) {
            final int stackSize = itemStack.getMaxStackSize();
            if (amount < stackSize) {
                return String.format("%d", amount);
            } else {
                final int amountstack = amount / stackSize;
                final int amountremainder = amount % stackSize;
                return String.format("%d(%d:%d)", amount, amountstack, amountremainder);
            }
        }
    }
}

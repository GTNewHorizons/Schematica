package com.github.lunatrius.schematica.client.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemSlab;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.github.lunatrius.schematica.client.compat.ArchitectureCraftSupport;
import com.github.lunatrius.schematica.client.compat.CarpentersBlocksSupport;
import com.github.lunatrius.schematica.client.compat.CookingForBlockheadsSupport;
import com.github.lunatrius.schematica.client.compat.EnderIOSlabSupport;
import com.github.lunatrius.schematica.client.compat.MicroblockSupport;
import com.github.lunatrius.schematica.client.world.SchematicWorld;
import com.github.lunatrius.schematica.reference.Mods;
import com.github.lunatrius.schematica.reference.Reference;

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
                    if (Mods.FORGE_MICROBLOCK && MicroblockSupport.isMultipartBlock(block)
                        && MicroblockSupport.addMaterials(
                            world.getTileEntity(x, y, z),
                            mcWorld.getTileEntity(wx, wy, wz),
                            (stack, placed) -> addMaterial(blockList, stack, stack.stackSize, placed))) {
                        continue;
                    }
                    final Block mcBlock = mcWorld.getBlock(wx, wy, wz);
                    final boolean carpenter = Mods.CARPENTERS_BLOCKS && CarpentersBlocksSupport.isCoverable(block);
                    final boolean garageDoor = carpenter && CarpentersBlocksSupport.isGarageDoor(block);
                    if (carpenter) {
                        CarpentersBlocksSupport.addMaterials(
                            world.getTileEntity(x, y, z),
                            block == mcBlock ? mcWorld.getTileEntity(wx, wy, wz) : null,
                            (material, placed) -> addMaterial(blockList, material, 1, placed));
                        if (garageDoor && !CarpentersBlocksSupport.isGarageDoorHost(world.getTileEntity(x, y, z))) {
                            continue;
                        }
                    }
                    final ItemStack stack = BlockToItemStack.getItemStack(player, block, world, x, y, z);

                    if (stack == null || stack.getItem() == null) {
                        Reference.logger.debug("Could not find the item for: {}", block);
                        continue;
                    }

                    final ItemStack placed = block == mcBlock && (carpenter
                        || BlockToItemStack.matchesSavedState(block, player, mcWorld, wx, wy, wz, world, x, y, z))
                            ? BlockToItemStack.getItemStack(player, mcBlock, mcWorld, wx, wy, wz)
                            : null;
                    final boolean complete = placed != null && BlockToItemStack.matches(placed, stack)
                        && (!garageDoor || CarpentersBlocksSupport.isGarageDoorHost(mcWorld.getTileEntity(wx, wy, wz)));
                    final WrappedItemStack blockMaterial = addMaterial(blockList, stack, 1, complete);
                    if (!complete) {
                        blockMaterial.pickedBlocksNeeded++;
                    }
                    if (block instanceof BlockSlab && block.isOpaqueCube() && stack.getItem() instanceof ItemSlab) {
                        if (complete || (BlockToItemStack.matchesHalfSlab(mcWorld, wx, wy, wz, stack)
                            && BlockToItemStack.matchesPaint(block, mcWorld, wx, wy, wz, world, x, y, z))) {
                            blockMaterial.placed++;
                        } else {
                            blockMaterial.pickedBlocksNeeded++;
                        }
                        blockMaterial.total++;
                    }
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
                    if (Mods.COOKING_FOR_BLOCKHEADS && CookingForBlockheadsSupport.isKitchenBlock(block)) {
                        CookingForBlockheadsSupport
                            .addDyeRequirement(world, x, y, z, mcWorld, wx, wy, wz, (stackDye, dyed) -> {
                                final WrappedItemStack dye = addMaterial(blockList, stackDye, 1, dyed);
                                if (!dyed) {
                                    dye.kitchenDyesNeeded++;
                                }
                            });
                    }
                }
            }
        }

        for (WrappedItemStack wrappedItemStack : blockList) {
            countInventory(wrappedItemStack, player);
        }

        if (!player.capabilities.isCreativeMode) {
            addLooseBlockStock(blockList, player.inventory.mainInventory);
            if (Mods.COOKING_FOR_BLOCKHEADS && CookingForBlockheadsSupport.SUPPORTS_DYE_SUBSTITUTES) {
                addKitchenDyeSubstitutes(blockList, player.inventory.mainInventory);
            }
        }
        return blockList;
    }

    public static int getPlayerHeadStockRevision() {
        return PlayerHeadProfiles.INSTANCE.getRevision();
    }

    public static void refreshPlayerHeadStock(List<WrappedItemStack> materials, EntityPlayer player) {
        for (WrappedItemStack row : materials) {
            if (BlockToItemStack.isPlayerHead(row.itemStack)) {
                countInventory(row, player);
            }
        }
    }

    private static void countInventory(WrappedItemStack row, EntityPlayer player) {
        if (player.capabilities.isCreativeMode) {
            row.inventory = -1;
            return;
        }
        final ItemStack target = row.itemStack;
        final boolean playerHead = BlockToItemStack.isPlayerHead(target);
        row.inventory = 0;
        for (ItemStack stack : player.inventory.mainInventory) {
            if (stack != null && (playerHead ? BlockToItemStack.matchesBlockStock(stack, target)
                : BlockToItemStack.matches(stack, target))) {
                row.inventory += stack.stackSize;
            }
        }
    }

    private void addLooseBlockStock(List<WrappedItemStack> materials, ItemStack[] inventory) {
        for (WrappedItemStack row : materials) {
            if (row.pickedBlocksNeeded == 0 || !(row.itemStack.getItem() instanceof ItemBlock)) {
                continue;
            }
            int available = 0;
            for (ItemStack stack : inventory) {
                if (stack != null && matchesOnlyBlockStock(stack, row.itemStack)) {
                    available += stack.stackSize;
                }
            }
            if (available == 0) {
                continue;
            }
            // Tagged stock may be required as an exact ingredient in another row.
            for (WrappedItemStack material : materials) {
                if (matchesOnlyBlockStock(material.itemStack, row.itemStack)) {
                    available -= Math.min(material.inventory, material.total - material.placed);
                }
            }
            row.inventory += Math.min(Math.max(available, 0), row.pickedBlocksNeeded);
        }
    }

    private static boolean matchesOnlyBlockStock(ItemStack candidate, ItemStack target) {
        return BlockToItemStack.matchesBlockStock(candidate, target) && !BlockToItemStack.matches(candidate, target);
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
                    && CookingForBlockheadsSupport.getDyeDamage(stack) == damage) {
                    available += stack.stackSize;
                }
            }
            // Reserve dyes that a different row needs as exact ingredients before counting substitutes.
            for (WrappedItemStack material : materials) {
                if (material != dye && CookingForBlockheadsSupport.getDyeDamage(material.itemStack) == damage) {
                    available -= Math.min(material.inventory, material.total - material.placed);
                }
            }
            dye.inventory += Math.min(Math.max(available, 0), dye.kitchenDyesNeeded);
        }
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
        private int pickedBlocksNeeded;

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
            if (Mods.ARCHITECTURE_CRAFT) {
                final String material = ArchitectureCraftSupport.getMaterialName(this.itemStack);
                if (!material.isEmpty()) {
                    return name + " (" + material + ")";
                }
            }
            return name;
        }

        public String getExportDisplayName() {
            final String name = getItemStackDisplayName();
            if (Mods.ENDER_IO && EnderIOSlabSupport.isPaintedSlab(Block.getBlockFromItem(this.itemStack.getItem()))) {
                return name + " (" + EnderIOSlabSupport.getPaintName(this.itemStack) + ")";
            }
            if (this.itemStack.hasTagCompound() && BlockToItemStack.isBanner(this.itemStack)) {
                final List<String> patterns = getBannerPatternNames();
                if (!patterns.isEmpty()) {
                    return name + " (" + String.join(", ", patterns) + ")";
                }
            }
            return name;
        }

        private List<String> getBannerPatternNames() {
            final List<String> names = new ArrayList<>();
            final ItemStack sample = this.itemStack.copy();
            final NBTTagCompound tile = sample.getTagCompound()
                .getCompoundTag("BlockEntityTag");
            final NBTTagList patterns = tile.getTagList("Patterns", Constants.NBT.TAG_COMPOUND);
            // Et Futurum's tooltip lists six patterns, but imported banners can render more layers.
            final int tooltipLimit = 6;
            for (int start = 0; start < patterns.tagCount(); start += tooltipLimit) {
                final NBTTagList batch = new NBTTagList();
                final int end = Math.min(start + tooltipLimit, patterns.tagCount());
                for (int i = start; i < end; i++) {
                    batch.appendTag(patterns.getCompoundTagAt(i));
                }
                tile.setTag("Patterns", batch);
                sample.getItem()
                    .addInformation(sample, null, names, false);
            }
            return names;
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

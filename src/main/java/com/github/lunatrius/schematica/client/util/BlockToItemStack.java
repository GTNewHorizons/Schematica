package com.github.lunatrius.schematica.client.util;

import java.util.Objects;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.tileentity.TileEntitySkull;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import com.github.lunatrius.schematica.client.compat.ArchitectureCraftSupport;
import com.github.lunatrius.schematica.client.compat.CarpentersBlocksSupport;
import com.github.lunatrius.schematica.client.compat.EnderIOSlabSupport;
import com.github.lunatrius.schematica.client.compat.MicroblockSupport;
import com.github.lunatrius.schematica.reference.Mods;
import com.github.lunatrius.schematica.reference.Reference;
import com.google.common.collect.Iterables;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

import cpw.mods.fml.common.registry.GameRegistry;

public class BlockToItemStack {

    private static final int PLAYER_SKULL = 3;
    private static final String SKULL_OWNER = "SkullOwner";

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
            if (block == Blocks.skull && world.getTileEntity(x, y, z) instanceof TileEntitySkull skull) {
                final ItemStack item = new ItemStack(Items.skull, 1, skull.func_145904_a());
                final GameProfile owner = skull.func_152108_a();
                if (isPlayerHead(item) && owner != null) {
                    final NBTTagCompound ownerTag = new NBTTagCompound();
                    NBTUtil.func_152460_a(ownerTag, owner);
                    item.setTagInfo(SKULL_OWNER, ownerTag);
                }
                return item;
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
        return matches(stack, target, false);
    }

    public static boolean matchesBlockStock(ItemStack stack, ItemStack target) {
        return matches(stack, target, true);
    }

    /** Compares a placed item with a schematic requirement after server-side placement. */
    public static boolean matchesPlaced(ItemStack stack, ItemStack target) {
        return isPlayerHead(stack) && isPlayerHead(target) ? matchesHead(stack, target, true) : matches(stack, target);
    }

    private static boolean matches(ItemStack stack, ItemStack target, boolean blockStock) {
        if (stack.getItem() != target.getItem()) {
            return false;
        }
        if ((stack.hasTagCompound() || target.hasTagCompound()) && isBanner(target)) {
            return matchesBanner(stack, target);
        }
        if (stack.getItemDamage() != target.getItemDamage()) {
            return false;
        }
        if (isPlayerHead(target)) {
            return matchesHead(stack, target, false);
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
        // Ordinary block picks can omit per-item data from stock, such as a dropped machine's configuration.
        return ItemStack.areItemStackTagsEqual(stack, target) || (hasOnlyCosmeticTags(target)
            && (hasOnlyCosmeticTags(stack) || (blockStock && target.getItem() instanceof ItemBlock)));
    }

    static boolean isBanner(ItemStack stack) {
        return stack.getItem() == GameRegistry.findItem("etfuturum", "banner");
    }

    private static boolean matchesBanner(ItemStack stack, ItemStack target) {
        final NBTTagCompound actual = stack.hasTagCompound() ? stack.getTagCompound()
            .getCompoundTag("BlockEntityTag") : new NBTTagCompound();
        final NBTTagCompound expected = target.hasTagCompound() ? target.getTagCompound()
            .getCompoundTag("BlockEntityTag") : new NBTTagCompound();
        if (bannerColor(stack, actual) != bannerColor(target, expected)) {
            return false;
        }
        final NBTTagList patterns = actual.getTagList("Patterns", Constants.NBT.TAG_COMPOUND);
        final NBTTagList expectedPatterns = expected.getTagList("Patterns", Constants.NBT.TAG_COMPOUND);
        return patterns.equals(expectedPatterns) || (patterns.tagCount() == 0 && expectedPatterns.tagCount() == 0);
    }

    private static int bannerColor(ItemStack stack, NBTTagCompound tile) {
        return tile.hasKey("Base", Constants.NBT.TAG_ANY_NUMERIC) ? tile.getInteger("Base")
            : stack.getItemDamage() & 15;
    }

    static boolean isPlayerHead(ItemStack stack) {
        return stack.getItem() == Items.skull && stack.getItemDamage() == PLAYER_SKULL;
    }

    // This is advisory stock for Materials. The placing server may resolve the name differently.
    static boolean matchesNameOnlyHeadStock(ItemStack stack, ItemStack target) {
        if (!isPlayerHead(stack)) {
            return false;
        }
        try {
            return PlayerHeadProfiles.INSTANCE.matchesNameOnlyStock(getHeadOwner(stack), getHeadOwner(target));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static GameProfile getHeadOwner(ItemStack stack) {
        final NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return null;
        }
        if (tag.hasKey(SKULL_OWNER, 10)) {
            return NBTUtil.func_152459_a(tag.getCompoundTag(SKULL_OWNER));
        }
        final String name = tag.hasKey(SKULL_OWNER, 8) ? tag.getString(SKULL_OWNER) : "";
        return name.isEmpty() ? null : new GameProfile(null, name);
    }

    private static boolean matchesHead(ItemStack stack, ItemStack target, boolean placed) {
        try {
            final GameProfile actual = getHeadOwner(stack);
            final GameProfile expected = getHeadOwner(target);
            // Vanilla completes legacy names on the placing server. This schematic only specified the name.
            return (placed && PlayerHeadProfiles.isNameOnly(expected)
                && actual != null
                && actual.getId() != null
                && expected.getName()
                    .equalsIgnoreCase(actual.getName()))
                || matchesHeadOwner(actual, expected);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean matchesHeadOwner(GameProfile actual, GameProfile expected) {
        if (actual == null || expected == null) {
            return actual == expected;
        }
        if (!Objects.equals(actual.getId(), expected.getId()) || (actual.getId() == null && !actual.getName()
            .equalsIgnoreCase(expected.getName()))) {
            return false;
        }
        // Vanilla renders the first texture property. Its timestamp and cape can change independently of the skin.
        final Property actualTexture = Iterables.getFirst(
            actual.getProperties()
                .get("textures"),
            null);
        final Property expectedTexture = Iterables.getFirst(
            expected.getProperties()
                .get("textures"),
            null);
        if (actualTexture == null || expectedTexture == null) {
            return actualTexture == expectedTexture;
        }
        if (actualTexture.getValue()
            .equals(expectedTexture.getValue())) {
            return true;
        }
        try {
            return Objects.equals(getSkinUrl(actual), getSkinUrl(expected));
        } catch (RuntimeException e) {
            // Some malformed texture payloads make Authlib throw instead of returning an empty map.
            return false;
        }
    }

    private static String getSkinUrl(GameProfile owner) {
        return PlayerHeadProfiles.skinUrl(
            Minecraft.getMinecraft()
                .func_152347_ac(),
            owner,
            false);
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

    /** Compares base placement state once both locations have the same block. */
    public static boolean matchesSavedState(Block block, EntityPlayer player, World world, int x, int y, int z,
        World schematic, int localX, int localY, int localZ) {
        final int savedMetadata = schematic.getBlockMetadata(localX, localY, localZ);
        if (!matchesMetadata(block, world.getBlockMetadata(x, y, z), savedMetadata)
            || !matchesPaint(block, world, x, y, z, schematic, localX, localY, localZ)) {
            return false;
        }
        if (block != Blocks.skull && (!block.hasTileEntity(savedMetadata) || !isBannerBlock(block))) {
            return true;
        }
        final ItemStack actual = getItemStack(player, block, world, x, y, z);
        final ItemStack expected = getItemStack(player, block, schematic, localX, localY, localZ);
        return actual != null && expected != null && matchesPlaced(actual, expected);
    }

    private static boolean isBannerBlock(Block block) {
        final Item banner = GameRegistry.findItem("etfuturum", "banner");
        return banner != null && block == Block.getBlockFromItem(banner);
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

package com.github.lunatrius.schematica.client.compat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.BiConsumer;

import net.blay09.mods.cookingforblockheads.block.BlockBaseKitchen;
import net.blay09.mods.cookingforblockheads.tile.BaseKitchenTileWithInventory;
import net.blay09.mods.cookingforblockheads.tile.TileCookingTable;
import net.blay09.mods.cookingforblockheads.tile.TileFridge;
import net.blay09.mods.cookingforblockheads.tile.TileSink;
import net.blay09.mods.cookingforblockheads.utils.DyeUtils;
import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.github.lunatrius.schematica.client.world.SchematicWorld;
import com.github.lunatrius.schematica.reference.Reference;

public final class CookingForBlockheadsSupport {

    public static final boolean SUPPORTS_DYE_SUBSTITUTES = supportsDyeSubstitutes();

    private CookingForBlockheadsSupport() {}

    public static boolean isKitchenBlock(Block block) {
        return block instanceof BlockBaseKitchen;
    }

    public static int getDyeDamage(ItemStack stack) {
        return DyeUtils.rawDyeDamageFromStack(stack);
    }

    public static void addDyeRequirement(SchematicWorld schematic, int x, int y, int z, World world, int wx, int wy,
        int wz, BiConsumer<ItemStack, Boolean> add) {
        final TileEntity tile = schematic.getTileEntity(x, y, z);
        final int color = getColor(tile);
        if (color < 0 || color > 15) {
            return;
        }
        final Block block = schematic.getBlock(x, y, z);
        final boolean pairFridges = tile instanceof TileFridge && !schematic.isRenderingLayer;
        if (pairFridges && colorAt(schematic, block, x, y - 1, z) == color) {
            return;
        }
        final boolean hasUpperFridge = pairFridges && colorAt(schematic, block, x, y + 1, z) == color;
        final int currentColor = colorAt(world, block, wx, wy, wz);
        final int upperColor = hasUpperFridge ? colorAt(world, block, wx, wy + 1, wz) : -1;
        if (color == 0 && currentColor <= 0 && upperColor <= 0) {
            return;
        }
        add.accept(
            new ItemStack(Items.dye, 1, 15 - color),
            currentColor == color && (!hasUpperFridge || upperColor == color));
    }

    private static int colorAt(World world, Block block, int x, int y, int z) {
        return world.getBlock(x, y, z) == block ? getColor(world.getTileEntity(x, y, z)) : -1;
    }

    private static int getColor(TileEntity tile) {
        if (tile instanceof BaseKitchenTileWithInventory kitchen) {
            return kitchen.getColor();
        }
        if (tile instanceof TileCookingTable table) {
            return table.getColor();
        }
        return tile instanceof TileSink sink ? sink.getColor() : -1;
    }

    private static boolean supportsDyeSubstitutes() {
        try {
            final Method method = DyeUtils.class.getMethod("rawDyeDamageFromStack", ItemStack.class);
            return method.getReturnType() == int.class && Modifier.isStatic(method.getModifiers());
        } catch (ReflectiveOperationException | LinkageError e) {
            Reference.logger.debug("Cooking for Blockheads dye substitutes are unavailable", e);
            return false;
        }
    }
}

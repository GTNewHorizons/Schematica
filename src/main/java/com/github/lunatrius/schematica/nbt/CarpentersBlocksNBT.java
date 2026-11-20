package com.github.lunatrius.schematica.nbt;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.carpentersblocks.tileentity.TEBase;

import cpw.mods.fml.common.registry.GameData;

public final class CarpentersBlocksNBT {

    private static final String ITEM_REGISTRY_NAME = "cbUniqueId";

    private CarpentersBlocksNBT() {}

    public static void remapAttributeItems(NBTTagCompound tile) {
        final NBTTagList attributes = tile.getTagList(TEBase.TAG_ATTR_LIST, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < attributes.tagCount(); i++) {
            final NBTTagCompound attribute = attributes.getCompoundTagAt(i);
            final String name = attribute.getString(ITEM_REGISTRY_NAME);
            if (!name.isEmpty()) {
                final Item item = GameData.getItemRegistry()
                    .getObject(name);
                // EndlessIDs can prefer idExt over the vanilla id. Copy the current stack's ID encoding only.
                attribute.removeTag("idExt");
                if (item == null) {
                    attribute.setShort("id", (short) 0);
                    continue;
                }
                final NBTTagCompound stack = new ItemStack(item).writeToNBT(new NBTTagCompound());
                attribute.setTag("id", stack.getTag("id"));
                if (stack.hasKey("idExt")) {
                    attribute.setTag("idExt", stack.getTag("idExt"));
                }
            }
        }
    }
}

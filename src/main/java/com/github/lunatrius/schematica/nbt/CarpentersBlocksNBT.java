package com.github.lunatrius.schematica.nbt;

import net.minecraft.item.Item;
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
                // Carpenter's Blocks uses the registry name only when the numeric ID fails to resolve.
                final Item item = GameData.getItemRegistry()
                    .getObject(name);
                attribute.setShort("id", (short) (item == null ? 0 : Item.getIdFromItem(item)));
            }
        }
    }
}

package com.github.lunatrius.schematica.world.schematic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.Constants;

import com.github.lunatrius.schematica.api.ISchematic;
import com.github.lunatrius.schematica.api.event.PreSchematicSaveEvent;
import com.github.lunatrius.schematica.nbt.CarpentersBlocksNBT;
import com.github.lunatrius.schematica.nbt.LegacyGregTechOre;
import com.github.lunatrius.schematica.nbt.NBTHelper;
import com.github.lunatrius.schematica.reference.Mods;
import com.github.lunatrius.schematica.reference.Names;
import com.github.lunatrius.schematica.reference.Reference;
import com.github.lunatrius.schematica.world.storage.Schematic;

import cpw.mods.fml.common.registry.FMLControlledNamespacedRegistry;
import cpw.mods.fml.common.registry.GameData;

public class SchematicAlpha extends SchematicFormat {

    private static final FMLControlledNamespacedRegistry<Block> BLOCK_REGISTRY = GameData.getBlockRegistry();

    @Override
    public ISchematic readFromNBT(NBTTagCompound tagCompound) {
        return readFromNBT(tagCompound, false);
    }

    public ISchematic readFromNBT(NBTTagCompound tagCompound, boolean schemplusFile) {
        ItemStack icon = SchematicUtil.getIconFromNBT(tagCompound);

        byte[] localBlocks = tagCompound.getByteArray(Names.NBT.BLOCKS);
        byte[] localMetadata = tagCompound.getByteArray(Names.NBT.DATA);
        byte[] addBlocks = byteArrayOrNull(tagCompound, Names.NBT.ADD_BLOCKS);
        byte[] legacyAdd = byteArrayOrNull(tagCompound, Names.NBT.ADD_BLOCKS_SCHEMATICA);
        byte[] addBlocksHigh = byteArrayOrNull(tagCompound, Names.NBT.ADD_BLOCKS_HIGH);
        int blockIdBytes = tagCompound.hasKey(Names.NBT.BLOCK_ID_BYTES)
            ? tagCompound.getByte(Names.NBT.BLOCK_ID_BYTES) & 0xFF
            : 0;

        int width = tagCompound.getShort(Names.NBT.WIDTH);
        int length = tagCompound.getShort(Names.NBT.LENGTH);
        int height = tagCompound.getShort(Names.NBT.HEIGHT);
        int size = width * length * height;
        if (localBlocks.length < size || localMetadata.length < size) {
            throw new IllegalArgumentException(
                "Schematic block arrays have " + localBlocks.length + " entries, expected at least " + size);
        }

        byte[] highMetadata = readHighMetadata(tagCompound, localMetadata.length);
        int[] blockIds = BlockIdCodec
            .decode(localBlocks, addBlocks, legacyAdd, addBlocksHigh, blockIdBytes, schemplusFile);
        Map<Integer, Integer> oldToNew = readMapping(tagCompound);

        ISchematic schematic = new Schematic(icon, width, height, length);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    int index = index(x, y, z, width, length);
                    int blockId = blockIds[index];
                    Integer mapped = oldToNew.get(blockId);
                    if (mapped != null) {
                        blockId = mapped;
                    }
                    int meta = localMetadata[index] & 0xFF;
                    if (highMetadata != null) {
                        meta |= (highMetadata[index] & 0xFF) << 8;
                    }
                    schematic.setBlock(x, y, z, BLOCK_REGISTRY.getObjectById(blockId), meta);
                }
            }
        }

        loadTiles(tagCompound, schematic);
        return schematic;
    }

    private static byte[] readHighMetadata(NBTTagCompound tag, int length) {
        if (!tag.hasKey(Names.NBT.DATA_HIGH)) {
            return null;
        }
        if (!tag.hasKey(Names.NBT.DATA_HIGH, Constants.NBT.TAG_BYTE_ARRAY)) {
            throw new IllegalArgumentException("Invalid " + Names.NBT.DATA_HIGH + " type");
        }
        final byte[] metadata = tag.getByteArray(Names.NBT.DATA_HIGH);
        if (metadata.length != length) {
            throw new IllegalArgumentException("Invalid " + Names.NBT.DATA_HIGH + " length");
        }
        return metadata;
    }

    private static void loadTiles(NBTTagCompound tagCompound, ISchematic schematic) {
        final NBTTagList tiles = tagCompound.getTagList(Names.NBT.TILE_ENTITIES, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < tiles.tagCount(); i++) {
            final NBTTagCompound tag = tiles.getCompoundTagAt(i);
            try {
                if (Mods.POSTEA && "GT_TileEntity_Ores".equals(tag.getString("id"))
                    && LegacyGregTechOre.replace(schematic, tag)) {
                    continue;
                }
            } catch (Exception e) {
                Reference.logger.debug("Could not convert a legacy GregTech ore", e);
            }
            try {
                if (Mods.CARPENTERS_BLOCKS) {
                    CarpentersBlocksNBT.remapAttributeItems(tag);
                }
                final TileEntity tileEntity = NBTHelper.readTileEntityFromCompound(tag);
                if (tileEntity != null) {
                    schematic.setTileEntity(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord, tileEntity);
                }
            } catch (Exception e) {
                Reference.logger.error("TileEntity failed to load properly!", e);
            }
        }
    }

    @Override
    public boolean writeToNBT(NBTTagCompound tagCompound, ISchematic schematic, World backupWorld) {
        NBTTagCompound tagCompoundIcon = new NBTTagCompound();
        ItemStack icon = schematic.getIcon();
        icon.writeToNBT(tagCompoundIcon);
        tagCompound.setTag(Names.NBT.ICON, tagCompoundIcon);

        int width = schematic.getWidth();
        int length = schematic.getLength();
        int height = schematic.getHeight();
        tagCompound.setShort(Names.NBT.WIDTH, (short) width);
        tagCompound.setShort(Names.NBT.LENGTH, (short) length);
        tagCompound.setShort(Names.NBT.HEIGHT, (short) height);

        int size = width * length * height;
        int[] blockIds = new int[size];
        byte[] localMetadata = new byte[size];
        byte[] highMetadata = null;
        Map<String, Integer> mappings = new HashMap<>();
        boolean loggedTruncation = false;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    int index = index(x, y, z, width, length);
                    Block block = schematic.getBlock(x, y, z);
                    int rawId = block == null ? -1 : BLOCK_REGISTRY.getId(block);
                    int blockId = rawId;
                    if (rawId < 0) {
                        blockId = 0;
                    } else if (rawId > BlockIdCodec.MAX_SAVED_ID) {
                        blockId = rawId & BlockIdCodec.MAX_SAVED_ID;
                        if (!loggedTruncation) {
                            Reference.logger.error(
                                "Block {} has id {}, above the 24-bit schematic limit. The saved id will be truncated.",
                                BLOCK_REGISTRY.getNameForObject(block),
                                rawId);
                            loggedTruncation = true;
                        }
                    }
                    blockIds[index] = blockId;
                    int metadata = schematic.getBlockMetadata(x, y, z);
                    localMetadata[index] = (byte) metadata;
                    if ((metadata >>> 8) != 0) {
                        if (highMetadata == null) {
                            highMetadata = new byte[size];
                        }
                        highMetadata[index] = (byte) (metadata >>> 8);
                    }
                    if (block != null) {
                        String name = BLOCK_REGISTRY.getNameForObject(block);
                        if (!mappings.containsKey(name)) {
                            mappings.put(name, blockId);
                        }
                    }
                }
            }
        }

        int count = 20;
        NBTTagList tileEntitiesList = new NBTTagList();
        for (TileEntity tileEntity : schematic.getTileEntities()) {
            try {
                if (!tileEntity.hasWorldObj()) {
                    tileEntity.setWorldObj(backupWorld);
                }
                tileEntitiesList.appendTag(NBTHelper.writeTileEntityToCompound(tileEntity));
            } catch (Exception e) {
                int pos = index(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord, width, length);
                if (--count > 0) {
                    Block block = schematic.getBlock(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord);
                    Reference.logger.error(
                        "Block {}[{}] with TileEntity %s failed to save! Replacing with bedrock...",
                        block,
                        block != null ? BLOCK_REGISTRY.getNameForObject(block) : "?",
                        tileEntity.getClass()
                            .getName(),
                        e);
                }
                if (pos >= 0 && pos < blockIds.length) {
                    int bedrockId = BLOCK_REGISTRY.getId(Blocks.bedrock);
                    blockIds[pos] = bedrockId < 0 ? 0 : bedrockId;
                    localMetadata[pos] = 0;
                    if (highMetadata != null) {
                        highMetadata[pos] = 0;
                    }
                }
            }
        }

        NBTTagList entityList = new NBTTagList();
        List<Entity> entities = schematic.getEntities();
        for (Entity entity : entities) {
            try {
                NBTTagCompound entityCompound = NBTHelper.writeEntityToCompound(entity);
                if (entityCompound != null) {
                    entityList.appendTag(entityCompound);
                }
            } catch (Throwable t) {
                Reference.logger.error("Entity {} failed to save, skipping!", entity, t);
            }
        }

        PreSchematicSaveEvent event = new PreSchematicSaveEvent(schematic, mappings);
        MinecraftForge.EVENT_BUS.post(event);

        BlockIdCodec.Encoded encoded = BlockIdCodec.encode(blockIds);
        tagCompound.setString(Names.NBT.MATERIALS, Names.NBT.FORMAT_ALPHA);
        tagCompound.setByteArray(Names.NBT.BLOCKS, encoded.blocks);
        tagCompound.setByteArray(Names.NBT.DATA, localMetadata);
        if (highMetadata != null) {
            tagCompound.setByteArray(Names.NBT.DATA_HIGH, highMetadata);
        } else {
            tagCompound.removeTag(Names.NBT.DATA_HIGH);
        }
        tagCompound.removeTag(Names.NBT.ADD_BLOCKS_SCHEMATICA);
        tagCompound.removeTag(Names.NBT.BLOCK_ID_BYTES);
        tagCompound.removeTag(Names.NBT.ADD_BLOCKS);
        tagCompound.removeTag(Names.NBT.ADD_BLOCKS_HIGH);
        // A one-cell packed array otherwise looks like a legacy .schemplus high byte.
        if (encoded.blockIdBytes > 1 || (size == 1 && encoded.addBlocks != null)) {
            tagCompound.setByte(Names.NBT.BLOCK_ID_BYTES, (byte) encoded.blockIdBytes);
        }
        if (encoded.addBlocks != null) {
            tagCompound.setByteArray(Names.NBT.ADD_BLOCKS, encoded.addBlocks);
        }
        if (encoded.addBlocksHigh != null) {
            tagCompound.setByteArray(Names.NBT.ADD_BLOCKS_HIGH, encoded.addBlocksHigh);
        }
        tagCompound.setTag(Names.NBT.ENTITIES, entityList);
        tagCompound.setTag(Names.NBT.TILE_ENTITIES, tileEntitiesList);
        tagCompound.setTag(Names.NBT.MAPPING_SCHEMATICA, writeMapping(mappings));
        NBTTagCompound extendedMetadata = event.extendedMetadata;
        if (!extendedMetadata.hasNoTags()) {
            tagCompound.setTag(Names.NBT.EXTENDED_METADATA, extendedMetadata);
        } else {
            tagCompound.removeTag(Names.NBT.EXTENDED_METADATA);
        }

        return true;
    }

    private static Map<Integer, Integer> readMapping(NBTTagCompound tagCompound) {
        Map<Integer, Integer> oldToNew = new HashMap<>();
        if (!tagCompound.hasKey(Names.NBT.MAPPING_SCHEMATICA)) {
            return oldToNew;
        }

        NBTTagCompound mapping = tagCompound.getCompoundTag(Names.NBT.MAPPING_SCHEMATICA);
        for (String name : mapping.func_150296_c()) {
            NBTBase stored = mapping.getTag(name);
            int oldId = stored instanceof NBTTagShort ? mapping.getShort(name) & 0xFFFF : mapping.getInteger(name);
            oldToNew.put(oldId, BLOCK_REGISTRY.getId(name));
        }
        return oldToNew;
    }

    private static NBTTagCompound writeMapping(Map<String, Integer> mappings) {
        NBTTagCompound nbtMapping = new NBTTagCompound();
        for (Map.Entry<String, Integer> entry : mappings.entrySet()) {
            nbtMapping.setInteger(entry.getKey(), entry.getValue());
        }
        return nbtMapping;
    }

    private static byte[] byteArrayOrNull(NBTTagCompound tagCompound, String key) {
        return tagCompound.hasKey(key) ? tagCompound.getByteArray(key) : null;
    }

    private static int index(int x, int y, int z, int width, int length) {
        return x + (y * length + z) * width;
    }
}

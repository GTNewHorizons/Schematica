package com.github.lunatrius.schematica.network.message;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import com.github.lunatrius.schematica.api.ISchematic;
import com.github.lunatrius.schematica.handler.DownloadHandler;
import com.github.lunatrius.schematica.nbt.NBTHelper;
import com.github.lunatrius.schematica.reference.Constants;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.registry.FMLControlledNamespacedRegistry;
import cpw.mods.fml.common.registry.GameData;
import io.netty.buffer.ByteBuf;

public class MessageDownloadChunk implements IMessage, IMessageHandler<MessageDownloadChunk, IMessage> {

    public static final FMLControlledNamespacedRegistry<Block> BLOCK_REGISTRY = GameData.getBlockRegistry();
    private static final int CELL_COUNT = Constants.SchematicChunk.WIDTH * Constants.SchematicChunk.HEIGHT
        * Constants.SchematicChunk.LENGTH;

    public int baseX;
    public int baseY;
    public int baseZ;

    private final int[] blockIds = new int[CELL_COUNT];
    private final char[] metadata = new char[CELL_COUNT];
    private List<TileEntity> tileEntities = new ArrayList<>();
    private List<Entity> entities = new ArrayList<>();

    public MessageDownloadChunk() {}

    public MessageDownloadChunk(ISchematic schematic, int baseX, int baseY, int baseZ) {
        this.baseX = baseX;
        this.baseY = baseY;
        this.baseZ = baseZ;

        int index = 0;
        for (int x = 0; x < Constants.SchematicChunk.WIDTH; x++) {
            for (int y = 0; y < Constants.SchematicChunk.HEIGHT; y++) {
                for (int z = 0; z < Constants.SchematicChunk.LENGTH; z++, index++) {
                    final Block block = schematic.getBlock(baseX + x, baseY + y, baseZ + z);
                    this.blockIds[index] = BLOCK_REGISTRY.getId(block);
                    this.metadata[index] = (char) schematic.getBlockMetadata(baseX + x, baseY + y, baseZ + z);
                    final TileEntity tileEntity = schematic.getTileEntity(baseX + x, baseY + y, baseZ + z);
                    if (tileEntity != null) {
                        this.tileEntities.add(tileEntity);
                    }
                }
            }
        }
    }

    private void copyToSchematic(final ISchematic schematic) {
        int index = 0;
        for (int x = 0; x < Constants.SchematicChunk.WIDTH; x++) {
            for (int y = 0; y < Constants.SchematicChunk.HEIGHT; y++) {
                for (int z = 0; z < Constants.SchematicChunk.LENGTH; z++, index++) {
                    final Block block = BLOCK_REGISTRY.getObjectById(this.blockIds[index]);
                    schematic.setBlock(this.baseX + x, this.baseY + y, this.baseZ + z, block, this.metadata[index]);
                }
            }
        }

        for (TileEntity tileEntity : this.tileEntities) {
            schematic.setTileEntity(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord, tileEntity);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        if (DownloadHandler.INSTANCE.schematic == null) {
            // Legacy servers send a chunk when an unacknowledged begin packet times out.
            buf.skipBytes(buf.readableBytes());
            return;
        }

        this.baseX = buf.readShort();
        this.baseY = buf.readShort();
        this.baseZ = buf.readShort();

        for (int index = 0; index < CELL_COUNT; index++) {
            this.blockIds[index] = buf.readInt();
            this.metadata[index] = (char) buf.readUnsignedShort();
        }

        final NBTTagCompound compound = ByteBufUtils.readTag(buf);
        this.tileEntities = NBTHelper.readTileEntitiesFromCompound(compound, this.tileEntities);

        final NBTTagCompound compound2 = ByteBufUtils.readTag(buf);
        this.entities = NBTHelper.readEntitiesFromCompound(compound2, this.entities);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeShort(this.baseX);
        buf.writeShort(this.baseY);
        buf.writeShort(this.baseZ);

        for (int index = 0; index < CELL_COUNT; index++) {
            buf.writeInt(this.blockIds[index]);
            buf.writeShort(this.metadata[index]);
        }

        final NBTTagCompound compound = NBTHelper.writeTileEntitiesToCompound(this.tileEntities);
        ByteBufUtils.writeTag(buf, compound);

        final NBTTagCompound compound1 = NBTHelper.writeEntitiesToCompound(this.entities);
        ByteBufUtils.writeTag(buf, compound1);
    }

    @Override
    public IMessage onMessage(MessageDownloadChunk message, MessageContext ctx) {
        final ISchematic schematic = DownloadHandler.INSTANCE.schematic;
        if (schematic == null) {
            return null;
        }
        message.copyToSchematic(schematic);

        return new MessageDownloadChunkAck(message.baseX, message.baseY, message.baseZ);
    }
}

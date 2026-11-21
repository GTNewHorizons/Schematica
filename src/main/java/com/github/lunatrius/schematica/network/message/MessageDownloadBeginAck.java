package com.github.lunatrius.schematica.network.message;

import net.minecraft.entity.player.EntityPlayerMP;

import com.github.lunatrius.schematica.handler.DownloadHandler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class MessageDownloadBeginAck implements IMessage, IMessageHandler<MessageDownloadBeginAck, IMessage> {

    private boolean supportsExtendedBlockData;

    public MessageDownloadBeginAck() {}

    MessageDownloadBeginAck(boolean supportsExtendedBlockData) {
        this.supportsExtendedBlockData = supportsExtendedBlockData;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        // Older clients acknowledge with an empty payload.
        this.supportsExtendedBlockData = buf.readableBytes() == 1 && buf.readUnsignedByte() == 1;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        if (this.supportsExtendedBlockData) {
            buf.writeByte(1);
        }
    }

    @Override
    public IMessage onMessage(MessageDownloadBeginAck message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().playerEntity;
        DownloadHandler.INSTANCE.confirmBegin(player, message.supportsExtendedBlockData);

        return null;
    }
}

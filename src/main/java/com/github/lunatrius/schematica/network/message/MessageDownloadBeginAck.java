package com.github.lunatrius.schematica.network.message;

import net.minecraft.entity.player.EntityPlayerMP;

import com.github.lunatrius.schematica.handler.DownloadHandler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class MessageDownloadBeginAck implements IMessage, IMessageHandler<MessageDownloadBeginAck, IMessage> {

    private int protocolVersion;

    public MessageDownloadBeginAck() {}

    MessageDownloadBeginAck(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        // The original acknowledgment has no payload.
        this.protocolVersion = buf.readableBytes() == 1 ? buf.readUnsignedByte() : 0;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(this.protocolVersion);
    }

    @Override
    public IMessage onMessage(MessageDownloadBeginAck message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().playerEntity;
        DownloadHandler.INSTANCE.confirmBegin(player, message.protocolVersion);

        return null;
    }
}

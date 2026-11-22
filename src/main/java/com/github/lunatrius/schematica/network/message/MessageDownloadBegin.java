package com.github.lunatrius.schematica.network.message;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;

import com.github.lunatrius.schematica.api.ISchematic;
import com.github.lunatrius.schematica.handler.DownloadHandler;
import com.github.lunatrius.schematica.reference.Names;
import com.github.lunatrius.schematica.world.storage.Schematic;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class MessageDownloadBegin implements IMessage, IMessageHandler<MessageDownloadBegin, IMessage> {

    public ItemStack icon;
    public int width;
    public int height;
    public int length;

    public MessageDownloadBegin() {}

    public MessageDownloadBegin(ISchematic schematic) {
        this.icon = schematic.getIcon();
        this.width = schematic.getWidth();
        this.height = schematic.getHeight();
        this.length = schematic.getLength();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.icon = ByteBufUtils.readItemStack(buf);
        this.width = buf.readShort();
        this.height = buf.readShort();
        this.length = buf.readShort();
        if (buf.isReadable()) {
            if (this.width != 0 || this.height != 0 || this.length != 0 || buf.readableBytes() != 3 * Short.BYTES) {
                throw new IllegalArgumentException("Invalid extended schematic download dimensions");
            }
            this.width = buf.readShort();
            this.height = buf.readShort();
            this.length = buf.readShort();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeItemStack(buf, this.icon);
        // The original decoder reads only the empty dimensions.
        buf.writeShort(0);
        buf.writeShort(0);
        buf.writeShort(0);
        buf.writeShort(this.width);
        buf.writeShort(this.height);
        buf.writeShort(this.length);
    }

    @Override
    public IMessage onMessage(MessageDownloadBegin message, MessageContext ctx) {
        if (DownloadHandler.INSTANCE.serverProtocolVersion != DownloadHandler.PROTOCOL_VERSION) {
            // Withhold the acknowledgment so the old server never sends an incompatible chunk.
            final Minecraft minecraft = Minecraft.getMinecraft();
            final String messageKey = Names.Command.Download.Message.DOWNLOAD_SERVER_REQUIRES_UPDATE;
            minecraft.func_152344_a(
                () -> minecraft.ingameGUI.getChatGUI()
                    .printChatMessageWithOptionalDeletion(
                        new ChatComponentTranslation(messageKey),
                        messageKey.hashCode()));
            return null;
        }

        DownloadHandler.INSTANCE.schematic = new Schematic(message.icon, message.width, message.height, message.length);
        return new MessageDownloadBeginAck(DownloadHandler.PROTOCOL_VERSION);
    }
}

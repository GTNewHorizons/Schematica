package com.github.lunatrius.schematica.handler;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.StatCollector;

import com.github.lunatrius.schematica.api.ISchematic;
import com.github.lunatrius.schematica.network.PacketHandler;
import com.github.lunatrius.schematica.network.message.MessageDownloadBegin;
import com.github.lunatrius.schematica.network.message.MessageDownloadChunk;
import com.github.lunatrius.schematica.network.message.MessageDownloadEnd;
import com.github.lunatrius.schematica.network.transfer.SchematicTransfer;
import com.github.lunatrius.schematica.reference.Constants;
import com.github.lunatrius.schematica.reference.Names;
import com.github.lunatrius.schematica.reference.Reference;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class DownloadHandler {

    public static final int PROTOCOL_VERSION = 2;
    public static final DownloadHandler INSTANCE = new DownloadHandler();

    // Version advertised by the connected server via MessageCapabilities.
    public int serverProtocolVersion;
    public ISchematic schematic;

    private final Map<EntityPlayerMP, SchematicTransfer> transferMap = new LinkedHashMap<>();

    private DownloadHandler() {}

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            return;
        }

        processQueue();
    }

    public void startDownload(EntityPlayerMP player, ISchematic schematic, String name) {
        final SchematicTransfer transfer = new SchematicTransfer(schematic, name);
        synchronized (this) {
            this.transferMap.put(player, transfer);
        }
    }

    public synchronized void cancelDownload(EntityPlayerMP player) {
        this.transferMap.remove(player);
    }

    public synchronized void confirmBegin(EntityPlayerMP player, int protocolVersion) {
        final SchematicTransfer transfer = this.transferMap.get(player);
        if (transfer != null && transfer.state == SchematicTransfer.State.BEGIN) {
            transfer.clientProtocolVersion = protocolVersion;
            transfer.setState(SchematicTransfer.State.CHUNK_WAIT);
        }
    }

    public synchronized void confirmChunk(EntityPlayerMP player, int x, int y, int z) {
        final SchematicTransfer transfer = this.transferMap.get(player);
        if (transfer != null && transfer.state == SchematicTransfer.State.CHUNK) {
            transfer.confirmChunk(x, y, z);
        }
    }

    private synchronized void processQueue() {
        if (this.transferMap.isEmpty()) {
            return;
        }

        final EntityPlayerMP player = this.transferMap.keySet()
            .iterator()
            .next();
        final SchematicTransfer transfer = this.transferMap.remove(player);

        if (transfer == null) {
            return;
        }
        this.transferMap.put(player, transfer);

        if (!transfer.state.isWaiting()) {
            if (++transfer.timeout >= Constants.Network.TIMEOUT) {
                if (++transfer.retries >= Constants.Network.RETRIES) {
                    Reference.logger.warn("{}'s download was dropped!", player.getDisplayName());
                    this.transferMap.remove(player);
                    return;
                }

                Reference.logger
                    .warn("{}'s download timed out, retrying (#{})", player.getDisplayName(), transfer.retries);

                if (transfer.state == SchematicTransfer.State.BEGIN) {
                    sendBegin(player, transfer);
                } else {
                    sendChunk(player, transfer);
                }
                transfer.timeout = 0;
            }
        } else if (transfer.state == SchematicTransfer.State.BEGIN_WAIT) {
            transfer.setState(SchematicTransfer.State.BEGIN);
            sendBegin(player, transfer);
        } else if (transfer.state == SchematicTransfer.State.CHUNK_WAIT) {
            if (transfer.clientProtocolVersion != PROTOCOL_VERSION) {
                // Translate on the server because old clients do not have this key.
                player.addChatMessage(
                    new ChatComponentText(
                        StatCollector.translateToLocalFormatted(
                            Names.Command.Download.Message.DOWNLOAD_REQUIRES_UPDATE,
                            transfer.name)));
                this.transferMap.remove(player);
                return;
            }
            transfer.setState(SchematicTransfer.State.CHUNK);
            sendChunk(player, transfer);
        } else if (transfer.state == SchematicTransfer.State.END_WAIT) {
            this.transferMap.remove(player);
            sendEnd(player, transfer);
        }
    }

    private void sendBegin(EntityPlayerMP player, SchematicTransfer transfer) {
        MessageDownloadBegin message = new MessageDownloadBegin(transfer.schematic);
        PacketHandler.INSTANCE.sendTo(message, player);
    }

    private void sendChunk(EntityPlayerMP player, SchematicTransfer transfer) {
        Reference.logger.trace("Sending chunk {},{},{}", transfer.baseX, transfer.baseY, transfer.baseZ);
        MessageDownloadChunk message = new MessageDownloadChunk(
            transfer.schematic,
            transfer.baseX,
            transfer.baseY,
            transfer.baseZ);
        PacketHandler.INSTANCE.sendTo(message, player);
    }

    private void sendEnd(EntityPlayerMP player, SchematicTransfer transfer) {
        MessageDownloadEnd message = new MessageDownloadEnd(transfer.name);
        PacketHandler.INSTANCE.sendTo(message, player);
    }
}

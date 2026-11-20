package com.github.lunatrius.schematica.compat.architecturecraft;

import gcewing.architecture.ArchitectureCraft;
import gcewing.architecture.common.network.ChannelOutput;

/**
 * Isolates direct ArchitectureCraft class references. Do not use without checking if ArchitectureCraft is loaded.
 */
class ArchitectureCraftBridge {

    static void sendOrientationUpdate(int x, int y, int z, byte side, byte turn) {
        ChannelOutput out = ArchitectureCraft.channel.openServer("SetOrientation");
        out.writeInt(x);
        out.writeInt(y);
        out.writeInt(z);
        out.writeByte(side);
        out.writeByte(turn);
        out.close();
    }
}

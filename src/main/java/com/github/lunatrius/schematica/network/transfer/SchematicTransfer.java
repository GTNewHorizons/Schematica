package com.github.lunatrius.schematica.network.transfer;

import net.minecraft.block.Block;

import com.github.lunatrius.schematica.api.ISchematic;
import com.github.lunatrius.schematica.reference.Constants;

public class SchematicTransfer {

    public enum State {

        BEGIN_WAIT(true),
        BEGIN,
        CHUNK_WAIT(true),
        CHUNK,
        END_WAIT(true),
        END;

        private boolean waiting;

        State() {}

        State(boolean waiting) {
            this.waiting = waiting;
        }

        public boolean isWaiting() {
            return this.waiting;
        }
    }

    public final ISchematic schematic;
    public final String name;

    public final int width;
    public final int height;
    public final int length;
    public final boolean requiresExtendedBlockData;

    public State state = State.BEGIN_WAIT;
    public int timeout = 0;
    public int retries = 0;
    public boolean supportsExtendedBlockData;

    public int baseX = 0;
    public int baseY = 0;
    public int baseZ = 0;

    public SchematicTransfer(final ISchematic schematic, String name) {
        this.schematic = schematic;
        this.name = name;

        this.width = schematic.getWidth();
        this.height = schematic.getHeight();
        this.length = schematic.getLength();
        this.requiresExtendedBlockData = requiresExtendedBlockData();
    }

    public boolean confirmChunk(final int chunkX, final int chunkY, final int chunkZ) {
        if (chunkX == this.baseX && chunkY == this.baseY && chunkZ == this.baseZ) {
            setState(State.CHUNK_WAIT);
            this.baseX += Constants.SchematicChunk.WIDTH;

            if (this.baseX >= this.width) {
                this.baseX = 0;
                this.baseY += Constants.SchematicChunk.HEIGHT;

                if (this.baseY >= this.height) {
                    this.baseY = 0;
                    this.baseZ += Constants.SchematicChunk.LENGTH;

                    if (this.baseZ >= this.length) {
                        setState(State.END_WAIT);
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private boolean requiresExtendedBlockData() {
        for (int x = 0; x < this.width; x++) {
            for (int y = 0; y < this.height; y++) {
                for (int z = 0; z < this.length; z++) {
                    final int blockId = Block.getIdFromBlock(this.schematic.getBlock(x, y, z));
                    // Legacy clients truncate metadata and standard IDs. Their schemplus writer misencodes 256
                    // multiples.
                    if (this.schematic.getBlockMetadata(x, y, z) > 0xF || blockId > 0xFFF
                        || (blockId != 0 && (blockId & 0xFF) == 0)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void setState(State state) {
        this.state = state;
        this.timeout = 0;
        this.retries = 0;
    }
}

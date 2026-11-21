package com.github.lunatrius.schematica.world.schematic;

/**
 * Block ids stored in a schematic, up to 24 bits.
 *
 * <p>
 * Classic files keep the low 8 bits in {@code Blocks} and the next 4 bits as nibbles in {@code AddBlocks}. That
 * covers ids below 4096. Wider files store a full second byte per block in {@code AddBlocks} and, when any id is above
 * 65535, a third byte in {@code AddBlocksHigh}. {@code BlockIdBytes} is 2 or 3 on those files so a one-block schematic
 * is not read back as nibbles.
 *
 * <p>
 * Older {@code .schemplus} files use the full-byte {@code AddBlocks} layout and have no {@code BlockIdBytes} tag. A
 * one-block file of that kind is only recognized when {@code schemplusFile} is true. Ids already saved by the old
 * schemplus writer that were divisible by 256 dropped a byte on the way in; those files do not contain the missing
 * bits, so loading them gives the value that was actually written.
 */
public final class BlockIdCodec {

    public static final int MAX_SAVED_ID = 0xFFFFFF;
    public static final int CLASSIC_MAX_ID = 0xFFF;
    public static final int TWO_BYTE_MAX_ID = 0xFFFF;

    private BlockIdCodec() {}

    public static final class Encoded {

        public final byte[] blocks;
        public final byte[] addBlocks;
        public final byte[] addBlocksHigh;
        /** 1 = classic 8/12-bit, 2 = 16-bit, 3 = 24-bit. Classic files omit the tag. */
        public final int blockIdBytes;

        Encoded(byte[] blocks, byte[] addBlocks, byte[] addBlocksHigh, int blockIdBytes) {
            this.blocks = blocks;
            this.addBlocks = addBlocks;
            this.addBlocksHigh = addBlocksHigh;
            this.blockIdBytes = blockIdBytes;
        }
    }

    public static Encoded encode(int[] ids) {
        int[] stored = new int[ids.length];
        int max = 0;
        for (int i = 0; i < ids.length; i++) {
            int id = ids[i];
            if (id < 0) {
                id = 0;
            } else if (id > MAX_SAVED_ID) {
                id &= MAX_SAVED_ID;
            }
            stored[i] = id;
            if (id > max) {
                max = id;
            }
        }

        if (max <= CLASSIC_MAX_ID) {
            return encodeClassic(stored);
        }
        return encodeWide(stored, max <= TWO_BYTE_MAX_ID ? 2 : 3);
    }

    /**
     * @param blockIdBytes  value of the {@code BlockIdBytes} tag, or 0 when the tag is absent
     * @param schemplusFile true when the file name ends in {@code .schemplus}
     */
    public static int[] decode(byte[] blocks, byte[] addBlocks, byte[] legacyAdd, byte[] addHigh, int blockIdBytes,
        boolean schemplusFile) {
        if (blocks == null) {
            throw new IllegalArgumentException("Blocks array is missing");
        }
        int count = blocks.length;
        if (count == 0) {
            return new int[0];
        }
        if (addHigh != null || blockIdBytes >= 3) {
            return combine(blocks, addBlocks, addHigh);
        }
        if (blockIdBytes == 2) {
            return combine(blocks, addBlocks != null ? addBlocks : legacyAdd, null);
        }
        if (blockIdBytes == 1) {
            return addBlocks == null ? lowByte(blocks) : decodeNibbles(blocks, addBlocks);
        }
        // The former compatibility branch wrote Add alongside a packed preview in AddBlocks.
        if (legacyAdd != null) {
            return combine(blocks, legacyAdd, null);
        }
        if (addBlocks == null) {
            return lowByte(blocks);
        }
        if (addBlocks.length == count && (count != 1 || schemplusFile)) {
            return combine(blocks, addBlocks, null);
        }
        return decodeNibbles(blocks, addBlocks);
    }

    private static Encoded encodeClassic(int[] ids) {
        byte[] blocks = new byte[ids.length];
        boolean extra = false;
        byte[] nibbles = new byte[ids.length];
        for (int i = 0; i < ids.length; i++) {
            blocks[i] = (byte) ids[i];
            int high = (ids[i] >> 8) & 0xF;
            nibbles[i] = (byte) high;
            if (high != 0) {
                extra = true;
            }
        }
        if (!extra) {
            return new Encoded(blocks, null, null, 1);
        }

        byte[] packed = new byte[(ids.length + 1) / 2];
        for (int i = 0; i < packed.length; i++) {
            int even = nibbles[i * 2] & 0xF;
            int odd = (i * 2 + 1 < nibbles.length) ? (nibbles[i * 2 + 1] & 0xF) : 0;
            packed[i] = (byte) ((even << 4) | odd);
        }
        return new Encoded(blocks, packed, null, 1);
    }

    private static Encoded encodeWide(int[] ids, int blockIdBytes) {
        byte[] blocks = new byte[ids.length];
        byte[] addBlocks = new byte[ids.length];
        byte[] addHigh = blockIdBytes == 3 ? new byte[ids.length] : null;
        for (int i = 0; i < ids.length; i++) {
            blocks[i] = (byte) ids[i];
            addBlocks[i] = (byte) (ids[i] >> 8);
            if (addHigh != null) {
                addHigh[i] = (byte) (ids[i] >> 16);
            }
        }
        return new Encoded(blocks, addBlocks, addHigh, blockIdBytes);
    }

    private static int[] lowByte(byte[] blocks) {
        int[] ids = new int[blocks.length];
        for (int i = 0; i < blocks.length; i++) {
            ids[i] = blocks[i] & 0xFF;
        }
        return ids;
    }

    private static int[] decodeNibbles(byte[] blocks, byte[] packed) {
        int[] ids = new int[blocks.length];
        for (int i = 0; i < blocks.length; i++) {
            int packedIndex = i >>> 1;
            int high = 0;
            if (packedIndex < packed.length) {
                int value = packed[packedIndex] & 0xFF;
                high = (i & 1) == 0 ? (value >> 4) & 0xF : value & 0xF;
            }
            ids[i] = (blocks[i] & 0xFF) | (high << 8);
        }
        return ids;
    }

    private static int[] combine(byte[] blocks, byte[] mid, byte[] high) {
        if (mid == null || mid.length < blocks.length) {
            throw new IllegalArgumentException("AddBlocks does not cover every block");
        }
        if (high != null && high.length < blocks.length) {
            throw new IllegalArgumentException("AddBlocksHigh does not cover every block");
        }
        int[] ids = new int[blocks.length];
        for (int i = 0; i < blocks.length; i++) {
            int id = (blocks[i] & 0xFF) | ((mid[i] & 0xFF) << 8);
            if (high != null) {
                id |= (high[i] & 0xFF) << 16;
            }
            ids[i] = id;
        }
        return ids;
    }
}

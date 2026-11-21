package com.github.lunatrius.schematica.world.schematic;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.github.lunatrius.schematica.world.schematic.BlockIdCodec.Encoded;

public class BlockIdCodecTest {

    private static final int[] SAMPLE_IDS = { 0, 255, 256, 4095, 4096, 11277, 32767, 32768, 65535, 65536, 16777215 };

    @Test
    public void classicFilesRoundTripWithoutTheWidthTag() {
        int[] ids = { 0, 255, 256, 4095 };
        Encoded encoded = BlockIdCodec.encode(ids);
        assertEquals(1, encoded.blockIdBytes);
        int[] decoded = BlockIdCodec.decode(encoded.blocks, encoded.addBlocks, null, null, 0, false);
        assertArrayEquals(ids, decoded);
    }

    @Test
    public void roundTripsUpTo24Bits() {
        assertRoundTrip(SAMPLE_IDS, 3);
    }

    @Test
    public void keepsClassicLayoutWhenEveryIdFitsIn12Bits() {
        int[] ids = { 0, 255, 256, 512, 4095 };
        Encoded encoded = assertRoundTrip(ids, 1);
        assertNull(encoded.addBlocksHigh);
        assertEquals(3, encoded.addBlocks.length);
        assertEquals(0x00, encoded.blocks[2] & 0xFF);
        assertEquals(0x12, encoded.addBlocks[1] & 0xFF);
    }

    @Test
    public void omitsAddBlocksWhenEveryIdFitsIn8Bits() {
        Encoded encoded = assertRoundTrip(new int[] { 0, 1, 255 }, 1);
        assertNull(encoded.addBlocks);
        assertNull(encoded.addBlocksHigh);
    }

    @Test
    public void legacyTwelveBitFileDropsBitsAbove4095() {
        byte[] blocks = { (byte) 0x0D };
        byte[] addBlocks = { (byte) 0xC0 };
        int[] ids = BlockIdCodec.decode(blocks, addBlocks, null, null, 0, false);
        assertArrayEquals(new int[] { 3085 }, ids);
    }

    @Test
    public void writesTheReportedConcreteIdAsTwoBytes() {
        Encoded encoded = assertRoundTrip(new int[] { 11277 }, 2);
        assertEquals(0x0D, encoded.blocks[0] & 0xFF);
        assertEquals(0x2C, encoded.addBlocks[0] & 0xFF);
        assertNull(encoded.addBlocksHigh);
    }

    @Test
    public void oneBlockExtendedFileIsNotReadAsNibbles() {
        Encoded encoded = BlockIdCodec.encode(new int[] { 4096 });
        assertEquals(2, encoded.blockIdBytes);

        int[] withTag = BlockIdCodec.decode(encoded.blocks, encoded.addBlocks, null, null, encoded.blockIdBytes, false);
        assertArrayEquals(new int[] { 4096 }, withTag);

        int[] asClassicNibble = BlockIdCodec.decode(encoded.blocks, encoded.addBlocks, null, null, 0, false);
        assertArrayEquals(new int[] { 256 }, asClassicNibble);
    }

    @Test
    public void readsLegacySchemplusBytesIncludingASingleBlock() {
        byte[] blocks = { (byte) 0x0D };
        byte[] addBlocks = { (byte) 0x2C };
        int[] ids = BlockIdCodec.decode(blocks, addBlocks, null, null, 0, true);
        assertArrayEquals(new int[] { 11277 }, ids);
    }

    @Test
    public void readsTheLegacyFullByteAddKey() {
        byte[] blocks = { 0x00 };
        byte[] legacyAdd = { 0x02 };
        int[] ids = BlockIdCodec.decode(blocks, null, legacyAdd, null, 0, false);
        assertArrayEquals(new int[] { 512 }, ids);
    }

    @Test
    public void readsAMultipleOf256ThatTheOldSchemplusWriterTruncated() {
        byte[] blocks = { 0x00 };
        byte[] addBlocks = { 0x01 };
        int[] ids = BlockIdCodec.decode(blocks, addBlocks, null, null, 0, true);
        assertArrayEquals(new int[] { 256 }, ids);
    }

    @Test
    public void omitsTheHighByteWhenNoIdNeedsIt() {
        Encoded encoded = assertRoundTrip(new int[] { 1, 4096, 65535 }, 2);
        assertNull(encoded.addBlocksHigh);
        assertEquals(3, encoded.addBlocks.length);
    }

    private static Encoded assertRoundTrip(int[] ids, int blockIdBytes) {
        Encoded encoded = BlockIdCodec.encode(ids);
        assertEquals(blockIdBytes, encoded.blockIdBytes);
        int[] decoded = BlockIdCodec
            .decode(encoded.blocks, encoded.addBlocks, null, encoded.addBlocksHigh, encoded.blockIdBytes, false);
        assertArrayEquals(ids, decoded);
        if (encoded.blockIdBytes == 1) {
            int[] untagged = BlockIdCodec.decode(encoded.blocks, encoded.addBlocks, null, null, 0, false);
            assertArrayEquals(ids, untagged);
        }
        return encoded;
    }
}

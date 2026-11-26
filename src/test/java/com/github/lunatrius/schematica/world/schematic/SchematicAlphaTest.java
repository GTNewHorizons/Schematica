package com.github.lunatrius.schematica.world.schematic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Collections;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.github.lunatrius.schematica.Schematica;
import com.github.lunatrius.schematica.api.ISchematic;
import com.github.lunatrius.schematica.handler.DownloadHandler;
import com.github.lunatrius.schematica.nbt.NBTHelper;
import com.github.lunatrius.schematica.network.message.MessageCapabilities;
import com.github.lunatrius.schematica.network.message.MessageDownloadBegin;
import com.github.lunatrius.schematica.network.message.MessageDownloadChunk;
import com.github.lunatrius.schematica.proxy.ServerProxy;
import com.github.lunatrius.schematica.reference.Constants;
import com.github.lunatrius.schematica.reference.Names;
import com.github.lunatrius.schematica.world.storage.Schematic;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.registry.FMLControlledNamespacedRegistry;
import cpw.mods.fml.common.registry.GameData;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class SchematicAlphaTest {

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void legacyFilesAndCurrentDownloadsPreserveWideBlockData() throws Exception {
        runFixture("wideBlockData");
    }

    @Test
    public void discardsLegacyChunkSentAfterBeginTimeout() throws Exception {
        runFixture("legacyChunkAfterBeginTimeout");
    }

    private void runFixture(String name) throws Exception {
        // Forge's registry expects to be loaded by LaunchWrapper, including when no game is running.
        final String[] entries = System.getProperty("java.class.path")
            .split(File.pathSeparator);
        final URL[] classpath = new URL[entries.length];
        for (int i = 0; i < entries.length; i++) {
            classpath[i] = new File(entries[i]).toURI()
                .toURL();
        }
        final LaunchClassLoader previous = Launch.classLoader;
        try (LaunchClassLoader loader = new LaunchClassLoader(classpath)) {
            Launch.classLoader = loader;
            loader.loadClass(Fixture.class.getName())
                .getMethod(name, File.class)
                .invoke(null, this.temp.getRoot());
        } finally {
            Launch.classLoader = previous;
        }
    }

    public static class Fixture {

        private static void bootstrap(File gameDir) throws ReflectiveOperationException {
            Loader.injectData("7", "10", "99", "99", "1.7.10", "9.05", gameDir, Collections.emptyList());
            // Simulate a discovered mod list for compatibility checks in the headless fixture.
            final Field namedMods = Loader.class.getDeclaredField("namedMods");
            namedMods.setAccessible(true);
            namedMods.set(Loader.instance(), Collections.emptyMap());
            Bootstrap.func_151354_b();
            Schematica.proxy = new ServerProxy() {

                @Override
                public File getDataDirectory() {
                    return gameDir;
                }
            };
        }

        public static void wideBlockData(File gameDir) throws Exception {
            bootstrap(gameDir);
            final Method register = FMLControlledNamespacedRegistry.class
                .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
            register.setAccessible(true);
            final int[] ids = { 0x10C, 0xFEBA, 0x20D, 0x1000, 0xFFFF, 0x10006 };
            final int[] metadata = { 3, 0xFF, 0x100, 0xABCD, 0xFFFF, 0xFE01 };
            final Block[] blocks = new Block[ids.length];
            final Schematic source = new Schematic(new ItemStack(Blocks.stone), ids.length, 1, 1);
            for (int x = 0; x < ids.length; x++) {
                blocks[x] = new Block(Material.rock) {};
                register.invoke(GameData.getBlockRegistry(), ids[x], "fixture:block" + x, blocks[x]);
                assertTrue(source.setBlock(x, 0, 0, blocks[x], metadata[x]));
            }
            final SchematicAlpha format = new SchematicAlpha();
            final NBTTagCompound tags = new NBTTagCompound();

            assertTrue(format.writeToNBT(tags, source, null));
            assertEquals(3, tags.getByte(Names.NBT.BLOCK_ID_BYTES));
            assertCells(format.readFromNBT(tags), blocks, metadata);

            // Older compatibility builds wrote Add with one high byte per cell and a packed preview.
            final NBTTagCompound branchTags = new NBTTagCompound();
            branchTags.setShort(Names.NBT.WIDTH, (short) 2);
            branchTags.setShort(Names.NBT.HEIGHT, (short) 1);
            branchTags.setShort(Names.NBT.LENGTH, (short) 1);
            branchTags.setByteArray(Names.NBT.BLOCKS, new byte[] { 0x0C, (byte) 0xBA });
            branchTags.setByteArray(Names.NBT.ADD_BLOCKS, new byte[] { 0x1E });
            branchTags.setByteArray(Names.NBT.ADD_BLOCKS_SCHEMATICA, new byte[] { 1, (byte) 0xFE });
            branchTags.setByteArray(Names.NBT.DATA, new byte[] { 3, (byte) 0xFF });
            assertCells(format.readFromNBT(branchTags), new Block[] { blocks[0], blocks[1] }, new int[] { 3, 0xFF });

            branchTags.setByteArray(Names.NBT.BLOCKS, new byte[] { 0x0C, 0x0D });
            branchTags.setByteArray(Names.NBT.ADD_BLOCKS, new byte[] { 0x12 });
            branchTags.setByteArray(Names.NBT.ADD_BLOCKS_SCHEMATICA, new byte[] { (byte) 0xFE });
            final Block[] classicBlocks = { blocks[0], blocks[2] };
            final int[] classicMetadata = { 3, 0xFF };
            assertCells(format.readFromNBT(branchTags), classicBlocks, classicMetadata);
            branchTags.setByteArray(Names.NBT.ADD_BLOCKS_SCHEMATICA, new byte[0]);
            final ISchematic classic = format.readFromNBT(branchTags);
            assertCells(classic, classicBlocks, classicMetadata);
            assertTrue(format.writeToNBT(branchTags, classic, null));
            assertFalse(branchTags.hasKey(Names.NBT.ADD_BLOCKS_SCHEMATICA));
            assertCells(format.readFromNBT(branchTags), classicBlocks, classicMetadata);

            final File download = new File(gameDir, "download.schemplus");
            final Schematic single = new Schematic(source.getIcon(), 1, 1, 1);
            assertTrue(single.setBlock(0, 0, 0, blocks[0], 3));
            assertTrue(SchematicFormat.writeToFile(download, single, null));
            assertCells(SchematicFormat.readFromFile(download), new Block[] { blocks[0] }, new int[] { 3 });

            assertTrue(format.writeToNBT(tags, single, null));
            assertEquals(1, tags.getByte(Names.NBT.BLOCK_ID_BYTES));
            assertFalse(tags.hasKey(Names.NBT.DATA_HIGH));
            assertFalse(tags.hasKey(Names.NBT.ADD_BLOCKS_HIGH));
            assertCells(format.readFromNBT(tags, true), new Block[] { blocks[0] }, new int[] { 3 });

            assertDownloadRoundTrip(source, blocks, metadata);
        }

        public static void legacyChunkAfterBeginTimeout(File gameDir) throws ReflectiveOperationException {
            bootstrap(gameDir);
            final ByteBuf packet = Unpooled.buffer();
            try {
                // Old servers send this layout after the client refuses to acknowledge their begin packet.
                packet.writeShort(0);
                packet.writeShort(0);
                packet.writeShort(0);
                final int cells = Constants.SchematicChunk.WIDTH * Constants.SchematicChunk.HEIGHT
                    * Constants.SchematicChunk.LENGTH;
                for (int index = 0; index < cells; index++) {
                    packet.writeInt(0);
                    packet.writeByte(0);
                }
                ByteBufUtils.writeTag(packet, NBTHelper.writeTileEntitiesToCompound(Collections.emptyList()));
                ByteBufUtils.writeTag(packet, NBTHelper.writeEntitiesToCompound(Collections.emptyList()));

                final MessageDownloadChunk chunk = new MessageDownloadChunk();
                chunk.fromBytes(packet);
                assertFalse(packet.isReadable());
                assertNull(chunk.onMessage(chunk, null));
                assertNull(DownloadHandler.INSTANCE.schematic);
            } finally {
                packet.release();
            }
        }

        private static void assertDownloadRoundTrip(Schematic source, Block[] blocks, int[] metadata) {
            final ByteBuf packet = Unpooled.buffer();
            try {
                new MessageCapabilities(true, true, true).toBytes(packet);
                packet.skipBytes(3);
                assertEquals(DownloadHandler.PROTOCOL_VERSION, packet.readUnsignedByte());
                packet.clear();
                new MessageDownloadBegin(source).toBytes(packet);
                final ByteBuf legacyReader = packet.duplicate();
                ByteBufUtils.readItemStack(legacyReader);
                assertEquals(0, legacyReader.readShort());
                assertEquals(0, legacyReader.readShort());
                assertEquals(0, legacyReader.readShort());
                final MessageDownloadBegin begin = new MessageDownloadBegin();
                begin.fromBytes(packet);
                assertEquals(source.getWidth(), begin.width);
                assertFalse(packet.isReadable());
                DownloadHandler.INSTANCE.serverProtocolVersion = DownloadHandler.PROTOCOL_VERSION;
                assertNotNull(begin.onMessage(begin, null));

                packet.clear();
                new MessageDownloadChunk(source, 0, 0, 0).toBytes(packet);
                final MessageDownloadChunk chunk = new MessageDownloadChunk();
                chunk.fromBytes(packet);
                assertFalse(packet.isReadable());
                assertNotNull(chunk.onMessage(chunk, null));
                assertCells(DownloadHandler.INSTANCE.schematic, blocks, metadata);
            } finally {
                DownloadHandler.INSTANCE.schematic = null;
                DownloadHandler.INSTANCE.serverProtocolVersion = 0;
                packet.release();
            }
        }

        private static void assertCells(ISchematic schematic, Block[] blocks, int[] metadata) {
            for (int x = 0; x < blocks.length; x++) {
                assertSame(blocks[x], schematic.getBlock(x, 0, 0));
                assertEquals(metadata[x], schematic.getBlockMetadata(x, 0, 0));
            }
        }
    }
}

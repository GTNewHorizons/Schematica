package com.github.lunatrius.schematica.proxy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.lunatrius.core.util.vector.Vector3d;
import com.github.lunatrius.core.util.vector.Vector3i;
import com.github.lunatrius.schematica.api.ISchematic;
import com.github.lunatrius.schematica.client.printer.SchematicPrinter;
import com.github.lunatrius.schematica.client.renderer.RendererSchematicGlobal;
import com.github.lunatrius.schematica.client.world.SchematicWorld;
import com.github.lunatrius.schematica.compat.ILOTRPresent;
import com.github.lunatrius.schematica.compat.NoLOTRProxy;
import com.github.lunatrius.schematica.handler.ConfigurationHandler;
import com.github.lunatrius.schematica.handler.client.ChatEventHandler;
import com.github.lunatrius.schematica.handler.client.InputHandler;
import com.github.lunatrius.schematica.handler.client.OverlayHandler;
import com.github.lunatrius.schematica.handler.client.RenderTickHandler;
import com.github.lunatrius.schematica.handler.client.TickHandler;
import com.github.lunatrius.schematica.handler.client.WorldHandler;
import com.github.lunatrius.schematica.reference.Constants;
import com.github.lunatrius.schematica.reference.Reference;
import com.github.lunatrius.schematica.util.Coordinates;
import com.github.lunatrius.schematica.world.schematic.SchematicFormat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import cpw.mods.fml.client.config.GuiConfigEntries;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    public static final Vector3d playerPosition = new Vector3d();
    public static final Vector3i pointA = new Vector3i();
    public static final Vector3i pointB = new Vector3i();
    public static final Vector3i pointMin = new Vector3i();
    public static final Vector3i pointMax = new Vector3i();
    private static final Minecraft MINECRAFT = Minecraft.getMinecraft();
    public static boolean isRenderingGuide = false;
    public static boolean isPendingReset = false;
    public static ForgeDirection orientation = ForgeDirection.UNKNOWN;
    public static int rotationRender = 0;
    public static SchematicWorld schematic = null;
    public static MovingObjectPosition movingObjectPosition = null;
    private final SchematicWorld schematicWorld = null;
    public static ILOTRPresent lotrProxy = null;
    private static final Gson gson = new GsonBuilder().setPrettyPrinting()
        .create();
    private static final Type schematicDataType = new TypeToken<Map<String, Map<String, SchematicData>>>() {}.getType();

    public static void setPlayerData(EntityPlayer player, float partialTicks) {
        playerPosition.x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        playerPosition.y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        playerPosition.z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

        orientation = getOrientation(player);

        rotationRender = MathHelper.floor_double(player.rotationYaw / 90) & 3;
    }

    private static ForgeDirection getOrientation(EntityPlayer player) {
        if (player.rotationPitch > 45) {
            return ForgeDirection.DOWN;
        } else if (player.rotationPitch < -45) {
            return ForgeDirection.UP;
        } else {
            switch (MathHelper.floor_double(player.rotationYaw / 90.0 + 0.5) & 3) {
                case 0:
                    return ForgeDirection.SOUTH;
                case 1:
                    return ForgeDirection.WEST;
                case 2:
                    return ForgeDirection.NORTH;
                case 3:
                    return ForgeDirection.EAST;
            }
        }

        return ForgeDirection.UNKNOWN;
    }

    public static void updatePoints() {
        pointMin.x = Math.min(pointA.x, pointB.x);
        pointMin.y = Math.min(pointA.y, pointB.y);
        pointMin.z = Math.min(pointA.z, pointB.z);

        pointMax.x = Math.max(pointA.x, pointB.x);
        pointMax.y = Math.max(pointA.y, pointB.y);
        pointMax.z = Math.max(pointA.z, pointB.z);
    }

    public static void movePointToPlayer(Vector3i point) {
        point.x = (int) Math.floor(playerPosition.x);
        point.y = (int) Math.floor(playerPosition.y - 1);
        point.z = (int) Math.floor(playerPosition.z);

        switch (rotationRender) {
            case 0:
                point.x -= 1;
                point.z += 1;
                break;
            case 1:
                point.x -= 1;
                point.z -= 1;
                break;
            case 2:
                point.x += 1;
                point.z -= 1;
                break;
            case 3:
                point.x += 1;
                point.z += 1;
                break;
        }
    }

    public static void moveSchematicToPlayer(SchematicWorld schematic) {
        if (schematic != null) {
            Vector3i position = schematic.position;
            position.x = (int) Math.floor(playerPosition.x);
            position.y = (int) Math.floor(playerPosition.y) - 1;
            position.z = (int) Math.floor(playerPosition.z);

            switch (rotationRender) {
                case 0:
                    position.x -= schematic.getWidth();
                    position.z += 1;
                    break;
                case 1:
                    position.x -= schematic.getWidth();
                    position.z -= schematic.getLength();
                    break;
                case 2:
                    position.x += 1;
                    position.z -= schematic.getLength();
                    break;
                case 3:
                    position.x += 1;
                    position.z += 1;
                    break;
            }
        }
    }

    public static void moveSchematic(SchematicWorld schematic, Integer x, Integer y, Integer z) {
        if (schematic != null) {
            Vector3i position = schematic.position;
            position.x = x;
            position.y = y;
            position.z = z;
        }
    }

    private static class SchematicData {

        public int X;
        public int Y;
        public int Z;
        // default value is zero, ensuring backwards compatibility for updates that don't store the rotation
        public int RotationX;
        public int Rotation;
        public int RotationZ;
        public int FlipX;
        public int FlipY;
        public int FlipZ;

        SchematicData() {}
    }

    private static Map<String, Map<String, SchematicData>> openCoordinatesFile()
        throws ClassCastException, IOException {
        File coordinatesFile = new File(ConfigurationHandler.schematicDirectory, Constants.Files.Coordinates + ".json");
        Map<String, Map<String, SchematicData>> coordinates = new HashMap<>();
        if (coordinatesFile.exists() && coordinatesFile.canRead() && coordinatesFile.canWrite()) {
            try (Reader reader = Files.newBufferedReader(
                new File(ConfigurationHandler.schematicDirectory, Constants.Files.Coordinates + ".json").toPath(),
                StandardCharsets.UTF_8)) {
                coordinates = gson.fromJson(reader, schematicDataType);
            } catch (Exception e1) {
                // as I forgot to specify utf-8 before older Coordinates.json files will be in the default charset
                try (Reader reader = Files.newBufferedReader(
                    new File(ConfigurationHandler.schematicDirectory, Constants.Files.Coordinates + ".json").toPath(),
                    Charset.defaultCharset())) {
                    coordinates = gson.fromJson(reader, schematicDataType);
                } catch (Exception e2) {
                    // failed to read file in utf-8, trying with default charset
                    throw new ClassCastException("Failed to convert json file to Map<String,SchematicData>");
                }
            }

        } else if (!coordinatesFile.exists()) {
            if (saveCoordinatesFile(coordinates)) {
                Reference.logger.info("Created new coordinates file");
            } else throw new IOException("Failed to create coordinates file");
        } else {
            throw new IOException("No read/write permission for coordinates file");
        }
        return coordinates;
    }

    private static boolean saveCoordinatesFile(Map<String, Map<String, SchematicData>> map) {
        File coordinatesFile = new File(ConfigurationHandler.schematicDirectory, Constants.Files.Coordinates + ".json");
        try (OutputStreamWriter writer = new OutputStreamWriter(
            new FileOutputStream(coordinatesFile.getAbsoluteFile()),
            StandardCharsets.UTF_8)) {
            gson.toJson(map, schematicDataType, writer);
            writer.flush();
            Reference.logger.info("Successfully written to coordinates file");
            return true;
        } catch (IOException e) {
            Reference.logger.info("Failed to write to coordinates file");
            return false;
        }
    }

    public static boolean addCoordinatesAndRotation(String worldServerName, String schematicName, Integer X, Integer Y,
        Integer Z, Integer rotationX, Integer rotationY, Integer rotationZ, Integer flipX, Integer flipY,
        Integer flipZ) {
        try {
            Map<String, Map<String, SchematicData>> coordinates = openCoordinatesFile();
            SchematicData schematicData = new SchematicData();
            schematicData.X = X;
            schematicData.Y = Y;
            schematicData.Z = Z;
            schematicData.RotationX = rotationX; // This value is left as "Rotation" to provide backwards compat
            schematicData.Rotation = rotationY;
            schematicData.RotationZ = rotationZ;
            schematicData.FlipX = flipX;
            schematicData.FlipY = flipY;
            schematicData.FlipZ = flipZ;

            if (coordinates.containsKey(worldServerName)) {
                coordinates.get(worldServerName)
                    .put(schematicName, schematicData);
            } else {
                coordinates.put(worldServerName, new HashMap<>() {

                    {
                        put(schematicName, schematicData);
                    }
                });
            }
            saveCoordinatesFile(coordinates);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * gets the coordinates if present
     *
     * @return {@link Coordinates} containing rotation, flip and position information, or null if not found.
     */
    public static Coordinates getCoordinates(String worldServerName, String schematicName) {
        try {
            Map<String, Map<String, SchematicData>> coordinates = openCoordinatesFile();
            if (coordinates.containsKey(worldServerName)) {
                Map<String, SchematicData> schematicMap = coordinates.get(worldServerName);
                if (schematicMap.containsKey(schematicName)) {
                    SchematicData schematicData = schematicMap.get(schematicName);
                    return new Coordinates(
                        schematicData.RotationX,
                        schematicData.Rotation,
                        schematicData.RotationZ,
                        schematicData.FlipX,
                        schematicData.FlipY,
                        schematicData.FlipZ,
                        schematicData.X,
                        schematicData.Y,
                        schematicData.Z);
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);

        final Property[] sliders = { ConfigurationHandler.propAlpha, ConfigurationHandler.propBlockDelta,
            ConfigurationHandler.propPlaceDelay, ConfigurationHandler.propTimeout };
        for (Property prop : sliders) {
            prop.setConfigEntryClass(GuiConfigEntries.NumberSliderEntry.class);
        }

        for (KeyBinding keyBinding : InputHandler.KEY_BINDINGS) {
            ClientRegistry.registerKeyBinding(keyBinding);
        }
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        FMLCommonHandler.instance()
            .bus()
            .register(InputHandler.INSTANCE);
        FMLCommonHandler.instance()
            .bus()
            .register(TickHandler.INSTANCE);
        FMLCommonHandler.instance()
            .bus()
            .register(RenderTickHandler.INSTANCE);
        FMLCommonHandler.instance()
            .bus()
            .register(ConfigurationHandler.INSTANCE);

        MinecraftForge.EVENT_BUS.register(RendererSchematicGlobal.INSTANCE);
        MinecraftForge.EVENT_BUS.register(ChatEventHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(new OverlayHandler());
        MinecraftForge.EVENT_BUS.register(new WorldHandler());
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
        try {
            if (Loader.isModLoaded("lotr")) {
                Reference.logger.info("Lotr mod detected, creating proxy");
                lotrProxy = Class.forName(Reference.LOTR_PROXY)
                    .asSubclass(ILOTRPresent.class)
                    .getDeclaredConstructor()
                    .newInstance();
            } else {
                lotrProxy = new NoLOTRProxy();
            }
        } catch (Exception e) {
            Reference.logger.warn("Failed to create lotr proxy in the normal way");
            lotrProxy = new NoLOTRProxy();
        }
    }

    @Override
    public File getDataDirectory() {
        final File file = MINECRAFT.mcDataDir;
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            Reference.logger.debug("Could not canonize path!", e);
        }
        return file;
    }

    @Override
    public void resetSettings() {
        super.resetSettings();

        ChatEventHandler.INSTANCE.chatLines = 0;

        SchematicPrinter.INSTANCE.setEnabled(true);
        unloadSchematic();

        playerPosition.set(0, 0, 0);
        orientation = ForgeDirection.UNKNOWN;
        rotationRender = 0;

        pointA.set(0, 0, 0);
        pointB.set(0, 0, 0);
        updatePoints();
    }

    @Override
    public void unloadSchematic() {
        schematic = null;
        RendererSchematicGlobal.INSTANCE.destroyRendererSchematicChunks();
        SchematicPrinter.INSTANCE.setSchematic(null);
    }

    @Override
    public boolean loadSchematic(EntityPlayer player, File directory, String filename) {
        ISchematic schematic = SchematicFormat.readFromFile(directory, filename);
        if (schematic == null) {
            return false;
        }

        SchematicWorld world = new SchematicWorld(schematic, filename);

        Reference.logger
            .debug("Loaded {} [w:{},h:{},l:{}]", filename, world.getWidth(), world.getHeight(), world.getLength());

        ClientProxy.schematic = world;
        RendererSchematicGlobal.INSTANCE.createRendererSchematicChunks(world);
        SchematicPrinter.INSTANCE.setSchematic(world);
        world.isRendering = true;

        return true;
    }

    @Override
    public boolean isPlayerQuotaExceeded(EntityPlayer player) {
        return false;
    }

    @Override
    public File getPlayerSchematicDirectory(EntityPlayer player, boolean privateDirectory) {
        return ConfigurationHandler.schematicDirectory;
    }
}

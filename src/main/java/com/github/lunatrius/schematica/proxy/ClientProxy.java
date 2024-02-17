package com.github.lunatrius.schematica.proxy;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
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

import org.apache.commons.lang3.tuple.ImmutableTriple;

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

    public static Map<String, Map<String, Map<String, Integer>>> openCoordinatesFile()
            throws ClassCastException, IOException {
        Gson gson = new Gson();
        File coordinatesFile = new File(ConfigurationHandler.schematicDirectory, Constants.Files.Coordinates + ".json");
        Map<String, Map<String, Map<String, Integer>>> coordinates = new HashMap<>();
        if (coordinatesFile.exists() && coordinatesFile.canRead() && coordinatesFile.canWrite()) {
            try (Reader reader = Files.newBufferedReader(
                    new File(ConfigurationHandler.schematicDirectory, Constants.Files.Coordinates + ".json")
                            .toPath())) {
                coordinates = gson
                        .fromJson(reader, new TypeToken<Map<String, Map<String, Map<String, Integer>>>>() {}.getType());
            } catch (Exception e) {
                throw new ClassCastException("Failed to convert json file to Map<String,Map<String,Integer>>");
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

    public static boolean saveCoordinatesFile(Map<String, Map<String, Map<String, Integer>>> map) {
        Gson gsonBuilder = new GsonBuilder().setPrettyPrinting().create();
        File coordinatesFile = new File(ConfigurationHandler.schematicDirectory, Constants.Files.Coordinates + ".json");
        try (FileWriter writer = new FileWriter(coordinatesFile.getAbsoluteFile())) {
            gsonBuilder
                    .toJson(map, new TypeToken<Map<String, Map<String, Map<String, Integer>>>>() {}.getType(), writer);
            writer.flush();
            Reference.logger.info("Successfully written to coordinates file");
            return true;
        } catch (IOException e) {
            Reference.logger.info("Failed to write to coordinates file");
            return false;
        }
    }

    public static boolean addCoordinatesAndRotation(String worldServerName, String schematicName, Integer X, Integer Y,
            Integer Z, Integer rotation) {
        try {
            Map<String, Map<String, Map<String, Integer>>> coordinates = openCoordinatesFile();
            if (coordinates.containsKey(worldServerName)) {
                coordinates.get(worldServerName).put(schematicName, new HashMap<>() {

                    {
                        put("X", X);
                        put("Y", Y);
                        put("Z", Z);
                        put("Rotation", rotation);
                    }
                });
            } else {
                coordinates.put(worldServerName, new HashMap<>() {

                    {
                        put(schematicName, new HashMap<>() {

                            {
                                put("X", X);
                                put("Y", Y);
                                put("Z", Z);
                                put("Rotation", rotation);
                            }
                        });
                    }
                });
            }
            saveCoordinatesFile(coordinates);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * gets the coordinates if present
     *
     * @return {@link ImmutableTriple} with bool (true if coordinates found, false if not), {@link Integer} rotation
     *         (number of times schematic has been rotated [0-3]), and {@link ImmutableTriple} storing X,Y,Z
     *         {@link Integer}
     */
    public static ImmutableTriple<Boolean, Integer, ImmutableTriple<Integer, Integer, Integer>> getCoordinates(
            String worldServerName, String schematicName) {
        try {
            Map<String, Map<String, Map<String, Integer>>> coordinates = openCoordinatesFile();
            if (coordinates.containsKey(worldServerName)) {
                Map<String, Map<String, Integer>> schematicMap = coordinates.get(worldServerName);
                if (schematicMap.containsKey(schematicName)) {
                    Map<String, Integer> coordinateMap = schematicMap.get(schematicName);
                    if (coordinateMap.containsKey("X") && coordinateMap.containsKey("Y")
                            && coordinateMap.containsKey("Z")) {
                        return new ImmutableTriple<>(
                                true,
                                coordinateMap.getOrDefault("Rotation", 0),
                                new ImmutableTriple<>(
                                        coordinateMap.get("X"),
                                        coordinateMap.get("Y"),
                                        coordinateMap.get("Z")));
                    }
                }
            }
            return new ImmutableTriple<>(false, null, null);
        } catch (Exception e) {

            return new ImmutableTriple<>(false, null, null);
        }
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

        FMLCommonHandler.instance().bus().register(InputHandler.INSTANCE);
        FMLCommonHandler.instance().bus().register(TickHandler.INSTANCE);
        FMLCommonHandler.instance().bus().register(RenderTickHandler.INSTANCE);
        FMLCommonHandler.instance().bus().register(ConfigurationHandler.INSTANCE);

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
                lotrProxy = Class.forName(Reference.LOTR_PROXY).asSubclass(ILOTRPresent.class).getDeclaredConstructor()
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

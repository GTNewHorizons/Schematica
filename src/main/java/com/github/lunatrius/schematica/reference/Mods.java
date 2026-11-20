package com.github.lunatrius.schematica.reference;

import cpw.mods.fml.common.Loader;

/** Snapshots mod presence on first use, after FML mod discovery. */
public final class Mods {

    public static final String COOKING_FOR_BLOCKHEADS_ID = "cookingforblockheads";

    public static final boolean ARCHITECTURE_CRAFT = Loader.isModLoaded("ArchitectureCraft");
    public static final boolean CARPENTERS_BLOCKS = Loader.isModLoaded("CarpentersBlocks");
    public static final boolean COOKING_FOR_BLOCKHEADS = Loader.isModLoaded(COOKING_FOR_BLOCKHEADS_ID);
    public static final boolean FORGE_MICROBLOCK = Loader.isModLoaded("ForgeMicroblock");
    public static final boolean FORGE_MULTIPART = Loader.isModLoaded("ForgeMultipart");
    public static final boolean GTNH = Loader.isModLoaded("dreamcraft");
    public static final boolean LOTR = Loader.isModLoaded("lotr");
    public static final boolean POSTEA = Loader.isModLoaded("postea");

    private Mods() {}
}

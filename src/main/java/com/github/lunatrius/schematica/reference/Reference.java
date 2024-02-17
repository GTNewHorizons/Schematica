package com.github.lunatrius.schematica.reference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Reference {

    public static final String MODID = "Schematica";
    public static final String NAME = "Schematica";
    public static final String VERSION = SchematicaVersion.VERSION;
    public static final String DEPENDENCIES = "required-after:LunatriusCore;";
    public static final String PROXY_SERVER = "com.github.lunatrius.schematica.proxy.ServerProxy";
    public static final String PROXY_CLIENT = "com.github.lunatrius.schematica.proxy.ClientProxy";
    public static final String LOTR_PROXY = "com.github.lunatrius.schematica.compat.LOTRProxy";
    public static final String GUI_FACTORY = "com.github.lunatrius.schematica.client.gui.GuiFactory";

    public static Logger logger = LogManager.getLogger(Reference.MODID);
}

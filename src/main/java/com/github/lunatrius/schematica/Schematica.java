package com.github.lunatrius.schematica;

import java.util.Map;

import com.github.lunatrius.schematica.proxy.CommonProxy;
import com.github.lunatrius.schematica.reference.Reference;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.NetworkCheckHandler;
import cpw.mods.fml.relauncher.Side;

@Mod(
        modid = Reference.MODID,
        name = Reference.NAME,
        version = Reference.VERSION,
        dependencies = Reference.DEPENDENCIES,
        guiFactory = Reference.GUI_FACTORY)
public class Schematica {

    @Instance(Reference.MODID)
    public static Schematica instance;

    @SidedProxy(serverSide = Reference.PROXY_SERVER, clientSide = Reference.PROXY_CLIENT)
    public static CommonProxy proxy;

    @NetworkCheckHandler
    public boolean checkModList(Map<String, String> versions, Side side) {
        if (side == Side.CLIENT) {
            if (versions.containsKey("Schematica")) {
                String version = versions.get("Schematica");
                String[] splitVersion = version.split("\\.");
                boolean isAllowed = false;

                if (splitVersion.length < 3) return false;

                try {
                    int major = Integer.parseInt(splitVersion[0]);
                    int rev = Integer.parseInt(splitVersion[1]);

                    if (major > 1) {
                        isAllowed = true;
                    } else if (major == 1 && rev >= 11) {
                        isAllowed = true;
                    }
                } catch (NumberFormatException e) {
                    Reference.logger.warn("Failed to parse client version of " + version);
                }

                return isAllowed;
            }
        }

        return true;
    }

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }
}

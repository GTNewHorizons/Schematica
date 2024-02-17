package com.github.lunatrius.schematica.world.schematic;

import net.minecraft.nbt.NBTTagCompound;

import org.apache.commons.lang3.NotImplementedException;

import com.github.lunatrius.schematica.api.ISchematic;

// TODO: https://web.archive.org/web/20170708003352/http://minecraft.gamepedia.com/Data_values_(Classic)
public class SchematicClassic extends SchematicFormat {

    @Override
    public ISchematic readFromNBT(NBTTagCompound tagCompound) {
        throw new NotImplementedException("Not implemented");
    }

    @Override
    public boolean writeToNBT(NBTTagCompound tagCompound, ISchematic schematic) {
        throw new NotImplementedException("Not implemented");
    }
}

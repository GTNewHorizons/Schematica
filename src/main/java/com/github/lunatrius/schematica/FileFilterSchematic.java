package com.github.lunatrius.schematica;

import java.io.File;
import java.io.FileFilter;

import com.github.lunatrius.schematica.handler.ConfigurationHandler;

public class FileFilterSchematic implements FileFilter {

    private final boolean directory;

    public FileFilterSchematic(boolean dir) {
        this.directory = dir;
    }

    @Override
    public boolean accept(File file) {
        if (this.directory) {
            return file.isDirectory();
        }
        if (ConfigurationHandler.useSchematicplusFormat) {
            return file.getName()
                .toLowerCase()
                .endsWith(".schemplus");
        } else {
            return file.getName()
                .toLowerCase()
                .endsWith(".schematic");
        }
    }
}

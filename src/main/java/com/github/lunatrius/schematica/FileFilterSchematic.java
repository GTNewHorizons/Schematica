package com.github.lunatrius.schematica;

import java.io.File;
import java.io.FileFilter;
import java.util.Locale;

import com.github.lunatrius.schematica.util.FileUtils;

public class FileFilterSchematic implements FileFilter {

    public static final String EXTENSION_SCHEMATIC = ".schematic";
    public static final String EXTENSION_SCHEMPLUS = ".schemplus";

    private final boolean directory;

    public FileFilterSchematic(boolean dir) {
        this.directory = dir;
    }

    @Override
    public boolean accept(File file) {
        if (this.directory) {
            return file.isDirectory();
        }
        return isSchematicName(file.getName());
    }

    public static boolean isSchematicName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(EXTENSION_SCHEMATIC) || lower.endsWith(EXTENSION_SCHEMPLUS);
    }

    public static boolean isSchemplusName(String name) {
        return name.toLowerCase(Locale.ROOT)
            .endsWith(EXTENSION_SCHEMPLUS);
    }

    public static String stripExtension(String filename) {
        if (filename == null) {
            return null;
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(EXTENSION_SCHEMPLUS)) {
            return filename.substring(0, filename.length() - EXTENSION_SCHEMPLUS.length());
        }
        if (lower.endsWith(EXTENSION_SCHEMATIC)) {
            return filename.substring(0, filename.length() - EXTENSION_SCHEMATIC.length());
        }
        return filename;
    }

    /**
     * Finds {@code name}, {@code name.schematic}, or {@code name.schemplus} inside {@code directory}.
     * A bare name prefers {@code .schematic} when both files exist.
     */
    public static File resolve(File directory, String name) {
        if (directory == null || name == null || name.isEmpty()) {
            return null;
        }

        File direct = new File(directory, name);
        if (isSchematicName(name) && FileUtils.contains(directory, direct) && direct.isFile()) {
            return direct;
        }
        if (isSchematicName(name)) {
            return null;
        }

        File schematic = new File(directory, name + EXTENSION_SCHEMATIC);
        if (FileUtils.contains(directory, schematic) && schematic.isFile()) {
            return schematic;
        }
        File schemplus = new File(directory, name + EXTENSION_SCHEMPLUS);
        if (FileUtils.contains(directory, schemplus) && schemplus.isFile()) {
            return schemplus;
        }
        return null;
    }
}

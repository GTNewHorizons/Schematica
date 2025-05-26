package com.github.lunatrius.schematica.util;

public class Coordinates {

    public final int rotX;
    public final int rotY;
    public final int rotZ;

    public final int flipX;
    public final int flipY;
    public final int flipZ;

    public final int posX;
    public final int posY;
    public final int posZ;

    public Coordinates(int rotX, int rotY, int rotZ, int flipX, int flipY, int flipZ, int posX, int posY, int posZ) {
        this.rotX = rotX;
        this.rotY = rotY;
        this.rotZ = rotZ;
        this.flipX = flipX;
        this.flipY = flipY;
        this.flipZ = flipZ;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
    }
}

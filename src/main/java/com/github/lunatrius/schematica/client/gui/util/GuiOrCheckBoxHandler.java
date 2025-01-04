package com.github.lunatrius.schematica.client.gui.util;

import cpw.mods.fml.client.config.GuiCheckBox;

public class GuiOrCheckBoxHandler {

    private int checkedBoxId;
    private final GuiCheckBox[] boxes;

    // Sets up a set of check boxes that are OR'd together. First box input will be set to the starting true checkbox.
    public GuiOrCheckBoxHandler(GuiCheckBox... boxes) {
        this.boxes = boxes;
        checkBox(boxes[0]);
    }

    public void checkBox(GuiCheckBox boxToCheck) {
        checkBox(boxToCheck.id);
    }

    public void checkBox(int id) {
        boolean foundCheck = false;
        for (GuiCheckBox box : boxes) {
            if (box.id == id) {
                box.setIsChecked(true);
                checkedBoxId = id;
                foundCheck = true;
            } else {
                box.setIsChecked(false);
            }
        }
        if (!foundCheck) {
            throw new RuntimeException(
                "Check box passed in that doesn't exist in the handler! Check to make sure you're passing in the correct box.");
        }
    }

    public int getCheckedBoxId() {
        return checkedBoxId;
    }
}

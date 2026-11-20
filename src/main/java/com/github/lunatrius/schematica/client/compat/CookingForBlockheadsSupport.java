package com.github.lunatrius.schematica.client.compat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import net.blay09.mods.cookingforblockheads.utils.DyeUtils;
import net.minecraft.item.ItemStack;

import com.github.lunatrius.schematica.reference.Reference;

public final class CookingForBlockheadsSupport {

    public static final boolean SUPPORTS_DYE_SUBSTITUTES = supportsDyeSubstitutes();

    private CookingForBlockheadsSupport() {}

    public static int getDyeDamage(ItemStack stack) {
        return DyeUtils.rawDyeDamageFromStack(stack);
    }

    private static boolean supportsDyeSubstitutes() {
        try {
            final Method method = DyeUtils.class.getMethod("rawDyeDamageFromStack", ItemStack.class);
            return method.getReturnType() == int.class && Modifier.isStatic(method.getModifiers());
        } catch (ReflectiveOperationException | LinkageError e) {
            Reference.logger.debug("Cooking for Blockheads dye substitutes are unavailable", e);
            return false;
        }
    }
}

package com.github.lunatrius.schematica.client.printer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemBucket;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer.C05PacketPlayerLook;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.Action;
import net.minecraftforge.fluids.BlockFluidBase;
import net.minecraftforge.fluids.IFluidBlock;

import com.github.lunatrius.schematica.client.compat.ArchitectureCraftSupport;
import com.github.lunatrius.schematica.client.compat.CarpentersBlocksSupport;
import com.github.lunatrius.schematica.client.util.BlockToItemStack;
import com.github.lunatrius.schematica.handler.ConfigurationHandler;
import com.github.lunatrius.schematica.proxy.ClientProxy;
import com.github.lunatrius.schematica.reference.Constants;
import com.github.lunatrius.schematica.reference.Mods;

final class PrinterInteraction {

    final World world;
    final EntityClientPlayerMP player;
    private final PlayerControllerMP controller;
    private final InventoryPlayer inventory;

    PrinterInteraction(World world, EntityClientPlayerMP player, PlayerControllerMP controller) {
        this.world = world;
        this.player = player;
        this.controller = controller;
        this.inventory = player.inventory;
    }

    boolean canSelectItem(ItemStack item) {
        final int slot = findItemSlot(item);
        return (slot >= 0 && slot < Constants.Inventory.Size.HOTBAR)
            || (!ConfigurationHandler.swapSlotsQueue.isEmpty() && (slot >= 0 || this.controller.isInCreativeMode()));
    }

    boolean selectItem(ItemStack item) {
        return selectItem(item, true) && this.inventory.getCurrentItem() != null
            && BlockToItemStack.matches(this.inventory.getCurrentItem(), item);
    }

    private boolean selectItem(ItemStack item, boolean allowInventorySwap) {
        final int slot = findItemSlot(item);
        final boolean inHotbar = slot >= Constants.Inventory.InventoryOffset.HOTBAR
            && slot < Constants.Inventory.InventoryOffset.HOTBAR + Constants.Inventory.Size.HOTBAR;

        if (this.controller.isInCreativeMode() && !inHotbar && !ConfigurationHandler.swapSlotsQueue.isEmpty()) {
            this.inventory.currentItem = nextSwapSlot();
            this.inventory.setInventorySlotContents(this.inventory.currentItem, item.copy());
            this.controller.sendSlotPacket(
                this.inventory.getStackInSlot(this.inventory.currentItem),
                Constants.Inventory.SlotOffset.HOTBAR + this.inventory.currentItem);
            return true;
        }

        if (inHotbar) {
            this.inventory.currentItem = slot;
            return true;
        }
        return allowInventorySwap && slot >= Constants.Inventory.InventoryOffset.INVENTORY
            && slot < Constants.Inventory.InventoryOffset.INVENTORY + Constants.Inventory.Size.INVENTORY
            && swapIntoHotbar(slot)
            && selectItem(item, false);
    }

    private int findItemSlot(ItemStack item) {
        final boolean shape = Mods.ARCHITECTURE_CRAFT && ArchitectureCraftSupport.isShapeItem(item);
        final int connections = shape ? ArchitectureCraftSupport.getDisabledConnections(item) : 0;
        final boolean canSwap = !ConfigurationHandler.swapSlotsQueue.isEmpty()
            && this.player.openContainer == this.player.inventoryContainer;
        int fallback = -1;
        for (int i = 0; i < this.inventory.mainInventory.length; i++) {
            final ItemStack stack = this.inventory.mainInventory[i];
            if (stack != null && stack.stackSize > 0 && BlockToItemStack.matches(stack, item)) {
                if (!shape || (ArchitectureCraftSupport.getDisabledConnections(stack) == connections
                    && (i < Constants.Inventory.Size.HOTBAR || canSwap))) {
                    return i;
                }
                if (fallback < 0) {
                    fallback = i;
                }
            }
        }
        // Creative can make an exact stack. Survival can use the sawbench shape and adjust its connections by hand.
        return shape && this.controller.isInCreativeMode() && !ConfigurationHandler.swapSlotsQueue.isEmpty() ? -1
            : fallback;
    }

    private boolean swapIntoHotbar(int sourceSlot) {
        if (this.player.openContainer != this.player.inventoryContainer
            || ConfigurationHandler.swapSlotsQueue.isEmpty()) {
            return false;
        }
        this.controller
            .windowClick(this.player.inventoryContainer.windowId, sourceSlot, nextSwapSlot(), 2, this.player);
        return true;
    }

    private static int nextSwapSlot() {
        final int slot = ConfigurationHandler.swapSlotsQueue.poll() % Constants.Inventory.Size.HOTBAR;
        ConfigurationHandler.swapSlotsQueue.offer(slot);
        return slot;
    }

    static boolean isBlacklisted(Block block, ItemStack item) {
        return block instanceof IFluidBlock || block instanceof BlockLiquid
            || item.getItem() instanceof ItemBucket
            || item.getItem() == Items.sign
            || ClientProxy.lotrProxy.isBlackListed(block, item);
    }

    ForgeDirection[] findSupports(int x, int y, int z) {
        final List<ForgeDirection> supports = new ArrayList<>();
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            final int sx = x + side.offsetX;
            final int sy = y + side.offsetY;
            final int sz = z + side.offsetZ;
            final Block block = this.world.getBlock(sx, sy, sz);
            if (block != null && !block.isAir(this.world, sx, sy, sz)
                && !(block instanceof BlockFluidBase)
                && !block.isReplaceable(this.world, sx, sy, sz)) {
                supports.add(side);
            }
        }
        return supports.toArray(new ForgeDirection[supports.size()]);
    }

    static boolean canClickForPlacement(Block clicked, ItemStack item, boolean sneaking) {
        return !Mods.CARPENTERS_BLOCKS || CarpentersBlocksSupport.allowsPlacementClick(clicked, item, sneaking);
    }

    boolean place(ItemStack held, int x, int y, int z, int side, Vec3 hit) {
        return held != null && canClickForPlacement(this.world.getBlock(x, y, z), held, this.player.isSneaking())
            && useBlock(held, x, y, z, side, hit);
    }

    boolean useBlock(ItemStack held, int x, int y, int z, int side, Vec3 hit) {
        if (!isStillHolding(held)) {
            return false;
        }
        // Interaction listeners can send packets before PlayerControllerMP handles the vanilla click.
        this.controller.syncCurrentPlayItem();
        if (ForgeEventFactory.onPlayerInteract(this.player, Action.RIGHT_CLICK_BLOCK, x, y, z, side, this.world)
            .isCanceled() || !isStillHolding(held)) {
            return false;
        }
        final boolean success = this.controller.onPlayerRightClick(this.player, this.world, held, x, y, z, side, hit);
        if (success) {
            this.player.swingItem();
        }
        return success;
    }

    boolean tapWithoutMining(int x, int y, int z, int side) {
        final ItemStack held = this.player.getHeldItem();
        final Block block = this.world.getBlock(x, y, z);
        if (!isStillHolding(held) || block.getPlayerRelativeBlockHardness(this.player, this.world, x, y, z) >= 0) {
            return false;
        }
        this.controller.syncCurrentPlayItem();
        if (ForgeEventFactory.onPlayerInteract(this.player, Action.LEFT_CLICK_BLOCK, x, y, z, side, this.world)
            .isCanceled() || held != this.player.getHeldItem()
            || this.world.getBlock(x, y, z) != block) {
            return false;
        }
        // Send the initial hit and cancel mining so the mod can handle the tool without client-side block removal.
        this.player.sendQueue.addToSendQueue(new C07PacketPlayerDigging(0, x, y, z, side));
        this.player.sendQueue.addToSendQueue(new C07PacketPlayerDigging(1, x, y, z, side));
        this.player.swingItem();
        return true;
    }

    private boolean isStillHolding(ItemStack item) {
        return item != null && item.stackSize > 0 && item == this.player.getHeldItem();
    }

    void syncSneaking(boolean sneaking) {
        this.player.setSneaking(sneaking);
        this.player.sendQueue.addToSendQueue(new C0BPacketEntityAction(this.player, sneaking ? 1 : 2));
    }

    boolean withSneaking(boolean sneaking, BooleanSupplier action) {
        final boolean previousInput = this.player.movementInput.sneak;
        this.player.movementInput.sneak = sneaking;
        if (!sneaking) {
            syncSneaking(false);
        }
        try {
            return action.getAsBoolean();
        } finally {
            this.player.movementInput.sneak = previousInput;
            if (!sneaking) {
                syncSneaking(true);
            }
        }
    }

    boolean withLook(float yaw, float pitch, BooleanSupplier action) {
        final float previousYaw = this.player.rotationYaw;
        final float previousPitch = this.player.rotationPitch;
        syncLook(yaw, pitch);
        try {
            return action.getAsBoolean();
        } finally {
            syncLook(previousYaw, previousPitch);
        }
    }

    private void syncLook(float yaw, float pitch) {
        this.player.rotationYaw = yaw;
        this.player.rotationPitch = pitch;
        this.player.sendQueue.addToSendQueue(new C05PacketPlayerLook(yaw, pitch, this.player.onGround));
    }
}

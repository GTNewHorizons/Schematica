package com.github.lunatrius.schematica.client.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import net.minecraft.block.Block;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.lunatrius.schematica.reference.Mods;

import codechicken.lib.raytracer.IndexedCuboid6;
import codechicken.lib.raytracer.RayTracer;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.vec.Cuboid6;
import codechicken.lib.vec.Vector3;
import codechicken.microblock.CommonMicroClass;
import codechicken.microblock.ExecutablePlacement;
import codechicken.microblock.ExpandingPlacement;
import codechicken.microblock.ItemMicroPart;
import codechicken.microblock.Microblock;
import codechicken.microblock.MicroblockPlacement;
import codechicken.multipart.TMultiPart;
import codechicken.multipart.TileMultipart;
import scala.collection.Iterator;

public final class MicroblockSupport {

    private static final double[] FACE_SAMPLES = { 0.5, 0.125, 0.875, 0.03125, 0.96875 };
    private static final double SERVER_SNEAK_EYE_OFFSET = 0.08;

    private MicroblockSupport() {}

    public static boolean isMicroblockItem(ItemStack stack) {
        return stack.getItem() instanceof ItemMicroPart;
    }

    public static boolean itemsMatch(ItemStack stack, ItemStack target) {
        return stack.isItemEqual(target) && stack.hasTagCompound()
            && target.hasTagCompound()
            && stack.getTagCompound()
                .getString("mat")
                .equals(
                    target.getTagCompound()
                        .getString("mat"));
    }

    public static boolean hasMicroblocks(TileEntity tile) {
        return !getParts(tile).isEmpty();
    }

    public static void renderMissingParts(IBlockAccess schematic, TileEntity placed, int x, int y, int z, int pass) {
        if (!(placed instanceof TileMultipart)) {
            return;
        }

        final List<Microblock> missing = getParts(schematic.getTileEntity(x, y, z));
        if (missing.isEmpty()) {
            return;
        }
        final List<Microblock> actual = getParts(placed);
        missing.removeIf(part -> removeMatch(actual, part));
        if (missing.isEmpty()) {
            return;
        }

        final CCRenderState state = CCRenderState.instance();
        state.resetInstance();
        state.lightMatrix.locate(schematic, x, y, z);
        try {
            final Vector3 position = new Vector3(x, y, z);
            for (Microblock part : missing) {
                part.renderStatic(position, pass);
            }
        } finally {
            state.lightMatrix.access = null;
        }
    }

    public static boolean addMaterials(TileEntity schematic, TileEntity world, BiConsumer<ItemStack, Boolean> add) {
        final List<Microblock> expected = getParts(schematic);
        if (expected.isEmpty()) {
            return false;
        }

        final List<Microblock> unmatched = getParts(world);
        for (Microblock part : expected) {
            if (removeMatch(unmatched, part)) {
                for (ItemStack stack : part.getDrops()) {
                    add.accept(stack, true);
                }
                continue;
            }
            final Microblock partial = findPartialMatch(unmatched, part);
            if (partial == null) {
                for (ItemStack stack : part.getDrops()) {
                    add.accept(stack, false);
                }
                continue;
            }
            unmatched.remove(partial);
            for (ItemStack stack : partial.getDrops()) {
                add.accept(stack, true);
            }
            int remaining = part.getSize() - partial.getSize();
            while (remaining > 0) {
                final int size = Integer.highestOneBit(remaining);
                add.accept(ItemMicroPart.create(part.itemClassID() << 8 | size, part.getMaterial()), false);
                remaining -= size;
            }
        }
        return true;
    }

    public static Click findPlacement(EntityClientPlayerMP player, TileEntity schematic, int x, int y, int z,
        Predicate<ItemStack> hasItem) {
        final List<Microblock> expected = getParts(schematic);
        final List<Microblock> unmatched = getParts(player.worldObj.getTileEntity(x, y, z));
        expected.removeIf(part -> removeMatch(unmatched, part));

        final List<PlacementRequest> additions = new ArrayList<>();
        final List<PlacementRequest> expansions = new ArrayList<>();
        for (Microblock part : expected) {
            final Microblock partial = findPartialMatch(unmatched, part);
            final int existingSize = partial == null ? 0 : partial.getSize();
            final List<PlacementRequest> requests = existingSize == 0 ? additions : expansions;
            for (int size = Integer.highestOneBit(part.getSize() - existingSize); size > 0; size >>= 1) {
                final ItemStack item = ItemMicroPart.create(part.itemClassID() << 8 | size, part.getMaterial());
                if (hasItem.test(item)) {
                    requests.add(new PlacementRequest(part, item, existingSize + size));
                }
            }
        }
        if (additions.isEmpty() && expansions.isEmpty()) {
            return null;
        }

        final float yaw = player.rotationYaw;
        final float pitch = player.rotationPitch;
        final boolean sneaking = player.movementInput.sneak;
        try {
            // Additions sneak to bypass block activation. FMP expands an existing part with a normal click.
            // Finish an exposed partial part before adding parts that could obstruct it.
            final Click expansion = findClick(player, x, y, z, expansions, false, true, block -> true);
            if (expansion != null) {
                return expansion;
            }
            player.rotationYaw = yaw;
            player.rotationPitch = pitch;
            final Click addition = findClick(
                player,
                x,
                y,
                z,
                additions,
                true,
                false,
                block -> !Mods.CARPENTERS_BLOCKS || !CarpentersBlocksSupport.isCoverable(block));
            if (addition != null || !Mods.CARPENTERS_BLOCKS) {
                return addition;
            }
            player.rotationYaw = yaw;
            player.rotationPitch = pitch;
            // Carpenter's intercepts sneak-clicks with microblocks before Forge Multipart can use them.
            return findClick(
                player,
                x,
                y,
                z,
                additions,
                false,
                false,
                CarpentersBlocksSupport::passesThroughMicroblockClick);
        } finally {
            player.rotationYaw = yaw;
            player.rotationPitch = pitch;
            player.movementInput.sneak = sneaking;
        }
    }

    private static Click findClick(EntityClientPlayerMP player, int x, int y, int z, List<PlacementRequest> requests,
        boolean sneaking, boolean expanding, Predicate<Block> canClick) {
        if (requests.isEmpty()) {
            return null;
        }
        player.movementInput.sneak = sneaking;
        final Vec3 eye = RayTracer.getStartVec(player);
        // CodeChickenLib uses the server's standing eye height above the player's feet, then adjusts for sneaking.
        final double serverEyeOffset = player.boundingBox.minY + 1.62F
            - player.posY
            - (sneaking ? SERVER_SNEAK_EYE_OFFSET : 0);
        return new PlacementSearch(player, x, y, z, requests, eye, serverEyeOffset, expanding, canClick).find();
    }

    private static final class PlacementSearch {

        private final EntityClientPlayerMP player;
        private final World world;
        private final int x, y, z;
        private final List<PlacementRequest> requests;
        private final Vec3 eye;
        private final Vec3 serverEye;
        private final double reach;
        private final double aimOffsetY;
        private final boolean expanding;
        private final Predicate<Block> canClick;

        private PlacementSearch(EntityClientPlayerMP player, int x, int y, int z, List<PlacementRequest> requests,
            Vec3 eye, double serverEyeOffset, boolean expanding, Predicate<Block> canClick) {
            this.player = player;
            this.world = player.worldObj;
            this.x = x;
            this.y = y;
            this.z = z;
            this.requests = requests;
            this.eye = eye;
            this.serverEye = eye.addVector(0, serverEyeOffset, 0);
            this.reach = RayTracer.getBlockReachDistance(player);
            this.aimOffsetY = -serverEyeOffset / 2;
            this.expanding = expanding;
            this.canClick = canClick;
        }

        private Click find() {
            final Vec3 look = player.getLook(1);
            final MovingObjectPosition visible = world.rayTraceBlocks(
                eye.addVector(0, 0, 0),
                eye.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach));
            if (visible != null
                && Math.abs(visible.blockX - x) + Math.abs(visible.blockY - y) + Math.abs(visible.blockZ - z)
                    <= (expanding ? 0 : 1)) {
                final Click click = tryAim(visible.blockX, visible.blockY, visible.blockZ, visible.sideHit);
                if (click != null) {
                    return click;
                }
            }

            final Click internal = tryBlock(x, y, z, ForgeDirection.UNKNOWN);
            if (internal != null || expanding) {
                return internal;
            }
            for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
                final Click external = tryBlock(
                    x + side.offsetX,
                    y + side.offsetY,
                    z + side.offsetZ,
                    side.getOpposite());
                if (external != null) {
                    return external;
                }
            }
            return null;
        }

        private Click tryBlock(int bx, int by, int bz, ForgeDirection face) {
            final Block block = world.getBlock(bx, by, bz);
            if (world.isAirBlock(bx, by, bz) || !canClick.test(block)) {
                return null;
            }
            if (world.getTileEntity(bx, by, bz) instanceof TileMultipart multipart) {
                final Iterator<TMultiPart> parts = multipart.partList()
                    .iterator();
                final Vector3 origin = new Vector3(bx, by, bz);
                while (parts.hasNext()) {
                    for (IndexedCuboid6 box : parts.next()
                        .getSubParts()) {
                        final Click click = tryBounds(
                            bx,
                            by,
                            bz,
                            face,
                            box.copy()
                                .add(origin));
                        if (click != null) {
                            return click;
                        }
                    }
                }
                return null;
            }

            block.setBlockBoundsBasedOnState(world, bx, by, bz);
            final AxisAlignedBB selected = block.getSelectedBoundingBoxFromPool(world, bx, by, bz);
            final Cuboid6 bounds = selected == null ? new Cuboid6(bx, by, bz, bx + 1, by + 1, bz + 1)
                : new Cuboid6(selected);
            return tryBounds(bx, by, bz, face, bounds);
        }

        private Click tryBounds(int bx, int by, int bz, ForgeDirection face, Cuboid6 bounds) {
            if (face != ForgeDirection.UNKNOWN) {
                return tryFace(bx, by, bz, face, bounds);
            }
            for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
                final Click click = tryFace(bx, by, bz, side, bounds);
                if (click != null) {
                    return click;
                }
            }
            return null;
        }

        private Click tryFace(int bx, int by, int bz, ForgeDirection face, Cuboid6 bounds) {
            final double plane = bounds.getSide(face.ordinal());
            final double distance = face.offsetX != 0 ? (eye.xCoord - plane) * face.offsetX
                : face.offsetY != 0 ? (eye.yCoord - plane) * face.offsetY : (eye.zCoord - plane) * face.offsetZ;
            if (distance <= 0 || distance > reach) {
                return null;
            }

            // Sample the actual click surface, including the narrow borders used by the placement grid.
            for (double u : FACE_SAMPLES) {
                for (double v : FACE_SAMPLES) {
                    final double hx = face.offsetX == 0 ? bounds.min.x + (bounds.max.x - bounds.min.x) * u : plane;
                    final double hy = face.offsetY == 0
                        ? bounds.min.y + (bounds.max.y - bounds.min.y) * (face.offsetX == 0 ? v : u)
                        : plane;
                    final double hz = face.offsetZ == 0 ? bounds.min.z + (bounds.max.z - bounds.min.z) * v : plane;
                    final double aimY = hy + aimOffsetY;
                    if (eye.squareDistanceTo(hx, aimY, hz) > reach * reach) {
                        continue;
                    }
                    aimAt(player, eye, hx, aimY, hz);
                    final Click click = tryAim(bx, by, bz, face.ordinal());
                    if (click != null) {
                        return click;
                    }
                }
            }
            return null;
        }

        private Click tryAim(int bx, int by, int bz, int face) {
            final Vec3 look = player.getLook(1);
            final double dx = look.xCoord * reach;
            final double dy = look.yCoord * reach;
            final double dz = look.zCoord * reach;
            final Vec3 end = eye.addVector(dx, dy, dz);
            final Block block = world.getBlock(bx, by, bz);
            if (!canClick.test(block)) {
                return null;
            }
            final MovingObjectPosition hit = block.collisionRayTrace(world, bx, by, bz, eye, end);
            if (hit == null || hit.sideHit != face) {
                return null;
            }

            for (PlacementRequest request : requests) {
                final ExecutablePlacement placement = predictPlacement(player, x, y, z, request, hit, expanding);
                if (placement == null) {
                    continue;
                }
                final MovingObjectPosition serverHit = block
                    .collisionRayTrace(world, bx, by, bz, serverEye, serverEye.addVector(dx, dy, dz));
                if (serverHit == null || serverHit.sideHit != face
                    || predictPlacement(player, x, y, z, request, serverHit, expanding) == null) {
                    continue;
                }
                final MovingObjectPosition visible = world.rayTraceBlocks(eye.addVector(0, 0, 0), end);
                if (visible != null && visible.blockX == bx
                    && visible.blockY == by
                    && visible.blockZ == bz
                    && visible.sideHit == face) {
                    return new Click(
                        request.item,
                        hit,
                        player.rotationYaw,
                        player.rotationPitch,
                        player.isSneaking(),
                        x,
                        y,
                        z,
                        placement.part());
                }
                return null;
            }
            return null;
        }
    }

    private static ExecutablePlacement predictPlacement(EntityClientPlayerMP player, int x, int y, int z,
        PlacementRequest request, MovingObjectPosition hit, boolean expanding) {
        final int damage = request.item.getItemDamage();
        final CommonMicroClass microClass = CommonMicroClass.getMicroClass(damage);
        final ExecutablePlacement placement = new MicroblockPlacement(
            player,
            hit,
            damage & 0xff,
            request.part.getMaterial(),
            !player.capabilities.isCreativeMode,
            microClass.placementProperties()).apply();
        return placement != null && (placement instanceof ExpandingPlacement) == expanding
            && placement.pos().x == x
            && placement.pos().y == y
            && placement.pos().z == z
            && sameOrientation(placement.part(), request.part)
            && placement.part()
                .getSize() == request.resultSize ? placement : null;
    }

    private static void aimAt(EntityClientPlayerMP player, Vec3 eye, double x, double y, double z) {
        final double dx = x - eye.xCoord;
        final double dy = y - eye.yCoord;
        final double dz = z - eye.zCoord;
        player.rotationYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        player.rotationPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
    }

    private static List<Microblock> getParts(TileEntity tile) {
        final List<Microblock> parts = new ArrayList<>();
        if (tile instanceof TileMultipart multipart) {
            final Iterator<TMultiPart> iterator = multipart.partList()
                .iterator();
            while (iterator.hasNext()) {
                if (iterator.next() instanceof Microblock part) {
                    parts.add(part);
                }
            }
        }
        return parts;
    }

    private static boolean removeMatch(List<Microblock> parts, Microblock expected) {
        for (int i = 0; i < parts.size(); i++) {
            final Microblock part = parts.get(i);
            if (part.getSize() == expected.getSize() && sameOrientation(part, expected)) {
                parts.remove(i);
                return true;
            }
        }
        return false;
    }

    private static boolean sameOrientation(Microblock part, Microblock expected) {
        return part.getMaterial() == expected.getMaterial() && part.getShape() == expected.getShape()
            && part.microClass() == expected.microClass();
    }

    private static Microblock findPartialMatch(List<Microblock> parts, Microblock expected) {
        Microblock partial = null;
        for (Microblock part : parts) {
            if (sameOrientation(part, expected) && part.getSize() < expected.getSize()
                && (partial == null || part.getSize() > partial.getSize())) {
                partial = part;
            }
        }
        return partial;
    }

    private static final class PlacementRequest {

        private final Microblock part;
        private final ItemStack item;
        private final int resultSize;

        private PlacementRequest(Microblock part, ItemStack item, int resultSize) {
            this.part = part;
            this.item = item;
            this.resultSize = resultSize;
        }
    }

    public static final class Click {

        public final ItemStack item;
        public final MovingObjectPosition hit;
        public final float yaw;
        public final float pitch;
        public final boolean sneaking;

        private final int x, y, z;
        private final Microblock result;

        private Click(ItemStack item, MovingObjectPosition hit, float yaw, float pitch, boolean sneaking, int x, int y,
            int z, Microblock result) {
            this.item = item;
            this.hit = hit;
            this.yaw = yaw;
            this.pitch = pitch;
            this.sneaking = sneaking;
            this.x = x;
            this.y = y;
            this.z = z;
            this.result = result;
        }

        public boolean targets(int x, int y, int z) {
            return this.x == x && this.y == y && this.z == z;
        }

        public boolean isConfirmed(TileEntity tile) {
            for (Microblock part : getParts(tile)) {
                if (part.getSize() == result.getSize() && sameOrientation(part, result)) {
                    return true;
                }
            }
            return false;
        }
    }
}

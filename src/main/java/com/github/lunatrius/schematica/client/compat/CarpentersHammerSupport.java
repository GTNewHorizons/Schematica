package com.github.lunatrius.schematica.client.compat;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.carpentersblocks.api.ICarpentersHammer;
import com.carpentersblocks.block.BlockCarpentersBarrier;
import com.carpentersblocks.block.BlockCarpentersBed;
import com.carpentersblocks.block.BlockCarpentersBlock;
import com.carpentersblocks.block.BlockCarpentersButton;
import com.carpentersblocks.block.BlockCarpentersDaylightSensor;
import com.carpentersblocks.block.BlockCarpentersDoor;
import com.carpentersblocks.block.BlockCarpentersFlowerPot;
import com.carpentersblocks.block.BlockCarpentersGarageDoor;
import com.carpentersblocks.block.BlockCarpentersGate;
import com.carpentersblocks.block.BlockCarpentersHatch;
import com.carpentersblocks.block.BlockCarpentersLadder;
import com.carpentersblocks.block.BlockCarpentersLever;
import com.carpentersblocks.block.BlockCarpentersPressurePlate;
import com.carpentersblocks.block.BlockCarpentersSafe;
import com.carpentersblocks.block.BlockCarpentersSlope;
import com.carpentersblocks.block.BlockCarpentersStairs;
import com.carpentersblocks.block.BlockCarpentersTorch;
import com.carpentersblocks.data.Barrier;
import com.carpentersblocks.data.Bed;
import com.carpentersblocks.data.Button;
import com.carpentersblocks.data.DaylightSensor;
import com.carpentersblocks.data.GarageDoor;
import com.carpentersblocks.data.Gate;
import com.carpentersblocks.data.Hatch;
import com.carpentersblocks.data.Hinge;
import com.carpentersblocks.data.Ladder;
import com.carpentersblocks.data.Lever;
import com.carpentersblocks.data.PressurePlate;
import com.carpentersblocks.data.Safe;
import com.carpentersblocks.data.Slab;
import com.carpentersblocks.data.Slope;
import com.carpentersblocks.data.Stairs;
import com.carpentersblocks.data.Torch;
import com.carpentersblocks.tileentity.TEBase;
import com.carpentersblocks.util.handler.DesignHandler;
import com.carpentersblocks.util.registry.FeatureRegistry;
import com.carpentersblocks.util.registry.ItemRegistry;
import com.github.lunatrius.schematica.client.world.SchematicWorld;

public final class CarpentersHammerSupport {

    private static final Slab SLAB = new Slab();
    private static final Ladder LADDER = new Ladder();
    private static final Torch TORCH = new Torch();
    private static final DaylightSensor SENSOR = new DaylightSensor();
    private static final PressurePlate PRESSURE_PLATE = new PressurePlate();
    private static final Button BUTTON = new Button();
    private static final Lever LEVER = new Lever();

    private static final BlockSettings NO_SETTINGS = new BlockSettings(Block.class);
    // Settings are ordered by the sequence in which the hammer should adjust them.
    private static final BlockSettings[] BLOCK_SETTINGS = {
        new BlockSettings(
            BlockCarpentersGarageDoor.class,
            cycle(GarageDoor.INSTANCE::getType, BlockCarpentersGarageDoor.type.length),
            toggle(GarageDoor.INSTANCE::getRigidity, Click.SNEAK_RIGHT)),
        new BlockSettings(
            BlockCarpentersBarrier.class,
            new Setting<>(Barrier::getType, CarpentersHammerSupport::fenceClick),
            toggle(Barrier::getPost, Click.MANUAL)),
        new BlockSettings(BlockCarpentersGate.class, new Setting<>(Gate::getType, CarpentersHammerSupport::fenceClick)),
        new BlockSettings(
            BlockCarpentersDoor.class,
            cycle(Hinge::getType, BlockCarpentersDoor.type.length),
            toggle(Hinge::getHinge, Click.MANUAL)),
        new BlockSettings(
            BlockCarpentersHatch.class,
            cycle(Hatch::getType, Hatch.TYPE_PANEL + 1),
            toggle(Hatch::getRigidity, Click.SNEAK_RIGHT),
            toggle(Hatch::getDir, Click.MANUAL)),
        new BlockSettings(BlockCarpentersLadder.class, cycle(LADDER::getType, BlockCarpentersLadder.type.length)),
        new BlockSettings(BlockCarpentersTorch.class, cycle(TORCH::getType, BlockCarpentersTorch.type.length)),
        new BlockSettings(BlockCarpentersBed.class, decoration("bed")),
        new BlockSettings(BlockCarpentersFlowerPot.class, decoration("flowerpot")),
        new BlockSettings(
            BlockCarpentersDaylightSensor.class,
            cycle(SENSOR::getSensitivity, 3),
            toggle(SENSOR::getPolarity, Click.MANUAL)),
        new BlockSettings(
            BlockCarpentersPressurePlate.class,
            cycle(PRESSURE_PLATE::getTriggerEntity, 4),
            toggle(PRESSURE_PLATE::getPolarity, Click.MANUAL)),
        new BlockSettings(BlockCarpentersButton.class, toggle(BUTTON::getPolarity, Click.MANUAL)),
        new BlockSettings(BlockCarpentersLever.class, toggle(LEVER::getPolarity, Click.MANUAL)),
        new BlockSettings(
            BlockCarpentersSafe.class,
            toggle(Safe::isLocked, Click.SNEAK_RIGHT),
            cycle(Safe::getAutoPerm, 4)) };

    private CarpentersHammerSupport() {}

    static boolean matchesSettings(Block block, TEBase expected, TEBase actual) {
        if (block instanceof BlockCarpentersTorch && TORCH.getDirection(actual) != TORCH.getDirection(expected)) {
            return false;
        }
        if (block instanceof BlockCarpentersLadder && LADDER.getDirection(actual) != LADDER.getDirection(expected)) {
            return false;
        }
        return settingsFor(block).findClick(expected, actual) == null;
    }

    public static ItemStack findHammer(ItemStack[] inventory, Predicate<ItemStack> available) {
        for (ItemStack stack : inventory) {
            if (stack != null && stack.stackSize > 0
                && stack.getItem() instanceof ICarpentersHammer
                && available.test(stack)) {
                return stack;
            }
        }
        if (ItemRegistry.itemCarpentersHammer != null) {
            final ItemStack hammer = new ItemStack(ItemRegistry.itemCarpentersHammer);
            if (available.test(hammer)) {
                return hammer;
            }
        }
        return null;
    }

    public static boolean canUseHammer(EntityPlayer player, World world) {
        final ItemStack stack = player.getHeldItem();
        return stack != null && stack.stackSize > 0
            && stack.getItem() instanceof ICarpentersHammer hammer
            && hammer.canUseHammer(world, player);
    }

    public static Adjustment findAdjustment(Block block, SchematicWorld schematic, TileEntity target,
        TileEntity placed) {
        if (!(target instanceof TEBase expected) || !(placed instanceof TEBase actual)) {
            return null;
        }

        final BlockSettings settings = settingsFor(block);
        Click click = settings.findClick(expected, actual);
        ForgeDirection face = ForgeDirection.UP;
        if (block instanceof BlockCarpentersBlock && expected.getData() != actual.getData()
            && validIndex(expected.getData(), Slab.SLAB_Z_POS + 1)
            && validIndex(actual.getData(), Slab.SLAB_Z_POS + 1)) {
            // A second click shapes a different half only after the first restores the full cube.
            face = SLAB.isFullCube(actual) ? SLAB.getDirection(expected)
                .getOpposite() : ForgeDirection.UP;
            click = Click.RIGHT;
        } else if (block instanceof BlockCarpentersStairs) {
            click = shapeClick(
                expected.getData(),
                actual.getData(),
                Stairs.stairsList.length,
                CarpentersHammerSupport::nextStairShape);
        } else if (block instanceof BlockCarpentersSlope) {
            click = canReshapeSlope(expected.getData(), actual.getData()) ? Click.RIGHT : null;
        }
        if (click == null || click == Click.MANUAL) {
            return null;
        }

        final Collection<TEBase> affected = affectedBlocks(block, actual, expected, click);
        if (affected.isEmpty()) {
            return null;
        }
        for (TEBase piece : affected) {
            final int x = piece.xCoord - schematic.position.x;
            final int y = piece.yCoord - schematic.position.y;
            final int z = piece.zCoord - schematic.position.z;
            if (schematic.getBlockIncludingHiddenLayers(x, y, z) != block
                || !(schematic.getTileEntity(x, y, z) instanceof TEBase saved)
                || settings.findClick(expected, saved) != null) {
                return null;
            }
        }
        return new Adjustment(click, face.ordinal(), affected, block, actual, settings);
    }

    private static Collection<TEBase> affectedBlocks(Block block, TEBase actual, TEBase expected, Click click) {
        final World world = actual.getWorldObj();
        if (CarpentersBlocksSupport.isGarageDoor(block)) {
            final GarageDoor door = GarageDoor.INSTANCE;
            if (door.getDirection(actual) != door.getDirection(expected) || door.getDirection(actual)
                .ordinal() < 2 || door.getDirection(actual) == ForgeDirection.UNKNOWN || !door.isVisible(actual)) {
                return Collections.emptyList();
            }
            TEBase activation = actual;
            if (door.isOpen(actual)) {
                // Carpenter's Blocks redirects both right-click paths to the bottom of an open door.
                activation = door.getBottommost(world, actual.xCoord, actual.yCoord, actual.zCoord);
                if (activation == null || door.getDirection(activation) != door.getDirection(actual)
                    || (click == Click.RIGHT ? door.getType(activation) != door.getType(actual)
                        : door.getRigidity(activation) != door.getRigidity(actual))) {
                    return Collections.emptyList();
                }
            }
            final Collection<TEBase> pieces = door.getBlocks(activation, block);
            // A capped client search can omit panels the server will change.
            return pieces.size() > FeatureRegistry.multiBlockSizeLimit || !pieces.contains(actual)
                ? Collections.emptyList()
                : pieces;
        }
        if (block instanceof BlockCarpentersBed) {
            final TEBase other = Bed.getOppositeTE(actual);
            return other == null ? Collections.emptyList() : Arrays.asList(actual, other);
        }
        if (block instanceof BlockCarpentersFlowerPot && actual.hasAttribute(TEBase.ATTR_COVER[6])) {
            // Turning the pot's design drops its existing base cover.
            return Collections.emptyList();
        }
        if (block instanceof BlockCarpentersDoor) {
            final int otherY = actual.yCoord + (Hinge.getPiece(actual) == Hinge.PIECE_TOP ? -1 : 1);
            if (world.getBlock(actual.xCoord, otherY, actual.zCoord) == block
                && world.getTileEntity(actual.xCoord, otherY, actual.zCoord) instanceof TEBase other) {
                return Arrays.asList(actual, other);
            }
            return Collections.emptyList();
        }
        return Collections.singletonList(actual);
    }

    private static BlockSettings settingsFor(Block block) {
        for (BlockSettings settings : BLOCK_SETTINGS) {
            if (settings.blockType.isInstance(block)) {
                return settings;
            }
        }
        return NO_SETTINGS;
    }

    private static <T> Setting<T> toggle(Function<TEBase, T> read, Click click) {
        return new Setting<>(read, (expected, actual) -> expected.equals(actual) ? null : click);
    }

    private static Setting<Integer> cycle(Function<TEBase, Integer> read, int variants) {
        return new Setting<>(read, (expected, actual) -> cycle(expected, actual, variants));
    }

    private static Setting<String> decoration(String designType) {
        return new Setting<>(TEBase::getDesign, (expected, actual) -> {
            if (expected.equals(actual)) {
                return null;
            }
            return expected.isEmpty() ? Click.SNEAK_RIGHT
                : DesignHandler.getListForType(designType)
                    .contains(expected) ? Click.RIGHT : null;
        });
    }

    private static Click fenceClick(int expected, int actual) {
        final Click type = cycle(expected, actual, Barrier.TYPE_WALL + 1);
        return type != null && expected <= Barrier.TYPE_VANILLA_X3 && actual <= Barrier.TYPE_VANILLA_X3
            ? Click.SNEAK_RIGHT
            : type;
    }

    private static Click cycle(int expected, int actual, int variants) {
        return toggle(
            expected != actual && validIndex(expected, variants) && validIndex(actual, variants),
            Click.RIGHT);
    }

    private static Click toggle(boolean different, Click click) {
        return different ? click : null;
    }

    private static boolean validIndex(int value, int size) {
        return value >= 0 && value < size;
    }

    private static int nextStairShape(int id) {
        return switch (id >> 2) {
            case 0 -> id + 8;
            case 1, 3 -> id + 12;
            case 2, 4, 6 -> id - 4;
            case 5 -> Stairs.ID_NORMAL_SE;
            default -> id;
        };
    }

    static boolean canReshapeStair(int expected, int actual) {
        return shapeClick(expected, actual, Stairs.stairsList.length, CarpentersHammerSupport::nextStairShape) != null;
    }

    static boolean canReshapeSlope(int expected, int actual) {
        return shapeClick(expected, actual, Slope.ID_PRISM_WEDGE_POS_E + 1, id -> {
            final Slope slope = Slope.getSlopeById(id);
            return slope.slopeType.getNextSlopeType(slope).slopeID;
        }) != null;
    }

    private static Click shapeClick(int expected, int actual, int variants, IntUnaryOperator reshape) {
        if (expected == actual || !validIndex(expected, variants) || !validIndex(actual, variants)) {
            return null;
        }
        int next = actual;
        for (int step = 0; step < variants; step++) {
            next = reshape.applyAsInt(next);
            if (next == expected) {
                return Click.RIGHT;
            }
            if (next == actual) {
                break;
            }
        }
        return null;
    }

    private static final class Setting<T> {

        private final Function<TEBase, T> read;
        private final BiFunction<T, T, Click> chooseClick;

        private Setting(Function<TEBase, T> read, BiFunction<T, T, Click> chooseClick) {
            this.read = read;
            this.chooseClick = chooseClick;
        }

        private Click findClick(TEBase expected, TEBase actual) {
            return this.chooseClick.apply(this.read.apply(expected), this.read.apply(actual));
        }
    }

    private static final class BlockSettings {

        private final Class<? extends Block> blockType;
        private final Setting<?>[] settings;

        private BlockSettings(Class<? extends Block> blockType, Setting<?>... settings) {
            this.blockType = blockType;
            this.settings = settings;
        }

        private Click findClick(TEBase expected, TEBase actual) {
            for (Setting<?> setting : this.settings) {
                final Click click = setting.findClick(expected, actual);
                if (click != null) {
                    return click;
                }
            }
            return null;
        }

        private boolean usesEntireDataValue() {
            // Shape IDs and barriers retain their existing raw-data acknowledgment.
            return this.settings.length == 0 || this.blockType == BlockCarpentersBarrier.class;
        }

        private Object[] snapshot(TEBase tile) {
            if (usesEntireDataValue()) {
                return new Object[] { tile.getData() };
            }
            final Object[] values = new Object[this.settings.length];
            for (int i = 0; i < values.length; i++) {
                values[i] = this.settings[i].read.apply(tile);
            }
            return values;
        }

        private boolean matches(TEBase tile, Object[] values) {
            if (usesEntireDataValue()) {
                return values[0].equals(tile.getData());
            }
            for (int i = 0; i < values.length; i++) {
                if (!values[i].equals(this.settings[i].read.apply(tile))) {
                    return false;
                }
            }
            return true;
        }
    }

    public enum Click {
        MANUAL,
        RIGHT,
        SNEAK_RIGHT
    }

    public static final class Adjustment {

        public final Click click;
        public final int face;
        public final Collection<? extends TileEntity> affected;

        private final Block block;
        private final TEBase clicked;
        private final BlockSettings settings;
        private final Object[] previousState;

        private Adjustment(Click click, int face, Collection<TEBase> affected, Block block, TEBase clicked,
            BlockSettings settings) {
            this.click = click;
            this.face = face;
            this.affected = affected;
            this.block = block;
            this.clicked = clicked;
            this.settings = settings;
            this.previousState = settings.snapshot(clicked);
        }

        public boolean isConfirmed(World world) {
            if (!(world.getTileEntity(
                this.clicked.xCoord,
                this.clicked.yCoord,
                this.clicked.zCoord) instanceof TEBase current)) {
                return false;
            }
            if (this.settings.matches(current, this.previousState)) {
                return false;
            }
            final Object[] state = this.settings.snapshot(current);
            for (TileEntity piece : this.affected) {
                if (world.getBlock(piece.xCoord, piece.yCoord, piece.zCoord) != this.block
                    || !(world.getTileEntity(piece.xCoord, piece.yCoord, piece.zCoord) instanceof TEBase updated)
                    || !this.settings.matches(updated, state)) {
                    return false;
                }
            }
            return true;
        }
    }
}

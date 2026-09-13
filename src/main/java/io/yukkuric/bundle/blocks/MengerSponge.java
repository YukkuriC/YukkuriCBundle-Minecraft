package io.yukkuric.bundle.blocks;

import io.yukkuric.bundle.blocks.be.MengerSpongeBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidUtil;

public class MengerSponge extends Block implements EntityBlock {
    public static final String ID = "menger_sponge";

    public static final VoxelShape SPONGE_SHAPE = buildSpongeShape();

    public MengerSponge() {
        super(
                BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_YELLOW)
                        .noOcclusion()
                        .pushReaction(PushReaction.BLOCK)
                        .sound(SoundType.SPONGE)
                        .explosionResistance(114514)
        );
    }

    private static VoxelShape buildSpongeShape() {
        VoxelShape holes = Shapes.or(
                Shapes.or(Block.box(0, 6, 6, 16, 10, 10),
                        Block.box(6, 0, 6, 10, 16, 10)),
                Block.box(6, 6, 0, 10, 10, 16));
        return Shapes.join(Shapes.block(), holes, BooleanOp.ONLY_FIRST).optimize();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SPONGE_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SPONGE_SHAPE;
    }

    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SPONGE_SHAPE;
    }

    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MengerSpongeBE(pos, state);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(level.getBlockEntity(pos) instanceof MengerSpongeBE be))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        var fluidItem = stack.getCapability(Capabilities.FluidHandler.ITEM);
        if (fluidItem != null) {
            var fluidCap = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
            var isInsert = fluidItem.getFluidInTank(0).isEmpty() && !be.getFluid().isEmpty();
            var moved = isInsert
                    ? FluidUtil.tryFluidTransfer(fluidItem, fluidCap, Integer.MAX_VALUE, true)
                    : FluidUtil.tryFluidTransfer(fluidCap, fluidItem, Integer.MAX_VALUE, true);
            if (!moved.isEmpty()) {
                player.setItemInHand(hand, fluidItem.getContainer());
                return ItemInteractionResult.CONSUME;
            }
        }
        if (be.getExemplar().isEmpty()) {
            var itemCap = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
            var remaining = itemCap.insertItem(0, stack, false);
            if (!player.getAbilities().instabuild) player.setItemInHand(hand, remaining);
            return ItemInteractionResult.CONSUME;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof MengerSpongeBE be)) return InteractionResult.PASS;
        if (!be.getExemplar().isEmpty()) {
            var itemCap = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
            var extracted = itemCap.extractItem(0, 3, false);
            if (!extracted.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, extracted);
                be.syncAndSave();
                return InteractionResult.CONSUME;
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof MengerSpongeBE be) {
            be.dropContents();
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}

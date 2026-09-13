package io.yukkuric.bundle.blocks;

import io.yukkuric.bundle.blocks.be.MengerSpongeBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.*;

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
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof MengerSpongeBE be) {
            be.dropContents();
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}

package io.yukkuric.bundle.blocks;

import io.yukkuric.bundle.blocks.be.MengerSpongeBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public class MengerSponge extends Block implements EntityBlock {
    public static final String ID = "menger_sponge";

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

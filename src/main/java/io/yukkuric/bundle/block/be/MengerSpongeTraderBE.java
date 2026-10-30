package io.yukkuric.bundle.block.be;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import static io.yukkuric.bundle.block.YCBlocks.BE_MENGER_SPONGE_TRADER;

public class MengerSpongeTraderBE extends BlockEntity {
    public MengerSpongeTraderBE(BlockPos pos, BlockState state) {
        super(BE_MENGER_SPONGE_TRADER.get(), pos, state);
    }
}

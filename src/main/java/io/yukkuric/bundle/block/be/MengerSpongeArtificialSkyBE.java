package io.yukkuric.bundle.block.be;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import static io.yukkuric.bundle.block.YCBlocks.BE_MENGER_SPONGE_ARTIFICIAL_SKY;

public class MengerSpongeArtificialSkyBE extends BlockEntity {
    public MengerSpongeArtificialSkyBE(BlockPos pos, BlockState state) {
        super(BE_MENGER_SPONGE_ARTIFICIAL_SKY.get(), pos, state);
    }
}

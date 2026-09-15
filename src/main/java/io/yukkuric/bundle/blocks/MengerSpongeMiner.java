package io.yukkuric.bundle.blocks;

import io.yukkuric.bundle.blocks.be.MengerSpongeMinerBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class MengerSpongeMiner extends AbstractMengerSponge<MengerSpongeMinerBE> {
    public static final String ID = "menger_sponge_miner";

    public MengerSpongeMiner() {
        super();
    }

    public MengerSpongeMinerBE newBlockEntity(BlockPos pos, BlockState state) {
        return new MengerSpongeMinerBE(pos, state);
    }
}

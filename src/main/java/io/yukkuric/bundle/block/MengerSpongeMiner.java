package io.yukkuric.bundle.block;

import io.yukkuric.bundle.block.be.MengerSpongeMinerBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class MengerSpongeMiner extends AbstractMengerSponge<MengerSpongeMinerBE> {
    public static final String ID = "menger_sponge/miner";

    public MengerSpongeMiner() {
        super();
    }

    public MengerSpongeMinerBE newBlockEntity(BlockPos pos, BlockState state) {
        return new MengerSpongeMinerBE(pos, state);
    }
}

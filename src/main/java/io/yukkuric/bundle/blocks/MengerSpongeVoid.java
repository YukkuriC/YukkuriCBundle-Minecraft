package io.yukkuric.bundle.blocks;

import io.yukkuric.bundle.blocks.be.MengerSpongeVoidBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class MengerSpongeVoid extends AbstractMengerSponge<MengerSpongeVoidBE> {
    public static final String ID = "menger_sponge_void";

    public MengerSpongeVoid() {
        super();
    }

    public MengerSpongeVoidBE newBlockEntity(BlockPos pos, BlockState state) {
        return new MengerSpongeVoidBE(pos, state);
    }
}

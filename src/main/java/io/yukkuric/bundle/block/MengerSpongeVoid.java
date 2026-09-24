package io.yukkuric.bundle.block;

import io.yukkuric.bundle.block.be.MengerSpongeVoidBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class MengerSpongeVoid extends AbstractMengerSponge<MengerSpongeVoidBE> {
    public static final String ID = "menger_sponge/void";

    public MengerSpongeVoid() {
        super();
    }

    public MengerSpongeVoidBE newBlockEntity(BlockPos pos, BlockState state) {
        return new MengerSpongeVoidBE(pos, state);
    }
}

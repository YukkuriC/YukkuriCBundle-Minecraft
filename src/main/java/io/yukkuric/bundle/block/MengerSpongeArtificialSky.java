package io.yukkuric.bundle.block;

import io.yukkuric.bundle.block.be.MengerSpongeArtificialSkyBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

public class MengerSpongeArtificialSky extends AbstractMengerSponge<MengerSpongeArtificialSkyBE> {
    public static final String ID = "menger_sponge/artificial_sky";

    public MengerSpongeArtificialSky() {
        super();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public MengerSpongeArtificialSkyBE newBlockEntity(BlockPos pos, BlockState state) {
        return new MengerSpongeArtificialSkyBE(pos, state);
    }
}

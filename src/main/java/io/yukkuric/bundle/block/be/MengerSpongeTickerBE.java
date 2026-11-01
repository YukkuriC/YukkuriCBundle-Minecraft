package io.yukkuric.bundle.block.be;

import io.yukkuric.bundle.block.MengerSpongeTicker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;

import static io.yukkuric.bundle.block.YCBlocks.BE_MENGER_SPONGE_TICKER;

public class MengerSpongeTickerBE extends BlockEntity {
    public static final int MAX_EXTRA_PER_TICK = 4096;

    public MengerSpongeTickerBE(BlockPos pos, BlockState state) {
        super(BE_MENGER_SPONGE_TICKER.get(), pos, state);
    }
    int extraCountThisTick = 0;
    int serverAgeThisTick = -1;

    void accumulateExtraTicking(int age, int extraCount) {
        if (age != serverAgeThisTick) {
            extraCountThisTick = 0;
            serverAgeThisTick = age;
        }
        extraCountThisTick = Math.min(MAX_EXTRA_PER_TICK, extraCountThisTick + extraCount);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, MengerSpongeTickerBE self) {
        if (!state.getValue(MengerSpongeTicker.LIT)) return;
        if (level.isClientSide) {
            return;
        }

        // count extra ticking
        var accMult = level.getBestNeighborSignal(pos) * (1 + self.extraCountThisTick);
        self.extraCountThisTick = 0;
        var targetPos = MengerSpongeTicker.getTargetPos(state, pos);

        // pick tickers
        BlockEntityTicker<BlockEntity> ticker = null;
        BlockState tickerR = null;
        var targetState = level.getBlockState(targetPos);
        if (targetState.isRandomlyTicking()) {
            tickerR = targetState;
        }
        var targetBE = level.getBlockEntity(targetPos);
        if (targetBE instanceof MengerSpongeTickerBE another) {
            another.accumulateExtraTicking(level.getServer().getTickCount(), accMult);
            return;
        } else if (targetBE != null) {
            ticker = (BlockEntityTicker<BlockEntity>) targetState.getTicker(level, targetBE.getType());
        }
        if (ticker == null && tickerR == null) return;

        while (accMult > 0) {
            accMult--;

            if (ticker != null) ticker.tick(level, targetPos, targetState, targetBE);
            if (tickerR != null) tickerR.randomTick((ServerLevel) level, targetPos, level.random);
        }
    }
}

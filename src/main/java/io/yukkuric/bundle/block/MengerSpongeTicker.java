package io.yukkuric.bundle.block;

import io.yukkuric.bundle.block.be.MengerSpongeTickerBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

public class MengerSpongeTicker extends AbstractMengerSponge<MengerSpongeTickerBE> {
    public static final String ID = "menger_sponge/ticker";
    public static final int DIST_MIN = 1;
    public static final int DIST_MAX = 5;
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final IntegerProperty DIST = IntegerProperty.create("dist", DIST_MIN, DIST_MAX);

    public MengerSpongeTicker() {
        super();
    }

    /** 被加速方块坐标 */
    public static BlockPos getTargetPos(BlockState state, BlockPos pos) {
        return pos.relative(state.getValue(FACING), state.getValue(DIST));
    }

    public MengerSpongeTickerBE newBlockEntity(BlockPos pos, BlockState state) {
        return new MengerSpongeTickerBE(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT, DIST);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(LIT, context.getLevel().hasNeighborSignal(context.getClickedPos()))
                .setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        int next = state.getValue(DIST) + (player.isShiftKeyDown() ? -1 : 1);
        if (next > DIST_MAX) next = DIST_MIN;
        else if (next < DIST_MIN) next = DIST_MAX;
        level.setBlock(pos, state.setValue(DIST, next), 3);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (level.isClientSide) return;
        boolean lit = level.hasNeighborSignal(pos);
        if (lit != state.getValue(LIT)) level.setBlock(pos, state.setValue(LIT, lit), 3);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return (lvl, pos, st, be) -> MengerSpongeTickerBE.tick(lvl, pos, st, (MengerSpongeTickerBE) be);
    }
}

package io.yukkuric.bundle.blocks;

import io.yukkuric.bundle.blocks.be.MengerSpongeBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidUtil;

public class MengerSponge extends AbstractMengerSponge<MengerSpongeBE> {
    public static final String ID = "menger_sponge";

    public MengerSponge() {
        super();
    }

    public MengerSpongeBE newBlockEntity(BlockPos pos, BlockState state) {
        return new MengerSpongeBE(pos, state);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(level.getBlockEntity(pos) instanceof MengerSpongeBE be))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        var fluidItem = stack.getCapability(Capabilities.FluidHandler.ITEM);
        if (fluidItem != null) {
            var fluidCap = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
            var isInsert = fluidItem.getFluidInTank(0).isEmpty() && !be.getFluid().isEmpty();
            var moved = isInsert
                    ? FluidUtil.tryFluidTransfer(fluidItem, fluidCap, Integer.MAX_VALUE, true)
                    : FluidUtil.tryFluidTransfer(fluidCap, fluidItem, Integer.MAX_VALUE, true);
            if (!moved.isEmpty()) {
                player.setItemInHand(hand, fluidItem.getContainer());
                return ItemInteractionResult.CONSUME;
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof MengerSpongeBE be) {
            be.dropContents();
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}

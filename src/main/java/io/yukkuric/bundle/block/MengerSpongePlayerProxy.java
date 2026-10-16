package io.yukkuric.bundle.block;

import io.yukkuric.bundle.block.be.MengerSpongePlayerProxyBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class MengerSpongePlayerProxy extends AbstractMengerSponge<MengerSpongePlayerProxyBE> {
    public static final String ID = "menger_sponge/player_proxy";

    public MengerSpongePlayerProxy() {
        super();
    }

    public MengerSpongePlayerProxyBE newBlockEntity(BlockPos pos, BlockState state) {
        return new MengerSpongePlayerProxyBE(pos, state);
    }

    /** 记录放置者，作为该海绵代理的目标玩家 */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof MengerSpongePlayerProxyBE be) {
            be.setPlayerId(player.getUUID());
        }
    }
}

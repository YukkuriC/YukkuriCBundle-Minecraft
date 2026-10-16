package io.yukkuric.bundle.block.be;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.*;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.IntStream;

import static io.yukkuric.bundle.block.YCBlocks.BE_MENGER_SPONGE_PLAYER_PROXY;

@EventBusSubscriber
public class MengerSpongePlayerProxyBE extends AbstractMengerSpongeDataBE {
    @Nullable
    private UUID playerId;
    private final WeakHashMap<Player, IItemHandler> _cacheItemCap = new WeakHashMap<>();
    public final EnergyCap energyCap = new EnergyCap(this);
    public MengerSpongePlayerProxyBE(BlockPos pos, BlockState state) {
        super(BE_MENGER_SPONGE_PLAYER_PROXY.get(), pos, state);
    }
    @Override
    protected void loadCustomData(CompoundTag nbt, HolderLookup.Provider provider) {
        playerId = nbt.hasUUID("Player") ? nbt.getUUID("Player") : null;
    }
    @Override
    protected void saveCustomData(CompoundTag nbt, HolderLookup.Provider provider) {
        if (playerId != null) nbt.putUUID("Player", playerId);
    }

    public void setPlayerId(UUID id) {
        playerId = id;
        syncAndSave();
    }
    /** 服务端查 server player list；客户端目前只认自己 */
    @Nullable
    public Player getPlayer() {
        if (playerId == null || level == null) return null;
        if (level.isClientSide) {
            var local = Minecraft.getInstance().player;
            // TODO: 本地已知其它玩家
            return local != null && local.getUUID().equals(playerId) ? local : null;
        }
        var server = level.getServer();
        return server == null ? null : server.getPlayerList().getPlayer(playerId);
    }

    //#region forge cap
    //#region item
    /** 目标玩家在线时直接代理其物品栏，可自由进出；否则为空（禁用交互） */
    public IItemHandler getItemCap() {
        return _cacheItemCap.computeIfAbsent(getPlayer(), player -> {
            if (player == null)
                return EmptyItemHandler.INSTANCE;
            return new PlayerMainInvWrapper(player.getInventory());
        });
    }
    //#endregion

    //#region energy
    public static class EnergyCap implements IEnergyStorage {
        public final MengerSpongePlayerProxyBE be;
        private final WeakHashMap<Player, IItemHandler> _cacheItemCap = new WeakHashMap<>();
        public EnergyCap(MengerSpongePlayerProxyBE be) {
            this.be = be;
        }

        /** 主手（当前选中快捷栏）+ 护甲 + 副手所在槽位 */
        private static IntStream focusedSlots(Player player) {
            var inv = player.getInventory();
            var armorStart = inv.items.size();
            return IntStream.concat(IntStream.of(inv.selected, Inventory.SLOT_OFFHAND),
                    IntStream.range(armorStart, armorStart + inv.armor.size()));
        }

        /** 有效范围：目标玩家物品栏中带物品级 energy cap 的物品，玩家无效时为空集 */
        private List<IEnergyStorage> getRange() {
            var player = be.getPlayer();
            if (player == null) return List.of();
            var itemCap = _cacheItemCap.computeIfAbsent(player, p -> new PlayerInvWrapper(p.getInventory()));
            var server = be.level.getServer();
            var slots = server != null && server.getTickCount() % 10 == 0
                    ? IntStream.range(0, itemCap.getSlots())
                    : focusedSlots(player);
            return slots.mapToObj(slot -> itemCap.getStackInSlot(slot).getCapability(Capabilities.EnergyStorage.ITEM))
                    .filter(Objects::nonNull).toList();
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            var left = maxReceive;
            for (var cap : getRange()) {
                if (left <= 0) break;
                left -= cap.receiveEnergy(left, simulate);
            }
            return maxReceive - left;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            var left = maxExtract;
            for (var cap : getRange()) {
                if (left <= 0) break;
                left -= cap.extractEnergy(left, simulate);
            }
            return maxExtract - left;
        }

        @Override
        public int getEnergyStored() {
            var total = 0L;
            for (var cap : getRange())
                total += cap.getEnergyStored();
            return (int) Math.min(total, Integer.MAX_VALUE);
        }

        @Override
        public int getMaxEnergyStored() {
            var total = 0L;
            for (var cap : getRange())
                total += cap.getMaxEnergyStored();
            return (int) Math.min(total, Integer.MAX_VALUE);
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
    //#endregion
    //#endregion

    @SubscribeEvent
    public static void registerCap(RegisterCapabilitiesEvent event) {
        var type = BE_MENGER_SPONGE_PLAYER_PROXY.get();
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, (be, side) -> be.getItemCap());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type, (be, side) -> be.energyCap);
    }
}

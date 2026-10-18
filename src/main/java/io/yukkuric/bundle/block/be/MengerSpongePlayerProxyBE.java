package io.yukkuric.bundle.block.be;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.*;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.stream.IntStream;

import static io.yukkuric.bundle.block.YCBlocks.BE_MENGER_SPONGE_PLAYER_PROXY;

@EventBusSubscriber
public class MengerSpongePlayerProxyBE extends AbstractMengerSpongeDataBE {
    @Nullable
    private UUID playerId;
    /** 缓存的目标玩家实体，实体更换（上线/掉线/换维度）时重置 */
    private WeakReference<Player> playerRef = new WeakReference<>(null);
    private final ItemCap itemCap = new ItemCap(this);
    public final EnergyCap energyCap = new EnergyCap(this);
    private final PlayerEventListener listener = new PlayerEventListener(this);

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

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (!level.isClientSide) NeoForge.EVENT_BUS.register(listener);
    }
    @Override
    public void setRemoved() {
        super.setRemoved();
        NeoForge.EVENT_BUS.unregister(listener);
    }

    /** 服务端查 server player list；客户端目前只认自己 */
    @Nullable
    public Player getPlayer() {
        if (playerId == null || level == null) return null;
        var current = resolvePlayer();
        setPlayer(current);
        return current;
    }

    /** 按 UUID 取目标玩家当前实体；客户端目前只认自己 */
    @Nullable
    private Player resolvePlayer() {
        if (level.isClientSide) {
            var local = Minecraft.getInstance().player;
            // TODO: 本地已知其它玩家
            return local != null && local.getUUID().equals(playerId) ? local : null;
        }
        var server = level.getServer();
        return server == null ? null : server.getPlayerList().getPlayer(playerId);
    }

    /** 缓存当前玩家值；实体更换时主动释放 cap 缓存的旧实体 */
    private void setPlayer(@Nullable Player player) {
        if (player == playerRef.get()) return;
        itemCap.invalidate();
        energyCap.invalidate();
        playerRef = new WeakReference<>(player);
    }

    /** 目标玩家掉线/换维度后主动重置 */
    private void resetIfTarget(Player player) {
        if (playerId != null && playerId.equals(player.getUUID())) setPlayer(null);
    }

    /**
     * 监听目标玩家掉线/换维度，主动释放 cap 缓存的旧实体。
     * 独立成类：@EventBusSubscriber 要求其类内的 @SubscribeEvent 方法必须为 static
     */
    private static class PlayerEventListener {
        private final MengerSpongePlayerProxyBE be;
        private PlayerEventListener(MengerSpongePlayerProxyBE be) {
            this.be = be;
        }

        @SubscribeEvent
        public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            be.resetIfTarget(event.getEntity());
        }

        @SubscribeEvent
        public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            be.resetIfTarget(event.getEntity());
        }
    }

    //#region forge cap
    //#region item
    /** 目标玩家在线时直接代理其主物品栏，可自由进出；否则为空（禁用交互） */
    public IItemHandler getItemCap() {
        return itemCap;
    }

    /** 惰性缓存当前玩家的主物品栏包装器，玩家实体更换时主动释放 */
    private static class ItemCap implements IItemHandler {
        private final MengerSpongePlayerProxyBE be;
        private IItemHandler cached;

        private ItemCap(MengerSpongePlayerProxyBE be) {
            this.be = be;
        }

        /** 释放缓存的包装器，玩家实体更换时调用 */
        void invalidate() {
            cached = null;
        }

        /** 目标玩家在线时其主物品栏包装器，否则为 null（禁用交互） */
        @Nullable
        private IItemHandler resolve() {
            var player = be.getPlayer();
            if (player == null) return null;
            if (cached == null) cached = new PlayerMainInvWrapper(player.getInventory());
            return cached;
        }

        @Override
        public int getSlots() {
            var inv = resolve();
            return inv == null ? 0 : inv.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            var inv = resolve();
            return inv == null ? ItemStack.EMPTY : inv.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            var inv = resolve();
            return inv == null ? stack : inv.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            var inv = resolve();
            return inv == null ? ItemStack.EMPTY : inv.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            var inv = resolve();
            return inv == null ? 1 : inv.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            var inv = resolve();
            return inv != null && inv.isItemValid(slot, stack);
        }
    }
    //#endregion

    //#region energy
    public static class EnergyCap implements IEnergyStorage {
        public final MengerSpongePlayerProxyBE be;
        /** 惰性缓存的整物品栏包装器，玩家实体更换时主动释放 */
        private IItemHandler cached;

        public EnergyCap(MengerSpongePlayerProxyBE be) {
            this.be = be;
        }

        /** 释放缓存的包装器，玩家实体更换时调用 */
        void invalidate() {
            cached = null;
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
            if (cached == null) cached = new PlayerInvWrapper(player.getInventory());
            var server = be.level.getServer();
            var slots = server != null && server.getTickCount() % 10 == 0
                    ? IntStream.range(0, cached.getSlots())
                    : focusedSlots(player);
            return slots.mapToObj(slot -> cached.getStackInSlot(slot).getCapability(Capabilities.EnergyStorage.ITEM))
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

    @SubscribeEvent
    public static void registerCap(RegisterCapabilitiesEvent event) {
        var type = BE_MENGER_SPONGE_PLAYER_PROXY.get();
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, (be, side) -> be.getItemCap());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type, (be, side) -> be.energyCap);
    }
    //#endregion
}

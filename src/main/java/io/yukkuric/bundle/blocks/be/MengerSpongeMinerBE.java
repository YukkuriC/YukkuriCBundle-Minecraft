package io.yukkuric.bundle.blocks.be;

import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

import static io.yukkuric.bundle.YukkuriCBundleMod.modLoc;
import static io.yukkuric.bundle.blocks.YCBlocks.BE_MENGER_SPONGE_MINER;

@EventBusSubscriber
public class MengerSpongeMinerBE extends AbstractMengerSpongeDataBE {
    public MengerSpongeMinerBE(BlockPos pos, BlockState state) {
        super(BE_MENGER_SPONGE_MINER.get(), pos, state);
    }
    public final ItemCap itemCap = new ItemCap(this);
    @Override
    protected void loadCustomData(CompoundTag nbt, HolderLookup.Provider provider) {
        itemCap.deserializeNBT(provider, nbt.getCompound("Items"));
    }
    @Override
    protected void saveCustomData(CompoundTag nbt, HolderLookup.Provider provider) {
        nbt.put("Items", itemCap.serializeNBT(provider));
    }

    //#region forge cap
    private static final TagKey<Block> TAG_ORES = TagKey.create(Registries.BLOCK, modLoc("ore_targets"));
    private static List<ItemStack> ORE_BLOCKS = null;
    public static List<ItemStack> getOreBlocks() {
        if (ORE_BLOCKS == null) {
            var blockSet = BuiltInRegistries.BLOCK.getOrCreateTag(TAG_ORES);
            var ret = new ArrayList<ItemStack>();
            for (var b : blockSet) {
                var stack = b.value().asItem().getDefaultInstance();
                stack = stack.copyWithCount(1_000_000_000);
                ret.add(stack);
            }
            ORE_BLOCKS = ImmutableList.copyOf(ret);
        }
        return ORE_BLOCKS;
    }

    //#region item
    public static class ItemCap extends ItemStackHandler {
        private final MengerSpongeMinerBE be;
        private final List<ItemStack> stacksSrc = getOreBlocks();
        private static final int RAND_RANGE = 9;
        private int offsetStep = 0;

        public ItemCap(MengerSpongeMinerBE be) {
            super(RAND_RANGE);
            this.be = be;
        }

        private int offsetSlot(int slot) {
            return (slot + offsetStep) % RAND_RANGE;
        }
        private ItemStack ensureStack(int slot) {
            var stack = stacks.get(slot);
            if (be.level == null || be.level.isClientSide) return stack;
            if (stack.isEmpty()) {
                stack = stacksSrc.get((int) (Math.random() * stacksSrc.size()));
                stack = stack.copyWithCount(stack.getMaxStackSize());
                stacks.set(slot, stack);
            }
            return stack;
        }

        @Override
        public int getSlots() {
            return super.getSlots() + stacksSrc.size();
        }
        @Override
        public ItemStack getStackInSlot(int slot) {
            return getStackInSlot(slot, true);
        }
        public ItemStack getStackInSlot(int slot, boolean doOffset) {
            if (slot < 0) return ItemStack.EMPTY;
            if (slot >= RAND_RANGE) return stacksSrc.get((slot - RAND_RANGE) % stacksSrc.size()).copy();
            if (doOffset) slot = offsetSlot(slot);
            return ensureStack(slot);
        }
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0) return ItemStack.EMPTY;
            if (slot >= RAND_RANGE) return stacksSrc.get((slot - RAND_RANGE) % stacksSrc.size()).copyWithCount(amount);
            slot = offsetSlot(slot);

            var ret = ensureStack(slot);
            if (!simulate) {
                stacks.set(slot, ItemStack.EMPTY);
                offsetStep = (offsetStep + 1) % RAND_RANGE;
                ensureStack(slot);
                onContentsChanged(slot);
            }
            return ret;
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            if (slot < 0 || slot >= RAND_RANGE) return;
            super.setStackInSlot(slot, stack);
        }
        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot < 0 || slot >= RAND_RANGE) return stack;
            return super.insertItem(slot, stack, simulate);
        }
        @Override
        protected void onContentsChanged(int slot) {
            be.syncAndSave();
        }
    }
    //#endregion

    @SubscribeEvent
    public static void registerCap(RegisterCapabilitiesEvent event) {
        var type = BE_MENGER_SPONGE_MINER.get();
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, (be, side) -> be.itemCap);
    }
    //#endregion
}
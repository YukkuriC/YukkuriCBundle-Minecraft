package io.yukkuric.bundle.block.be;

import io.yukkuric.bundle.block.be.cap.InfiniteInventory;
import io.yukkuric.bundle.block.be.part.TaggedItemsProvider;
import io.yukkuric.bundle.tag.YCTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import static io.yukkuric.bundle.block.YCBlocks.BE_MENGER_SPONGE_MINER;

@EventBusSubscriber
public class MengerSpongeMinerBE extends AbstractMengerSpongeDataBE {
    public MengerSpongeMinerBE(BlockPos pos, BlockState state) {
        super(BE_MENGER_SPONGE_MINER.get(), pos, state);
    }
    public static final int RAND_RANGE = 9;
    public static final TaggedItemsProvider ITEMS = new TaggedItemsProvider(YCTags.Blocks.OreTargets, YCTags.Items.OreTargets);
    public final InfiniteInventory itemCap = new InfiniteInventory(this, RAND_RANGE, ITEMS.get(), ITEMS::contains);
    @Override
    protected void loadCustomData(CompoundTag nbt, HolderLookup.Provider provider) {
        itemCap.deserializeNBT(provider, nbt.getCompound("Items"));
    }
    @Override
    protected void saveCustomData(CompoundTag nbt, HolderLookup.Provider provider) {
        nbt.put("Items", itemCap.serializeNBT(provider));
    }

    @SubscribeEvent
    public static void registerCap(RegisterCapabilitiesEvent event) {
        var type = BE_MENGER_SPONGE_MINER.get();
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, (be, side) -> be.itemCap);
    }
}
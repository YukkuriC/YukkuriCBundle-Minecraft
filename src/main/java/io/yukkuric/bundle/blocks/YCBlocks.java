package io.yukkuric.bundle.blocks;

import com.google.common.collect.ImmutableSet;
import io.yukkuric.bundle.blocks.be.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Arrays;
import java.util.function.Supplier;

import static io.yukkuric.bundle.YukkuriCBundleMod.MOD_ID;

public class YCBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items BLOCK_ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BE_TYPES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MOD_ID);
    private static <B extends Block> DeferredBlock<B> build(String name, Supplier<B> func) {
        var ret = BLOCKS.register(name, func);
        BLOCK_ITEMS.registerSimpleBlockItem(name, ret);
        return ret;
    }
    private static <B extends BlockEntity> Supplier<BlockEntityType<B>> buildBE(String name, BlockEntityType.BlockEntitySupplier<B> getter, Supplier<Block>... blockGetters) {
        return BE_TYPES.register(name, () -> {
            var targets = ImmutableSet.copyOf(Arrays.stream(blockGetters).map(Supplier::get).toList());
            return new BlockEntityType(getter, targets, null);
        });
    }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        BLOCK_ITEMS.register(bus);
        BE_TYPES.register(bus);
    }

    // blocks
    public static final DeferredBlock<MengerSponge> MENGER_SPONGE = build(MengerSponge.ID, MengerSponge::new);
    public static final DeferredBlock<MengerSpongeVoid> MENGER_SPONGE_VOID = build(MengerSpongeVoid.ID, MengerSpongeVoid::new);
    public static final DeferredBlock<MengerSpongeMiner> MENGER_SPONGE_MINER = build(MengerSpongeMiner.ID, MengerSpongeMiner::new);

    // BE
    public static final Supplier<BlockEntityType<MengerSpongeBE>> BE_MENGER_SPONGE = buildBE(MengerSponge.ID, MengerSpongeBE::new, YCBlocks.MENGER_SPONGE::get);
    public static final Supplier<BlockEntityType<MengerSpongeVoidBE>> BE_MENGER_SPONGE_VOID = buildBE(MengerSpongeVoid.ID, MengerSpongeVoidBE::new, YCBlocks.MENGER_SPONGE_VOID::get);
    public static final Supplier<BlockEntityType<MengerSpongeMinerBE>> BE_MENGER_SPONGE_MINER = buildBE(MengerSpongeMiner.ID, MengerSpongeMinerBE::new, YCBlocks.MENGER_SPONGE_MINER::get);
}

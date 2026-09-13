package io.yukkuric.bundle.blocks;

import io.yukkuric.bundle.blocks.be.MengerSpongeBE;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

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
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        BLOCK_ITEMS.register(bus);
        BE_TYPES.register(bus);
    }

    // blocks
    public static final DeferredBlock<MengerSponge> MENGER_SPONGE = build(MengerSponge.ID, MengerSponge::new);

    // BE
    public static final DeferredHolder<BlockEntityType<?>, ?> BE_MENGER_SPONGE = BE_TYPES.register(MengerSponge.ID, () -> {
        var sponge = YCBlocks.MENGER_SPONGE.get();
        return BlockEntityType.Builder.of(MengerSpongeBE::new, sponge).build(null);
    });
}

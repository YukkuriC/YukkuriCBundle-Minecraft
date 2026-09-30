package io.yukkuric.bundle.datagen.menger_sponge;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;

import java.util.Set;

public class MengerSpongeLootTables extends BlockLootSubProvider {
    public MengerSpongeLootTables(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        MengerSpongeConsts.getAllMengerSponges().forEach(sponge -> dropSelf(sponge.get()));
    }

    /** 仅生成门格海绵自身的战利品表，避免遍历全部方块 */
    @Override
    protected Iterable<Block> getKnownBlocks() {
        return MengerSpongeConsts.getAllMengerSponges().stream().map(h -> (Block) h.get()).toList();
    }
}

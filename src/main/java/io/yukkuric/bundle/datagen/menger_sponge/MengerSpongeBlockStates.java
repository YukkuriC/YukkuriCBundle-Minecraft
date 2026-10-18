package io.yukkuric.bundle.datagen.menger_sponge;

import io.yukkuric.bundle.YukkuriCBundleMod;
import io.yukkuric.bundle.block.MengerSpongeTicker;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class MengerSpongeBlockStates extends BlockStateProvider {
    public MengerSpongeBlockStates(PackOutput output, ExistingFileHelper efh) {
        super(output, YukkuriCBundleMod.MOD_ID, efh);
    }

    @Override
    protected void registerStatesAndModels() {
        MengerSpongeConsts.getAllMengerSponges().forEach(sponge -> {
            if (sponge.get() instanceof MengerSpongeTicker ticker) addTickerBlockState(ticker);
            else addBlockState(sponge.get());
        });
    }

    /** 生成 blockstate：单模型引用 {@code block/<注册路径>} */
    private void addBlockState(Block block) {
        var id = BuiltInRegistries.BLOCK.getKey(block);
        simpleBlock(block, models().getExistingFile(modLoc("block/" + id.getPath())));
    }

    /** 生成 blockstate：模型轴对称，FACING/DIST 不影响外观，仅按 LIT 切换点亮模型 */
    private void addTickerBlockState(MengerSpongeTicker block) {
        var plain = models().getExistingFile(modLoc("block/" + MengerSpongeTicker.ID));
        var lit = models().getExistingFile(modLoc("block/" + MengerSpongeTicker.ID + "_lit"));
        getVariantBuilder(block)
                .partialState().with(MengerSpongeTicker.LIT, false).modelForState().modelFile(plain).addModel()
                .partialState().with(MengerSpongeTicker.LIT, true).modelForState().modelFile(lit).addModel();
    }
}
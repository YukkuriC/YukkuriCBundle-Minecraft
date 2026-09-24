package io.yukkuric.bundle.datagen.menger_sponge;

import io.yukkuric.bundle.YukkuriCBundleMod;
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
        MengerSpongeConsts.SPONGES.forEach(sponge -> addBlockState(sponge.get()));
    }

    /** 生成 blockstate：单模型引用 {@code block/<注册路径>} */
    private void addBlockState(Block block) {
        var id = BuiltInRegistries.BLOCK.getKey(block);
        simpleBlock(block, models().getExistingFile(modLoc("block/" + id.getPath())));
    }
}
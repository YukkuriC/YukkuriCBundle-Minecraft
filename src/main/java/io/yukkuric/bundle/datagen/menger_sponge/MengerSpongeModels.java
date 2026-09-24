package io.yukkuric.bundle.datagen.menger_sponge;

import io.yukkuric.bundle.YukkuriCBundleMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class MengerSpongeModels extends ItemModelProvider {
    public MengerSpongeModels(PackOutput output, ExistingFileHelper efh) {
        super(output, YukkuriCBundleMod.MOD_ID, efh);
    }

    @Override
    protected void registerModels() {
        // block model 需先于 item model 生成，item model 以 block model 为父模型
        for (var sponge : MengerSpongeConsts.SPONGES) {
            addBlockModel(sponge.get());
            addItemModel(sponge.get());
        }
    }

    /** 生成 block model：以基础模型为父，仅替换贴图；基础方块自身不生成 */
    private void addBlockModel(Block block) {
        var id = BuiltInRegistries.BLOCK.getKey(block);
        if (id.getPath().equals(MengerSpongeConsts.BASE_BLOCK_ID)) return;
        var texture = modLoc("block/" + id.getPath());
        withExistingParent("block/" + id.getPath(), modLoc("block/" + MengerSpongeConsts.BASE_BLOCK_ID))
                .texture("0", texture)
                .texture("particle", texture);
    }

    /** 生成 item model：以同名 block model 为父 */
    private void addItemModel(Block block) {
        var id = BuiltInRegistries.BLOCK.getKey(block);
        withExistingParent("item/" + id.getPath(), modLoc("block/" + id.getPath()));
    }
}
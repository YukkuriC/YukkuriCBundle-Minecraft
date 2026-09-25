package io.yukkuric.bundle.datagen.damage;

import io.yukkuric.bundle.YukkuriCBundleMod;
import io.yukkuric.bundle.damage.YCDamageTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.DamageTypeTagsProvider;
import net.minecraft.tags.DamageTypeTags;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

public class YCDamageTypeTags extends DamageTypeTagsProvider {
    public YCDamageTypeTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookups, ExistingFileHelper efh) {
        super(output, lookups, YukkuriCBundleMod.MOD_ID, efh);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        // 魔法飞弹：无视护甲、无视无敌帧（bypasses_cooldown）
        tag(DamageTypeTags.BYPASSES_ARMOR).add(YCDamageTypes.MAGIC_MISSILE.getKey());
        tag(DamageTypeTags.BYPASSES_COOLDOWN).add(YCDamageTypes.MAGIC_MISSILE.getKey());
    }
}
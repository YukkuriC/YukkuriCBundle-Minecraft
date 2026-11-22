package io.yukkuric.bundle.block.be;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

import static io.yukkuric.bundle.block.YCBlocks.BE_MENGER_SPONGE_TRADER;

public class MengerSpongeTraderBE extends AbstractMengerSpongeDataBE {
    private static final String KEY_PROFESSION = "Profession";
    private static final String KEY_TYPE = "Type";

    /** 流浪商人标记：刷新时上方无职业方块，渲染与交易均按流浪商人处理 */
    public static final ResourceLocation WANDERING_PROFESSION = BuiltInRegistries.VILLAGER_PROFESSION.getKey(VillagerProfession.NONE);

    /** 上方方块变化时清空，渲染普通村民头，下次右键重新生成 */
    @Nullable
    private ResourceLocation professionId;
    /** 村民类型，决定头部皮肤 */
    private ResourceLocation typeId = BuiltInRegistries.VILLAGER_TYPE.getKey(VillagerType.PLAINS);

    public MengerSpongeTraderBE(BlockPos pos, BlockState state) {
        super(BE_MENGER_SPONGE_TRADER.get(), pos, state);
    }

    @Override
    protected void loadCustomData(CompoundTag nbt, HolderLookup.Provider provider) {
        professionId = nbt.contains(KEY_PROFESSION) ? ResourceLocation.parse(nbt.getString(KEY_PROFESSION)) : null;
        if (nbt.contains(KEY_TYPE)) typeId = ResourceLocation.parse(nbt.getString(KEY_TYPE));
    }

    @Override
    protected void saveCustomData(CompoundTag nbt, HolderLookup.Provider provider) {
        if (professionId != null) nbt.putString(KEY_PROFESSION, professionId.toString());
        nbt.putString(KEY_TYPE, typeId.toString());
    }

    public void clearProfession() {
        professionId = null;
        syncAndSave();
    }

    @Nullable
    public ResourceLocation getProfessionId() {
        return professionId;
    }

    public ResourceLocation getTypeId() {
        return typeId;
    }

    /** 交易用职业；流浪商人标记返回 null */
    @Nullable
    public VillagerProfession getProfession() {
        var profession = BuiltInRegistries.VILLAGER_PROFESSION.get(professionId);
        return profession == VillagerProfession.NONE ? null : profession;
    }

    /** 按上方方块重新生成职业数据并同步到客户端 */
    public void refresh() {
        var poi = PoiTypes.forState(level.getBlockState(worldPosition.above()));
        var profession = poi.map(MengerSpongeTraderBE::findProfession).orElse(VillagerProfession.NONE);
        professionId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
        typeId = BuiltInRegistries.VILLAGER_TYPE.getKey(VillagerType.byBiome(level.getBiome(worldPosition)));
        syncAndSave();
    }

    /** 取 POI 对应的职业，无匹配职业时为 null */
    @Nullable
    private static VillagerProfession findProfession(Holder<PoiType> poi) {
        for (var profession : BuiltInRegistries.VILLAGER_PROFESSION)
            if (profession.heldJobSite().test(poi)) return profession;
        return null;
    }
}

package io.yukkuric.bundle.interop;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;

/** 与 unbound 的联动：其职业交易由数据包定义，交给它按 unbound:&lt;职业&gt;/level_&lt;等级&gt; 自行生成 */
public final class UnboundTrades {
    public static final String MOD_ID = "unbound";
    private static final String TRADES_CLASS = "me.moltea.unbound.mc.trade.Trades";

    private static Method addMethod;

    private UnboundTrades() {
    }

    /** 按倍率重复生成该职业各等级的完整交易 */
    public static void addOffers(Level level, Entity entity, MerchantOffers offers, VillagerProfession profession, int multiplier) {
        try {
            if (addMethod == null)
                addMethod = Class.forName(TRADES_CLASS)
                        .getMethod("add", ServerLevel.class, Entity.class, MerchantOffers.class, ResourceLocation.class);
            var serverLevel = (ServerLevel) level;
            var id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
            for (int i = 0; i < multiplier; i++)
                for (int lvl = VillagerData.MIN_VILLAGER_LEVEL; lvl <= VillagerData.MAX_VILLAGER_LEVEL; lvl++)
                    addMethod.invoke(null, serverLevel, entity, offers,
                            ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "/level_" + lvl));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}

package io.yukkuric.bundle.block;

import io.yukkuric.bundle.block.be.MengerSpongeTraderBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class MengerSpongeTrader extends AbstractMengerSponge<MengerSpongeTraderBE> {
    public static final String ID = "menger_sponge/trader";
    /** 交易列表相对正常长度的倍数 */
    public static final int TRADE_MULTIPLIER = 3;

    /** 村民每升一级新增的交易数量 */
    private static final int TRADES_PER_LEVEL = 2;
    /** 流浪商人每批货物中的常规交易数量 */
    private static final int WANDERING_TRADER_TRADES_PER_LEVEL = 5;

    public MengerSpongeTrader() {
        super();
    }

    @Override
    public MengerSpongeTraderBE newBlockEntity(BlockPos pos, BlockState state) {
        return new MengerSpongeTraderBE(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        var profession = findProfession(level, pos.above());
        var merchant = new TradingMerchant(level, profession, pos);
        if (!merchant.getOffers().isEmpty()) {
            merchant.setTradingPlayer(player);
            merchant.openTradingScreen(player, getTradeTitle(profession), merchant.getMerchantLevel());
        }
        return InteractionResult.SUCCESS;
    }

    /** 取上方方块的职业方块对应职业，非职业方块时为 null */
    @Nullable
    private static VillagerProfession findProfession(Level level, BlockPos pos) {
        var poi = PoiTypes.forState(level.getBlockState(pos));
        if (poi.isEmpty()) return null;
        for (var profession : BuiltInRegistries.VILLAGER_PROFESSION) {
            if (profession.heldJobSite().test(poi.get())) return profession;
        }
        return null;
    }

    private static Component getTradeTitle(@Nullable VillagerProfession profession) {
        if (profession == null) return Component.translatable("entity.minecraft.wandering_trader");
        return Component.translatable("entity.minecraft.villager." + profession.name());
    }

    /** 临时商人，仅为交易窗口提供交易列表，不进入世界 */
    private static class TradingMerchant extends Villager {
        private MerchantOffers offers;
        private final boolean wandering;

        private TradingMerchant(Level level, @Nullable VillagerProfession profession, BlockPos pos) {
            super(EntityType.VILLAGER, level, VillagerType.PLAINS);
            // 上方方块为职业方块时视为大师级村民，否则视为流浪商人
            this.wandering = profession == null;
            this.setVillagerData(new VillagerData(VillagerType.PLAINS,
                    wandering ? VillagerProfession.NONE : profession,
                    getMerchantLevel()));
            this.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            this.offers = wandering
                    ? buildWanderingOffers(level.random)
                    : buildProfessionOffers(profession, level.random);
        }

        private int getMerchantLevel() {
            return wandering ? 1 : VillagerData.MAX_VILLAGER_LEVEL;
        }

        /** 大师级村民的交易：各级别交易均生成 */
        private MerchantOffers buildProfessionOffers(VillagerProfession profession, RandomSource random) {
            var offers = new MerchantOffers();
            var trades = VillagerTrades.TRADES.get(profession);
            if (trades == null) return offers;
            for (int i = 0; i < TRADE_MULTIPLIER; i++)
                for (int lvl = VillagerData.MIN_VILLAGER_LEVEL; lvl <= VillagerData.MAX_VILLAGER_LEVEL; lvl++)
                    addRandomOffers(offers, trades.get(lvl), TRADES_PER_LEVEL, random);
            return offers;
        }

        /** 流浪商人的交易：每批若干常规交易加一笔稀有交易 */
        private MerchantOffers buildWanderingOffers(RandomSource random) {
            var offers = new MerchantOffers();
            var generic = VillagerTrades.WANDERING_TRADER_TRADES.get(1);
            var rare = VillagerTrades.WANDERING_TRADER_TRADES.get(2);
            for (int i = 0; i < TRADE_MULTIPLIER; i++) {
                addRandomOffers(offers, generic, WANDERING_TRADER_TRADES_PER_LEVEL, random);
                if (rare != null)
                    addRandomOffers(offers, rare, 1, random);
            }
            return offers;
        }

        private void addRandomOffers(MerchantOffers offers, VillagerTrades.ItemListing[] listings, int count, RandomSource random) {
            if (listings == null) return;
            var pool = new ArrayList<>(List.of(listings));
            for (int added = 0; added < count && !pool.isEmpty(); ) {
                var offer = pool.remove(random.nextInt(pool.size())).getOffer(this, random);
                if (offer == null) continue;
                offers.add(offer);
                added++;
            }
        }

        @Override
        public MerchantOffers getOffers() {
            return offers;
        }

        @Override
        public void overrideOffers(MerchantOffers offers) {
            this.offers = offers;
        }

        /** 不累计使用次数，交易不会断货 */
        @Override
        public void notifyTrade(MerchantOffer offer) {
            offer.resetUses();
            var player = getTradingPlayer();
            if (player != null && player.containerMenu instanceof MerchantMenu menu)
                player.sendMerchantOffers(menu.containerId, offers, getMerchantLevel(), getVillagerXp(), showProgressBar(), canRestock());
        }

        @Override
        public void notifyTradeUpdated(ItemStack stack) {
        }

        @Override
        public boolean showProgressBar() {
            return false;
        }
    }
}

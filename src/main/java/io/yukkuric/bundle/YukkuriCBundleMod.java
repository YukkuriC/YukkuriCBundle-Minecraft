package io.yukkuric.bundle;

import com.mojang.logging.LogUtils;
import io.yukkuric.bundle.block.YCBlocks;
import io.yukkuric.bundle.client.block.*;
import io.yukkuric.bundle.client.entity.MagicMissileRenderer;
import io.yukkuric.bundle.client.entity.YCEntityRenderers;
import io.yukkuric.bundle.client.item.MissileEmitterRenderer;
import io.yukkuric.bundle.client.particle.FadeLightParticle;
import io.yukkuric.bundle.damage.YCDamageTypes;
import io.yukkuric.bundle.entity.YCEntityTypes;
import io.yukkuric.bundle.item.MissileEmitter;
import io.yukkuric.bundle.item.YCItems;
import io.yukkuric.bundle.particle.YCParticleTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import static io.yukkuric.bundle.block.YCBlocks.MENGER_SPONGE_DUPER;

@Mod(YukkuriCBundleMod.MOD_ID)
public class YukkuriCBundleMod {
    public static final String MOD_ID = "yukkuric_bundle";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static ResourceLocation modLoc(String path) {
        return ResourceLocation.tryBuild(MOD_ID, path);
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.yukkuric_bundle")).withTabsBefore(CreativeModeTabs.COMBAT).icon(() -> MENGER_SPONGE_DUPER.get().asItem().getDefaultInstance()).displayItems((parameters, output) -> {
        for (var e : YCItems.ITEMS.getEntries()) {
            output.accept(e.get());
        }
        for (var e : YCBlocks.BLOCKS.getEntries()) {
            output.accept(e.get());
        }
    }).build());

    public YukkuriCBundleMod(IEventBus modEventBus, ModContainer modContainer) {
        YCBlocks.register(modEventBus);
        YCItems.register(modEventBus);
        YCEntityTypes.TYPES.register(modEventBus);
        YCDamageTypes.DAMAGE_TYPES.register(modEventBus);
        YCParticleTypes.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        modEventBus.addListener((RegisterPayloadHandlersEvent event) -> event.registrar("1").playToServer(
                MissileEmitter.FirePayload.TYPE, MissileEmitter.FirePayload.STREAM_CODEC,
                (payload, context) -> MissileEmitter.fireIfEquipped(context.player())));
    }

    @EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
    public static class ClientRegistries {
        private static BlockEntityWithoutLevelRenderer missileEmitterRenderer;

        @SubscribeEvent
        public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(YCParticleTypes.FADE_LIGHT.get(), FadeLightParticle.Provider::new);
        }

        @SubscribeEvent
        public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
            event.registerItem(new IClientItemExtensions() {
                @Override
                public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    if (missileEmitterRenderer == null) {
                        Minecraft minecraft = Minecraft.getInstance();
                        missileEmitterRenderer = new MissileEmitterRenderer(minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
                    }
                    return missileEmitterRenderer;
                }

                @Override
                public int getArmorLayerTintColor(ItemStack stack, LivingEntity entity, ArmorMaterial.Layer layer, int layerIdx, int fallbackColor) {
                    // 头盔本体不渲染，改由 MissileEmitterRenderer.HeadLayer 在头部位置绘制物品模型
                    return 0;
                }
            }, YCItems.MISSILE_EMITTER.get());
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            NeoForge.EVENT_BUS.addListener(MagicMissileRenderer::renderStage);
        }

        /** 装备时玩家头部一并隐藏，由 HeadLayer 顶替；下一帧的 setModelProperties 会自行还原可见性 */
        @SubscribeEvent
        public static void hideHeadWhenWearing(RenderPlayerEvent.Pre event) {
            if (!event.getEntity().getItemBySlot(EquipmentSlot.HEAD).is(YCItems.MISSILE_EMITTER.get())) return;
            var model = event.getRenderer().getModel();
            model.head.visible = false;
            model.hat.visible = false;
        }

        /** 左键触发发射，不论主手道具，也不论指向实体还是空；挖方块（准星在方块上）时不触发 */
        @SubscribeEvent
        public static void onLeftClick(InputEvent.InteractionKeyMappingTriggered event) {
            if (!event.isAttack()) return;
            Minecraft minecraft = Minecraft.getInstance();
            var player = minecraft.player;
            if (player == null) return;

            // missile emitter head
            if (player.getItemBySlot(EquipmentSlot.HEAD).is(YCItems.MISSILE_EMITTER.get())) {
                var isDigging = minecraft.hitResult instanceof BlockHitResult blockHit &&
                        !minecraft.level.getBlockState(blockHit.getBlockPos()).isAir();
                if (isDigging && !player.isShiftKeyDown()) return;
                PacketDistributor.sendToServer(new MissileEmitter.FirePayload());
            }
        }

        @SubscribeEvent
        public static void addLayers(EntityRenderersEvent.AddLayers event) {
            for (var skin : event.getSkins()) {
                PlayerRenderer renderer = event.getSkin(skin);
                if (renderer != null) renderer.addLayer(new MissileEmitterRenderer.HeadLayer(renderer));
            }
        }

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(YCBlocks.BE_MENGER_SPONGE_DUPER.get(), MengerSpongeDuperRenderer::new);
            event.registerBlockEntityRenderer(YCBlocks.BE_MENGER_SPONGE_MINER.get(), MengerSpongeMinerRenderer::new);
            event.registerBlockEntityRenderer(YCBlocks.BE_MENGER_SPONGE_VOID.get(), MengerSpongeVoidRenderer::new);
            YCEntityRenderers.register(event);
        }
    }
}

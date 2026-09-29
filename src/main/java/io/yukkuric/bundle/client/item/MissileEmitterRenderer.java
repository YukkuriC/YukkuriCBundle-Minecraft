package io.yukkuric.bundle.client.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.yukkuric.bundle.client.CustomBakedModel;
import io.yukkuric.bundle.client.entity.MagicMissileRenderer;
import io.yukkuric.bundle.entity.MagicMissile;
import io.yukkuric.bundle.item.YCItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** 物品模型：一枚白色立方体，外加两个绕其环绕的八面体，环绕体样式复用魔法弹模型 */
public class MissileEmitterRenderer extends BlockEntityWithoutLevelRenderer {
    /** 立方体半边长 */
    private static final float CUBE_HALF = 0.125F;
    /** 八面体中心与立方体中心的距离 */
    private static final float ORBIT_RADIUS = 0.4F;
    /** 环绕体相对魔法弹本体的缩放，决定内层八面体与外层辉光球的大小 */
    private static final float ORBITER_SCALE = 0.4F;
    /** 待机状态环绕一圈所需 tick 数 */
    private static final float IDLE_ORBIT_TICKS = 40F;
    /** 使用状态环绕一圈所需 tick 数 */
    private static final float USING_ORBIT_TICKS = 10F;
    private static final ResourceLocation BLOCK_TEXTURE = ResourceLocation.tryParse("minecraft:block/white_concrete");
    private static final RandomSource RANDOM = RandomSource.create();

    public MissileEmitterRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet modelSet) {
        super(dispatcher, modelSet);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack pose, MultiBufferSource buffer, int light, int overlay) {
        Minecraft minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        float ticks = level == null ? 0F : level.getGameTime() + minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        boolean using = minecraft.player != null
                && minecraft.player.isUsingItem()
                && minecraft.player.getUseItem().is(YCItems.MISSILE_EMITTER.get());
        pose.pushPose();
        // 几何以原点为中心，这里把中心挪到 (0.5,0.5,0.5)，与常规物品模型的空间一致（渲染管线会先平移 -0.5）
        pose.translate(0.5F, 0.5F, 0.5F);
        renderModel(buffer, pose, light, orbitAngle(using, ticks));
        pose.popPose();
    }

    /** 以 pose 原点为中心绘制整机 */
    public static void renderModel(MultiBufferSource buffer, PoseStack pose, int light, float angle) {
        // 不透明部分用 entity 渲染类型，由 shader 按法线给出面间明暗，并随传入光照变亮变暗
        VertexConsumer solid = buffer.getBuffer(RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS));
        VertexConsumer glow = buffer.getBuffer(MagicMissileRenderer.OUTER_TYPE);

        draw(MODEL_CUBE, solid, pose, light, 1F, 1F, 1F, 1F);

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        for (int i = 0; i < 2; i++) {
            Vec3 color = i == 0 ? MagicMissile.COLOR_LOCKED : MagicMissile.COLOR_FREE;
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(i * 180F));
            pose.translate(ORBIT_RADIUS, 0F, 0F);

            pose.pushPose();
            float inner = ORBITER_SCALE * MagicMissileRenderer.INNER_RADIUS;
            pose.scale(inner, inner, inner);
            draw(MagicMissileRenderer.MODEL_INNER, solid, pose, light, 1F, 1F, 1F, 1F);
            pose.popPose();

            pose.pushPose();
            float outer = ORBITER_SCALE * MagicMissileRenderer.OUTER_RADIUS;
            pose.scale(outer, outer, outer);
            float alpha = MagicMissileRenderer.OUTER_ALPHA;
            draw(MagicMissileRenderer.MODEL_OUTER, glow, pose, light,
                    (float) color.x * alpha, (float) color.y * alpha, (float) color.z * alpha, alpha);
            pose.popPose();

            pose.popPose();
        }
        pose.popPose();
    }

    /** 环绕轨道角度，由待机/使用状态决定转速 */
    public static float orbitAngle(boolean using, float ticks) {
        return ticks / (using ? USING_ORBIT_TICKS : IDLE_ORBIT_TICKS) * 360F;
    }

    /** 头盔槽装备时替换玩家头部：把整机画到头部中心 */
    public static class HeadLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        public HeadLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            if (!player.getItemBySlot(EquipmentSlot.HEAD).is(YCItems.MISSILE_EMITTER.get())) return;
            boolean using = player.isUsingItem() && player.getUseItem().is(YCItems.MISSILE_EMITTER.get());
            float ticks = player.level().getGameTime() + partialTick;

            pose.pushPose();
            getParentModel().head.translateAndRotate(pose);
            // 头部立方体中心位于头部枢轴下方
            pose.translate(0F, -0.25F, 0F);
            pose.scale(2, 2, 2);
            renderModel(buffer, pose, light, orbitAngle(using, ticks));
            pose.popPose();
        }
    }

    private static void draw(BakedModel model, VertexConsumer consumer, PoseStack pose, int light, float r, float g, float b, float alpha) {
        for (var quad : model.getQuads(null, null, RANDOM)) {
            consumer.putBulkData(pose.last(), quad, r, g, b, alpha, light, OverlayTexture.NO_OVERLAY, false);
        }
    }

    private static TextureAtlasSprite whiteSprite() {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(BLOCK_TEXTURE);
    }

    private static final BakedModel MODEL_CUBE = new CustomBakedModel() {
        @Override
        protected List<BakedQuad> buildQuads() {
            TextureAtlasSprite sprite = whiteSprite();
            float s = CUBE_HALF;
            Vec3[] v = {
                    new Vec3(-s, -s, -s), new Vec3(s, -s, -s), new Vec3(s, -s, s), new Vec3(-s, -s, s),
                    new Vec3(-s, s, -s), new Vec3(s, s, -s), new Vec3(s, s, s), new Vec3(-s, s, s)
            };
            int[][] faces = {
                    {0, 1, 2, 3}, {4, 5, 6, 7},
                    {0, 1, 5, 4}, {1, 2, 6, 5}, {2, 3, 7, 6}, {3, 0, 4, 7}
            };
            List<BakedQuad> quads = new ArrayList<>(faces.length);
            for (int[] face : faces) {
                quads.add(bakeQuad(new Vec3[]{v[face[0]], v[face[1]], v[face[2]], v[face[3]]}, sprite));
            }
            return quads;
        }
    };
}
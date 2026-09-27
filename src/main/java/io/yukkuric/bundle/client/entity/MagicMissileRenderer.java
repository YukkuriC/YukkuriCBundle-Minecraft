package io.yukkuric.bundle.client.entity;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import io.yukkuric.bundle.client.CustomBakedModel;
import io.yukkuric.bundle.entity.MagicMissile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class MagicMissileRenderer extends EntityRenderer<MagicMissile> {
    public static final float OUTER_RADIUS = 0.5F;
    public static final float OUTER_ALPHA = 0.5F;
    public static final float INNER_RADIUS = 0.3F;
    private static final ResourceLocation TEXTURE = ResourceLocation.tryParse("minecraft:textures/block/white_concrete.png");
    private static final ResourceLocation BLOCK_TEXTURE = ResourceLocation.tryParse("minecraft:block/white_concrete");

    // 外层用相加混合（不遮内层颜色），镜像 translucent 只改透明度状态与写掩码；不写深度以免挡掉内层
    public static final RenderType OUTER_TYPE = RenderType.create(
            "magic_missile_outer",
            DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS,
            1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_TRANSLUCENT_SHADER)
                    .setTextureState(RenderStateShard.BLOCK_SHEET_MIPPED)
                    .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false)
    );

    public MagicMissileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(MagicMissile entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight) {
        if (entity.isExploding()) {
            super.render(entity, entityYaw, partialTick, pose, buffer, packedLight);
            return;
        }

        RandomSource random = entity.level().random;

        // 先画不透明内层，再画半透明外层，使内层透过外层可见；初始飞出阶段只画内层
        renderInner(entity, partialTick, pose, buffer, random, packedLight);
        if (!entity.isLaunching()) {
            Vec3 color = entity.isLocked() ? MagicMissile.COLOR_LOCKED : MagicMissile.COLOR_FREE;
            renderOuter(color, pose, buffer, random);
        }

        super.render(entity, entityYaw, partialTick, pose, buffer, packedLight);
    }

    private void renderInner(MagicMissile entity, float partialTick, PoseStack pose, MultiBufferSource buffer, RandomSource random, int packedLight) {
        pose.pushPose();
        pose.scale(INNER_RADIUS, INNER_RADIUS, INNER_RADIUS);
        float angle = (entity.tickCount + partialTick) * 30F;
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        pose.mulPose(Axis.XP.rotationDegrees(angle * 0.6F));

        // 两种模式：初始飞出阶段用 entity 渲染类型，接收环境光照与方向光明暗；
        // 进入 stage 0 改用 solid，方块 shader 不做方向光，配合全亮度即纯白发光
        boolean launching = entity.isLaunching();
        VertexConsumer consumer = buffer.getBuffer(launching ? RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS) : RenderType.solid());
        quadLoop(MODEL_INNER, consumer, pose, 1f, 1f, 1f, 1f, random, launching ? packedLight : LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    private static void renderOuter(Vec3 color, PoseStack pose, MultiBufferSource buffer, RandomSource random) {
        pose.pushPose();
        pose.scale(OUTER_RADIUS, OUTER_RADIUS, OUTER_RADIUS);

        VertexConsumer consumer = buffer.getBuffer(OUTER_TYPE);
        quadLoop(MODEL_OUTER, consumer, pose, (float) color.x * OUTER_ALPHA, (float) color.y * OUTER_ALPHA, (float) color.z * OUTER_ALPHA, OUTER_ALPHA, random, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    // 辉光层固定全亮度，不参与环境光照；遍历各朝向与无朝向四边形
    private static void quadLoop(BakedModel model, VertexConsumer consumer, PoseStack pose,
                                 float r, float g, float b, float alpha, RandomSource random, int light) {
        for (Direction direction : Direction.values()) {
            for (var quad : model.getQuads(null, direction, random)) {
                emit(consumer, pose, quad, r, g, b, alpha, light);
            }
        }
        for (var quad : model.getQuads(null, null, random)) {
            emit(consumer, pose, quad, r, g, b, alpha, light);
        }
    }

    private static void emit(VertexConsumer consumer, PoseStack pose, BakedQuad quad, float r, float g, float b, float alpha, int light) {
        consumer.putBulkData(pose.last(), quad, r, g, b, alpha, light, OverlayTexture.NO_OVERLAY, false);
    }

    private static TextureAtlasSprite whiteSprite() {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(BLOCK_TEXTURE);
    }

    @Override
    public ResourceLocation getTextureLocation(MagicMissile entity) {
        return TEXTURE;
    }

    // 内存模型：外层半透明球在类加载时单次构建并缓存，内层八面体直接写定几何
    public static final BakedModel MODEL_OUTER = new CustomBakedModel() {
        private static final int LON_SEG = 8;
        private static final int LAT_SEG = 6;

        @Override
        protected List<BakedQuad> buildQuads() {
            TextureAtlasSprite sprite = whiteSprite();
            List<BakedQuad> quads = new ArrayList<>(LON_SEG * LAT_SEG);
            for (int i = 0; i < LAT_SEG; i++) {
                double v0 = Math.PI * i / LAT_SEG;
                double v1 = Math.PI * (i + 1) / LAT_SEG;
                for (int j = 0; j < LON_SEG; j++) {
                    double u0 = 2 * Math.PI * j / LON_SEG;
                    double u1 = 2 * Math.PI * (j + 1) / LON_SEG;
                    Vec3[] corners = {
                            spherePoint(u0, v0), spherePoint(u1, v0),
                            spherePoint(u1, v1), spherePoint(u0, v1)
                    };
                    quads.add(bakeQuad(corners, sprite));
                }
            }
            return quads;
        }

        private static Vec3 spherePoint(double u, double v) {
            return new Vec3(Math.sin(v) * Math.cos(u), Math.cos(v), Math.sin(v) * Math.sin(u));
        }
    };

    public static final BakedModel MODEL_INNER = new CustomBakedModel() {
        @Override
        protected List<BakedQuad> buildQuads() {
            TextureAtlasSprite sprite = whiteSprite();
            Vec3[] v = {
                    new Vec3(1, 0, 0), new Vec3(-1, 0, 0),
                    new Vec3(0, 1, 0), new Vec3(0, -1, 0),
                    new Vec3(0, 0, 1), new Vec3(0, 0, -1)
            };
            int[][] faces = {
                    {2, 0, 4}, {2, 4, 1}, {2, 1, 5}, {2, 5, 0},
                    {3, 4, 0}, {3, 1, 4}, {3, 5, 1}, {3, 0, 5}
            };
            List<BakedQuad> quads = new ArrayList<>(faces.length);
            for (int[] face : faces) {
                Vec3[] corners = {v[face[0]], v[face[1]], v[face[2]], v[face[2]]};
                quads.add(bakeQuad(corners, sprite));
            }
            return quads;
        }
    };
}
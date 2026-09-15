package io.yukkuric.bundle.client.blocks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.yukkuric.bundle.blocks.be.MengerSpongeBE;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.textures.FluidSpriteCache;
import net.neoforged.neoforge.fluids.FluidStack;

public class MengerSpongeRenderer implements RendererCFG, BlockEntityRenderer<MengerSpongeBE> {
    /**
     * 中央样本物品的基础缩放
     */
    private static final float ITEM_SCALE = 0.35f;
    /**
     * 流体面片的最大不透明度
     */
    private static final float MAX_ALPHA = 0.75f;
    /**
     * 流体面片的最大半宽
     */
    private static final float FLUID_HALF = 1 / 4f;

    public MengerSpongeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRender(MengerSpongeBE be, Vec3 cameraPos) {
        return RendererCFG.shouldRender(be, cameraPos);
    }

    @Override
    public void render(MengerSpongeBE be, float partialTick, PoseStack pose, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        if (level == null) return;
        float fade = RendererCFG.getFadeFactor(be);
        if (fade <= 0) return;

        // 中央展示的样本物品：FADE_START~FADE_END 间尺寸线性缩小
        if (!be.isEmpty()) {
            pose.pushPose();
            pose.translate(0.5, 0.5, 0.5);
            pose.mulPose(Axis.YP.rotation((level.getGameTime() + partialTick) * 0.1f));
            pose.scale(ITEM_SCALE * fade, ITEM_SCALE * fade, ITEM_SCALE * fade);
            Minecraft.getInstance().getItemRenderer()
                    .renderStatic(be.getExemplar(), ItemDisplayContext.FIXED, packedLight, packedOverlay, pose, buffer, level, 0);
            pose.popPose();
        }

        // 方块中央始终正对摄像机的流体贴面
        renderHoleFluid(be.getFluid(), pose, buffer, packedLight, fade);
    }

    /**
     * 在方块中央渲染流体静置贴图面片，用相机基向量直接解出四个顶点，始终正对摄像机。
     * 不透明度恒为 MAX_ALPHA，FADE_START~FADE_END 间面片大小从最大值线性缩小至 0。
     */
    private static void renderHoleFluid(FluidStack fluid, PoseStack pose, MultiBufferSource buffer, int packedLight, float fade) {
        if (fluid.isEmpty()) return;
        var props = IClientFluidTypeExtensions.of(fluid.getFluid());
        TextureAtlasSprite texture = FluidSpriteCache.getSprite(props.getStillTexture(fluid));
        int alpha = (int) (MAX_ALPHA * 255);
        int color = (alpha << 24) | (props.getTintColor(fluid) & 0xFFFFFF);
        VertexConsumer builder = buffer.getBuffer(RenderType.translucent());

        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 normal = new Vec3(camera.getLookVector()).scale(-1); // 朝向摄像机
        Vec3 right = new Vec3(camera.getLeftVector()).scale(-1); // 相机右方向
        Vec3 up = new Vec3(camera.getUpVector());
        Vec3 center = new Vec3(0.5, 0.5, 0.5);
        double half = FLUID_HALF * fade;
        Vec3 offsetR = right.scale(half);
        Vec3 offsetU = up.scale(half);
        float centerU = (texture.getU0() + texture.getU1()) / 2f;
        float centerV = (texture.getV0() + texture.getV1()) / 2f;
        float shrink = texture.uvShrinkRatio() * 0.25f;
        float u0 = Mth.lerp(shrink, texture.getU0(), centerU);
        float u1 = Mth.lerp(shrink, texture.getU1(), centerU);
        float v0 = Mth.lerp(shrink, texture.getV0(), centerV);
        float v1 = Mth.lerp(shrink, texture.getV1(), centerV);
        // 左下、右下、右上、左上（相机视角），逆时针绕序使正面朝向摄像机
        putVertex(builder, pose, center.subtract(offsetR).subtract(offsetU), color, u0, v1, normal, packedLight);
        putVertex(builder, pose, center.add(offsetR).subtract(offsetU), color, u1, v1, normal, packedLight);
        putVertex(builder, pose, center.add(offsetR).add(offsetU), color, u1, v0, normal, packedLight);
        putVertex(builder, pose, center.subtract(offsetR).add(offsetU), color, u0, v0, normal, packedLight);
    }

    private static void putVertex(VertexConsumer builder, PoseStack ms, Vec3 pos,
                                  int color, float u, float v, Vec3 normal, int light) {
        var pose = ms.last();
        builder.addVertex(pose.pose(), (float) pos.x, (float) pos.y, (float) pos.z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}

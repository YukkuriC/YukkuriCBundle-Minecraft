package io.yukkuric.bundle.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.yukkuric.bundle.entity.MagicMissile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class MagicMissileRenderer extends EntityRenderer<MagicMissile> {
    /** 固定边长 0.5 */
    private static final float SIZE = 0.5F;
    /** 纯白方块模型：白色混凝土默认态 */
    private static final BlockState WHITE_STATE = Blocks.WHITE_CONCRETE.defaultBlockState();
    private static final ResourceLocation TEXTURE = ResourceLocation.tryParse("minecraft:textures/block/white_concrete.png");

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

        // 固定 0.5 边长、不旋转的白色方块：模型为 [0,1] 单位方块，缩放后居中于实体中心
        pose.pushPose();
        pose.translate(0.0, SIZE / 2.0, 0.0);
        pose.scale(SIZE, SIZE, SIZE);
        pose.translate(-0.5, -0.5, -0.5);

        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(WHITE_STATE);
        RandomSource random = entity.level().random;
        Vec3 color = entity.isLocked() ? MagicMissile.COLOR_LOCKED : MagicMissile.COLOR_FREE;
        VertexConsumer consumer = buffer.getBuffer(RenderType.solid());
        // 全亮度 + 关闭漫反射明暗，保证纯色、不受环境光影响
        for (Direction direction : Direction.values()) {
            for (var quad : model.getQuads(WHITE_STATE, direction, random)) {
                consumer.putBulkData(pose.last(), quad, (float) color.x, (float) color.y, (float) color.z, 1f,
                        LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, false);
            }
        }
        for (var quad : model.getQuads(WHITE_STATE, null, random)) {
            consumer.putBulkData(pose.last(), quad, (float) color.x, (float) color.y, (float) color.z, 1f,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, false);
        }

        pose.popPose();
        super.render(entity, entityYaw, partialTick, pose, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(MagicMissile entity) {
        return TEXTURE;
    }
}

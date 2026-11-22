package io.yukkuric.bundle.client.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.yukkuric.bundle.block.be.MengerSpongeArtificialSkyBE;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 以末地传送门的 RenderType 重绘门格海绵模型：逐块拆出烘焙模型的四边形，只提交顶点位置。
 */
public class MengerSpongeArtificialSkyRenderer implements BlockEntityRenderer<MengerSpongeArtificialSkyBE> {
    public MengerSpongeArtificialSkyRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MengerSpongeArtificialSkyBE be, float partialTick, PoseStack pose, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        VertexConsumer consumer = buffer.getBuffer(RenderType.endPortal());
        RandomSource random = RandomSource.create();
        for (Direction direction : Direction.values()) {
            for (var quad : model.getQuads(state, direction, random)) putQuad(consumer, pose.last(), quad);
        }
        for (var quad : model.getQuads(state, null, random)) putQuad(consumer, pose.last(), quad);
    }

    private static void putQuad(VertexConsumer consumer, PoseStack.Pose pose, BakedQuad quad) {
        int[] data = quad.getVertices();
        for (int i = 0; i < 4; i++) {
            int offset = i * 8;
            consumer.addVertex(pose,
                    Float.intBitsToFloat(data[offset]),
                    Float.intBitsToFloat(data[offset + 1]),
                    Float.intBitsToFloat(data[offset + 2]));
        }
    }
}

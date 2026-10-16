package io.yukkuric.bundle.client.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.yukkuric.bundle.block.be.MengerSpongePlayerProxyBE;
import net.minecraft.client.model.SkullModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

public class MengerSpongePlayerProxyRenderer implements RendererCFG, BlockEntityRenderer<MengerSpongePlayerProxyBE> {
    /**
     * 自转：每 X 秒一圈
     */
    private static final float SPIN_RADS_PER_TICK = (float) (Math.PI * 2 / (5 * 20));
    /**
     * 头部模型半高（格），借此把头部中心对齐到方块中心
     */
    private static final float HEAD_HALF = 0.25f;

    private final SkullModel headModel;

    public MengerSpongePlayerProxyRenderer(BlockEntityRendererProvider.Context context) {
        this.headModel = new SkullModel(context.getModelSet().bakeLayer(ModelLayers.PLAYER_HEAD));
    }

    @Override
    public boolean shouldRender(MengerSpongePlayerProxyBE be, Vec3 cameraPos) {
        return RendererCFG.shouldRender(be, cameraPos);
    }

    @Override
    public void render(MengerSpongePlayerProxyBE be, float partialTick, PoseStack pose, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        var level = be.getLevel();
        if (level == null) return;
        float fade = RendererCFG.getFadeFactor(be) * 0.5f;
        if (fade <= 0) return;
        if (!(be.getPlayer() instanceof AbstractClientPlayer player)) return;
        var consumer = buffer.getBuffer(RenderType.entityTranslucent(player.getSkin().texture()));
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        // 远离时整体线性缩小
        pose.scale(fade, fade, fade);
        pose.mulPose(Axis.YP.rotation((level.getGameTime() + partialTick) * SPIN_RADS_PER_TICK));
        // 头部模型需翻转 y 轴才立得住，翻转后再把头部中心抬到方块中心
        pose.scale(-1, -1, 1);
        pose.translate(0, HEAD_HALF, 0);
        headModel.renderToBuffer(pose, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}

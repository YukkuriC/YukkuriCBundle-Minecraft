package io.yukkuric.bundle.client.block;

import com.mojang.blaze3d.vertex.PoseStack;
import io.yukkuric.bundle.block.MengerSpongeTicker;
import io.yukkuric.bundle.block.be.MengerSpongeTickerBE;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class MengerSpongeTickerRenderer implements RendererCFG, BlockEntityRenderer<MengerSpongeTickerBE> {
    private static final AABB WIREFRAME = new AABB(0, 0, 0, 1, 1, 1);

    public MengerSpongeTickerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRender(MengerSpongeTickerBE be, Vec3 cameraPos) {
        return RendererCFG.shouldRender(be, cameraPos);
    }

    @Override
    public void render(MengerSpongeTickerBE be, float partialTick, PoseStack pose, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        var state = be.getBlockState();
        // if (!state.getValue(MengerSpongeTicker.LIT)) return;
        var origin = be.getBlockPos();
        var target = MengerSpongeTicker.getTargetPos(state, origin);
        pose.pushPose();
        pose.translate(target.getX() - origin.getX(), target.getY() - origin.getY(), target.getZ() - origin.getZ());
        LevelRenderer.renderLineBox(pose, buffer.getBuffer(RenderType.lines()), WIREFRAME, 1f, 1f, 1f, 1f);
        pose.popPose();
    }
}

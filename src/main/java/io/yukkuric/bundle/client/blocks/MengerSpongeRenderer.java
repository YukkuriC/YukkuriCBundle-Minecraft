package io.yukkuric.bundle.client.blocks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.yukkuric.bundle.blocks.be.MengerSpongeBE;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class MengerSpongeRenderer implements BlockEntityRenderer<MengerSpongeBE> {
    public MengerSpongeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRender(MengerSpongeBE be, Vec3 cameraPos) {
        return be.getBlockPos().distToCenterSqr(cameraPos.x, cameraPos.y, cameraPos.z) <= 25.0;
    }

    @Override
    public void render(MengerSpongeBE be, float partialTick, PoseStack pose, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        ItemStack exemplar = be.getExemplar();
        Level level = be.getLevel();
        if (exemplar.isEmpty() || level == null) return;

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotation((level.getGameTime() + partialTick) * 0.1f));
        pose.scale(0.35f, 0.35f, 0.35f);
        Minecraft.getInstance().getItemRenderer()
                .renderStatic(exemplar, ItemDisplayContext.FIXED, packedLight, packedOverlay, pose, buffer, level, 0);
        pose.popPose();
    }
}

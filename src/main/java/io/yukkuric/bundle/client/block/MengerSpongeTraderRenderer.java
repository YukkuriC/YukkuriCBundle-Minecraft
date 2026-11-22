package io.yukkuric.bundle.client.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.yukkuric.bundle.block.be.MengerSpongeTraderBE;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public class MengerSpongeTraderRenderer implements RendererCFG, BlockEntityRenderer<MengerSpongeTraderBE> {
    private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static final ResourceLocation VILLAGER_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png");
    private static final ResourceLocation WANDERING_TRADER_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/wandering_trader.png");
    /**
     * 头部模型半高（格），借此把头部中心对齐到方块中心
     */
    private static final float HEAD_HALF = 5 / 16f;

    private final VillagerModel<Villager> model;

    public MengerSpongeTraderRenderer(BlockEntityRendererProvider.Context context) {
        this.model = new VillagerModel<>(context.getModelSet().bakeLayer(ModelLayers.VILLAGER));
    }

    @Override
    public boolean shouldRender(MengerSpongeTraderBE be, Vec3 cameraPos) {
        return RendererCFG.shouldRender(be, cameraPos);
    }

    @Override
    public void render(MengerSpongeTraderBE be, float partialTick, PoseStack pose, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        float fade = RendererCFG.getFadeFactor(be);
        if (fade <= 0) return;
        var level = be.getLevel();
        for (var dir : SIDES) {
            var neighbor = level.getBlockState(be.getBlockPos().relative(dir));
            // 侧面被方块挡住时不渲染该侧的头；空气与流体不算遮挡
            if (!neighbor.isAir() && neighbor.getFluidState().isEmpty()) continue;
            pose.pushPose();
            // 头部中心落在侧面所在的平面上，向内嵌半个头
            pose.translate(0.5 + dir.getStepX() * 0.5, 0.5, 0.5 + dir.getStepZ() * 0.5);
            pose.scale(fade, fade, fade);
            // 平视朝外：鼻尖（模型 -z）指向该侧面
            pose.mulPose(Axis.YP.rotationDegrees(180 - dir.toYRot()));
            pose.scale(-1, -1, 1);
            pose.translate(0, HEAD_HALF, 0);
            renderHead(be, pose, buffer, packedLight);
            pose.popPose();
        }
    }

    private void renderHead(MengerSpongeTraderBE be, PoseStack pose, MultiBufferSource buffer, int packedLight) {
        var head = model.getHead();
        var profession = be.getProfessionId();
        if (profession == null) {
            renderVillagerHead(head, be.getTypeId(), pose, buffer, packedLight, null);
        } else if (profession.equals(MengerSpongeTraderBE.WANDERING_PROFESSION)) {
            head.render(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(WANDERING_TRADER_TEXTURE)),
                    packedLight, OverlayTexture.NO_OVERLAY);
        } else {
            renderVillagerHead(head, be.getTypeId(), pose, buffer, packedLight, profession);
        }
    }

    /** 村民头由基底层、类型层、职业层依次叠加 */
    private static void renderVillagerHead(ModelPart head, ResourceLocation typeId, PoseStack pose,
                                           MultiBufferSource buffer, int packedLight, @Nullable ResourceLocation professionId) {
        head.render(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(VILLAGER_TEXTURE)),
                packedLight, OverlayTexture.NO_OVERLAY);
        var typeTexture = typeId.withPath(path -> "textures/entity/villager/type/" + path + ".png");
        head.render(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(typeTexture)),
                packedLight, OverlayTexture.NO_OVERLAY);
        if (professionId == null) return;
        var professionTexture = professionId.withPath(path -> "textures/entity/villager/profession/" + path + ".png");
        head.render(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(professionTexture)),
                packedLight, OverlayTexture.NO_OVERLAY);
    }
}

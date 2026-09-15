package io.yukkuric.bundle.client.blocks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.yukkuric.bundle.blocks.be.MengerSpongeMinerBE;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class MengerSpongeMinerRenderer implements RendererCFG, BlockEntityRenderer<MengerSpongeMinerBE> {
    /**
     * 公转：每 X 秒一圈
     */
    private static final float ORBIT_RADS_PER_TICK = (float) (Math.PI * 2 / (4 * 20));
    /**
     * 自转：每 X 秒一圈
     */
    private static final float SPIN_RADS_PER_TICK = (float) (Math.PI * 2 / (6 * 20));
    /**
     * 倾角方向进动：每 X 秒一圈
     */
    private static final float PRECESS_RADS_PER_TICK = (float) (Math.PI * 2 / (20 * 20));
    /**
     * 轨道面倾角
     */
    private static final float ORBIT_TILT = (float) Math.toRadians(15);
    /**
     * 公转半径（格）
     */
    private static final float ORBIT_RADIUS = 1.0f;
    /**
     * 圈上物品数 = 物品栏前 X 格
     */
    private static final int ITEM_COUNT = 9;
    /**
     * 单个物品缩放
     */
    private static final float SCALE = 0.7f;

    public MengerSpongeMinerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRender(MengerSpongeMinerBE be, Vec3 cameraPos) {
        return RendererCFG.shouldRender(be, cameraPos);
    }

    @Override
    public AABB getRenderBoundingBox(MengerSpongeMinerBE be) {
        return new AABB(be.getBlockPos()).inflate(ORBIT_RADIUS * 2);
    }

    @Override
    public void render(MengerSpongeMinerBE be, float partialTick, PoseStack pose, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        if (level == null) return;
        float fade = RendererCFG.getFadeFactor(be);
        if (fade <= 0) return;
        float time = level.getGameTime() + partialTick;
        float orbitBase = time * ORBIT_RADS_PER_TICK;
        float spin = time * SPIN_RADS_PER_TICK;
        float prec = time * PRECESS_RADS_PER_TICK;
        float sinPrec = Mth.sin(prec), cosPrec = Mth.cos(prec);
        float sinTilt = Mth.sin(ORBIT_TILT), cosTilt = Mth.cos(ORBIT_TILT);

        for (int i = 0; i < ITEM_COUNT; i++) {
            var stack = be.itemCap.getStackInSlot(i);
            float orbit = orbitBase + (float) (Math.PI * 2 * i / ITEM_COUNT);
            // 轨道面内绕圈，先绕 X 轴倾斜 ORBIT_TILT，再绕 Y 轴进动倾角方向
            float x = Mth.cos(orbit) * ORBIT_RADIUS;
            float y = -Mth.sin(orbit) * ORBIT_RADIUS * sinTilt;
            float z = Mth.sin(orbit) * ORBIT_RADIUS * cosTilt;
            float wx = x * cosPrec + z * sinPrec;
            float wz = -x * sinPrec + z * cosPrec;
            pose.pushPose();
            pose.translate(0.5 + wx, 0.5 + y, 0.5 + wz);
            // 纵向俯仰跟随各自公转角度，横向以 SPIN_RADS_PER_TICK 自转
            pose.mulPose(Axis.XP.rotation(orbit));
            pose.mulPose(Axis.YP.rotation(spin));
            pose.scale(SCALE * fade, SCALE * fade, SCALE * fade);
            Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, packedLight, packedOverlay, pose, buffer, level, 0);
            pose.popPose();
        }
    }
}

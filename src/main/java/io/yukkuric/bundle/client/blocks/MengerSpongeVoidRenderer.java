package io.yukkuric.bundle.client.blocks;

import com.mojang.blaze3d.vertex.*;
import io.yukkuric.bundle.blocks.be.MengerSpongeVoidBE;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.WeakHashMap;

public class MengerSpongeVoidRenderer implements RendererCFG, BlockEntityRenderer<MengerSpongeVoidBE> {
    /**
     * 循环节奏：每 X + Y 秒一个周期。X 秒内不渲染任何故障（静默），
     * 随后 Y 秒内逐帧随机抖动 RGB 三个错位通道，然后回到静默，如此循环。
     */
    private static final double OFF_MIN = 3.0;   // X 的最小静默秒数
    private static final double OFF_MAX = 8.0;   // X 的最大静默秒数
    private static final double ON_MIN = 0.2;    // Y 的最小故障秒数
    private static final double ON_MAX = 0.5;    // Y 的最大故障秒数
    /**
     * 每个 RGB 通道相对方块原位的最大错位距离（单位：格），错位在 ±MAX_SHIFT 内随机。
     * 实际幅度随相机距离在 FADE_START~FADE_END 间线性衰减至 0。
     */
    private static final double MAX_SHIFT = 0.09;
    /**
     * 减淡（加色）通道的透明度：两个做减淡的通道叠加时会相加变亮，混出互补色。
     */
    private static final float DODGE_ALPHA = 0.6f;
    /**
     * 本次故障中随机选中的主通道（采用 alpha 混合）的透明度，作为更实的底色。
     */
    private static final float MAIN_ALPHA = 1.0f;
    /**
     * 减淡（additive）混合的叠加类型，与 alpha 混合不同，重叠会相加变亮。
     */
    private static final RenderType GLITCH = RenderType.create(
            "menger_sponge_void_glitch",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            786432,
            true,
            true,
            RenderType.CompositeState.builder()
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setShaderState(RenderStateShard.RENDERTYPE_TRANSLUCENT_SHADER)
                    .setTextureState(RenderStateShard.BLOCK_SHEET_MIPPED)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setOutputState(RenderStateShard.TRANSLUCENT_TARGET)
                    .createCompositeState(true));

    /**
     * 每个 BlockEntity 各自独立的故障状态，互不同步。
     */
    private static final WeakHashMap<MengerSpongeVoidBE, State> STATES = new WeakHashMap<>();

    private static class State {
        boolean glitching = true;
        long phaseEndAt = Long.MIN_VALUE;
    }

    public MengerSpongeVoidRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRender(MengerSpongeVoidBE be, Vec3 cameraPos) {
        return RendererCFG.shouldRender(be, cameraPos);
    }

    private static final double[] SHIFT_R = new double[3];
    private static final double[] SHIFT_G = new double[3];
    private static final double[] SHIFT_B = new double[3];

    private static void fillPass(double[] out, RandomSource random, float fade) {
        double shift = MAX_SHIFT * fade;
        out[0] = (random.nextDouble() * 2.0 - 1.0) * shift;
        out[1] = (random.nextDouble() * 2.0 - 1.0) * shift;
        out[2] = (random.nextDouble() * 2.0 - 1.0) * shift;
    }

    private static void updatePhase(State state, long gameTime, RandomSource random) {
        if (gameTime < state.phaseEndAt) return;
        if (!state.glitching) {
            state.glitching = true;
            double seconds = ON_MIN + random.nextDouble() * (ON_MAX - ON_MIN);
            state.phaseEndAt = gameTime + (long) (seconds * 20.0);
        } else {
            state.glitching = false;
            double seconds = OFF_MIN + random.nextDouble() * (OFF_MAX - OFF_MIN);
            state.phaseEndAt = gameTime + (long) (seconds * 20.0);
        }
    }

    @Override
    public void render(MengerSpongeVoidBE be, float partialTick, PoseStack pose, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        if (level == null) return;
        long gameTime = level.getGameTime();

        State state = STATES.computeIfAbsent(be, k -> new State());
        updatePhase(state, gameTime, level.random);

        // FADE_START~FADE_END 间整体错位幅度线性淡出
        float fade = RendererCFG.getFadeFactor(be);
        if (fade <= 0) return;

        // X 秒静默期：不渲染任何故障
        if (!state.glitching) return;

        // Y 秒故障期：逐帧随机重掷三个通道的错位（幅度随距离衰减）
        fillPass(SHIFT_R, level.random, fade);
        fillPass(SHIFT_G, level.random, fade);
        fillPass(SHIFT_B, level.random, fade);

        BlockState blockState = be.getBlockState();
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(blockState);

        pose.pushPose();
        var mainChannel = level.random.nextInt(3);
        renderChannel(pose, buffer, model, blockState, level.random, packedLight, packedOverlay,
                SHIFT_R, mainChannel == 0, 1f, 0f, 0f);
        renderChannel(pose, buffer, model, blockState, level.random, packedLight, packedOverlay,
                SHIFT_G, mainChannel == 1, 0f, 1f, 0f);
        renderChannel(pose, buffer, model, blockState, level.random, packedLight, packedOverlay,
                SHIFT_B, mainChannel == 2, 0f, 0f, 1f);
        pose.popPose();
    }

    private void renderChannel(PoseStack pose, MultiBufferSource buffer, BakedModel model, BlockState state,
                               RandomSource random, int packedLight, int packedOverlay, double[] off, boolean isMain,
                               float red, float green, float blue) {
        pose.pushPose();
        pose.translate(off[0], off[1], off[2]);
        VertexConsumer consumer = buffer.getBuffer(isMain ? RenderType.translucent() : GLITCH);
        float alpha = isMain ? MAIN_ALPHA : DODGE_ALPHA;
        for (Direction direction : Direction.values()) {
            for (var quad : model.getQuads(state, direction, random)) {
                consumer.putBulkData(pose.last(), quad, red, green, blue, alpha, packedLight, packedOverlay);
            }
        }
        for (var quad : model.getQuads(state, null, random)) {
            consumer.putBulkData(pose.last(), quad, red, green, blue, alpha, packedLight, packedOverlay);
        }
        pose.popPose();
    }
}
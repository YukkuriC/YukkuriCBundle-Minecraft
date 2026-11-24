package io.yukkuric.bundle.net;

import io.yukkuric.bundle.YukkuriCBundleMod;
import io.yukkuric.bundle.item.ShiftScrollable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** shift+滚轮切换类物品的公共协议：客户端判断手里的物品后上报，服务端转交给物品处理 */
public class ScrollSwitch {
    /** 客户端调用，返回 true 表示已上报，调用方应取消滚轮事件 */
    public static boolean tryScrollOnClient(Player player, double delta) {
        if (!player.isShiftKeyDown() || player.isSpectator()) return false;

        boolean offHand;
        if (player.getMainHandItem().getItem() instanceof ShiftScrollable) offHand = false;
        else if (player.getOffhandItem().getItem() instanceof ShiftScrollable) offHand = true;
        else return false;

        PacketDistributor.sendToServer(new Payload(offHand, delta));
        return true;
    }

    /** 服务端调用，交给物品自己处理轮换 */
    public static void handleServer(Player player, boolean offHand, double delta) {
        ItemStack stack = player.getItemInHand(offHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (stack.getItem() instanceof ShiftScrollable scrollable) {
            scrollable.onShiftScroll(player, stack, delta < 0);
        }
    }

    public record Payload(boolean offHand, double delta) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(YukkuriCBundleMod.modLoc("shift_scroll"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Payload::offHand,
                ByteBufCodecs.DOUBLE, Payload::delta,
                Payload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}

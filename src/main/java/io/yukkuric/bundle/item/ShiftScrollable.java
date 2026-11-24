package io.yukkuric.bundle.item;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** shift+滚轮类物品的公共入口 */
public interface ShiftScrollable {
    /** 服务端调用，increase 为 true 表示滚轮向上 */
    void onShiftScroll(Player player, ItemStack stack, boolean increase);
}

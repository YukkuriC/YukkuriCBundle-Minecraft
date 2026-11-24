package io.yukkuric.bundle.item;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashMap;
import java.util.Map;

import static io.yukkuric.bundle.YukkuriCBundleMod.MOD_ID;

public class YCDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MOD_ID);

    /** 槽位编号 -> 记录的位置 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Map<String, AddressBook.Location>>> ADDRESS_BOOK_LOCATIONS =
            DATA_COMPONENTS.registerComponentType("address_book_locations", builder -> builder
                    .persistent(Codec.unboundedMap(Codec.STRING, AddressBook.Location.CODEC))
                    .networkSynchronized(ByteBufCodecs.map(
                            HashMap::newHashMap,
                            ByteBufCodecs.STRING_UTF8,
                            AddressBook.Location.STREAM_CODEC
                    )));

    /** 当前选中的槽位，shift+滚轮轮换 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ADDRESS_BOOK_SLOT =
            DATA_COMPONENTS.registerComponentType("address_book_slot", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** 槽位编号 -> 该槽位对应的物品名称 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Map<String, Component>>> ADDRESS_BOOK_NAMES =
            DATA_COMPONENTS.registerComponentType("address_book_names", builder -> builder
                    .persistent(Codec.unboundedMap(Codec.STRING, ComponentSerialization.CODEC))
                    .networkSynchronized(ByteBufCodecs.map(
                            HashMap::newHashMap,
                            ByteBufCodecs.STRING_UTF8,
                            ComponentSerialization.STREAM_CODEC
                    )));

    public static void register(IEventBus bus) {
        DATA_COMPONENTS.register(bus);
    }
}

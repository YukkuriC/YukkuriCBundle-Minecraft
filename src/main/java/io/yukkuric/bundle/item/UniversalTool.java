package io.yukkuric.bundle.item;

import io.yukkuric.bundle.YukkuriCBundleMod;
import io.yukkuric.bundle.tag.YCTags;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;

public class UniversalTool extends DiggerItem {
    public static final String ID = "universal_tool";
    public static final ResourceLocation IDLoc = YukkuriCBundleMod.modLoc(ID);
    public static final Properties PROPS = new Properties()
            .stacksTo(1).rarity(Rarity.RARE);

    public static final int COMMON_ENCHANTMENT_LEVEL = 10;
    public static final int[] MODE_ENCHANTMENT_LEVELS = new int[]{10, 1, 5};
    public static final ResourceKey<Enchantment>[] COMMON_ENCHANTMENTS = new ResourceKey[]{
            Enchantments.SHARPNESS, Enchantments.LOOTING, Enchantments.EFFICIENCY};
    /** 下标即模式 */
    public static final ResourceKey<Enchantment>[] MODE_ENCHANTMENTS = new ResourceKey[]{
            Enchantments.FORTUNE,
            Enchantments.SILK_TOUCH,
            Enchantments.FIRE_ASPECT,
    };

    public static final ItemAttributeModifiers EXTRA_ATTRS;
    static {
        var attrBuilder = ItemAttributeModifiers.builder();
        var addRange = new AttributeModifier(IDLoc, 3f, AttributeModifier.Operation.ADD_VALUE);
        attrBuilder.add(Attributes.BLOCK_INTERACTION_RANGE, addRange, EquipmentSlotGroup.MAINHAND);
        attrBuilder.add(Attributes.ENTITY_INTERACTION_RANGE, addRange, EquipmentSlotGroup.MAINHAND);
        EXTRA_ATTRS = attrBuilder.build();
    }
    public final ItemAttributeModifiers MERGED_ATTRS;

    public UniversalTool() {
        super(Tiers.NETHERITE, YCTags.Blocks.PaxelMineable, PROPS);
        // merge attrs
        var attrs = createAttributes(Tiers.NETHERITE, 5, 0);
        var list = new ArrayList<>(attrs.modifiers());
        list.addAll(EXTRA_ATTRS.modifiers());
        MERGED_ATTRS = new ItemAttributeModifiers(Collections.unmodifiableList(list), true);
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        return MERGED_ATTRS;
    }

    private ItemStack _cached;
    public ItemStack getDefaultInstance() {
        if (_cached == null) {
            _cached = super.getDefaultInstance();
            UpdateEnchantmentsByMode(_cached);
        }
        return _cached;
    }

    /** 显示为“{原名} ({模式名})” */
    @Override
    public Component getName(ItemStack stack) {
        var name = super.getName(stack);
        var modeName = ModeName(stack);
        return modeName == null ? name : Component.empty().append(name).append(" (").append(modeName).append(")");
    }

    /** shift+右键点空气时切换模式，仅服务端执行，结果随物品栏同步回客户端 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player.isShiftKeyDown()) {
            var mode = GetMode(stack);
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData((mode + 1) % MODE_ENCHANTMENTS.length));
            UpdateEnchantmentsByMode(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    public static int GetMode(ItemStack src) {
        return src.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.DEFAULT).value();
    }

    public static ItemStack UpdateEnchantmentsByMode(ItemStack src) {
        // common stuff
        if (!src.has(DataComponents.UNBREAKABLE)) src.set(DataComponents.UNBREAKABLE, new Unbreakable(true));

        // enchantments
        var registries = currentRegistries();
        if (registries == null) return src;
        var lookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
        var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);

        // common enchantments
        for (var id : COMMON_ENCHANTMENTS)
            enchantments.set(lookup.getOrThrow(id), COMMON_ENCHANTMENT_LEVEL);

        // modelData as mode
        var mode = GetMode(src);
        enchantments.set(lookup.getOrThrow(MODE_ENCHANTMENTS[mode]), MODE_ENCHANTMENT_LEVELS[mode]);

        src.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
        return src;
    }

    /** 模式名取该模式独特附魔的描述组件 */
    @Nullable
    private static Component ModeName(ItemStack stack) {
        var registries = currentRegistries();
        if (registries == null) return null;
        var enchantment = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(MODE_ENCHANTMENTS[GetMode(stack)]);
        return enchantment.value().description();
    }

    /** 附魔 holder 属于数据包注册表，只能从运行中的服务端（含单人集成服务端）或客户端连接取得 */
    @Nullable
    private static HolderLookup.Provider currentRegistries() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) return server.registryAccess();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            var level = Minecraft.getInstance().level;
            if (level != null) return level.registryAccess();
        }
        return null;
    }
}
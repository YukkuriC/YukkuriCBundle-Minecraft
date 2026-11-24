package io.yukkuric.bundle.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.yukkuric.bundle.YukkuriCBundleMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AddressBook extends Item {
    public static final String ID = "address_book";
    public static final int MAX_SLOTS = 64;
    public static final Properties PROPS = new Properties()
            .stacksTo(1).rarity(Rarity.RARE);

    public AddressBook() {
        super(PROPS);
    }

    /** shift+右键：把点选方块及其法向量所在的位置与维度存入当前槽位 */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;

        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        int slot = getSelectedSlot(stack);
        setLocation(stack, slot, new Location(level.dimension(), context.getClickedPos(), context.getClickedFace()));

        if (!level.isClientSide) {
            player.displayClientMessage(
                    Component.translatable("tooltip.yukkuric_bundle.address_book.saved", slot), true);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 右键：按末影珍珠的方式传送到当前槽位记录的位置 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Location location = getLocation(stack, getSelectedSlot(stack));
        if (location == null) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(
                        Component.translatable("tooltip.yukkuric_bundle.address_book.empty"), true);
            }
            return InteractionResultHolder.pass(stack);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            var targetLevel = serverPlayer.server.getLevel(location.dimension());
            Vec3 target = Vec3.atBottomCenterOf(location.target());
            serverPlayer.changeDimension(new DimensionTransition(targetLevel, target,
                    serverPlayer.getDeltaMovement(), serverPlayer.getYRot(), serverPlayer.getXRot(),
                    DimensionTransition.DO_NOTHING));
            serverPlayer.resetFallDistance();
            serverPlayer.resetCurrentImpulseContext();
            targetLevel.playSound(null, target.x, target.y, target.z,
                    SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1f, 1f);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** 附魔光效只反映“当前槽位是否存有坐标” */
    @Override
    public boolean isFoil(ItemStack stack) {
        return getLocation(stack, getSelectedSlot(stack)) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int slot = getSelectedSlot(stack);
        tooltip.add(Component.translatable("tooltip.yukkuric_bundle.address_book.slot", slot)
                .withStyle(ChatFormatting.GRAY));

        Location location = getLocation(stack, slot);
        if (location == null) {
            tooltip.add(Component.translatable("tooltip.yukkuric_bundle.address_book.empty")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            BlockPos target = location.target();
            tooltip.add(Component.translatable("tooltip.yukkuric_bundle.address_book.location",
                            location.dimension().location().toString(),
                            target.getX(), target.getY(), target.getZ())
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /** 像法术书一样：铁砧等途径改的名字记到当前槽位上，切槽时换回该槽位保存的名字 */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        String key = String.valueOf(getSelectedSlot(stack));

        var customName = stack.get(DataComponents.CUSTOM_NAME);
        var savedNames = stack.get(YCDataComponents.ADDRESS_BOOK_NAMES.get());

        if (customName != null) {
            if (savedNames != null) {
                if (!customName.equals(savedNames.get(key))) {
                    var mutNames = new HashMap<>(savedNames);
                    mutNames.put(key, customName);
                    stack.set(YCDataComponents.ADDRESS_BOOK_NAMES.get(), mutNames);
                }
            } else {
                var mutNames = new HashMap<String, Component>();
                mutNames.put(key, customName);
                stack.set(YCDataComponents.ADDRESS_BOOK_NAMES.get(), mutNames);
            }
        } else if (savedNames != null) {
            var mutNames = new HashMap<>(savedNames);
            mutNames.remove(key);
            if (mutNames.isEmpty()) {
                stack.remove(YCDataComponents.ADDRESS_BOOK_NAMES.get());
            } else {
                stack.set(YCDataComponents.ADDRESS_BOOK_NAMES.get(), mutNames);
            }
        }
    }

    public static int getSelectedSlot(ItemStack stack) {
        return Mth.clamp(stack.getOrDefault(YCDataComponents.ADDRESS_BOOK_SLOT.get(), 0), 0, MAX_SLOTS - 1);
    }

    public static Location getLocation(ItemStack stack, int slot) {
        var locations = stack.get(YCDataComponents.ADDRESS_BOOK_LOCATIONS.get());
        return locations == null ? null : locations.get(String.valueOf(slot));
    }

    public static void setLocation(ItemStack stack, int slot, Location location) {
        var locations = stack.get(YCDataComponents.ADDRESS_BOOK_LOCATIONS.get());
        var mutLocations = locations != null ? new HashMap<>(locations) : new HashMap<String, Location>();
        mutLocations.put(String.valueOf(slot), location);
        stack.set(YCDataComponents.ADDRESS_BOOK_LOCATIONS.get(), mutLocations);
    }

    public static int rotateSlot(ItemStack stack, boolean increase) {
        int slot = Mth.clamp(getSelectedSlot(stack) + (increase ? 1 : -1), 0, MAX_SLOTS - 1);
        stack.set(YCDataComponents.ADDRESS_BOOK_SLOT.get(), slot);

        var names = stack.getOrDefault(YCDataComponents.ADDRESS_BOOK_NAMES.get(),
                Collections.<String, Component>emptyMap());
        Component name = names.get(String.valueOf(slot));
        if (name != null) {
            stack.set(DataComponents.CUSTOM_NAME, name);
        } else {
            stack.remove(DataComponents.CUSTOM_NAME);
        }

        return slot;
    }

    /** shift+滚轮由客户端上报，服务端改槽位后回显到动作栏 */
    public static void handleScroll(Player player, boolean offHand, double delta) {
        ItemStack stack = player.getItemInHand(offHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!(stack.getItem() instanceof AddressBook)) return;

        int slot = rotateSlot(stack, delta < 0);
        player.displayClientMessage(
                Component.translatable("tooltip.yukkuric_bundle.address_book.slot", slot)
                        .withStyle(ChatFormatting.GRAY), true);
    }

    /**
     * 点选方块 + 法向量。传送目标为被点选方块沿法向量相邻的那一格。
     */
    public record Location(ResourceKey<Level> dimension, BlockPos pos, Direction facing) {
        public static final Codec<Location> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Location::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(Location::pos),
                Direction.CODEC.fieldOf("facing").forGetter(Location::facing)
        ).apply(instance, Location::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Location> STREAM_CODEC = StreamCodec.composite(
                ResourceKey.streamCodec(Registries.DIMENSION), Location::dimension,
                BlockPos.STREAM_CODEC, Location::pos,
                Direction.STREAM_CODEC, Location::facing,
                Location::new
        );

        public BlockPos target() {
            return this.pos.relative(this.facing);
        }
    }

    /** 客户端拦截滚轮后通知服务端切换槽位 */
    public record ScrollPayload(boolean offHand, double delta) implements CustomPacketPayload {
        public static final Type<ScrollPayload> TYPE = new Type<>(YukkuriCBundleMod.modLoc(ID + "_scroll"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ScrollPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, ScrollPayload::offHand,
                ByteBufCodecs.DOUBLE, ScrollPayload::delta,
                ScrollPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}

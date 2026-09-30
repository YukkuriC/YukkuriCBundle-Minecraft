package io.yukkuric.bundle.block.be.cap;

import io.yukkuric.bundle.block.be.AbstractMengerSpongeDataBE;
import io.yukkuric.bundle.tag.YCTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.function.Predicate;

public class InfiniteInventory extends ItemStackHandler {
    AbstractMengerSpongeDataBE be;

    private final List<ItemStack> stacksSrc;
    private final int randRange;
    private int offsetStep = 0;
    Predicate<ItemStack> funcContains;

    public InfiniteInventory(AbstractMengerSpongeDataBE be, int randRange, List<ItemStack> stacksSrc, Predicate<ItemStack> funcContains) {
        super(randRange);
        this.randRange = randRange;
        this.be = be;
        this.stacksSrc = stacksSrc;
        this.funcContains = funcContains;
    }

    private int offsetSlot(int slot) {
        return (slot + offsetStep) % randRange;
    }
    private ItemStack ensureStack(int slot) {
        var stack = stacks.get(slot);
        var level = be.getLevel();
        if (level == null || level.isClientSide) return stack;
        if (stack.isEmpty()) {
            stack = stacksSrc.get((int) (Math.random() * stacksSrc.size()));
            stack = stack.copyWithCount(stack.getMaxStackSize());
            stacks.set(slot, stack);
        }
        return stack;
    }

    @Override
    public int getSlots() {
        return super.getSlots() + stacksSrc.size();
    }
    @Override
    public ItemStack getStackInSlot(int slot) {
        return getStackInSlot(slot, true);
    }
    public ItemStack getStackInSlot(int slot, boolean doOffset) {
        if (slot < 0) return ItemStack.EMPTY;
        if (slot >= randRange) return stacksSrc.get((slot - randRange) % stacksSrc.size()).copy();
        if (doOffset) slot = offsetSlot(slot);
        return ensureStack(slot);
    }
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot < 0) return ItemStack.EMPTY;
        if (slot >= randRange) return stacksSrc.get((slot - randRange) % stacksSrc.size()).copyWithCount(amount);
        slot = offsetSlot(slot);

        var ret = ensureStack(slot);
        if (!simulate) {
            stacks.set(slot, ItemStack.EMPTY);
            offsetStep = (offsetStep + 1) % randRange;
            ensureStack(slot);
            onContentsChanged(slot);
        }
        return ret;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (slot < 0 || slot >= randRange) return;
        super.setStackInSlot(slot, stack);
    }
    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.is(YCTags.Items.OreTargets) || (stack.getItem() instanceof BlockItem bi && bi.getBlock().defaultBlockState().is(YCTags.Blocks.OreTargets))) {
            return ItemStack.EMPTY;
        }
        if (slot < 0 || slot >= randRange) return stack;
        return super.insertItem(slot, stack, simulate);
    }
    @Override
    protected void onContentsChanged(int slot) {
        be.syncAndSave();
    }
}

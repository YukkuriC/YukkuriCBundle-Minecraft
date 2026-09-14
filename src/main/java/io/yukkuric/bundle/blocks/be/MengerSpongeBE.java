package io.yukkuric.bundle.blocks.be;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import static io.yukkuric.bundle.blocks.YCBlocks.BE_MENGER_SPONGE;

public class MengerSpongeBE extends BlockEntity {
    private ItemStack exemplar = ItemStack.EMPTY;
    private int total = 0;
    private FluidStack fluid = FluidStack.EMPTY;
    private int energy = 0;

    public MengerSpongeBE(BlockPos pos, BlockState state) {
        super(BE_MENGER_SPONGE.get(), pos, state);
    }

    public boolean isEmpty() {
        return total <= 0;
    }

    public ItemStack getExemplar() {
        return exemplar;
    }

    public FluidStack getFluid() {
        return fluid;
    }

    public void setFluid(FluidStack in) {
        this.fluid = in == null ? FluidStack.EMPTY : in.copy();
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider provider) {
        super.loadAdditional(nbt, provider);
        if (nbt.get("Exemplar") instanceof CompoundTag exemplarTag) {
            exemplar = ItemStack.parse(provider, exemplarTag).orElse(ItemStack.EMPTY);
        }
        total = nbt.getInt("Total");
        if (nbt.get("Fluid") instanceof CompoundTag fluidTag) {
            fluid = FluidStack.parse(provider, fluidTag).orElse(FluidStack.EMPTY);
        }
        energy = nbt.getInt("Energy");
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider provider) {
        super.saveAdditional(nbt, provider);
        saveCustomData(nbt, provider);
    }

    private void saveCustomData(CompoundTag nbt, HolderLookup.Provider provider) {
        if (!exemplar.isEmpty()) nbt.put("Exemplar", exemplar.save(provider));
        nbt.putInt("Total", total);
        if (!fluid.isEmpty()) nbt.put("Fluid", fluid.save(provider));
        nbt.putInt("Energy", energy);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        saveCustomData(tag, provider);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void syncAndSave() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public void dropContents() {
        if (level == null || level.isClientSide || exemplar.isEmpty() || total <= 0) return;
        Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), exemplar.copyWithCount(total));
        total = 0;
        setChanged();
    }

    //#region item
    public static class ItemCap implements IItemHandler {
        private final MengerSpongeBE be;

        public ItemCap(MengerSpongeBE be) {
            this.be = be;
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot != 0 || be.exemplar.isEmpty()) return ItemStack.EMPTY;
            return be.exemplar.copyWithCount(be.total);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || slot != 0) return stack;
            if (!be.exemplar.isEmpty() && !ItemStack.isSameItemSameComponents(be.exemplar, stack)) return stack;
            if (!simulate) {
                if (be.exemplar.isEmpty()) be.exemplar = stack.copyWithCount(1);
                be.total += stack.getCount() * 3;
                be.syncAndSave();
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0 || amount <= 0 || be.exemplar.isEmpty() || be.total <= 0) return ItemStack.EMPTY;
            int toExtract = Math.min(amount, be.total);
            ItemStack result = be.exemplar.copyWithCount(toExtract);
            if (!simulate) {
                be.total -= toExtract;
                if (be.total <= 0) be.exemplar = ItemStack.EMPTY;
                be.syncAndSave();
            }
            return result;
        }

        @Override
        public int getSlotLimit(int slot) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0;
        }
    }
    //#endregion

    //#region fluid
    public static class FluidCap implements IFluidHandler {
        private final MengerSpongeBE be;

        public FluidCap(MengerSpongeBE be) {
            this.be = be;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return be.getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return !stack.isEmpty();
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return 0;
            long desired = (long) resource.getAmount() * 3;
            if (be.fluid.isEmpty()) {
                int added = (int) Math.min(desired, Integer.MAX_VALUE);
                if (action.execute()) {
                    be.setFluid(new FluidStack(resource.getFluid(), added));
                    be.syncAndSave();
                }
                return added;
            }
            if (!FluidStack.isSameFluidSameComponents(be.fluid, resource)) return 0;
            int added = (int) Math.min(desired, (long) Integer.MAX_VALUE - be.fluid.getAmount());
            if (added > 0 && action.execute()) {
                be.fluid.grow(added);
                be.syncAndSave();
            }
            return added;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || be.fluid.isEmpty() || !FluidStack.isSameFluidSameComponents(be.fluid, resource))
                return FluidStack.EMPTY;
            return drainInternal(resource.getAmount(), action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain <= 0 || be.fluid.isEmpty()) return FluidStack.EMPTY;
            return drainInternal(maxDrain, action);
        }

        private FluidStack drainInternal(int amount, FluidAction action) {
            int toDrain = Math.min(amount, be.fluid.getAmount());
            FluidStack result = be.fluid.copyWithAmount(toDrain);
            if (toDrain > 0 && action.execute()) {
                be.fluid.shrink(toDrain);
                if (be.fluid.isEmpty()) be.setFluid(FluidStack.EMPTY);
                be.syncAndSave();
            }
            return result;
        }
    }
    //#endregion

    //#region energy
    public static class EnergyCap implements IEnergyStorage {
        private final MengerSpongeBE be;

        public EnergyCap(MengerSpongeBE be) {
            this.be = be;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (maxReceive <= 0) return 0;
            int added = (int) Math.min((long) maxReceive * 3, (long) Integer.MAX_VALUE - be.energy);
            if (added > 0 && !simulate) {
                be.energy += added;
                be.syncAndSave();
            }
            return added;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (maxExtract <= 0) return 0;
            int toExtract = Math.min(maxExtract, be.energy);
            if (toExtract > 0 && !simulate) {
                be.energy -= toExtract;
                be.syncAndSave();
            }
            return toExtract;
        }

        @Override
        public int getEnergyStored() {
            return be.energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
    //#endregion
}
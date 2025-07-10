package moe.paring.createlogisticsbackport.foundation.item;

import com.simibubi.create.foundation.blockEntity.SyncedBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

public class SmartInventory2 extends RecipeWrapper
        implements IItemHandlerModifiable, INBTSerializable<CompoundTag> {

    protected boolean extractionAllowed;
    protected boolean insertionAllowed;
    protected boolean stackNonStackables;
    protected SyncedStackHandler wrapped;
    protected int stackSize;

    public SmartInventory2(int slots, SyncedBlockEntity be) {
        this(slots, be, 64, false);
    }

    public SmartInventory2(int slots, SyncedBlockEntity be, BiPredicate<Integer, ItemStack> isValid) {
        this(slots, be, 64, false, isValid);
    }

    public SmartInventory2(int slots, SyncedBlockEntity be, int stackSize, boolean stackNonStackables) {
        this(new SyncedStackHandler(slots, be, stackNonStackables, stackSize), stackSize, stackNonStackables);
    }

    public SmartInventory2(int slots, SyncedBlockEntity be, int stackSize, boolean stackNonStackables, BiPredicate<Integer, ItemStack> isValid) {
        this(new SyncedStackHandler(slots, be, stackNonStackables, stackSize, isValid), stackSize, stackNonStackables);
    }

    public SmartInventory2(IItemHandlerModifiable inv, int stackSize, boolean stackNonStackables) {
        super(inv);
        this.stackNonStackables = stackNonStackables;
        insertionAllowed = true;
        extractionAllowed = true;
        this.stackSize = stackSize;
        wrapped = (SyncedStackHandler) inv;
    }

    public SmartInventory2 withMaxStackSize(int maxStackSize) {
        stackSize = maxStackSize;
        wrapped.stackSize = maxStackSize;
        return this;
    }

    public SmartInventory2 whenContentsChanged(Consumer<Integer> updateCallback) {
        ((SyncedStackHandler) inv).whenContentsChange(updateCallback);
        return this;
    }

    public SmartInventory2 allowInsertion() {
        insertionAllowed = true;
        return this;
    }

    public SmartInventory2 allowExtraction() {
        extractionAllowed = true;
        return this;
    }

    public SmartInventory2 forbidInsertion() {
        insertionAllowed = false;
        return this;
    }

    public SmartInventory2 forbidExtraction() {
        extractionAllowed = false;
        return this;
    }

    @Override
    public int getSlots() {
        return inv.getSlots();
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (!insertionAllowed)
            return stack;
        return inv.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!extractionAllowed)
            return ItemStack.EMPTY;
        if (stackNonStackables) {
            ItemStack extractItem = inv.extractItem(slot, amount, true);
            if (!extractItem.isEmpty() && extractItem.getMaxStackSize() < extractItem.getCount())
                amount = extractItem.getMaxStackSize();
        }
        return inv.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return Math.min(inv.getSlotLimit(slot), stackSize);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return inv.isItemValid(slot, stack);
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return inv.getStackInSlot(slot);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        inv.setStackInSlot(slot, stack);
    }

    public int getStackLimit(int slot, @Nonnull ItemStack stack) {
        return Math.min(getSlotLimit(slot), stack.getMaxStackSize());
    }

    @Override
    public CompoundTag serializeNBT() {
        return getInv().serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        getInv().deserializeNBT(nbt);
    }

    private SyncedStackHandler getInv() {
        return (SyncedStackHandler) inv;
    }

    protected static class SyncedStackHandler extends ItemStackHandler {

        private SyncedBlockEntity blockEntity;
        private boolean stackNonStackables;
        private int stackSize;
        private BiPredicate<Integer, ItemStack> isValid = super::isItemValid;
        private Consumer<Integer> updateCallback;

        public SyncedStackHandler(int slots, SyncedBlockEntity be, boolean stackNonStackables, int stackSize, BiPredicate<Integer, ItemStack> isValid) {
            this(slots, be, stackNonStackables, stackSize);
            this.isValid = isValid;
        }

        public SyncedStackHandler(int slots, SyncedBlockEntity be, boolean stackNonStackables, int stackSize) {
            super(slots);
            this.blockEntity = be;
            this.stackNonStackables = stackNonStackables;
            this.stackSize = stackSize;
        }

        @Override
        protected void onContentsChanged(int slot) {
            super.onContentsChanged(slot);
            if (updateCallback != null)
                updateCallback.accept(slot);
            blockEntity.notifyUpdate();
        }

        @Override
        public int getSlotLimit(int slot) {
            return Math.min(stackNonStackables ? 64 : super.getSlotLimit(slot), stackSize);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return isValid.test(slot, stack);
        }

        public void whenContentsChange(Consumer<Integer> updateCallback) {
            this.updateCallback = updateCallback;
        }

    }

}


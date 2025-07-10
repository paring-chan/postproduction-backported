package moe.paring.createlogisticsbackport.content.logistics.packager.repackager;

import com.simibubi.create.content.logistics.crate.BottomlessItemHandler;
import moe.paring.createlogisticsbackport.content.logistics.BigItemStack;
import moe.paring.createlogisticsbackport.content.logistics.box.PackageItem;
import moe.paring.createlogisticsbackport.content.logistics.packager.PackagerBlockEntity;
import moe.paring.createlogisticsbackport.content.logistics.packager.PackagerItemHandler;
import moe.paring.createlogisticsbackport.content.logistics.packager.PackagingRequest;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;

import java.util.List;

public class RepackagerBlockEntity extends PackagerBlockEntity {

	public PackageRepackageHelper repackageHelper;

	public RepackagerBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
		super(typeIn, pos, state);
		repackageHelper = new PackageRepackageHelper();
	}

	public boolean unwrapBox(ItemStack box, boolean simulate) {
		if (animationTicks > 0)
			return false;

		IItemHandler targetInv = targetInventory.getInventory();
		if (targetInv == null || targetInv instanceof PackagerItemHandler)
			return false;

		boolean targetIsCreativeCrate = targetInv instanceof BottomlessItemHandler;
		boolean anySpace = false;

		for (int slot = 0; slot < targetInv.getSlots(); slot++) {
			ItemStack remainder = targetInv.insertItem(slot, box, simulate);
			if (!remainder.isEmpty())
				continue;
			anySpace = true;
			break;
		}

		if (!targetIsCreativeCrate && !anySpace)
			return false;
		if (simulate)
			return true;

		previouslyUnwrapped = box;
		animationInward = true;
		animationTicks = CYCLE;
		notifyUpdate();
		return true;
	}

	@Override
	public void recheckIfLinksPresent() {
	}

	@Override
	public boolean redstoneModeActive() {
		return true;
	}

	public void attemptToSend(List<PackagingRequest> queuedRequests) {
		if (!heldBox.isEmpty() || animationTicks != 0 || buttonCooldown > 0)
			return;
		if (!queuedExitingPackages.isEmpty())
			return;

		IItemHandler targetInv = targetInventory.getInventory();
		if (targetInv == null || targetInv instanceof PackagerItemHandler)
			return;

		attemptToRepackage(targetInv);
		if (heldBox.isEmpty())
			return;

		updateSignAddress();
		if (!signBasedAddress.isBlank())
			PackageItem.addAddress(heldBox, signBasedAddress);
	}

	protected void attemptToRepackage(IItemHandler targetInv) {
		repackageHelper.clear();
		int completedOrderId = -1;

		for (int slot = 0; slot < targetInv.getSlots(); slot++) {
			ItemStack extracted = targetInv.extractItem(slot, 1, true);
			if (extracted.isEmpty() || !PackageItem.isPackage(extracted))
				continue;

			if (!repackageHelper.isFragmented(extracted)) {
				targetInv.extractItem(slot, 1, false);
				heldBox = extracted.copy();
				animationInward = false;
				animationTicks = CYCLE;
				notifyUpdate();
				return;
			}

			completedOrderId = repackageHelper.addPackageFragment(extracted);
			if (completedOrderId != -1)
				break;
		}

		if (completedOrderId == -1)
			return;

		List<BigItemStack> boxesToExport = repackageHelper.repack(completedOrderId, level.getRandom());

		for (int slot = 0; slot < targetInv.getSlots(); slot++) {
			ItemStack extracted = targetInv.extractItem(slot, 1, true);
			if (extracted.isEmpty() || !PackageItem.isPackage(extracted))
				continue;
			if (PackageItem.getOrderId(extracted) != completedOrderId)
				continue;
			targetInv.extractItem(slot, 1, false);
		}

		if (boxesToExport.isEmpty())
			return;

		queuedExitingPackages.addAll(boxesToExport);
		notifyUpdate();
	}

}

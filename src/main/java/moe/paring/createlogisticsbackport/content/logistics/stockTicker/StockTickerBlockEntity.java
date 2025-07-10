package moe.paring.createlogisticsbackport.content.logistics.stockTicker;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllPackets;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.compat.computercraft.AbstractComputerBehaviour;
import com.simibubi.create.compat.computercraft.ComputerCraftProxy;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import com.simibubi.create.content.equipment.goggles.IHaveHoveringInformation;
import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.foundation.item.SmartInventory;
import com.simibubi.create.foundation.utility.Iterate;
import com.simibubi.create.foundation.utility.Lang;
import com.simibubi.create.foundation.utility.NBTHelper;
import moe.paring.createlogisticsbackport.content.logistics.BigItemStack;
import moe.paring.createlogisticsbackport.content.logistics.packager.IdentifiedInventory;
import moe.paring.createlogisticsbackport.content.logistics.packager.InventorySummary;
import moe.paring.createlogisticsbackport.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import moe.paring.createlogisticsbackport.content.logistics.packagerLink.WiFiParticle;
import moe.paring.createlogisticsbackport.registry.ExtraPackets;
import moe.paring.createlogisticsbackport.registry.ExtraSoundEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.IntStream;

public class StockTickerBlockEntity extends StockCheckingBlockEntity implements IHaveHoveringInformation {

	public AbstractComputerBehaviour computerBehaviour;

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		behaviours.add(computerBehaviour = ComputerCraftProxy.behaviour(this));
	}

	// Player-interface Feature
	protected List<List<BigItemStack>> lastClientsideStockSnapshot;
	protected InventorySummary lastClientsideStockSnapshotAsSummary;
	protected List<BigItemStack> newlyReceivedStockSnapshot;
	protected String previouslyUsedAddress;
	protected int activeLinks;
	protected int ticksSinceLastUpdate;
	protected List<ItemStack> categories;
	protected Map<UUID, List<Integer>> hiddenCategoriesByPlayer;

	// Shop feature
	protected SmartInventory receivedPayments;
	protected LazyOptional<IItemHandler> capability;

	public StockTickerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		previouslyUsedAddress = "";
		receivedPayments = new SmartInventory(27, this, 64, false);
		capability = LazyOptional.of(() -> receivedPayments);
		categories = new ArrayList<>();
		hiddenCategoriesByPlayer = new HashMap<>();
	}

	public void refreshClientStockSnapshot() {
		ticksSinceLastUpdate = 0;
		ExtraPackets.getChannel()
			.sendToServer(new LogisticalStockRequestPacket(worldPosition));
	}

	public IItemHandler getReceivedPaymentsHandler() {
		return receivedPayments;
	}

	public List<List<BigItemStack>> getClientStockSnapshot() {
		return lastClientsideStockSnapshot;
	}

	public InventorySummary getLastClientsideStockSnapshotAsSummary() {
		return lastClientsideStockSnapshotAsSummary;
	}

	public int getTicksSinceLastUpdate() {
		return ticksSinceLastUpdate;
	}

	@Override
	public boolean broadcastPackageRequest(LogisticallyLinkedBehaviour.RequestType type, PackageOrderWithCrafts order, IdentifiedInventory ignoredHandler, String address) {
		boolean result = super.broadcastPackageRequest(type, order, ignoredHandler, address);
		previouslyUsedAddress = address;
		notifyUpdate();
		return result;
	}

	@Override
	public InventorySummary getRecentSummary() {
		InventorySummary recentSummary = super.getRecentSummary();
		int contributingLinks = recentSummary.contributingLinks;
		if (activeLinks != contributingLinks && !isRemoved()) {
			activeLinks = contributingLinks;
			sendData();
		}
		return recentSummary;
	}

	@Override
	public void tick() {
		super.tick();
		if (level.isClientSide()) {
			if (ticksSinceLastUpdate < 100)
				ticksSinceLastUpdate += 1;
			return;
		}
	}

	@Override
	protected void write(CompoundTag tag, boolean clientPacket) {
		super.write(tag, clientPacket);
		tag.putString("PreviousAddress", previouslyUsedAddress);
		tag.put("ReceivedPayments", receivedPayments.serializeNBT());
		tag.put("Categories", NBTHelper.writeItemList(categories));
		tag.put("HiddenCategories", NBTHelper.writeCompoundList(hiddenCategoriesByPlayer.entrySet(), e -> {
			CompoundTag c = new CompoundTag();
			c.putUUID("Id", e.getKey());
			c.putIntArray("Indices", e.getValue());
			return c;
		}));

		if (clientPacket)
			tag.putInt("ActiveLinks", activeLinks);
	}

	@Override
	protected void read(CompoundTag tag, boolean clientPacket) {
		super.read(tag, clientPacket);
		previouslyUsedAddress = tag.getString("PreviousAddress");
		receivedPayments.deserializeNBT(tag.getCompound("ReceivedPayments"));
		categories = NBTHelper.readItemList(tag.getList("Categories", Tag.TAG_COMPOUND));
		categories.removeIf(stack -> !stack.isEmpty() && !(stack.getItem() instanceof FilterItem));
		hiddenCategoriesByPlayer.clear();

		NBTHelper.iterateCompoundList(tag.getList("HiddenCategories", Tag.TAG_COMPOUND),
			c -> hiddenCategoriesByPlayer.put(c.getUUID("Id"), IntStream.of(c.getIntArray("Indices"))
				.boxed()
				.toList()));

		if (clientPacket)
			activeLinks = tag.getInt("ActiveLinks");
	}

	public void receiveStockPacket(List<BigItemStack> stacks, boolean endOfTransmission) {
		if (newlyReceivedStockSnapshot == null)
			newlyReceivedStockSnapshot = new ArrayList<>();
		newlyReceivedStockSnapshot.addAll(stacks);

		if (!endOfTransmission)
			return;

		lastClientsideStockSnapshotAsSummary = new InventorySummary();
		lastClientsideStockSnapshot = new ArrayList<>();

		for (BigItemStack bigStack : newlyReceivedStockSnapshot)
			lastClientsideStockSnapshotAsSummary.add(bigStack);

		for (ItemStack filter : categories) {
			List<BigItemStack> inCategory = new ArrayList<>();
			if (!filter.isEmpty()) {
				FilterItemStack filterItemStack = FilterItemStack.of(filter);
				for (Iterator<BigItemStack> iterator = newlyReceivedStockSnapshot.iterator(); iterator.hasNext(); ) {
					BigItemStack bigStack = iterator.next();
					if (!filterItemStack.test(level, bigStack.stack))
						continue;
					inCategory.add(bigStack);
					iterator.remove();
				}
			}
			lastClientsideStockSnapshot.add(inCategory);
		}

		List<BigItemStack> unsorted = new ArrayList<>(newlyReceivedStockSnapshot);
		lastClientsideStockSnapshot.add(unsorted);
		newlyReceivedStockSnapshot = null;
	}

	public boolean isKeeperPresent() {
		for (int yOffset : Iterate.zeroAndOne) {
			for (Direction side : Iterate.horizontalDirections) {
				BlockPos seatPos = worldPosition.below(yOffset)
					.relative(side);
				for (SeatEntity seatEntity : level.getEntitiesOfClass(SeatEntity.class, new AABB(seatPos)))
					if (seatEntity.isVehicle())
						return true;
				if (yOffset == 0 && AllBlockEntityTypes.HEATER.is(level.getBlockEntity(seatPos)))
					return true;
			}
		}
		return false;
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public boolean addToTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		if (receivedPayments.isEmpty())
			return false;
		if (!behaviour.mayAdministrate(Minecraft.getInstance().player))
			return false;

		Lang.translate("stock_ticker.contains_payments")
			.style(ChatFormatting.WHITE)
			.forGoggles(tooltip);

		InventorySummary summary = new InventorySummary();
		for (int i = 0; i < receivedPayments.getSlots(); i++)
			summary.add(receivedPayments.getStackInSlot(i));
		for (BigItemStack entry : summary.getStacksByCount())
			Lang.builder()
				.text(Component.translatable(entry.stack.getDescriptionId())
					.getString() + " x" + entry.count)
				.style(ChatFormatting.GREEN)
				.forGoggles(tooltip);

		Lang.translate("stock_ticker.click_to_retrieve")
			.style(ChatFormatting.GRAY)
			.forGoggles(tooltip);
		return true;
	}

	@Override
	public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
		if (isItemHandlerCap(cap))
			return capability.cast();
		if (computerBehaviour.isPeripheralCap(cap))
			return computerBehaviour.getPeripheralCapability();

		return super.getCapability(cap, side);
	}

	@Override
	public void destroy() {
		ItemHelper.dropContents(level, worldPosition, receivedPayments);
		for (ItemStack filter : categories)
			if (!filter.isEmpty() && filter.getItem() instanceof FilterItem)
				Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
					filter);
		super.destroy();
	}

	@Override
	public void invalidate() {
		capability.invalidate();
		super.invalidate();
	}

	public void playEffect() {
		ExtraSoundEvents.STOCK_LINK.playAt(level, worldPosition, 1.0f, 1.0f, false);
		Vec3 vec3 = Vec3.atCenterOf(worldPosition);
		level.addParticle(new WiFiParticle.Data(), vec3.x, vec3.y, vec3.z, 1, 1, 1);
	}

	public class CategoryMenuProvider implements MenuProvider {

		@Override
		public AbstractContainerMenu createMenu(int pContainerId, Inventory pPlayerInventory, Player pPlayer) {
			return StockKeeperCategoryMenu.create(pContainerId, pPlayerInventory, StockTickerBlockEntity.this);
		}

		@Override
		public Component getDisplayName() {
			return Component.empty();
		}

	}

	public class RequestMenuProvider implements MenuProvider {

		@Override
		public AbstractContainerMenu createMenu(int pContainerId, Inventory pPlayerInventory, Player pPlayer) {
			return StockKeeperRequestMenu.create(pContainerId, pPlayerInventory, StockTickerBlockEntity.this);
		}

		@Override
		public Component getDisplayName() {
			return Component.empty();
		}

	}

}

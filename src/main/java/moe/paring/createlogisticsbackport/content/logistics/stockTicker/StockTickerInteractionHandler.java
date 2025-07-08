package moe.paring.createlogisticsbackport.content.logistics.stockTicker;

import com.simibubi.create.AllEntityTypes;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import com.simibubi.create.foundation.utility.Couple;
import com.simibubi.create.foundation.utility.Iterate;
import com.simibubi.create.foundation.utility.Lang;
import moe.paring.createlogisticsbackport.CreateLogisticsBackport;
import moe.paring.createlogisticsbackport.content.logistics.BigItemStack;
import moe.paring.createlogisticsbackport.content.logistics.packager.InventorySummary;
import moe.paring.createlogisticsbackport.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import moe.paring.createlogisticsbackport.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import moe.paring.createlogisticsbackport.content.logistics.tableCloth.ShoppingListItem;
import moe.paring.createlogisticsbackport.content.logistics.tableCloth.ShoppingListItem.ShoppingList;
import moe.paring.createlogisticsbackport.registry.ExtraEntityTypes;
import moe.paring.createlogisticsbackport.registry.ExtraItems;
import moe.paring.createlogisticsbackport.registry.ExtraSoundEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber
public class StockTickerInteractionHandler {

	@SubscribeEvent
	public static void interactWithLogisticsManager(EntityInteractSpecific event) {
		Entity entity = event.getTarget();
		Player player = event.getEntity();
		if (player == null || entity == null)
			return;
		if (player.isSpectator())
			return;

		Level level = event.getLevel();
		BlockPos targetPos = getStockTickerPosition(entity);
		if (targetPos == null)
			return;

		if (interactWithLogisticsManagerAt(player, level, targetPos)) {
			event.setCancellationResult(InteractionResult.SUCCESS);
			event.setCanceled(true);
		}
	}

	public static boolean interactWithLogisticsManagerAt(Player player, Level level, BlockPos targetPos) {
		ItemStack mainHandItem = player.getMainHandItem();

		if (ExtraItems.SHOPPING_LIST.isIn(mainHandItem)) {
			interactWithShop(player, level, targetPos, mainHandItem);
			return true;
		}

		if (level.isClientSide())
			return true;
		if (!(level.getBlockEntity(targetPos) instanceof StockTickerBlockEntity stbe))
			return false;

		if (!stbe.behaviour.mayInteract(player)) {
			player.displayClientMessage(Lang.translate("stock_keeper.locked")
				.style(ChatFormatting.RED)
				.component(), true);
			return true;
		}

		if (player instanceof ServerPlayer sp) {
			boolean showLockOption =
				stbe.behaviour.mayAdministrate(player) && CreateLogisticsBackport.LOGISTICS.isLockable(stbe.behaviour.freqId);
			boolean isCurrentlyLocked = CreateLogisticsBackport.LOGISTICS.isLocked(stbe.behaviour.freqId);

			NetworkHooks.openScreen(sp, stbe.new RequestMenuProvider(), buf -> {
				buf.writeBoolean(showLockOption);
				buf.writeBoolean(isCurrentlyLocked);
				buf.writeBlockPos(targetPos);
			});
			stbe.getRecentSummary()
				.divideAndSendTo(sp, targetPos);
		}

		return true;
	}

	private static void interactWithShop(Player player, Level level, BlockPos targetPos, ItemStack mainHandItem) {
		if (level.isClientSide())
			return;
		if (!(level.getBlockEntity(targetPos) instanceof StockTickerBlockEntity tickerBE))
			return;

		ShoppingList list = ShoppingListItem.getList(mainHandItem);
		if (list == null)
			return;

		if (!tickerBE.behaviour.freqId.equals(list.shopNetwork())) {
			AllSoundEvents.DENY.playOnServer(level, player.blockPosition());
			Lang.translate("stock_keeper.wrong_network")
				.style(ChatFormatting.RED)
				.sendStatus(player);
			return;
		}

		Couple<InventorySummary> bakeEntries = list.bakeEntries(level, null);
		InventorySummary paymentEntries = bakeEntries.getSecond();
		InventorySummary orderEntries = bakeEntries.getFirst();
		PackageOrder order = new PackageOrder(orderEntries.getStacksByCount());

		// Must be up-to-date
		tickerBE.getAccurateSummary();

		// Check stock levels
		InventorySummary recentSummary = tickerBE.getRecentSummary();
		for (BigItemStack entry : order.stacks()) {
			if (recentSummary.getCountOf(entry.stack) >= entry.count)
				continue;

			AllSoundEvents.DENY.playOnServer(level, player.blockPosition());
			Lang.translate("stock_keeper.stock_level_too_low")
				.style(ChatFormatting.RED)
				.sendStatus(player);
			return;
		}

		// Check space in stock ticker
		int occupiedSlots = 0;
		for (BigItemStack entry : paymentEntries.getStacksByCount())
			occupiedSlots += Mth.ceil(entry.count / (float) entry.stack.getMaxStackSize());
		for (int i = 0; i < tickerBE.receivedPayments.getSlots(); i++)
			if (tickerBE.receivedPayments.getStackInSlot(i)
				.isEmpty())
				occupiedSlots--;

		if (occupiedSlots > 0) {
			AllSoundEvents.DENY.playOnServer(level, player.blockPosition());
			Lang.translate("stock_keeper.cash_register_full")
				.style(ChatFormatting.RED)
				.sendStatus(player);
			return;
		}

		// Transfer payment to stock ticker
		for (boolean simulate : Iterate.trueAndFalse) {
			InventorySummary tally = paymentEntries.copy();
			List<ItemStack> toTransfer = new ArrayList<>();

			for (int i = 0; i < player.getInventory().items.size(); i++) {
				ItemStack item = player.getInventory()
					.getItem(i);
				if (item.isEmpty())
					continue;
				int countOf = tally.getCountOf(item);
				if (countOf == 0)
					continue;
				int toRemove = Math.min(item.getCount(), countOf);
				tally.add(item, -toRemove);

				if (simulate)
					continue;

				int newStackSize = item.getCount() - toRemove;
				player.getInventory()
					.setItem(i, newStackSize == 0 ? ItemStack.EMPTY : item.copyWithCount(newStackSize));
				toTransfer.add(item.copyWithCount(toRemove));
			}

			if (simulate && tally.getTotalCount() != 0) {
				AllSoundEvents.DENY.playOnServer(level, player.blockPosition());
				Lang.translate("stock_keeper.too_broke")
					.style(ChatFormatting.RED)
					.sendStatus(player);
				return;
			}

			if (simulate)
				continue;

			toTransfer.forEach(s -> ItemHandlerHelper.insertItemStacked(tickerBE.receivedPayments, s, false));
		}

		tickerBE.broadcastPackageRequest(RequestType.PLAYER, order, null, ShoppingListItem.getAddress(mainHandItem));
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		if (!order.isEmpty())
			ExtraSoundEvents.STOCK_TICKER_TRADE.playOnServer(level, tickerBE.getBlockPos());
	}

	public static BlockPos getStockTickerPosition(Entity entity) {
		Entity rootVehicle = entity.getRootVehicle();
		if (!(rootVehicle instanceof SeatEntity))
			return null;
		if (!(entity instanceof LivingEntity))
			return null;
		if (ExtraEntityTypes.PACKAGE.is(entity))
			return null;

		BlockPos pos = entity.blockPosition();
		int stations = 0;
		BlockPos targetPos = null;

		for (Direction d : Iterate.horizontalDirections) {
			for (int y : Iterate.zeroAndOne) {
				BlockPos workstationPos = pos.relative(d)
					.above(y);
				if (!(entity.level()
					.getBlockState(workstationPos)
					.getBlock() instanceof StockTickerBlock))
					continue;
				targetPos = workstationPos;
				stations++;
			}
		}

		if (stations != 1)
			return null;
		return targetPos;
	}

}

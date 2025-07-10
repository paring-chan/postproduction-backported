package moe.paring.createlogisticsbackport.content.logistics.factoryBoard;

import com.simibubi.create.AllMenuTypes;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.foundation.gui.menu.GhostItemMenu;
import com.simibubi.create.foundation.utility.Lang;
import moe.paring.createlogisticsbackport.registry.ExtraMenuTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

public class FactoryPanelSetItemMenu extends GhostItemMenu<FactoryPanelBehaviour> {

	public FactoryPanelSetItemMenu(MenuType<?> type, int id, Inventory inv, FactoryPanelBehaviour contentHolder) {
		super(type, id, inv, contentHolder);
	}

	public FactoryPanelSetItemMenu(MenuType<?> type, int id, Inventory inv, FriendlyByteBuf extraData) {
		super(type, id, inv, extraData);
	}

	public static FactoryPanelSetItemMenu create(int id, Inventory inv, FactoryPanelBehaviour be) {
		return new FactoryPanelSetItemMenu(ExtraMenuTypes.FACTORY_PANEL_SET_ITEM.get(), id, inv, be);
	}

	@Override
	protected ItemStackHandler createGhostInventory() {
		return new ItemStackHandler(1);
	}

	@Override
	protected boolean allowRepeats() {
		return true;
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	protected FactoryPanelBehaviour createOnClient(FriendlyByteBuf extraData) {
		FactoryPanelPosition pos = FactoryPanelPosition.receive(extraData);
		return FactoryPanelBehaviour.at(Minecraft.getInstance().level, pos);
	}

	@Override
	protected void addSlots() {
		int playerX = 13;
		int playerY = 112;
		int slotX = 74;
		int slotY = 28;

		addPlayerSlots(playerX, playerY);
		addSlot(new SlotItemHandler(ghostInventory, 0, slotX, slotY));
	}

	@Override
	protected void saveData(FactoryPanelBehaviour contentHolder) {
		if (!contentHolder.setFilter(ghostInventory.getStackInSlot(0))) {
			player.displayClientMessage(Lang.translateDirect("logistics.filter.invalid_item"), true);
			AllSoundEvents.DENY.playOnServer(player.level(), player.blockPosition(), 1, 1);
			return;
		}
		player.level()
			.playSound(null, contentHolder.getPos(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, .25f, .1f);
	}

}

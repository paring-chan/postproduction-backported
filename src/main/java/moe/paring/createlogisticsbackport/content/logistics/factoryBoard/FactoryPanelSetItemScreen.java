package moe.paring.createlogisticsbackport.content.logistics.factoryBoard;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.element.GuiGameElement;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.utility.Lang;
import moe.paring.createlogisticsbackport.registry.ExtraBlocks;
import moe.paring.createlogisticsbackport.registry.ExtraGuiTextures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;

public class FactoryPanelSetItemScreen extends AbstractSimiContainerScreen<FactoryPanelSetItemMenu> {

	private IconButton confirmButton;
	private List<Rect2i> extraAreas = Collections.emptyList();

	public FactoryPanelSetItemScreen(FactoryPanelSetItemMenu container, Inventory inv, Component title) {
		super(container, inv, title);
	}

	@Override
	protected void init() {
		int bgHeight = ExtraGuiTextures.FACTORY_GAUGE_SET_ITEM.getHeight();
		int bgWidth = ExtraGuiTextures.FACTORY_GAUGE_SET_ITEM.getWidth();
		setWindowSize(bgWidth, bgHeight + AllGuiTextures.PLAYER_INVENTORY.height);
		super.init();
		clearWidgets();
		int x = getGuiLeft();
		int y = getGuiTop();


		confirmButton = new IconButton(x + bgWidth - 40, y + bgHeight - 25, AllIcons.I_CONFIRM);
		confirmButton.withCallback(() -> minecraft.player.closeContainer());
		addRenderableWidget(confirmButton);
		
		extraAreas = List.of(new Rect2i(x + bgWidth, y + bgHeight - 30, 40, 20));
	}

	@Override
	protected void renderBg(GuiGraphics pGuiGraphics, float pPartialTick, int pMouseX, int pMouseY) {
		int x = getGuiLeft();
		int y = getGuiTop();
		ExtraGuiTextures.FACTORY_GAUGE_SET_ITEM.render(pGuiGraphics, x - 5, y);
		renderPlayerInventory(pGuiGraphics, x + 5, y + 94);

		ItemStack stack = ExtraBlocks.FACTORY_GAUGE.asStack();
		Component title = Lang.translate("gui.factory_panel.place_item_to_monitor")
			.component();
		pGuiGraphics.drawString(font, title, x + imageWidth / 2 - font.width(title) / 2 - 5, y + 4, 0x3D3C48, false);

		GuiGameElement.of(stack)
			.scale(3)
			.render(pGuiGraphics, x + 180, y + 48);
	}
	
	@Override
	public List<Rect2i> getExtraAreas() {
		return extraAreas;
	}

}

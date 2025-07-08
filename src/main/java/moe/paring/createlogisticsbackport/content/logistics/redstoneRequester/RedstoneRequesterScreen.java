package moe.paring.createlogisticsbackport.content.logistics.redstoneRequester;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllPackets;
import com.simibubi.create.content.trains.station.NoShadowFontWrapper;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.element.GuiGameElement;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.utility.Lang;
import moe.paring.createlogisticsbackport.content.logistics.AddressEditBox;
import moe.paring.createlogisticsbackport.content.logistics.BigItemStack;
import moe.paring.createlogisticsbackport.polyfill.NewIconButton;
import moe.paring.createlogisticsbackport.registry.ExtraBlocks;
import moe.paring.createlogisticsbackport.registry.ExtraGuiTextures;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RedstoneRequesterScreen extends AbstractSimiContainerScreen<RedstoneRequesterMenu> {

	private EditBox addressBox;
	private IconButton confirmButton;
	private List<Rect2i> extraAreas = Collections.emptyList();
	private List<Integer> amounts = new ArrayList<>();

	private NewIconButton dontAllowPartial;
	private NewIconButton allowPartial;

	public RedstoneRequesterScreen(RedstoneRequesterMenu container, Inventory inv, Component title) {
		super(container, inv, title);

		for (int i = 0; i < 9; i++)
			amounts.add(1);

		List<BigItemStack> stacks = menu.contentHolder.encodedRequest.stacks();
		for (int i = 0; i < stacks.size(); i++)
			amounts.set(i, Math.max(1, stacks.get(i).count));
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		addressBox.tick();
		for (int i = 0; i < amounts.size(); i++)
			if (menu.ghostInventory.getStackInSlot(i)
				.isEmpty())
				amounts.set(i, 1);
	}

	@Override
	protected void init() {
		int bgHeight = ExtraGuiTextures.REDSTONE_REQUESTER.getHeight();
		int bgWidth = ExtraGuiTextures.REDSTONE_REQUESTER.getWidth();
		setWindowSize(bgWidth, bgHeight + AllGuiTextures.PLAYER_INVENTORY.height);
		super.init();
		clearWidgets();
		int x = getGuiLeft();
		int y = getGuiTop();

		if (addressBox == null) {
			addressBox = new AddressEditBox(this, new NoShadowFontWrapper(font), x + 55, y + 68, 110, 10, false);
			addressBox.setValue(menu.contentHolder.encodedTargetAdress);
			addressBox.setTextColor(0x555555);
		}
		addRenderableWidget(addressBox);

		confirmButton = new IconButton(x + bgWidth - 30, y + bgHeight - 25, AllIcons.I_CONFIRM);
		confirmButton.withCallback(() -> minecraft.player.closeContainer());
		addRenderableWidget(confirmButton);

		allowPartial = new NewIconButton(x + 12, y + bgHeight - 25, ExtraGuiTextures.I_PARTIAL_REQUESTS);
		allowPartial.withCallback(() -> {
			allowPartial.green = true;
			dontAllowPartial.green = false;
		});
		allowPartial.green = menu.contentHolder.allowPartialRequests;
		allowPartial.setToolTip(Lang.translate("gui.redstone_requester.allow_partial")
			.component());
		addRenderableWidget(allowPartial);

		dontAllowPartial = new IconButton(x + 12 + 18, y + bgHeight - 25, ExtraGuiTextures.I_FULL_REQUESTS);
		dontAllowPartial.withCallback(() -> {
			allowPartial.green = false;
			dontAllowPartial.green = true;
		});
		dontAllowPartial.green = !menu.contentHolder.allowPartialRequests;
		dontAllowPartial.setToolTip(Lang.translate("gui.redstone_requester.dont_allow_partial")
			.component());
		addRenderableWidget(dontAllowPartial);

		extraAreas = List.of(new Rect2i(x + bgWidth, y + bgHeight - 50, 70, 60));
	}

	@Override
	protected void renderBg(GuiGraphics pGuiGraphics, float pPartialTick, int pMouseX, int pMouseY) {
		int x = getGuiLeft();
		int y = getGuiTop();
		ExtraGuiTextures.REDSTONE_REQUESTER.render(pGuiGraphics, x + 3, y);
		renderPlayerInventory(pGuiGraphics, x - 3, y + 124);

		ItemStack stack = ExtraBlocks.REDSTONE_REQUESTER.asStack();
		Component title = Lang.text(stack.getHoverName()
			.getString())
			.component();
		pGuiGraphics.drawString(font, title, x + 117 - font.width(title) / 2, y + 4, 0x3D3C48, false);

		GuiGameElement.of(stack)
			.scale(3)
			.render(pGuiGraphics, x + 245, y + 80);
	}

	@Override
	protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		super.renderForeground(graphics, mouseX, mouseY, partialTicks);
		int x = getGuiLeft();
		int y = getGuiTop();

		for (int i = 0; i < amounts.size(); i++) {
			int inputX = x + 27 + i * 20;
			int inputY = y + 28;
			ItemStack itemStack = menu.ghostInventory.getStackInSlot(i);
			if (itemStack.isEmpty())
				continue;
			PoseStack ms = graphics.pose();
			ms.pushPose();
			ms.translate(0, 0, 100);
			graphics.renderItemDecorations(font, itemStack, inputX, inputY, "" + amounts.get(i));
			ms.popPose();
		}

		if (addressBox.isHovered() && !addressBox.isFocused()) {
			if (addressBox.getValue()
				.isBlank())
				graphics.renderComponentTooltip(font,
					List.of(Lang.translate("gui.redstone_requester.requester_address")
						.color(ScrollInput.HEADER_RGB)
						.component(),
						Lang.translate("gui.redstone_requester.requester_address_tip")
							.style(ChatFormatting.GRAY)
							.component(),
						Lang.translate("gui.redstone_requester.requester_address_tip_1")
							.style(ChatFormatting.GRAY)
							.component(),
						Lang.translate("gui.schedule.lmb_edit")
							.style(ChatFormatting.DARK_GRAY)
							.style(ChatFormatting.ITALIC)
							.component()),
					mouseX, mouseY);
			else
				graphics.renderComponentTooltip(font,
					List.of(Lang.translate("gui.redstone_requester.requester_address_given")
						.color(ScrollInput.HEADER_RGB)
						.component(),
						Lang.text("'" + addressBox.getValue() + "'")
							.style(ChatFormatting.GRAY)
							.component()),
					mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double pDelta) {
		int x = getGuiLeft();
		int y = getGuiTop();

		if (addressBox.mouseScrolled(mouseX, mouseY, pDelta))
			return true;

		for (int i = 0; i < amounts.size(); i++) {
			int inputX = x + 27 + i * 20;
			int inputY = y + 28;
			if (mouseX >= inputX && mouseX < inputX + 16 && mouseY >= inputY && mouseY < inputY + 16) {
				ItemStack itemStack = menu.ghostInventory.getStackInSlot(i);
				if (itemStack.isEmpty())
					return true;
				amounts.set(i,
					Mth.clamp((int) (amounts.get(i) + Math.signum(pDelta) * (hasShiftDown() ? 10 : 1)), 1, 256));
				return true;
			}
		}

		return super.mouseScrolled(mouseX, mouseY, pDelta);
	}

	@Override
	protected List<Component> getTooltipFromContainerItem(ItemStack pStack) {
		List<Component> tooltip = super.getTooltipFromContainerItem(pStack);
		if (!(hoveredSlot instanceof SlotItemHandler))
			return tooltip;

		int slotIndex = this.hoveredSlot.getSlotIndex();
		if (slotIndex >= amounts.size())
			return tooltip;

		return List.of(Lang.translate("gui.factory_panel.send_item", Lang.itemName(pStack)
			.add(Lang.text(" x" + amounts.get(slotIndex))))
			.color(ScrollInput.HEADER_RGB)
			.component(),
			Lang.translate("gui.factory_panel.scroll_to_change_amount")
				.style(ChatFormatting.DARK_GRAY)
				.style(ChatFormatting.ITALIC)
				.component(),
			Lang.translate("gui.scrollInput.shiftScrollsFaster")
				.style(ChatFormatting.DARK_GRAY)
				.style(ChatFormatting.ITALIC)
				.component());
	}

	@Override
	public List<Rect2i> getExtraAreas() {
		return extraAreas;
	}

	@Override
	public void removed() {
		AllPackets.getChannel()
			.sendToServer(new RedstoneRequesterConfigurationPacket(menu.contentHolder.getBlockPos(),
				addressBox.getValue(), allowPartial.green, amounts));
		super.removed();
	}

}

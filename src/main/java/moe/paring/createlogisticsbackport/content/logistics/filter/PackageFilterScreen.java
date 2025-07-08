package moe.paring.createlogisticsbackport.content.logistics.filter;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPackets;
import com.simibubi.create.content.logistics.filter.AbstractFilterScreen;
import com.simibubi.create.content.logistics.filter.AttributeFilterMenu;
import com.simibubi.create.content.logistics.filter.AttributeFilterScreen;
import com.simibubi.create.content.logistics.filter.FilterScreenPacket;
import com.simibubi.create.content.logistics.filter.FilterScreenPacket.Option;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.element.GuiGameElement;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.Indicator;
import moe.paring.createlogisticsbackport.content.logistics.AddressEditBox;
import moe.paring.createlogisticsbackport.content.logistics.box.PackageStyles;
import moe.paring.createlogisticsbackport.registry.ExtraGuiTextures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

public class PackageFilterScreen extends AbstractFilterScreen<PackageFilterMenu> {

	private EditBox addressBox;
	private boolean deferFocus;

	public PackageFilterScreen(PackageFilterMenu menu, Inventory inv, Component title) {
		super(menu, inv, title, ExtraGuiTextures.PACKAGE_FILTER);
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		if (deferFocus) {
			deferFocus = false;
			setFocused(addressBox);
		}
		addressBox.tick();
	}

	@Override
	protected void init() {
		setWindowOffset(-11, 7);
		super.init();

		int x = leftPos;
		int y = topPos;

		addressBox = new AddressEditBox(this, this.font, x + 44, y + 28, 129, 9, false);
		addressBox.setTextColor(0xffffff);
		addressBox.setValue(menu.address);
		addressBox.setResponder(this::onAddressEdited);
		addRenderableWidget(addressBox);

		setFocused(addressBox);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		super.render(graphics, mouseX, mouseY, partialTicks);

		PoseStack ms = graphics.pose();
		ms.pushPose();
		ms.translate(leftPos + 16, topPos + 23, 0);
		GuiGameElement.of(PackageStyles.getDefaultBox())
			.render(graphics);
		ms.popPose();
	}

	public void onAddressEdited(String s) {
		menu.address = s;
		CompoundTag tag = new CompoundTag();
		tag.putString("Address", s);
		AllPackets.getChannel()
			.sendToServer(new FilterScreenPacket(Option.UPDATE_ADDRESS, tag));
	}

	@Override
	public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
		return super.mouseClicked(pMouseX, pMouseY, pButton);
	}
	
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (addressBox.mouseScrolled(mouseX, mouseY, delta))
			return true;
		return super.mouseScrolled(mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(int pKeyCode, int pScanCode, int pModifiers) {
		if (pKeyCode == GLFW.GLFW_KEY_ENTER)
			setFocused(null);
		return super.keyPressed(pKeyCode, pScanCode, pModifiers);
	}

	@Override
	public boolean charTyped(char pCodePoint, int pModifiers) {
		return super.charTyped(pCodePoint, pModifiers);
	}

	@Override
	protected void contentsCleared() {
		addressBox.setValue("");
		deferFocus = true;
	}

	@Override
	protected boolean isButtonEnabled(IconButton button) {
		return false;
	}

	@Override
	protected boolean isIndicatorOn(Indicator indicator) {
		return false;
	}

}

package moe.paring.createlogisticsbackport.content.logistics.stockTicker;

import com.jozufozu.flywheel.core.PartialModel;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllPackets;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.compat.Mods;
import com.simibubi.create.compat.jei.CreateJEI;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import com.simibubi.create.content.equipment.clipboard.ClipboardEntry;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerRenderer;
import com.simibubi.create.content.trains.station.NoShadowFontWrapper;
import com.simibubi.create.foundation.gui.UIRenderHelper;
import com.simibubi.create.foundation.gui.element.GuiGameElement;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.render.CachedBufferer;
import com.simibubi.create.foundation.utility.*;
import com.simibubi.create.foundation.utility.animation.LerpedFloat;
import com.simibubi.create.foundation.utility.animation.LerpedFloat.Chaser;
import com.simibubi.create.infrastructure.config.AllConfigs;
import moe.paring.createlogisticsbackport.CreateBackportJEI;
import moe.paring.createlogisticsbackport.ExtraMods;
import moe.paring.createlogisticsbackport.UIRenderHelperExtra;
import moe.paring.createlogisticsbackport.accessor.ClipboardEntryMixinAccessor;
import moe.paring.createlogisticsbackport.config.ExtraConfigs;
import moe.paring.createlogisticsbackport.content.logistics.AddressEditBox;
import moe.paring.createlogisticsbackport.content.logistics.BigItemStack;
import moe.paring.createlogisticsbackport.content.logistics.factoryBoard.FactoryPanelScreen;
import moe.paring.createlogisticsbackport.content.logistics.packager.InventorySummary;
import moe.paring.createlogisticsbackport.content.logistics.stockTicker.PackageOrderWithCrafts.CraftingEntry;
import moe.paring.createlogisticsbackport.foundation.gui.ScreenWithStencils;
import moe.paring.createlogisticsbackport.mixin.BlazeBurnerBlockEntityAccessor;
import moe.paring.createlogisticsbackport.polyfill.BlazeBurnerRendererExtra;
import moe.paring.createlogisticsbackport.polyfill.ICanPlayUiSound;
import moe.paring.createlogisticsbackport.polyfill.behaviour.BlazeBurnerStockKeeperBehaviour;
import moe.paring.createlogisticsbackport.registry.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.Function;

public class StockKeeperRequestScreen extends AbstractSimiContainerScreen<StockKeeperRequestMenu>
        implements ScreenWithStencils, ICanPlayUiSound {

    public static class CategoryEntry {
        boolean hidden;
        String name;
        int y;
        int targetBECategory;

        public CategoryEntry(int targetBECategory, String name, int y) {
            this.targetBECategory = targetBECategory;
            this.name = name;
            hidden = false;
            this.y = y;
        }
    }

    ;

    private static final ExtraGuiTextures NUMBERS = ExtraGuiTextures.NUMBERS;
    private static final ExtraGuiTextures HEADER = ExtraGuiTextures.STOCK_KEEPER_REQUEST_HEADER;
    private static final ExtraGuiTextures BODY = ExtraGuiTextures.STOCK_KEEPER_REQUEST_BODY;
    private static final ExtraGuiTextures FOOTER = ExtraGuiTextures.STOCK_KEEPER_REQUEST_FOOTER;

    StockTickerBlockEntity blockEntity;
    public LerpedFloat itemScroll;

    final int rows = 9;
    final int cols = 9;
    final int rowHeight = 20;
    final int colWidth = 20;
    final Couple<Integer> noneHovered = Couple.create(-1, -1);
    int itemsX;
    int itemsY;
    int orderY;
    int lockX;
    int lockY;
    int windowWidth;
    int windowHeight;

    public EditBox searchBox;
    public AddressEditBox addressBox;

    int emptyTicks = 0;
    int successTicks = 0;

    public List<List<BigItemStack>> currentItemSource;
    public List<List<BigItemStack>> displayedItems;
    public List<CategoryEntry> categories;

    public List<BigItemStack> itemsToOrder;
    public List<CraftableBigItemStack> recipesToOrder;

    WeakReference<LivingEntity> stockKeeper;
    WeakReference<BlazeBurnerBlockEntity> blaze;

    boolean encodeRequester; // Redstone requesters
    ItemStack itemToProgram;
    List<List<ClipboardEntry>> clipboardItem;

    private boolean isAdmin;
    private boolean isLocked;
    private boolean scrollHandleActive;

    public boolean refreshSearchNextTick;
    public boolean moveToTopNextTick;
    private List<Rect2i> extraAreas = Collections.emptyList();

    private Set<Integer> hiddenCategories;
    private InventorySummary forcedEntries;
    private boolean canRequestCraftingPackage;

    public StockKeeperRequestScreen(StockKeeperRequestMenu container, Inventory inv, Component title) {
        super(container, inv, title);
        displayedItems = new ArrayList<>();
        itemsToOrder = new ArrayList<>();
        recipesToOrder = new ArrayList<>();
        categories = new ArrayList<>();
        isAdmin = menu.isAdmin;
        isLocked = menu.isLocked;
        blockEntity = container.contentHolder;
        blockEntity.lastClientsideStockSnapshot = null;
        blockEntity.ticksSinceLastUpdate = 15;
        emptyTicks = 0;
        successTicks = 0;
        itemScroll = LerpedFloat.linear()
                .startWithValue(0);
        stockKeeper = new WeakReference<>(null);
        blaze = new WeakReference<>(null);
        refreshSearchNextTick = false;
        moveToTopNextTick = false;
        menu.screenReference = this;
        canRequestCraftingPackage = false;
        hiddenCategories =
                new HashSet<>(blockEntity.hiddenCategoriesByPlayer.getOrDefault(menu.player.getUUID(), List.of()));

        forcedEntries = new InventorySummary();

        itemToProgram = menu.player.getMainHandItem();
        encodeRequester =
                ExtraItemTags.TABLE_CLOTHS.matches(itemToProgram) || ExtraBlocks.REDSTONE_REQUESTER.isIn(itemToProgram);

        if (AllBlocks.CLIPBOARD.isIn(itemToProgram)) {
            clipboardItem = ClipboardEntry.readAll(itemToProgram);
            boolean anyItems = false;
            for (List<ClipboardEntry> list : clipboardItem)
                for (ClipboardEntry entry : list)
                    if (!entry.icon.isEmpty())
                        anyItems = true;
            if (!anyItems)
                clipboardItem = null;
        }

        // Find the keeper for rendering
        for (int yOffset : Iterate.zeroAndOne) {
            for (Direction side : Iterate.horizontalDirections) {
                BlockPos seatPos = blockEntity.getBlockPos()
                        .below(yOffset)
                        .relative(side);
                for (SeatEntity seatEntity : blockEntity.getLevel()
                        .getEntitiesOfClass(SeatEntity.class, new AABB(seatPos)))
                    if (!seatEntity.getPassengers()
                            .isEmpty()
                            && seatEntity.getPassengers()
                            .get(0) instanceof LivingEntity keeper)
                        stockKeeper = new WeakReference<>(keeper);
                if (yOffset == 0 && blockEntity.getLevel()
                        .getBlockEntity(seatPos) instanceof BlazeBurnerBlockEntity bbbe) {
                    blaze = new WeakReference<>(bbbe);
                    return;
                }
            }
        }
    }

    @Override
    protected void init() {
        int appropriateHeight = Minecraft.getInstance()
                .getWindow()
                .getGuiScaledHeight() - 10;
        appropriateHeight -=
                Mth.positiveModulo(appropriateHeight - HEADER.getHeight() - FOOTER.getHeight(), BODY.getHeight());
        appropriateHeight =
                Math.min(appropriateHeight, HEADER.getHeight() + FOOTER.getHeight() + BODY.getHeight() * 17);

        setWindowSize(windowWidth = 226, windowHeight = appropriateHeight);
        super.init();
        clearWidgets();

        int x = getGuiLeft();
        int y = getGuiTop();

        itemsX = x + (windowWidth - cols * colWidth) / 2 + 1;
        itemsY = y + 33;
        orderY = y + windowHeight - 72;
        lockX = x + 186;
        lockY = y + 18;

        MutableComponent searchLabel = Lang.translateDirect("gui.stock_keeper.search_items");
        searchBox = new EditBox(new NoShadowFontWrapper(font), x + 71, y + 22, 100, 9, searchLabel);
        searchBox.setMaxLength(50);
        searchBox.setBordered(false);
        searchBox.setTextColor(0x4A2D31);
        addWidget(searchBox);

        boolean initial = addressBox == null;
        String previouslyUsedAddress = initial ? blockEntity.previouslyUsedAddress : addressBox.getValue();
        addressBox =
                new AddressEditBox(this, new NoShadowFontWrapper(font), x + 27, y + windowHeight - 36, 92, 10, true);
        addressBox.setTextColor(0x714A40);
        addressBox.setValue(previouslyUsedAddress);
        addRenderableWidget(addressBox);

        extraAreas = new ArrayList<>();
        int leftHeight = 40;
        int rightHeight = 50;

        LivingEntity keeper = stockKeeper.get();
        if (keeper != null && keeper.isAlive())
            leftHeight = (int) (Math.max(0, keeper.getBoundingBox()
                    .getYsize()) * 50);

        extraAreas.add(new Rect2i(0, y + windowHeight - 15 - leftHeight, x, height));
        if (encodeRequester)
            extraAreas.add(new Rect2i(x + windowWidth, y + windowHeight - 15 - rightHeight, rightHeight + 10, rightHeight));

        if (initial) {
            playUiSound(SoundEvents.WOOD_HIT, 0.5f, 1.5f);
            playUiSound(SoundEvents.BOOK_PAGE_TURN, 1, 1);
            syncJEI();
        }
    }

    private void refreshSearchResults(boolean scrollBackUp) {
        displayedItems = Collections.emptyList();
        if (scrollBackUp)
            itemScroll.startWithValue(0);

        if (currentItemSource == null) {
            clampScrollBar();
            return;
        }

        if (isSchematicListMode()) {
            clampScrollBar();
            requestSchematicList();
            return;
        }

        categories = new ArrayList<>();
        for (int i = 0; i < blockEntity.categories.size(); i++) {
            ItemStack stack = blockEntity.categories.get(i);
            CategoryEntry entry = new CategoryEntry(i, stack.isEmpty() ? ""
                    : stack.getHoverName()
                    .getString(),
                    0);
            entry.hidden = hiddenCategories.contains(i);
            categories.add(entry);
        }

        CategoryEntry unsorted = new CategoryEntry(-1, Lang.translate("gui.stock_keeper.unsorted_category")
                .string(), 0);
        unsorted.hidden = hiddenCategories.contains(-1);
        categories.add(unsorted);

        String valueWithPrefix = searchBox.getValue();
        boolean anyItemsInCategory = false;

        // Nothing is being filtered out
        if (valueWithPrefix.isBlank()) {
            displayedItems = new ArrayList<>(currentItemSource);

            int categoryY = 0;
            for (int categoryIndex = 0; categoryIndex < currentItemSource.size(); categoryIndex++) {
                categories.get(categoryIndex).y = categoryY;
                List<BigItemStack> displayedItemsInCategory = displayedItems.get(categoryIndex);
                if (displayedItemsInCategory.isEmpty())
                    continue;
                if (categoryIndex < currentItemSource.size() - 1)
                    anyItemsInCategory = true;

                categoryY += rowHeight;
                if (!categories.get(categoryIndex).hidden)
                    categoryY += Math.ceil(displayedItemsInCategory.size() / (float) cols) * rowHeight;
            }

            if (!anyItemsInCategory)
                categories.clear();

            clampScrollBar();
            updateCraftableAmounts();
            return;
        }

        // Filter by search string
        boolean modSearch = false;
        boolean tagSearch = false;
        if ((modSearch = valueWithPrefix.startsWith("@")) || (tagSearch = valueWithPrefix.startsWith("#")))
            valueWithPrefix = valueWithPrefix.substring(1);
        final String value = valueWithPrefix.toLowerCase(Locale.ROOT);

        displayedItems = new ArrayList<>();
        currentItemSource.forEach($ -> displayedItems.add(new ArrayList<>()));

        int categoryY = 0;
        for (int categoryIndex = 0; categoryIndex < displayedItems.size(); categoryIndex++) {
            List<BigItemStack> category = currentItemSource.get(categoryIndex);
            categories.get(categoryIndex).y = categoryY;

            if (displayedItems.size() <= categoryIndex)
                break;

            List<BigItemStack> displayedItemsInCategory = displayedItems.get(categoryIndex);
            for (BigItemStack entry : category) {
                ItemStack stack = entry.stack;

                if (modSearch) {
                    if (ForgeRegistries.ITEMS.getKey(stack.getItem())
                            .getNamespace()
                            .contains(value)) {
                        displayedItemsInCategory.add(entry);
                    }
                    continue;
                }

                if (tagSearch) {
                    if (stack.getTags()
                            .anyMatch(key -> key.location()
                                    .toString()
                                    .contains(value)))
                        displayedItemsInCategory.add(entry);
                    continue;
                }

                if (stack.getHoverName()
                        .getString()
                        .toLowerCase(Locale.ROOT)
                        .contains(value)
                        || ForgeRegistries.ITEMS.getKey(stack.getItem())
                        .getPath()
                        .contains(value)) {
                    displayedItemsInCategory.add(entry);
                    continue;
                }
            }

            if (displayedItemsInCategory.isEmpty())
                continue;
            if (categoryIndex < currentItemSource.size() - 1)
                anyItemsInCategory = true;

            categoryY += rowHeight;

            if (!categories.get(categoryIndex).hidden)
                categoryY += Math.ceil(displayedItemsInCategory.size() / (float) cols) * rowHeight;
        }

        if (!anyItemsInCategory)
            categories.clear();

        clampScrollBar();
        updateCraftableAmounts();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        addressBox.tick();

        if (!forcedEntries.isEmpty()) {
            InventorySummary summary = blockEntity.getLastClientsideStockSnapshotAsSummary();
            for (BigItemStack stack : forcedEntries.getStacks()) {
                int limitedAmount = -stack.count - 1;
                int actualAmount = summary.getCountOf(stack.stack);
                if (actualAmount <= limitedAmount)
                    forcedEntries.erase(stack.stack);
            }
        }

        boolean allEmpty = true;
        for (List<BigItemStack> list : displayedItems)
            allEmpty &= list.isEmpty();
        if (allEmpty)
            emptyTicks++;
        else
            emptyTicks = 0;

        if (successTicks > 0 && itemsToOrder.isEmpty())
            successTicks++;
        else
            successTicks = 0;

        List<List<BigItemStack>> clientStockSnapshot = blockEntity.getClientStockSnapshot();
        if (clientStockSnapshot != currentItemSource) {
            currentItemSource = clientStockSnapshot;
            refreshSearchResults(false);
            revalidateOrders();
        }

        if (refreshSearchNextTick) {
            refreshSearchNextTick = false;
            refreshSearchResults(moveToTopNextTick);
        }

        itemScroll.tickChaser();

        if (Math.abs(itemScroll.getValue() - itemScroll.getChaseTarget()) < 1 / 16f)
            itemScroll.setValue(itemScroll.getChaseTarget());

        if (blockEntity.ticksSinceLastUpdate > 15)
            blockEntity.refreshClientStockSnapshot();

        LivingEntity keeper = stockKeeper.get();
        BlazeBurnerBlockEntity blazeKeeper = blaze.get();
        if ((keeper == null || !keeper.isAlive()) && (blazeKeeper == null || blazeKeeper.isRemoved()))
            menu.player.closeContainer();
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        PoseStack ms = graphics.pose();
        ms.pushPose();
        ms.translate(0, 0, -300);
        super.renderBackground(graphics);
        ms.popPose();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        if (this != minecraft.screen)
            return; // stencil buffer does not cooperate with ponders gui fade out

        PoseStack ms = graphics.pose();
        float currentScroll = itemScroll.getValue(partialTicks);
        Couple<Integer> hoveredSlot = getHoveredSlot(mouseX, mouseY);

        int x = getGuiLeft();
        int y = getGuiTop();

        // BG
        HEADER.render(graphics, x - 15, y);
        y += HEADER.getHeight();
        for (int i = 0; i < (windowHeight - HEADER.getHeight() - FOOTER.getHeight()) / BODY.getHeight(); i++) {
            BODY.render(graphics, x - 15, y);
            y += BODY.getHeight();
        }
        FOOTER.render(graphics, x - 15, y);
        y = getGuiTop();

        // Render text input hints
        if (addressBox.getValue()
                .isBlank() && !addressBox.isFocused()) {
            graphics.drawString(Minecraft.getInstance().font, Lang.translate("gui.stock_keeper.package_adress")
                    .style(ChatFormatting.ITALIC)
                    .component(), addressBox.getX(), addressBox.getY(), 0xff_CDBCA8, false);
        }

        // Render keeper
        int entitySizeOffset = 0;
        LivingEntity keeper = stockKeeper.get();
        if (keeper != null && keeper.isAlive()) {
            ms.pushPose();
            ms.translate(0, 0, -300);
            entitySizeOffset = (int) (Math.max(0, keeper.getBoundingBox()
                    .getXsize() - 1) * 50);
            int entityX = x - 35 - entitySizeOffset;
            int entityY = y + windowHeight - 17;
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, entityX, entityY, 50, entityX - mouseX,
                    Mth.clamp(entityY - mouseY, -50, 10), keeper);
            ms.popPose();
        }

        BlazeBurnerBlockEntity keeperBE = blaze.get();
        if (keeperBE != null && !keeperBE.isRemoved()) {
            ms.pushPose();
            int entityX = x - 35;
            int entityY = y + windowHeight - 23;
            ms.translate(entityX, entityY, -100);
            ms.mulPose(Axis.XP.rotationDegrees(-22.5f));
            ms.mulPose(Axis.YP.rotationDegrees(-45));
            ms.scale(48, -48, 48);
            float animation = ((BlazeBurnerBlockEntityAccessor)keeperBE).getHeadAnimation().getValue(AnimationTickHolder.getPartialTicks()) * .175f;
            float horizontalAngle = AngleHelper.rad(270);
            HeatLevel heatLevel = keeperBE.getBehaviour(BlazeBurnerStockKeeperBehaviour.TYPE).getHeatLevelForRender();
            boolean canDrawFlame = heatLevel.isAtLeast(HeatLevel.FADING);
            boolean drawGoggles = ((BlazeBurnerBlockEntityAccessor) keeperBE).getGoggles();
            PartialModel drawHat = ExtraPartialModels.LOGISTICS_HAT;
            int hashCode = keeperBE.hashCode();
            Lighting.setupForEntityInInventory();

            VertexConsumer cutout = graphics.bufferSource().getBuffer(RenderType.cutoutMipped());
            CachedBufferer.partial(ExtraPartialModels.BLAZE_CAGE, keeperBE.getBlockState())
                    .rotateCentered(Direction.UP, horizontalAngle + Mth.PI)
                    .light(LightTexture.FULL_BRIGHT)
                    .renderInto(ms, cutout);

            BlazeBurnerRendererExtra.renderShared(ms, null, graphics.bufferSource(), minecraft.level,
                    keeperBE.getBlockState(), heatLevel, animation, horizontalAngle, canDrawFlame, drawGoggles, drawHat,
                    hashCode);
            Lighting.setupFor3DItems();
            ms.popPose();
        }

        // Render static item icons
        if (encodeRequester) {
            ms.pushPose();
            ms.translate(x + windowWidth + 5, y + windowHeight - 70, 0);
            ms.scale(3.5f, 3.5f, 3.5f);
            GuiGameElement.of(itemToProgram)
                    .render(graphics);
            ms.popPose();
        }

        // Render ordered items
        for (int index = 0; index < cols; index++) {
            if (itemsToOrder.size() <= index)
                break;

            BigItemStack entry = itemsToOrder.get(index);
            boolean isStackHovered = index == hoveredSlot.getSecond() && hoveredSlot.getFirst() == -1;

            ms.pushPose();
            ms.translate(itemsX + index * colWidth, orderY, 0);
            renderItemEntry(graphics, 1, entry, isStackHovered, true);
            ms.popPose();
        }

        if (itemsToOrder.size() > 9) {
            graphics.drawString(font, Component.literal("[+" + (itemsToOrder.size() - 9) + "]"), x + windowWidth - 40,
                    orderY + 21, 0xF8F8EC);
        }

        boolean justSent = itemsToOrder.isEmpty() && successTicks > 0;
        if (isConfirmHovered(mouseX, mouseY) && !justSent)
            ExtraGuiTextures.STOCK_KEEPER_REQUEST_SEND_HOVER.render(graphics, x + windowWidth - 81,
                    y + windowHeight - 41);

        MutableComponent headerTitle = Lang.translate("gui.stock_keeper.title")
                .component();
        graphics.drawString(font, headerTitle, x + windowWidth / 2 - font.width(headerTitle) / 2, y + 4, 0x714A40,
                false);
        MutableComponent component =
                Lang.translate(encodeRequester ? "gui.stock_keeper.configure" : "gui.stock_keeper.send")
                        .component();

        if (justSent) {
            float alpha = Mth.clamp((successTicks + partialTicks - 5f) / 5f, 0f, 1f);
            ms.pushPose();
            ms.translate(alpha * alpha * 50, 0, 0);
            if (successTicks < 10)
                graphics.drawString(font, component, x + windowWidth - 42 - font.width(component) / 2,
                        y + windowHeight - 35, new Color(0x252525).setAlpha(1 - alpha * alpha)
                                .getRGB(),
                        false);
            ms.popPose();

        } else {
            graphics.drawString(font, component, x + windowWidth - 42 - font.width(component) / 2,
                    y + windowHeight - 35, 0x252525, false);
        }

        // Request just sent
        if (justSent) {
            Component msg = Lang.translateDirect("gui.stock_keeper.request_sent");
            float alpha = Mth.clamp((successTicks + partialTicks - 10f) / 5f, 0f, 1f);
            int msgX = x + windowWidth / 2 - (font.width(msg) + 10) / 2;
            int msgY = orderY + 5;
            if (alpha > 0) {
                int c3 = new Color(0x8C5D4B).setAlpha(alpha)
                        .getRGB();
                int w = font.width(msg) + 14;
                ExtraGuiTextures.STOCK_KEEPER_REQUEST_BANNER_L.render(graphics, msgX - 8, msgY - 4);
                UIRenderHelperExtra.drawStretched(graphics, msgX, msgY - 4, w, 16, 0,
                        ExtraGuiTextures.STOCK_KEEPER_REQUEST_BANNER_M);
                ExtraGuiTextures.STOCK_KEEPER_REQUEST_BANNER_R.render(graphics, msgX + font.width(msg) + 10, msgY - 4);
                graphics.drawString(font, msg, msgX + 5, msgY, c3, false);
            }
        }

        int itemWindowX = x + 21;
        int itemWindowX2 = itemWindowX + 184;
        int itemWindowY = y + 17;
        int itemWindowY2 = y + windowHeight - 80;

        UIRenderHelper.swapAndBlitColor(minecraft.getMainRenderTarget(), UIRenderHelper.framebuffer);
        startStencil(graphics, itemWindowX - 5, itemWindowY, itemWindowX2 - itemWindowX + 10,
                itemWindowY2 - itemWindowY);

        ms.pushPose();
        ms.translate(0, -currentScroll * rowHeight, 0);

        // BG
        for (int sliceY = -2; sliceY < getMaxScroll() * rowHeight + windowHeight - 72; sliceY +=
                ExtraGuiTextures.STOCK_KEEPER_REQUEST_BG.getHeight()) {
            if (sliceY - currentScroll * rowHeight < -20)
                continue;
            if (sliceY - currentScroll * rowHeight > windowHeight - 72)
                continue;
            ExtraGuiTextures.STOCK_KEEPER_REQUEST_BG.render(graphics, x + 22, y + sliceY + 18);
        }

        // Search bar
        ExtraGuiTextures.STOCK_KEEPER_REQUEST_SEARCH.render(graphics, x + 42, searchBox.getY() - 5);
        searchBox.render(graphics, mouseX, mouseY, partialTicks);
        if (searchBox.getValue()
                .isBlank() && !searchBox.isFocused())
            graphics.drawString(font, searchBox.getMessage(),
                    x + windowWidth / 2 - font.width(searchBox.getMessage()) / 2, searchBox.getY(), 0xff4A2D31, false);

        // Something isnt right
        boolean allEmpty = true;
        for (List<BigItemStack> list : displayedItems)
            allEmpty &= list.isEmpty();
        if (allEmpty) {
            Component msg = getTroubleshootingMessage();
            float alpha = Mth.clamp((emptyTicks - 10f) / 5f, 0f, 1f);
            if (alpha > 0) {
                List<FormattedCharSequence> split = font.split(msg, 160);
                for (int i = 0; i < split.size(); i++) {
                    FormattedCharSequence sequence = split.get(i);
                    int lineWidth = font.width(sequence);
                    graphics.drawString(font, sequence, x + windowWidth / 2 - lineWidth / 2 + 1,
                            itemsY + 20 + 1 + i * (font.lineHeight + 1), new Color(0x4A2D31).setAlpha(alpha)
                                    .getRGB(),
                            false);
                    graphics.drawString(font, sequence, x + windowWidth / 2 - lineWidth / 2,
                            itemsY + 20 + i * (font.lineHeight + 1), new Color(0xF8F8EC).setAlpha(alpha)
                                    .getRGB(),
                            false);
                }
            }
        }

        // Items
        for (int categoryIndex = 0; categoryIndex < displayedItems.size(); categoryIndex++) {
            List<BigItemStack> category = displayedItems.get(categoryIndex);
            CategoryEntry categoryEntry = categories.isEmpty() ? null : categories.get(categoryIndex);
            int categoryY = categories.isEmpty() ? 0 : categoryEntry.y;
            if (category.isEmpty())
                continue;

            if (!categories.isEmpty()) {
                (categoryEntry.hidden ? ExtraGuiTextures.STOCK_KEEPER_CATEGORY_HIDDEN
                        : ExtraGuiTextures.STOCK_KEEPER_CATEGORY_SHOWN).render(graphics, itemsX, itemsY + categoryY + 6);
                graphics.drawString(font, categoryEntry.name, itemsX + 10, itemsY + categoryY + 8, 0x4A2D31, false);
                graphics.drawString(font, categoryEntry.name, itemsX + 9, itemsY + categoryY + 7, 0xF8F8EC, false);
                if (categoryEntry.hidden)
                    continue;
            }

            for (int index = 0; index < category.size(); index++) {
                int pY = itemsY + categoryY + (categories.isEmpty() ? 4 : rowHeight) + (index / cols) * rowHeight;
                float cullY = pY - currentScroll * rowHeight;

                if (cullY < y)
                    continue;
                if (cullY > y + windowHeight - 72)
                    break;

                boolean isStackHovered = index == hoveredSlot.getSecond() && categoryIndex == hoveredSlot.getFirst();
                BigItemStack entry = category.get(index);

                ms.pushPose();
                ms.translate(itemsX + (index % cols) * colWidth, pY, 0);
                renderItemEntry(graphics, 1, entry, isStackHovered, false);
                ms.popPose();
            }
        }

        // Render lock option
        if (isAdmin)
            (isLocked ? ExtraGuiTextures.STOCK_KEEPER_REQUEST_LOCKED : ExtraGuiTextures.STOCK_KEEPER_REQUEST_UNLOCKED)
                    .render(graphics, lockX, lockY);

        ms.popPose();
        endStencil();

        // Scroll bar
        int windowH = windowHeight - 92;
        int totalH = getMaxScroll() * rowHeight + windowH;
        int barSize = Math.max(5, Mth.floor((float) windowH / totalH * (windowH - 2)));
        if (barSize < windowH - 2) {
            int barX = itemsX + cols * colWidth;
            int barY = y + 15;
            ms.pushPose();
            ms.translate(0, (currentScroll * rowHeight) / totalH * (windowH - 2), 0);
            ExtraGuiTextures pad = ExtraGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_PAD;
            graphics.blit(pad.location, barX, barY, pad.getWidth(), barSize, pad.getStartX(), pad.getStartY(),
                    pad.getWidth(), pad.getHeight(), 256, 256);
            ExtraGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_TOP.render(graphics, barX, barY);
            if (barSize > 16)
                ExtraGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_MID.render(graphics, barX, barY + barSize / 2 - 4);
            ExtraGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_BOT.render(graphics, barX, barY + barSize - 5);
            ms.popPose();
        }

        // Render JEI imported
        if (recipesToOrder.size() > 0) {
            int jeiX = x + (windowWidth - colWidth * recipesToOrder.size()) / 2 + 1;
            int jeiY = orderY - 31;
            ms.pushPose();
            ms.translate(jeiX, jeiY, 200);
            int xoffset = -3;
            ExtraGuiTextures.STOCK_KEEPER_REQUEST_BLUEPRINT_LEFT.render(graphics, xoffset, -3);
            xoffset += 10;
            for (int i = 0; i <= (recipesToOrder.size() - 1) * 5; i++) {
                ExtraGuiTextures.STOCK_KEEPER_REQUEST_BLUEPRINT_MIDDLE.render(graphics, xoffset, -3);
                xoffset += 4;
            }
            ExtraGuiTextures.STOCK_KEEPER_REQUEST_BLUEPRINT_RIGHT.render(graphics, xoffset, -3);

            for (int index = 0; index < recipesToOrder.size(); index++) {
                CraftableBigItemStack craftableBigItemStack = recipesToOrder.get(index);
                boolean isStackHovered = index == hoveredSlot.getSecond() && -2 == hoveredSlot.getFirst();
                ms.pushPose();
                ms.translate(index * colWidth, 0, 0);
                renderItemEntry(graphics, 1, craftableBigItemStack, isStackHovered, true);
                ms.popPose();
            }

            ms.popPose();
        }

        UIRenderHelper.swapAndBlitColor(UIRenderHelper.framebuffer, minecraft.getMainRenderTarget());
    }

    @Override
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderForeground(graphics, mouseX, mouseY, partialTicks);
        float currentScroll = itemScroll.getValue(partialTicks);
        Couple<Integer> hoveredSlot = getHoveredSlot(mouseX, mouseY);

        // Render tooltip of hovered item
        if (hoveredSlot != noneHovered) {
            int slot = hoveredSlot.getSecond();
            boolean recipeHovered = hoveredSlot.getFirst() == -2;
            boolean orderHovered = hoveredSlot.getFirst() == -1;
            BigItemStack entry = recipeHovered ? recipesToOrder.get(slot)
                    : orderHovered ? itemsToOrder.get(slot)
                    : displayedItems.get(hoveredSlot.getFirst())
                    .get(slot);

            if (recipeHovered) {
                ArrayList<Component> lines =
                        new ArrayList<>(entry.stack.getTooltipLines(minecraft.player, TooltipFlag.NORMAL));
                if (lines.size() > 0)
                    lines.set(0, Lang.translateDirect("gui.stock_keeper.craft", lines.get(0)
                            .copy()));
                graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            } else
                graphics.renderTooltip(font, entry.stack, mouseX, mouseY);
        }

        // Render tooltip of lock option
        if (currentScroll < 1 && isAdmin && mouseX > lockX && mouseX <= lockX + 15 && mouseY > lockY
                && mouseY <= lockY + 15) {
            graphics.renderComponentTooltip(font,
                    List.of(
                            Lang.translate(isLocked ? "gui.stock_keeper.network_locked" : "gui.stock_keeper.network_open")
                                    .component(),
                            Lang.translate("gui.stock_keeper.network_lock_tip")
                                    .style(ChatFormatting.GRAY)
                                    .component(),
                            Lang.translate("gui.stock_keeper.network_lock_tip_1")
                                    .style(ChatFormatting.GRAY)
                                    .component(),
                            Lang.translate("gui.stock_keeper.network_lock_tip_2")
                                    .style(ChatFormatting.DARK_GRAY)
                                    .style(ChatFormatting.ITALIC)
                                    .component()),
                    mouseX, mouseY);
        }

        // Render tooltip of address input
        if (addressBox.getValue()
                .isBlank() && !addressBox.isFocused() && addressBox.isHovered()) {
            graphics.renderComponentTooltip(font, List.of(Lang.translate("gui.factory_panel.restocker_address")
                                    .color(ScrollInput.HEADER_RGB)
                                    .component(),
                            Lang.translate("gui.schedule.lmb_edit")
                                    .style(ChatFormatting.DARK_GRAY)
                                    .style(ChatFormatting.ITALIC)
                                    .component()),
                    mouseX, mouseY);
        }
    }

    private void renderItemEntry(GuiGraphics graphics, float scale, BigItemStack entry, boolean isStackHovered,
                                 boolean isRenderingOrders) {

        int customCount = entry.count;
        if (!isRenderingOrders) {
            BigItemStack order = getOrderForItem(entry.stack);
            if (entry.count < BigItemStack.INF) {
                int forcedCount = forcedEntries.getCountOf(entry.stack);
                if (forcedCount != 0)
                    customCount = Math.min(customCount, -forcedCount - 1);
                if (order != null)
                    customCount -= order.count;
                customCount = Math.max(0, customCount);
            }
            ExtraGuiTextures.STOCK_KEEPER_REQUEST_SLOT.render(graphics, 0, 0);
        }

        boolean craftable = entry instanceof CraftableBigItemStack;
        PoseStack ms = graphics.pose();
        ms.pushPose();

        float scaleFromHover = 1;
        if (isStackHovered)
            scaleFromHover += .075f;

        ms.translate((colWidth - 18) / 2.0, (rowHeight - 18) / 2.0, 0);
        ms.translate(18 / 2.0, 18 / 2.0, 0);
        ms.scale(scale, scale, scale);
        ms.scale(scaleFromHover, scaleFromHover, scaleFromHover);
        ms.translate(-18 / 2.0, -18 / 2.0, 0);
        if (customCount != 0 || craftable)
            GuiGameElement.of(entry.stack)
                    .render(graphics);
        ms.popPose();

        ms.pushPose();
        ms.translate(0, 0, 190);
        if (customCount != 0 || craftable)
            graphics.renderItemDecorations(font, entry.stack, 1, 1, "");
        ms.translate(0, 0, 10);
        if (customCount > 1 || craftable)
            drawItemCount(graphics, entry.count, customCount);
        ms.popPose();
    }

    private void drawItemCount(GuiGraphics graphics, int count, int customCount) {
        count = customCount;
        String text = count >= 1000000 ? (count / 1000000) + "m"
                : count >= 10000 ? (count / 1000) + "k"
                : count >= 1000 ? ((count * 10) / 1000) / 10f + "k" : count >= 100 ? count + "" : " " + count;

        if (count >= BigItemStack.INF)
            text = "+";

        if (text.isBlank())
            return;

        int x = (int) Math.floor(-text.length() * 2.5);
        for (char c : text.toCharArray()) {
            int index = c - '0';
            int xOffset = index * 6;
            int spriteWidth = NUMBERS.getWidth();

            switch (c) {
                case ' ':
                    x += 4;
                    continue;
                case '.':
                    spriteWidth = 3;
                    xOffset = 60;
                    break;
                case 'k':
                    xOffset = 64;
                    break;
                case 'm':
                    spriteWidth = 7;
                    xOffset = 70;
                    break;
                case '+':
                    spriteWidth = 9;
                    xOffset = 84;
                    break;
            }

            RenderSystem.enableBlend();
            graphics.blit(NUMBERS.location, 14 + x, 10, 0, NUMBERS.getStartX() + xOffset, NUMBERS.getStartY(),
                    spriteWidth, NUMBERS.getHeight(), 256, 256);
            x += spriteWidth - 1;
        }

    }

    @Nullable
    private BigItemStack getOrderForItem(ItemStack stack) {
        for (BigItemStack entry : itemsToOrder)
            if (ItemHandlerHelper.canItemStacksStack(stack, entry.stack))
                return entry;
        return null;
    }

    private void revalidateOrders() {
        Set<BigItemStack> invalid = new HashSet<>(itemsToOrder);
        InventorySummary summary = blockEntity.lastClientsideStockSnapshotAsSummary;
        if (currentItemSource == null || summary == null) {
            itemsToOrder.removeAll(invalid);
            return;
        }
        for (BigItemStack entry : itemsToOrder) {
            entry.count = Math.min(summary.getCountOf(entry.stack), entry.count);
            if (entry.count > 0)
                invalid.remove(entry);
        }

        itemsToOrder.removeAll(invalid);
    }

    private Couple<Integer> getHoveredSlot(int x, int y) {
        x += 1;
        if (x < itemsX || x >= itemsX + cols * colWidth || isSchematicListMode())
            return noneHovered;

        // Ordered item is hovered
        if (y >= orderY && y < orderY + rowHeight) {
            int col = (x - itemsX) / colWidth;
            if (itemsToOrder.size() <= col || col < 0)
                return noneHovered;
            return Couple.create(-1, col);
        }

        // Ordered recipe is hovered
        if (y >= orderY - 31 && y < orderY - 31 + rowHeight) {
            int jeiX = getGuiLeft() + (windowWidth - colWidth * recipesToOrder.size()) / 2 + 1;
            int col = Mth.floorDiv(x - jeiX, colWidth);
            if (recipesToOrder.size() > col && col >= 0)
                return Couple.create(-2, col);
        }

        if (y < getGuiTop() + 16 || y > getGuiTop() + windowHeight - 80)
            return noneHovered;
        if (!itemScroll.settled())
            return noneHovered;

        int localY = y - itemsY;

        for (int categoryIndex = 0; categoryIndex < displayedItems.size(); categoryIndex++) {
            CategoryEntry entry = categories.isEmpty() ? new CategoryEntry(0, "", 0) : categories.get(categoryIndex);
            if (entry.hidden)
                continue;

            int row = Mth.floor((localY - (categories.isEmpty() ? 4 : rowHeight) - entry.y) / (float) rowHeight
                    + itemScroll.getChaseTarget());

            int col = (x - itemsX) / colWidth;
            int slot = row * cols + col;

            if (slot < 0)
                return noneHovered;
            if (displayedItems.get(categoryIndex)
                    .size() <= slot)
                continue;

            return Couple.create(categoryIndex, slot);
        }

        return noneHovered;
    }

    private boolean isConfirmHovered(int mouseX, int mouseY) {
        int confirmX = getGuiLeft() + 143;
        int confirmY = getGuiTop() + windowHeight - 39;
        int confirmW = 78;
        int confirmH = 18;

        if (mouseX < confirmX || mouseX >= confirmX + confirmW)
            return false;
        if (mouseY < confirmY || mouseY >= confirmY + confirmH)
            return false;
        return true;
    }

    private Component getTroubleshootingMessage() {
        if (currentItemSource == null)
            return Lang.translate("gui.stock_keeper.checking_stocks")
                    .component();
        if (blockEntity.activeLinks == 0)
            return Lang.translate("gui.stock_keeper.no_packagers_linked")
                    .component();
        if (currentItemSource.isEmpty())
            return Lang.translate("gui.stock_keeper.inventories_empty")
                    .component();
        if (isSchematicListMode())
            return Lang
                    .translate(itemsToOrder.isEmpty() ? "gui.stock_keeper.schematic_list.no_results"
                            : "gui.stock_keeper.schematic_list.requesting")
                    .component();
        return Lang.translate("gui.stock_keeper.no_search_results")
                .component();
    }

    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        boolean lmb = pButton == GLFW.GLFW_MOUSE_BUTTON_LEFT;
        boolean rmb = pButton == GLFW.GLFW_MOUSE_BUTTON_RIGHT;

        // Search
        if (rmb && searchBox.isMouseOver(pMouseX, pMouseY)) {
            searchBox.setValue("");
            refreshSearchNextTick = true;
            moveToTopNextTick = true;
            searchBox.setFocused(true);
            syncJEI();
            return true;
        }

        if (addressBox.isFocused()) {
            boolean result = addressBox.mouseClicked(pMouseX, pMouseY, pButton);
            if (addressBox.isHovered() || result)
                return result;
            addressBox.setFocused(false);
        }
        if (searchBox.isFocused()) {
            if (searchBox.isHovered())
                return searchBox.mouseClicked(pMouseX, pMouseY, pButton);
            searchBox.setFocused(false);
        }

        // Scroll bar
        int barX = itemsX + cols * colWidth - 1;
        if (getMaxScroll() > 0 && lmb && pMouseX > barX && pMouseX <= barX + 8 && pMouseY > getGuiTop() + 15
                && pMouseY < getGuiTop() + windowHeight - 82) {
            scrollHandleActive = true;
            if (minecraft.isWindowActive())
                GLFW.glfwSetInputMode(minecraft.getWindow()
                        .getWindow(), 208897, GLFW.GLFW_CURSOR_HIDDEN);
            return true;
        }

        Couple<Integer> hoveredSlot = getHoveredSlot((int) pMouseX, (int) pMouseY);

        // Lock
        if (isAdmin && itemScroll.getChaseTarget() == 0 && lmb && pMouseX > lockX && pMouseX <= lockX + 15
                && pMouseY > lockY && pMouseY <= lockY + 15) {
            isLocked = !isLocked;
            ExtraPackets.getChannel()
                    .sendToServer(new StockKeeperLockPacket(blockEntity.getBlockPos(), isLocked));
            playUiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1, 1);
            return true;
        }

        // Confirm
        if (lmb && isConfirmHovered((int) pMouseX, (int) pMouseY)) {
            sendIt();
            playUiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1, 1);
            return true;
        }

        // Category hiding
        int localY = (int) (pMouseY - itemsY);
        if (itemScroll.settled() && lmb && !categories.isEmpty() && pMouseX >= itemsX
                && pMouseX < itemsX + cols * colWidth && pMouseY >= getGuiTop() + 16
                && pMouseY <= getGuiTop() + windowHeight - 80) {
            for (int categoryIndex = 0; categoryIndex < displayedItems.size(); categoryIndex++) {
                CategoryEntry entry = categories.get(categoryIndex);
                if (Mth.floor((localY - entry.y) / (float) rowHeight + itemScroll.getChaseTarget()) != 0)
                    continue;
                if (displayedItems.get(categoryIndex)
                        .isEmpty())
                    continue;
                int indexOf = entry.targetBECategory;
                if (indexOf >= blockEntity.categories.size())
                    continue;

                if (!entry.hidden) {
                    hiddenCategories.add(indexOf);
                    playUiSound(SoundEvents.ITEM_FRAME_ROTATE_ITEM, 1f, 1.5f);
                } else {
                    hiddenCategories.remove(indexOf);
                    playUiSound(SoundEvents.ITEM_FRAME_ROTATE_ITEM, 1f, 0.675f);
                }

                refreshSearchNextTick = true;
                moveToTopNextTick = false;
                return true;
            }
        }

        if (hoveredSlot == noneHovered || !lmb && !rmb)
            return super.mouseClicked(pMouseX, pMouseY, pButton);

        // Items
        boolean orderClicked = hoveredSlot.getFirst() == -1;
        boolean recipeClicked = hoveredSlot.getFirst() == -2;
        BigItemStack entry = recipeClicked ? recipesToOrder.get(hoveredSlot.getSecond())
                : orderClicked ? itemsToOrder.get(hoveredSlot.getSecond())
                : displayedItems.get(hoveredSlot.getFirst())
                .get(hoveredSlot.getSecond());

        ItemStack itemStack = entry.stack;
        int transfer = hasShiftDown() ? itemStack.getMaxStackSize() : hasControlDown() ? 10 : 1;

        if (recipeClicked && entry instanceof CraftableBigItemStack cbis) {
            if (rmb && cbis.count == 0) {
                recipesToOrder.remove(cbis);
                return true;
            }
            requestCraftable(cbis, rmb ? -transfer : transfer);
            return true;
        }

        BigItemStack existingOrder = getOrderForItem(entry.stack);
        if (existingOrder == null) {
            if (itemsToOrder.size() >= cols || rmb)
                return true;
            itemsToOrder.add(existingOrder = new BigItemStack(itemStack.copyWithCount(1), 0));
            playUiSound(SoundEvents.WOOL_STEP, 0.75f, 1.2f);
            playUiSound(SoundEvents.BAMBOO_WOOD_STEP, 0.75f, 0.8f);
        }

        int current = existingOrder.count;

        if (rmb || orderClicked) {
            existingOrder.count = current - transfer;
            if (existingOrder.count <= 0) {
                itemsToOrder.remove(existingOrder);
                playUiSound(SoundEvents.WOOL_STEP, 0.75f, 1.8f);
                playUiSound(SoundEvents.BAMBOO_WOOD_STEP, 0.75f, 1.8f);
            }
            return true;
        }

        existingOrder.count = current + Math.min(transfer, entry.count - current);
        return true;
    }

    @Override
    public boolean mouseReleased(double pMouseX, double pMouseY, int pButton) {
        if (pButton == GLFW.GLFW_MOUSE_BUTTON_LEFT && scrollHandleActive) {
            scrollHandleActive = false;
            if (minecraft.isWindowActive())
                GLFW.glfwSetInputMode(minecraft.getWindow()
                        .getWindow(), 208897, GLFW.GLFW_CURSOR_NORMAL);
        }
        return super.mouseReleased(pMouseX, pMouseY, pButton);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (addressBox.mouseScrolled(mouseX, mouseY, delta))
            return true;

        Couple<Integer> hoveredSlot = getHoveredSlot((int) mouseX, (int) mouseY);
        boolean noHover = hoveredSlot == noneHovered;

        if (noHover || hoveredSlot.getFirst() >= 0 && !hasShiftDown() && getMaxScroll() != 0) {
            int maxScroll = getMaxScroll();
            int direction = (int) (Math.ceil(Math.abs(delta)) * -Math.signum(delta));
            float newTarget = Mth.clamp(Math.round(itemScroll.getChaseTarget() + direction), 0, maxScroll);
            itemScroll.chase(newTarget, 0.5, Chaser.EXP);
            return true;
        }

        boolean orderClicked = hoveredSlot.getFirst() == -1;
        boolean recipeClicked = hoveredSlot.getFirst() == -2;
        BigItemStack entry = recipeClicked ? recipesToOrder.get(hoveredSlot.getSecond())
                : orderClicked ? itemsToOrder.get(hoveredSlot.getSecond())
                : displayedItems.get(hoveredSlot.getFirst())
                .get(hoveredSlot.getSecond());

        boolean remove = delta < 0;
        int transfer = Mth.ceil(Math.abs(delta)) * (hasControlDown() ? 10 : 1);

        if (recipeClicked && entry instanceof CraftableBigItemStack cbis) {
            requestCraftable(cbis, remove ? -transfer : transfer);
            return true;
        }

        BigItemStack existingOrder = orderClicked ? entry : getOrderForItem(entry.stack);
        if (existingOrder == null) {
            if (itemsToOrder.size() >= cols || remove)
                return true;
            itemsToOrder.add(existingOrder = new BigItemStack(entry.stack.copyWithCount(1), 0));
            playUiSound(SoundEvents.WOOL_STEP, 0.75f, 1.2f);
            playUiSound(SoundEvents.BAMBOO_WOOD_STEP, 0.75f, 0.8f);
        }

        int current = existingOrder.count;

        if (remove) {
            existingOrder.count = current - transfer;
            if (existingOrder.count <= 0) {
                itemsToOrder.remove(existingOrder);
                playUiSound(SoundEvents.WOOL_STEP, 0.75f, 1.8f);
                playUiSound(SoundEvents.BAMBOO_WOOD_STEP, 0.75f, 1.8f);
            } else if (existingOrder.count != current)
                playUiSound(AllSoundEvents.SCROLL_VALUE.getMainEvent(), 0.25f, 1.2f);
            return true;
        }

        existingOrder.count = current + Math.min(transfer, blockEntity.getLastClientsideStockSnapshotAsSummary()
                .getCountOf(entry.stack) - current);

        if (existingOrder.count != current && current != 0)
            playUiSound(AllSoundEvents.SCROLL_VALUE.getMainEvent(), 0.25f, 1.2f);

        return true;
    }

    private void clampScrollBar() {
        int maxScroll = getMaxScroll();
        float prevTarget = itemScroll.getChaseTarget();
        float newTarget = Mth.clamp(prevTarget, 0, maxScroll);
        if (prevTarget != newTarget)
            itemScroll.startWithValue(newTarget);
    }

    private int getMaxScroll() {
        int visibleHeight = windowHeight - 84;
        int totalRows = 2;
        for (int i = 0; i < displayedItems.size(); i++) {
            List<BigItemStack> list = displayedItems.get(i);
            if (list.isEmpty())
                continue;
            totalRows++;
            if (categories.size() > i && categories.get(i).hidden)
                continue;
            totalRows += Math.ceil(list.size() / (float) cols);
        }
        int maxScroll = (int) Math.max(0, (totalRows * rowHeight - visibleHeight + 50) / rowHeight);
        return maxScroll;
    }

    @Override
    public boolean mouseDragged(double pMouseX, double pMouseY, int pButton, double pDragX, double pDragY) {
        if (pButton != GLFW.GLFW_MOUSE_BUTTON_LEFT || !scrollHandleActive)
            return super.mouseDragged(pMouseX, pMouseY, pButton, pDragX, pDragY);

        Window window = minecraft.getWindow();
        double scaleX = window.getGuiScaledWidth() / (double) window.getScreenWidth();
        double scaleY = window.getGuiScaledHeight() / (double) window.getScreenHeight();

        int windowH = windowHeight - 92;
        int totalH = getMaxScroll() * rowHeight + windowH;
        int barSize = Math.max(5, Mth.floor((float) windowH / totalH * (windowH - 2)));

        int minY = getGuiTop() + 15 + barSize / 2;
        int maxY = getGuiTop() + 15 + windowH - barSize / 2;

        if (barSize >= windowH - 2)
            return true;

        int barX = itemsX + cols * colWidth;
        double target = (pMouseY - getGuiTop() - 15 - barSize / 2.0) * totalH / (windowH - 2) / rowHeight;
        itemScroll.chase(Mth.clamp(target, 0, getMaxScroll()), 0.8, Chaser.EXP);

        if (minecraft.isWindowActive()) {
            double forceX = (barX + 2) / scaleX;
            double forceY = Mth.clamp(pMouseY, minY, maxY) / scaleY;
            GLFW.glfwSetCursorPos(window.getWindow(), forceX, forceY);
        }

        return true;
    }

    @Override
    public boolean charTyped(char pCodePoint, int pModifiers) {
        if (addressBox.isFocused() && addressBox.charTyped(pCodePoint, pModifiers))
            return true;
        String s = searchBox.getValue();
        if (!searchBox.charTyped(pCodePoint, pModifiers))
            return false;
        if (!Objects.equals(s, searchBox.getValue())) {
            refreshSearchNextTick = true;
            moveToTopNextTick = true;
            syncJEI();
        }
        return true;
    }

    @Override
    public boolean keyPressed(int pKeyCode, int pScanCode, int pModifiers) {
        if (pKeyCode == GLFW.GLFW_KEY_ENTER && searchBox.isFocused()) {
            searchBox.setFocused(false);
            return true;
        }

        if (pKeyCode == GLFW.GLFW_KEY_ENTER && hasShiftDown()) {
            sendIt();
            return true;
        }

        if (addressBox.isFocused() && addressBox.keyPressed(pKeyCode, pScanCode, pModifiers))
            return true;

        String s = searchBox.getValue();
        if (!searchBox.keyPressed(pKeyCode, pScanCode, pModifiers))
            return searchBox.isFocused() && searchBox.isVisible() && pKeyCode != 256 ? true
                    : super.keyPressed(pKeyCode, pScanCode, pModifiers);
        if (!Objects.equals(s, searchBox.getValue())) {
            refreshSearchNextTick = true;
            moveToTopNextTick = true;
            syncJEI();
        }
        return true;
    }

    @Override
    public void removed() {
        SimpleChannel channel = ExtraPackets.getChannel();
        BlockPos pos = blockEntity.getBlockPos();
        channel.sendToServer(
                new PackageOrderRequestPacket(pos, PackageOrderWithCrafts.empty(), addressBox.getValue(), false));
        channel.sendToServer(new StockKeeperCategoryHidingPacket(pos, new ArrayList<>(hiddenCategories)));
        super.removed();
    }

    private void sendIt() {
        revalidateOrders();
        if (itemsToOrder.isEmpty())
            return;

        forcedEntries = new InventorySummary();
        InventorySummary summary = blockEntity.getLastClientsideStockSnapshotAsSummary();
        for (BigItemStack toOrder : itemsToOrder) {
            // momentarily cut the displayed stack size until the stock updates come in
            int countOf = summary.getCountOf(toOrder.stack);
            if (countOf == BigItemStack.INF)
                continue;
            forcedEntries.add(toOrder.stack.copy(), -1 - Math.max(0, countOf - toOrder.count));
        }

        PackageOrderWithCrafts order = PackageOrderWithCrafts.simple(itemsToOrder);

        if (canRequestCraftingPackage && !itemsToOrder.isEmpty() && !recipesToOrder.isEmpty()) {
            List<CraftingEntry> craftList = new ArrayList<>();
            for (CraftableBigItemStack cbis : recipesToOrder) {
                if (!(cbis.recipe instanceof CraftingRecipe cr))
                    continue;
                int craftedCount = 0;
                int targetCount = cbis.count / cbis.getOutputCount(blockEntity.getLevel());
                List<BigItemStack> mutableOrder = BigItemStack.duplicateWrappers(itemsToOrder);

                while (craftedCount < targetCount) {
                    // Carefully split the ordered recipes based on what exactly will be used to craft them
                    PackageOrder pattern = new PackageOrder(FactoryPanelScreen.convertRecipeToPackageOrderContext(cr, mutableOrder, true));
                    int maxCrafts = targetCount - craftedCount;
                    int availableCrafts = 0;

                    boolean itemsExhausted = false;
                    Outer:
                    while (availableCrafts < maxCrafts && !itemsExhausted) {
                        List<BigItemStack> previousSnapshot = BigItemStack.duplicateWrappers(mutableOrder);
                        itemsExhausted = true;
                        Pattern:
                        for (BigItemStack patternStack : pattern.stacks()) {
                            if (patternStack.stack.isEmpty())
                                continue;
                            for (BigItemStack ordered : mutableOrder) {
                                if (!ItemHandlerHelper.canItemStacksStack(ordered.stack, patternStack.stack))
                                    continue;
                                if (ordered.count == 0)
                                    continue;
                                ordered.count -= 1;
                                itemsExhausted = false;
                                continue Pattern;
                            }
                            mutableOrder = previousSnapshot;
                            break Outer;
                        }
                        availableCrafts++;
                    }

                    if (availableCrafts == 0)
                        break;

                    craftList.add(new CraftingEntry(pattern, availableCrafts));
                    craftedCount += availableCrafts;
                }

            }
            order = new PackageOrderWithCrafts(order.orderedStacks(), craftList);
        }

        ExtraPackets.getChannel()
                .sendToServer(new PackageOrderRequestPacket(blockEntity.getBlockPos(), order, addressBox.getValue(),
                        encodeRequester));

        itemsToOrder = new ArrayList<>();
        recipesToOrder = new ArrayList<>();
        blockEntity.ticksSinceLastUpdate = 10;
        successTicks = 1;

        if (isSchematicListMode())
            menu.player.closeContainer();
    }

    @Override
    public boolean keyReleased(int pKeyCode, int pScanCode, int pModifiers) {
        return super.keyReleased(pKeyCode, pScanCode, pModifiers);
    }

    @Override
    public List<Rect2i> getExtraAreas() {
        return extraAreas;
    }

    public boolean isSchematicListMode() {
        return clipboardItem != null;
    }

    public void requestSchematicList() {
        itemsToOrder.clear();
        InventorySummary availableItems = blockEntity.getLastClientsideStockSnapshotAsSummary();
        for (List<ClipboardEntry> list : clipboardItem) {
            for (ClipboardEntry entry : list) {
                ItemStack stack = entry.icon;
                int toOrder = Math.min(((ClipboardEntryMixinAccessor)entry).getCreate_logistics_backport$itemAmount(), availableItems.getCountOf(stack));
                if (toOrder == 0)
                    continue;
                itemsToOrder.add(new BigItemStack(stack, toOrder));
            }
        }
    }

    public void requestCraftable(CraftableBigItemStack cbis, int requestedDifference) {
        boolean takeOrdersAway = requestedDifference < 0;
        if (takeOrdersAway)
            requestedDifference = Math.max(-cbis.count, requestedDifference);
        if (requestedDifference == 0)
            return;

        InventorySummary availableItems = blockEntity.getLastClientsideStockSnapshotAsSummary();
        Function<ItemStack, Integer> countModifier = stack -> {
            BigItemStack ordered = getOrderForItem(stack);
            return ordered == null ? 0 : -ordered.count;
        };

        if (takeOrdersAway) {
            availableItems = new InventorySummary();
            for (BigItemStack ordered : itemsToOrder)
                availableItems.add(ordered.stack, ordered.count);
            countModifier = stack -> 0;
        }

        Pair<Integer, List<List<BigItemStack>>> craftingResult =
                maxCraftable(cbis, availableItems, countModifier, takeOrdersAway ? -1 : 9 - itemsToOrder.size());
        int outputCount = cbis.getOutputCount(blockEntity.getLevel());
        int adjustToRecipeAmount = Mth.ceil(Math.abs(requestedDifference) / (float) outputCount) * outputCount;
        int maxCraftable = Math.min(adjustToRecipeAmount, craftingResult.getFirst());

        if (maxCraftable == 0)
            return;

        cbis.count += takeOrdersAway ? -maxCraftable : maxCraftable;

        List<List<BigItemStack>> validEntriesByIngredient = craftingResult.getSecond();
        for (List<BigItemStack> list : validEntriesByIngredient) {
            int remaining = maxCraftable / outputCount;
            for (BigItemStack entry : list) {
                if (remaining <= 0)
                    break;

                int toTransfer = Math.min(remaining, entry.count);
                BigItemStack order = getOrderForItem(entry.stack);

                if (takeOrdersAway) {
                    if (order != null) {
                        order.count -= toTransfer;
                        if (order.count == 0)
                            itemsToOrder.remove(order);
                    }
                } else {
                    if (order == null)
                        itemsToOrder.add(order = new BigItemStack(entry.stack.copyWithCount(1), 0));
                    order.count += toTransfer;
                }

                remaining -= entry.count;
            }
        }

        updateCraftableAmounts();
    }

    private void updateCraftableAmounts() {
        InventorySummary usedItems = new InventorySummary();
        InventorySummary availableItems = new InventorySummary();

        for (BigItemStack ordered : itemsToOrder)
            availableItems.add(ordered.stack, ordered.count);

        for (CraftableBigItemStack cbis : recipesToOrder) {
            Pair<Integer, List<List<BigItemStack>>> craftingResult =
                    maxCraftable(cbis, availableItems, stack -> -usedItems.getCountOf(stack), -1);
            int maxCraftable = craftingResult.getFirst();
            List<List<BigItemStack>> validEntriesByIngredient = craftingResult.getSecond();
            int outputCount = cbis.getOutputCount(blockEntity.getLevel());

            // Only tweak amounts downward
            cbis.count = Math.min(cbis.count, maxCraftable);

            // Use ingredients up before checking next recipe
            for (List<BigItemStack> list : validEntriesByIngredient) {
                int remaining = cbis.count / outputCount;
                for (BigItemStack entry : list) {
                    if (remaining <= 0)
                        break;
                    usedItems.add(entry.stack, Math.min(remaining, entry.count));
                    remaining -= entry.count;
                }
            }
        }

        canRequestCraftingPackage = false;
        for (BigItemStack ordered : itemsToOrder)
            if (usedItems.getCountOf(ordered.stack) != ordered.count)
                return;
        canRequestCraftingPackage = true;
    }

    private Pair<Integer, List<List<BigItemStack>>> maxCraftable(CraftableBigItemStack cbis, InventorySummary summary,
                                                                 Function<ItemStack, Integer> countModifier, int newTypeLimit) {
        List<Ingredient> ingredients = cbis.getIngredients();
        List<List<BigItemStack>> validEntriesByIngredient = new ArrayList<>();
        List<BigItemStack> alreadyCreated = new ArrayList<>();

        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty())
                continue;
            List<BigItemStack> valid = new ArrayList<>();
            for (List<BigItemStack> list : summary.getItemMap()
                    .values())
                Entries:for (BigItemStack entry : list) {
                    if (!ingredient.test(entry.stack))
                        continue;
                    for (BigItemStack visitedStack : alreadyCreated) {
                        if (!ItemHandlerHelper.canItemStacksStack(visitedStack.stack, entry.stack))
                            continue;
                        valid.add(visitedStack);
                        continue Entries;
                    }
                    BigItemStack asBis = new BigItemStack(entry.stack,
                            summary.getCountOf(entry.stack) + countModifier.apply(entry.stack));
                    if (asBis.count > 0) {
                        valid.add(asBis);
                        alreadyCreated.add(asBis);
                    }
                }

            if (valid.isEmpty())
                return Pair.of(0, List.of());

            Collections.sort(valid,
                    (bis1, bis2) -> -Integer.compare(summary.getCountOf(bis1.stack), summary.getCountOf(bis2.stack)));
            validEntriesByIngredient.add(valid);
        }

        // Used new items may have to be trimmed
        if (newTypeLimit != -1) {
            int toRemove = (int) validEntriesByIngredient.stream()
                    .flatMap(l -> l.stream())
                    .filter(entry -> getOrderForItem(entry.stack) == null)
                    .distinct()
                    .count() - newTypeLimit;

            for (int i = 0; i < toRemove; i++)
                removeLeastEssentialItemStack(validEntriesByIngredient);
        }

        // Ingredients with shared items must divide counts
        validEntriesByIngredient = resolveIngredientAmounts(validEntriesByIngredient);

        // Determine the bottlenecking ingredient
        int minCount = Integer.MAX_VALUE;
        for (List<BigItemStack> list : validEntriesByIngredient) {
            int sum = 0;
            for (BigItemStack entry : list)
                sum += entry.count;
            minCount = Math.min(sum, minCount);
        }

        if (minCount == 0)
            return Pair.of(0, List.of());

        int outputCount = cbis.getOutputCount(blockEntity.getLevel());
        return Pair.of(minCount * outputCount, validEntriesByIngredient);
    }

    private void removeLeastEssentialItemStack(List<List<BigItemStack>> validIngredients) {
        List<BigItemStack> longest = null;
        int most = 0;
        for (List<BigItemStack> list : validIngredients) {
            int count = (int) list.stream()
                    .filter(entry -> getOrderForItem(entry.stack) == null)
                    .count();
            if (longest != null && count <= most)
                continue;
            longest = list;
            most = count;
        }

        if (longest.isEmpty())
            return;

        BigItemStack chosen = null;
        for (int i = 0; i < longest.size(); i++) {
            BigItemStack entry = longest.get(longest.size() - 1 - i);
            if (getOrderForItem(entry.stack) != null)
                continue;
            chosen = entry;
            break;
        }

        for (List<BigItemStack> list : validIngredients)
            list.remove(chosen);
    }

    private List<List<BigItemStack>> resolveIngredientAmounts(List<List<BigItemStack>> validIngredients) {
        List<List<BigItemStack>> resolvedIngredients = new ArrayList<>();
        for (int i = 0; i < validIngredients.size(); i++)
            resolvedIngredients.add(new ArrayList<>());

        boolean everythingTaken = false;
        while (!everythingTaken) {
            everythingTaken = true;
            Ingredients:
            for (int i = 0; i < validIngredients.size(); i++) {
                List<BigItemStack> list = validIngredients.get(i);
                List<BigItemStack> resolvedList = resolvedIngredients.get(i);
                for (BigItemStack bigItemStack : list) {
                    if (bigItemStack.count == 0)
                        continue;

                    bigItemStack.count -= 1;
                    everythingTaken = false;

                    for (BigItemStack resolvedItemStack : resolvedList) {
                        if (resolvedItemStack.stack == bigItemStack.stack) {
                            resolvedItemStack.count++;
                            continue Ingredients;
                        }
                    }

                    resolvedList.add(new BigItemStack(bigItemStack.stack, 1));
                    continue Ingredients;
                }
            }
        }

        return resolvedIngredients;
    }

    private void syncJEI() {
        if (ExtraMods.JEI.isLoaded() && ExtraConfigs.client().syncJeiSearch.get())
            CreateBackportJEI.runtime.getIngredientFilter().setFilterText(searchBox.getValue());
    }

}

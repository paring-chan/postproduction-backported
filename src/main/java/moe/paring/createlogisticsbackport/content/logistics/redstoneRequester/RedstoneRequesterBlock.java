package moe.paring.createlogisticsbackport.content.logistics.redstoneRequester;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllTags.AllItemTags;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.utility.Lang;
import moe.paring.createlogisticsbackport.content.logistics.BigItemStack;
import moe.paring.createlogisticsbackport.content.logistics.stockTicker.PackageOrder;
import moe.paring.createlogisticsbackport.content.logistics.stockTicker.PackageOrderWithCrafts;
import moe.paring.createlogisticsbackport.content.logistics.stockTicker.StockTickerBlockEntity;
import moe.paring.createlogisticsbackport.registry.ExtraBlockEntityTypes;
import moe.paring.createlogisticsbackport.registry.ExtraBlocks;
import moe.paring.createlogisticsbackport.registry.ExtraItemTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

public class RedstoneRequesterBlock extends Block implements IBE<RedstoneRequesterBlockEntity>, IWrenchable {

	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	public static final EnumProperty<Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

	public RedstoneRequesterBlock(Properties pProperties) {
		super(pProperties);
		registerDefaultState(defaultBlockState().setValue(POWERED, false));
	}

	@Override
	protected void createBlockStateDefinition(
		net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> pBuilder) {
		super.createBlockStateDefinition(pBuilder.add(POWERED, AXIS));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext pContext) {
		BlockState stateForPlacement = super.getStateForPlacement(pContext);
		if (stateForPlacement == null)
			return null;
		return stateForPlacement.setValue(AXIS, pContext.getHorizontalDirection()
			.getAxis())
			.setValue(POWERED, pContext.getLevel()
				.hasNeighborSignal(pContext.getClickedPos()));
	}

	@Override
	public boolean shouldCheckWeakPower(BlockState state, SignalGetter level, BlockPos pos, Direction side) {
		return false;
	}

	@Override
	public boolean hasAnalogOutputSignal(BlockState pState) {
		return true;
	}

	@Override
	public int getAnalogOutputSignal(BlockState pBlockState, Level pLevel, BlockPos pPos) {
		RedstoneRequesterBlockEntity req = getBlockEntity(pLevel, pPos);
		return req != null && req.lastRequestSucceeded ? 15 : 0;
	}

	@Override
	public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand,
		BlockHitResult pHit) {
		return onBlockEntityUse(pLevel, pPos, be -> be.use(pPlayer));
	}

	public static void programRequester(ServerPlayer player, StockTickerBlockEntity be, PackageOrderWithCrafts order, String address) {
		ItemStack stack = player.getMainHandItem();
		boolean isRequester = ExtraBlocks.REDSTONE_REQUESTER.isIn(stack);
		boolean isShopCloth = ExtraItemTags.TABLE_CLOTHS.matches(stack);
		if (!isRequester && !isShopCloth)
			return;

		AutoRequestData autoRequestData = new AutoRequestData();
		autoRequestData.encodedRequest = order;
		autoRequestData.encodedTargetAdress = address;
		autoRequestData.targetOffset = be.getBlockPos();
		autoRequestData.targetDim = player.level()
			.dimension()
			.location()
			.toString();

		autoRequestData.writeToItem(BlockPos.ZERO, stack);

		if (isRequester) {
			CompoundTag stackTag = stack.getTag();
			CompoundTag beTag = stackTag.getCompound(BlockItem.BLOCK_ENTITY_TAG);
			beTag.putUUID("Freq", be.behaviour.freqId);
			stackTag.put(BlockItem.BLOCK_ENTITY_TAG, beTag);
		}

		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
	}

	public static void appendRequesterTooltip(ItemStack pStack, List<Component> pTooltip) {
		if (!pStack.hasTag())
			return;

		CompoundTag compoundnbt = pStack.getTag();
		if (!compoundnbt.contains("EncodedRequest", Tag.TAG_COMPOUND))
			return;

		PackageOrder contents = PackageOrder.read(compoundnbt.getCompound("EncodedRequest"));
		for (BigItemStack entry : contents.stacks()) {
			pTooltip.add(entry.stack.getHoverName()
				.copy()
				.append(" x")
				.append(String.valueOf(entry.count))
				.withStyle(ChatFormatting.GRAY));
		}

		Lang.translate("logistically_linked.tooltip_clear")
			.style(ChatFormatting.DARK_GRAY)
			.addTo(pTooltip);
	}

	@Override
	public void setPlacedBy(Level pLevel, BlockPos requesterPos, BlockState pState, LivingEntity pPlacer, ItemStack pStack) {
		Player player = pPlacer instanceof Player ? (Player) pPlacer : null;
		withBlockEntityDo(pLevel, requesterPos, rrbe -> {
			AutoRequestData data = AutoRequestData.readFromItem(pLevel, player, requesterPos, pStack);
			if (data == null)
				return;
			rrbe.encodedRequest = data.encodedRequest;
			rrbe.encodedTargetAdress = data.encodedTargetAdress;
		});
	}

	@Override
	public void neighborChanged(BlockState pState, Level pLevel, BlockPos pPos, Block pNeighborBlock,
		BlockPos pNeighborPos, boolean pMovedByPiston) {
		if (pLevel.isClientSide())
			return;
		pLevel.setBlockAndUpdate(pPos, pState.setValue(POWERED, pLevel.hasNeighborSignal(pPos)));
		withBlockEntityDo(pLevel, pPos, RedstoneRequesterBlockEntity::onRedstonePowerChanged);
	}

	@Override
	public Class<RedstoneRequesterBlockEntity> getBlockEntityClass() {
		return RedstoneRequesterBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends RedstoneRequesterBlockEntity> getBlockEntityType() {
		return ExtraBlockEntityTypes.REDSTONE_REQUESTER.get();
	}

	@Override
	public boolean isPathfindable(BlockState pState, BlockGetter pLevel, BlockPos pPos, PathComputationType pType) {
		return false;
	}

	@Override
	public BlockState rotate(BlockState pState, Rotation pRotation) {
		return pState.setValue(AXIS, pRotation.rotate(Direction.get(AxisDirection.POSITIVE, pState.getValue(AXIS)))
			.getAxis());
	}

}

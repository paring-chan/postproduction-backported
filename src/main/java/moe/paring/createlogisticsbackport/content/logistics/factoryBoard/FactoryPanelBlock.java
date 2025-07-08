package moe.paring.createlogisticsbackport.content.logistics.factoryBoard;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.advancement.AdvancementBehaviour;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import com.simibubi.create.foundation.utility.AngleHelper;
import com.simibubi.create.foundation.utility.Lang;
import com.simibubi.create.foundation.utility.VecHelper;
import moe.paring.createlogisticsbackport.content.logistics.packagerLink.LogisticallyLinkedBlockItem;
import moe.paring.createlogisticsbackport.registry.ExtraBlockEntityTypes;
import moe.paring.createlogisticsbackport.registry.ExtraBlocks;
import moe.paring.createlogisticsbackport.registry.ExtraShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;

import java.util.UUID;

public class FactoryPanelBlock extends FaceAttachedHorizontalDirectionalBlock
	implements ProperWaterloggedBlock, IBE<FactoryPanelBlockEntity>, IWrenchable {

	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

	public static enum PanelSlot {
		TOP_LEFT(1, 1), TOP_RIGHT(0, 1), BOTTOM_LEFT(1, 0), BOTTOM_RIGHT(0, 0);

		public int xOffset;
		public int yOffset;

		private PanelSlot(int xOffset, int yOffset) {
			this.xOffset = xOffset;
			this.yOffset = yOffset;
		}
	}

	public static enum PanelState {
		PASSIVE, ACTIVE
	}

	public static enum PanelType {
		NETWORK, PACKAGER
	}

	public FactoryPanelBlock(Properties p_53182_) {
		super(p_53182_);
		registerDefaultState(defaultBlockState().setValue(WATERLOGGED, false)
			.setValue(POWERED, false));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
		super.createBlockStateDefinition(pBuilder.add(FACE, FACING, WATERLOGGED, POWERED));
	}

	@Override
	public boolean canSurvive(BlockState pState, LevelReader pLevel, BlockPos pPos) {
		return canAttachLenient(pLevel, pPos, getConnectedDirection(pState).getOpposite());
	}

	public static boolean canAttachLenient(LevelReader pReader, BlockPos pPos, Direction pDirection) {
		BlockPos blockpos = pPos.relative(pDirection);
		return !pReader.getBlockState(blockpos)
			.getCollisionShape(pReader, blockpos)
			.isEmpty();
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext pContext) {
		BlockState stateForPlacement = super.getStateForPlacement(pContext);
		if (stateForPlacement == null)
			return null;
		if (stateForPlacement.getValue(FACE) == AttachFace.FLOOR)
			stateForPlacement = stateForPlacement.setValue(FACING, stateForPlacement.getValue(FACING)
				.getOpposite());

		Level level = pContext.getLevel();
		BlockPos pos = pContext.getClickedPos();
		BlockState blockState = level.getBlockState(pos);
		FactoryPanelBlockEntity fpbe = getBlockEntity(level, pos);

		Vec3 location = pContext.getClickLocation();
		if (blockState.is(this) && location != null && fpbe != null) {
			if (!level.isClientSide()) {
				PanelSlot targetedSlot = getTargetedSlot(pos, blockState, location);
				ItemStack panelItem = FactoryPanelBlockItem.fixCtrlCopiedStack(pContext.getItemInHand());
				UUID networkFromStack = LogisticallyLinkedBlockItem.networkFromStack(panelItem);
				Player pPlayer = pContext.getPlayer();

				if (fpbe.addPanel(targetedSlot, networkFromStack) && pPlayer != null) {
					pPlayer.displayClientMessage(Lang.translateDirect("logistically_linked.connected"), true);

					if (!pPlayer.isCreative()) {
						panelItem.shrink(1);
						if (panelItem.isEmpty())
							pPlayer.setItemInHand(pContext.getHand(), ItemStack.EMPTY);
					}
				}
			}
			stateForPlacement = blockState;
		}

		return withWater(stateForPlacement, pContext);
	}

	@Override
	public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
		Level world = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Player player = context.getPlayer();
		PanelSlot slot = getTargetedSlot(pos, state, context.getClickLocation());

		if (!(world instanceof ServerLevel))
			return InteractionResult.SUCCESS;

		return onBlockEntityUse(world, pos, be -> {
			FactoryPanelBehaviour behaviour = be.panels.get(slot);
			if (behaviour == null || !behaviour.isActive())
				return InteractionResult.SUCCESS;

			BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(world, pos, world.getBlockState(pos), player);
			MinecraftForge.EVENT_BUS.post(event);
			if (event.isCanceled())
				return InteractionResult.SUCCESS;

			if (!be.removePanel(slot))
				return InteractionResult.SUCCESS;

			if (!player.isCreative())
				player.getInventory()
					.placeItemBackInInventory(ExtraBlocks.FACTORY_GAUGE.asStack());

			playRemoveSound(world, pos);
			if (be.activePanels() == 0)
				world.destroyBlock(pos, false);

			return InteractionResult.SUCCESS;
		});
	}

	@Override
	public void setPlacedBy(Level pLevel, BlockPos pPos, BlockState pState, LivingEntity pPlacer, ItemStack pStack) {
		super.setPlacedBy(pLevel, pPos, pState, pPlacer, pStack);
		if (pPlacer == null)
			return;
		AdvancementBehaviour.setPlacedBy(pLevel, pPos, pPlacer);
		double range = pPlacer.getAttribute(ForgeMod.BLOCK_REACH.get())
			.getValue() + 1;
		HitResult hitResult = pPlacer.pick(range, 1, false);
		Vec3 location = hitResult.getLocation();
		if (location == null)
			return;
		PanelSlot initialSlot = getTargetedSlot(pPos, pState, location);
		withBlockEntityDo(pLevel, pPos,
			fpbe -> fpbe.addPanel(initialSlot, LogisticallyLinkedBlockItem.networkFromStack(pStack)));
	}

	@Override
	public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand,
		BlockHitResult pHit) {
		if (pPlayer == null)
			return InteractionResult.PASS;
		ItemStack item = pPlayer.getItemInHand(pHand);
		if (pLevel.isClientSide)
			return InteractionResult.SUCCESS;
		if (!ExtraBlocks.FACTORY_GAUGE.isIn(item))
			return InteractionResult.SUCCESS;
		Vec3 location = pHit.getLocation();
		if (location == null)
			return InteractionResult.SUCCESS;

		if (!FactoryPanelBlockItem.isTuned(item)) {
			AllSoundEvents.DENY.playOnServer(pLevel, pPos);
			pPlayer.displayClientMessage(Lang.translate("factory_panel.tune_before_placing")
				.component(), true);
			return InteractionResult.FAIL;
		}

		PanelSlot newSlot = getTargetedSlot(pPos, pState, location);
		withBlockEntityDo(pLevel, pPos, fpbe -> {
			if (!fpbe.addPanel(newSlot, LogisticallyLinkedBlockItem.networkFromStack(FactoryPanelBlockItem.fixCtrlCopiedStack(item))))
				return;
			pPlayer.displayClientMessage(Lang.translateDirect("logistically_linked.connected"), true);
			pLevel.playSound(null, pPos, soundType.getPlaceSound(), SoundSource.BLOCKS);
			if (pPlayer.isCreative())
				return;
			item.shrink(1);
			if (item.isEmpty())
				pPlayer.setItemInHand(pHand, ItemStack.EMPTY);
		});
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest,
		FluidState fluid) {
		if (tryDestroySubPanelFirst(state, level, pos, player))
			return false;
		boolean result = super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
		return result;
	}

	private boolean tryDestroySubPanelFirst(BlockState state, Level level, BlockPos pos, Player player) {
		double range = player.getAttribute(ForgeMod.BLOCK_REACH.get())
			.getValue() + 1;
		HitResult hitResult = player.pick(range, 1, false);
		Vec3 location = hitResult.getLocation();
		PanelSlot destroyedSlot = getTargetedSlot(pos, state, location);
		return InteractionResult.SUCCESS == onBlockEntityUse(level, pos, fpbe -> {
			if (fpbe.activePanels() < 2)
				return InteractionResult.FAIL;
			if (!fpbe.removePanel(destroyedSlot))
				return InteractionResult.FAIL;
			if (!player.isCreative())
				popResource(level, pos, ExtraBlocks.FACTORY_GAUGE.asStack());
			return InteractionResult.SUCCESS;
		});
	}

	@Override
	public boolean isSignalSource(BlockState pState) {
		return true;
	}

	@Override
	public int getSignal(BlockState pBlockState, BlockGetter pBlockAccess, BlockPos pPos, Direction pSide) {
		return pBlockState.getValue(POWERED) ? 15 : 0;
	}

	@Override
	public int getDirectSignal(BlockState pBlockState, BlockGetter pBlockAccess, BlockPos pPos, Direction pSide) {
		return pBlockState.getValue(POWERED) && getConnectedDirection(pBlockState) == pSide ? 15 : 0;
	}

	@Override
	public boolean canBeReplaced(BlockState pState, BlockPlaceContext pUseContext) {
		if (pUseContext.isSecondaryUseActive())
			return false;
		if (!ExtraBlocks.FACTORY_GAUGE.isIn(pUseContext.getItemInHand()))
			return false;
		Vec3 location = pUseContext.getClickLocation();
		if (location == null)
			return false;

		BlockPos pos = pUseContext.getClickedPos();
		PanelSlot slot = getTargetedSlot(pos, pState, location);
		FactoryPanelBlockEntity blockEntity = getBlockEntity(pUseContext.getLevel(), pos);

		if (blockEntity == null)
			return false;
		if (blockEntity.panels.get(slot)
			.isActive())
			return false;
		return true;
	}

	@Override
	public VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos,
		CollisionContext pContext) {
		if (pContext instanceof EntityCollisionContext ecc && ecc.getEntity() == null)
			return getShape(pState, pLevel, pPos, pContext);
		return Shapes.empty();
	}

	@Override
	public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		FactoryPanelBlockEntity blockEntity = getBlockEntity(pLevel, pPos);
		if (blockEntity != null)
			return blockEntity.getShape();
		return ExtraShapes.FACTORY_PANEL_FALLBACK.get(getConnectedDirection(pState));
	}

	@Override
	public BlockState updateShape(BlockState pState, Direction pFacing, BlockState pFacingState, LevelAccessor pLevel,
		BlockPos pCurrentPos, BlockPos pFacingPos) {
		updateWater(pLevel, pState, pCurrentPos);
		return super.updateShape(pState, pFacing, pFacingState, pLevel, pCurrentPos, pFacingPos);
	}

	@Override
	public FluidState getFluidState(BlockState pState) {
		return fluidState(pState);
	}

	public static Direction connectedDirection(BlockState state) {
		return getConnectedDirection(state);
	}

	@Override
	public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pIsMoving) {
		IBE.onRemove(pState, pLevel, pPos, pNewState);
	}

	public static PanelSlot getTargetedSlot(BlockPos pos, BlockState blockState, Vec3 clickLocation) {
		double bestDistance = Double.MAX_VALUE;
		PanelSlot bestSlot = PanelSlot.BOTTOM_LEFT;
		Vec3 localClick = clickLocation.subtract(Vec3.atLowerCornerOf(pos));
		float xRot = Mth.RAD_TO_DEG * FactoryPanelBlock.getXRot(blockState);
		float yRot = Mth.RAD_TO_DEG * FactoryPanelBlock.getYRot(blockState);

		for (PanelSlot slot : PanelSlot.values()) {
			Vec3 vec = new Vec3(.25 + slot.xOffset * .5, 0, .25 + slot.yOffset * .5);
			vec = VecHelper.rotateCentered(vec, 180, Axis.Y);
			vec = VecHelper.rotateCentered(vec, xRot + 90, Axis.X);
			vec = VecHelper.rotateCentered(vec, yRot, Axis.Y);

			double diff = vec.distanceToSqr(localClick);
			if (diff > bestDistance)
				continue;
			bestDistance = diff;
			bestSlot = slot;
		}

		return bestSlot;
	}

	@Override
	public Class<FactoryPanelBlockEntity> getBlockEntityClass() {
		return FactoryPanelBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends FactoryPanelBlockEntity> getBlockEntityType() {
		return ExtraBlockEntityTypes.FACTORY_PANEL.get();
	}

	public static float getXRot(BlockState state) {
		AttachFace face = state.getOptionalValue(FactoryPanelBlock.FACE)
			.orElse(AttachFace.FLOOR);
		return face == AttachFace.CEILING ? Mth.PI / 2 : face == AttachFace.FLOOR ? -Mth.PI / 2 : 0;
	}

	public static float getYRot(BlockState state) {
		Direction facing = state.getOptionalValue(FactoryPanelBlock.FACING)
			.orElse(Direction.SOUTH);
		AttachFace face = state.getOptionalValue(FactoryPanelBlock.FACE)
			.orElse(AttachFace.FLOOR);
		return (face == AttachFace.CEILING ? Mth.PI : 0) + AngleHelper.rad(AngleHelper.horizontalAngle(facing));
	}
}

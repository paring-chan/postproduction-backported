package moe.paring.createlogisticsbackport.content.kinetics.chainConveyor;

import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.IHaveBigOutline;

import moe.paring.createlogisticsbackport.registry.ExtraBlockEntityTypes;
import moe.paring.createlogisticsbackport.registry.ExtraBlocks;
import moe.paring.createlogisticsbackport.registry.ExtraShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ChainConveyorBlock extends KineticBlock implements IBE<ChainConveyorBlockEntity>, IHaveBigOutline {

	public ChainConveyorBlock(Properties properties) {
		super(properties);
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return Axis.Y;
	}

	@Override
	public VoxelShape getInteractionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos) {
		return ExtraShapes.CHAIN_CONVEYOR_INTERACTION;
	}

	@Override
	public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		return ExtraShapes.CHAIN_CONVEYOR_INTERACTION;
	}

	@Override
	public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand,
		BlockHitResult pHit) {
		if (!pLevel.isClientSide() && pPlayer != null && pPlayer.getItemInHand(pHand)
			.is(Items.CHAIN))
			return InteractionResult.SUCCESS;
		if (pPlayer != null && ExtraBlocks.PACKAGE_FROGPORT.isIn(pPlayer.getItemInHand(pHand)))
			return InteractionResult.SUCCESS;
		return InteractionResult.PASS;
	}

	@Override
	public void playerWillDestroy(Level pLevel, BlockPos pPos, BlockState pState, Player pPlayer) {
		super.playerWillDestroy(pLevel, pPos, pState, pPlayer);
		if (pLevel.isClientSide())
			return;
		if (!pPlayer.isCreative())
			return;
		withBlockEntityDo(pLevel, pPos, be -> be.cancelDrops = true);
	}

	@Override
	public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null)
			return super.onSneakWrenched(state, context);

		withBlockEntityDo(context.getLevel(), context.getClickedPos(), be -> {
			be.cancelDrops = true;
			if (player.isCreative())
				return;
			for (BlockPos targetPos : be.connections) {
				int chainCost = ChainConveyorBlockEntity.getChainCost(targetPos);
				while (chainCost > 0) {
					player.getInventory()
						.placeItemBackInInventory(new ItemStack(Items.CHAIN, Math.min(chainCost, 64)));
					chainCost -= 64;
				}
			}
		});

		return super.onSneakWrenched(state, context);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext pContext) {
		for (int x = -1; x <= 1; x++)
			for (int z = -1; z <= 1; z++)
				if (pContext.getLevel()
					.getBlockState(pContext.getClickedPos()
						.offset(x, 0, z))
					.getBlock() == this)
					return null;

		return super.getStateForPlacement(pContext);
	}

	@Override
	public VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos,
		CollisionContext pContext) {
		return Shapes.block();
	}

	@Override
	public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pIsMoving) {
		IBE.onRemove(pState, pLevel, pPos, pNewState);
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face.getAxis() == getRotationAxis(state);
	}

	@Override
	public Class<ChainConveyorBlockEntity> getBlockEntityClass() {
		return ChainConveyorBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends ChainConveyorBlockEntity> getBlockEntityType() {
		return ExtraBlockEntityTypes.CHAIN_CONVEYOR.get();
	}
}

package moe.paring.createlogisticsbackport.content.logistics.packagerLink;

import com.simibubi.create.CreateClient;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.utility.AnimationTickHolder;
import moe.paring.createlogisticsbackport.content.logistics.factoryBoard.FactoryPanelBehaviour;
import moe.paring.createlogisticsbackport.content.logistics.factoryBoard.FactoryPanelConnectionHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.commons.lang3.tuple.Pair;

import java.util.UUID;

public class LogisticallyLinkedClientHandler {

	private static UUID previouslyHeldFrequency;

	public static void tick() {
		previouslyHeldFrequency = null;

		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null)
			return;
		ItemStack mainHandItem = player.getMainHandItem();
		if (!(mainHandItem.getItem() instanceof LogisticallyLinkedBlockItem)
			|| !LogisticallyLinkedBlockItem.isTuned(mainHandItem))
			return;

		CompoundTag tag = mainHandItem.getTag()
			.getCompound(BlockItem.BLOCK_ENTITY_TAG);
		if (!tag.hasUUID("Freq"))
			return;

		UUID uuid = tag.getUUID("Freq");
		previouslyHeldFrequency = uuid;

		for (LogisticallyLinkedBehaviour behaviour : LogisticallyLinkedBehaviour.getAllPresent(uuid, false, true)) {
			SmartBlockEntity be = behaviour.blockEntity;
			VoxelShape shape = be.getBlockState()
				.getShape(player.level(), be.getBlockPos());
			if (shape.isEmpty())
				continue;
			if (!player.blockPosition()
				.closerThan(be.getBlockPos(), 64))
				continue;
			for (int i = 0; i < shape.toAabbs()
				.size(); i++) {
				AABB aabb = shape.toAabbs()
					.get(i);
				CreateClient.OUTLINER
					.showAABB(Pair.of(behaviour, i), aabb.inflate(-1 / 128f)
						.move(be.getBlockPos()), 2)
					.lineWidth(1 / 32f)
					.disableLineNormals()
					.colored(AnimationTickHolder.getTicks() % 16 < 8 ? 0x708DAD : 0x90ADCD);
			}

		}
	}

	public static void tickPanel(FactoryPanelBehaviour fpb) {
		if (previouslyHeldFrequency == null)
			return;
		if (!previouslyHeldFrequency.equals(fpb.network))
			return;
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null)
			return;
		if (!player.blockPosition()
			.closerThan(fpb.getPos(), 64))
			return;

		CreateClient.OUTLINER
			.showAABB(fpb, FactoryPanelConnectionHandler.getBB(fpb.blockEntity.getBlockState(), fpb.getPanelPosition())
				.inflate(-1.5 / 128f))
			.lineWidth(1 / 32f)
			.disableLineNormals()
			.colored(AnimationTickHolder.getTicks() % 16 < 8 ? 0x708DAD : 0x90ADCD);
	}

}

package moe.paring.createlogisticsbackport.content.logistics.packagePort;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllPackets;
import com.simibubi.create.AllTags.AllItemTags;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.foundation.utility.AnimationTickHolder;
import com.simibubi.create.foundation.utility.Color;
import com.simibubi.create.foundation.utility.Lang;
import com.simibubi.create.infrastructure.config.AllConfigs;
import moe.paring.createlogisticsbackport.config.ExtraConfigs;
import moe.paring.createlogisticsbackport.registry.ExtraBlocks;
import moe.paring.createlogisticsbackport.registry.ExtraEntityTypes;
import moe.paring.createlogisticsbackport.registry.ExtraItemTags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public class PackagePortTargetSelectionHandler {

	public static PackagePortTarget activePackageTarget;
	public static Vec3 exactPositionOfTarget;
	public static boolean isPostbox;

	public static void flushSettings(BlockPos pos) {
		if (activePackageTarget == null) {
			Lang.translate("gui.package_port.not_targeting_anything")
				.sendStatus(Minecraft.getInstance().player);
			return;
		}

		if (validateDiff(exactPositionOfTarget, pos) == null) {
			activePackageTarget.relativePos = activePackageTarget.relativePos.subtract(pos);
			AllPackets.getChannel()
				.sendToServer(new PackagePortPlacementPacket(activePackageTarget, pos));
		}

		activePackageTarget = null;
		isPostbox = false;
		return;
	}

	public static boolean onUse() {
		Minecraft mc = Minecraft.getInstance();
		HitResult hitResult = mc.hitResult;
		ItemStack mainHandItem = mc.player.getMainHandItem();

		if (hitResult == null || hitResult.getType() == Type.MISS)
			return false;
		if (!(hitResult instanceof BlockHitResult bhr))
			return false;

		BlockPos pos = bhr.getBlockPos();
		if (!(mc.level.getBlockEntity(pos) instanceof StationBlockEntity sbe))
			return false;
		if (sbe.edgePoint == null)
			return false;
		if (!ExtraItemTags.POSTBOXES.matches(mainHandItem))
			return false;

		PackagePortTargetSelectionHandler.exactPositionOfTarget = Vec3.atCenterOf(pos);
		PackagePortTargetSelectionHandler.activePackageTarget = new PackagePortTarget.TrainStationFrogportTarget(pos);
		PackagePortTargetSelectionHandler.isPostbox = true;
		return true;
	}

	public static void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		boolean isPostbox = ExtraItemTags.POSTBOXES.matches(player.getMainHandItem());
		boolean isWrench = AllItemTags.WRENCH.matches(player.getMainHandItem());

		if (!isWrench) {
			if (activePackageTarget == null)
				return;
			if (!ExtraBlocks.PACKAGE_FROGPORT.isIn(player.getMainHandItem()) && !isPostbox)
				return;
		}

		HitResult objectMouseOver = mc.hitResult;
		if (!(objectMouseOver instanceof BlockHitResult blockRayTraceResult))
			return;

		if (isWrench) {
			if (blockRayTraceResult.getType() == Type.MISS)
				return;
			BlockPos pos = blockRayTraceResult.getBlockPos();
			if (!(mc.level.getBlockEntity(pos) instanceof PackagePortBlockEntity ppbe))
				return;
			if (ppbe.target == null)
				return;
			Vec3 source = Vec3.atBottomCenterOf(pos);
			Vec3 target = ppbe.target.getExactTargetLocation(ppbe, mc.level, pos);
			if (target == Vec3.ZERO)
				return;
			Color color = new Color(0x9ede73);
			animateConnection(mc, source, target, color);
			CreateClient.OUTLINER.chaseAABB("ChainPointSelected", new AABB(target, target))
				.colored(color)
				.lineWidth(1 / 5f)
				.disableLineNormals();
			return;
		}

		Vec3 target = exactPositionOfTarget;
		if (blockRayTraceResult.getType() == Type.MISS) {
			CreateClient.OUTLINER.chaseAABB("ChainPointSelected", new AABB(target, target))
				.colored(0x9ede73)
				.lineWidth(1 / 5f)
				.disableLineNormals();
			return;
		}

		BlockPos pos = blockRayTraceResult.getBlockPos();
		if (!mc.level.getBlockState(pos)
			.canBeReplaced())
			pos = pos.relative(blockRayTraceResult.getDirection());

		String validateDiff = validateDiff(target, pos);
		boolean valid = validateDiff == null;
		Color color = new Color(valid ? 0x9ede73 : 0xff7171);
		Vec3 source = Vec3.atBottomCenterOf(pos);

		Lang.translate(validateDiff != null ? validateDiff : "package_port.valid")
			.color(color.getRGB())
			.sendStatus(player);

		CreateClient.OUTLINER.chaseAABB("ChainPointSelected", new AABB(target, target))
			.colored(color)
			.lineWidth(1 / 5f)
			.disableLineNormals();

		if (!mc.level.getBlockState(pos)
			.canBeReplaced())
			return;

		CreateClient.OUTLINER.chaseAABB("TargetedFrogPos", new AABB(pos).contract(0, 1, 0)
			.deflate(0.125, 0, 0.125))
			.colored(color)
			.lineWidth(1 / 16f)
			.disableLineNormals();

		animateConnection(mc, source, target, color);

	}

	public static void animateConnection(Minecraft mc, Vec3 source, Vec3 target, Color color) {
		DustParticleOptions data = new DustParticleOptions(color.asVectorF(), 1);
		ClientLevel world = mc.level;
		double totalFlyingTicks = 10;
		int segments = (((int) totalFlyingTicks) / 3) + 1;
		double tickOffset = totalFlyingTicks / segments;

		for (int i = 0; i < segments; i++) {
			double ticks = ((AnimationTickHolder.getRenderTime() / 3) % tickOffset) + i * tickOffset;
			Vec3 vec = source.lerp(target, ticks / totalFlyingTicks);
			world.addParticle(data, vec.x, vec.y, vec.z, 0, 0, 0);
		}

	}

	public static String validateDiff(Vec3 target, BlockPos placedPos) {
		Vec3 source = Vec3.atBottomCenterOf(placedPos);
		Vec3 diff = target.subtract(source);
		if (diff.y < 0 && !isPostbox)
			return "package_port.cannot_reach_down";
		if (diff.length() > ExtraConfigs.server().logistics.packagePortRange.get())
			return "package_port.too_far";
		return null;
	}

}

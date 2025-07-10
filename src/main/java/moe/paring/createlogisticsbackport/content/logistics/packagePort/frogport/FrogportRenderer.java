package moe.paring.createlogisticsbackport.content.logistics.packagePort.frogport;

import com.jozufozu.flywheel.backend.Backend;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import com.simibubi.create.foundation.render.CachedBufferer;
import com.simibubi.create.foundation.render.SuperByteBuffer;
import moe.paring.createlogisticsbackport.polyfill.SmartBlockEntityExtras;
import moe.paring.createlogisticsbackport.registry.ExtraPartialModels;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

public class FrogportRenderer extends SmartBlockEntityRenderer<FrogportBlockEntity> implements SmartBlockEntityExtras<FrogportBlockEntity> {

    public FrogportRenderer(Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(FrogportBlockEntity blockEntity, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        SuperByteBuffer body = CachedBufferer.partial(ExtraPartialModels.FROGPORT_BODY, blockEntity.getBlockState());

        float yaw = blockEntity.getYaw();

        float headPitch = 80;
        float tonguePitch = 0;
        float tongueLength = 0;
        float headPitchModifier = 1;

        boolean hasTarget = blockEntity.target != null;
        boolean animating = blockEntity.isAnimationInProgress();
        boolean depositing = blockEntity.currentlyDepositing;

        Vec3 diff = Vec3.ZERO;

        if (blockEntity.addressFilter != null && !blockEntity.addressFilter.isBlank()) {
            renderNameplateOnHover(blockEntity, Component.literal(blockEntity.addressFilter), 1, ms, buffer, light);
        }

        if (Backend.canUseInstancing(blockEntity.getLevel())) {
            return;
        }

        if (hasTarget) {
            diff = blockEntity.target
                    .getExactTargetLocation(blockEntity, blockEntity.getLevel(), blockEntity.getBlockPos())
                    .subtract(0, animating && depositing ? 0 : 0.75, 0)
                    .subtract(Vec3.atCenterOf(blockEntity.getBlockPos()));
            tonguePitch = (float) Mth.atan2(diff.y, diff.multiply(1, 0, 1)
                    .length() + (3 / 16f)) * Mth.RAD_TO_DEG;
            tongueLength = Math.max((float) diff.length(), 1);
            headPitch = Mth.clamp(tonguePitch * 2, 60, 100);
        }

        if (animating) {
            float progress = blockEntity.animationProgress.getValue(partialTicks);
            float scale = 1;
            float itemDistance = 0;

            if (depositing) {
                double modifier = Math.max(0, 1 - Math.pow((progress - 0.25) * 4 - 1, 4));
                itemDistance =
                        (float) Math.max(tongueLength * Math.min(1, (progress - 0.25) * 3), tongueLength * modifier);
                tongueLength *= Math.max(0, 1 - Math.pow((progress * 1.25 - 0.25) * 4 - 1, 4));
                headPitchModifier = (float) Math.max(0, 1 - Math.pow((progress * 1.25) * 2 - 1, 4));
                scale = 0.25f + progress * 3 / 4;

            } else {
                tongueLength *= Math.pow(Math.max(0, 1 - progress * 1.25), 5);
                headPitchModifier = 1 - (float) Math.min(1, Math.max(0, (Math.pow(progress * 1.5, 2) - 0.5) * 2));
                scale = (float) Math.max(0.5, 1 - progress * 1.25);
                itemDistance = tongueLength;
            }

            renderPackage(blockEntity, ms, buffer, light, overlay, diff, scale, itemDistance);

        } else {
            tongueLength = 0;
            float anticipation = blockEntity.anticipationProgress.getValue(partialTicks);
            headPitchModifier =
                    anticipation > 0 ? (float) Math.max(0, 1 - Math.pow((anticipation * 1.25) * 2 - 1, 4)) : 0;
        }

        headPitch *= headPitchModifier;

        headPitch = Math.max(headPitch, blockEntity.manualOpenAnimationProgress.getValue(partialTicks) * 60);
        tongueLength = Math.max(tongueLength, blockEntity.manualOpenAnimationProgress.getValue(partialTicks) * 0.25f);


        body.centre()
                .rotateY(yaw)
                .unCentre()
                .light(light)
                .overlay(overlay)
                .renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));

        SuperByteBuffer head = CachedBufferer.partial(blockEntity.goggles ? ExtraPartialModels.FROGPORT_HEAD_GOGGLES : ExtraPartialModels.FROGPORT_HEAD, blockEntity.getBlockState());

        head.centre()
                .rotateY(yaw)
                .unCentre()
                .translate(8 / 16f, 10 / 16f, 11 / 16f)
                .rotateX(headPitch)
                .translateBack(8 / 16f, 10 / 16f, 11 / 16f);

        head.light(light)
//			.color(color)
                .overlay(overlay)
                .renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));

        SuperByteBuffer tongue = CachedBufferer.partial(ExtraPartialModels.FROGPORT_TONGUE, blockEntity.getBlockState());

        tongue.centre()
                .rotateY(yaw)
                .unCentre()
                .translate(8 / 16f, 10 / 16f, 11 / 16f)
                .rotateX(tonguePitch)
                .scale(1f, 1f, tongueLength / (7 / 16f))
                .translateBack(8 / 16f, 10 / 16f, 11 / 16f);

        tongue.light(light)
                .overlay(overlay)
                .renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));

        // hat

//		SuperByteBuffer hatBuffer = CachedBuffers.partial(AllPartialModels.TRAIN_HAT, blockEntity.getBlockState());
//		hatBuffer
//			.translate(8 / 16f, 14 / 16f, 8 / 16f)
//			.rotateYDegrees(yaw + 180)
//			.translate(0, 0, -3 / 16f)
//			.rotateX(-4)
//			.translateBack(0, 0, -3 / 16f)
//			.translate(0, 0, 1 / 16f)
//			.light(light)
//			.color(color)
//			.overlay(overlay)
//			.renderInto(ms, buffer.getBuffer(RenderType.solid()));

    }

    private void renderPackage(FrogportBlockEntity blockEntity, PoseStack ms, MultiBufferSource buffer, int light,
                               int overlay, Vec3 diff, float scale, float itemDistance) {
        if (blockEntity.animatedPackage == null)
            return;
        if (scale < 0.45)
            return;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(blockEntity.animatedPackage.getItem());
        if (key == null)
            return;
        SuperByteBuffer rigBuffer =
                CachedBufferer.partial(ExtraPartialModels.PACKAGE_RIGGING.get(key), blockEntity.getBlockState());
        SuperByteBuffer boxBuffer =
                CachedBufferer.partial(ExtraPartialModels.PACKAGES.get(key), blockEntity.getBlockState());

        boolean animating = blockEntity.isAnimationInProgress();
        boolean depositing = blockEntity.currentlyDepositing;

        for (SuperByteBuffer buf : new SuperByteBuffer[]{boxBuffer, rigBuffer}) {
            buf.translate(0, 3 / 16f, 0)
                    .translate(diff.normalize()
                            .scale(itemDistance)
                            .subtract(0, animating && depositing ? 0.75 : 0, 0))
                    .centre()
                    .scale(scale)
                    .unCentre()
                    .light(light)
                    .overlay(overlay)
                    .renderInto(ms, buffer.getBuffer(RenderType.cutout()));
            if (!blockEntity.currentlyDepositing)
                break;
        }
    }

}

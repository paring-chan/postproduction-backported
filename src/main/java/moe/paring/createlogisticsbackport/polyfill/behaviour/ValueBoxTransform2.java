package moe.paring.createlogisticsbackport.polyfill.behaviour;

import com.jozufozu.flywheel.util.transform.TransformStack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.utility.AngleHelper;
import com.simibubi.create.foundation.utility.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Pair;

import java.util.function.Function;

public abstract class ValueBoxTransform2 {

    protected float scale = getScale();

    public abstract Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state);

    public abstract void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms);

    public boolean testHit(LevelAccessor level, BlockPos pos, BlockState state, Vec3 localHit) {
        Vec3 offset = getLocalOffset(level, pos, state);
        if (offset == null)
            return false;
        return localHit.distanceTo(offset) < scale / 2;
    }

    public void transform(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
        Vec3 position = getLocalOffset(level, pos, state);
        if (position == null)
            return;
        ms.translate(position.x, position.y, position.z);
        rotate(level, pos, state, ms);
        ms.scale(scale, scale, scale);
    }

    public boolean shouldRender(LevelAccessor level, BlockPos pos, BlockState state) {
        return !state.isAir() && getLocalOffset(level, pos, state) != null;
    }

    public int getOverrideColor() {
        return -1;
    }

    protected Vec3 rotateHorizontally(BlockState state, Vec3 vec) {
        float yRot = 0;
        if (state.hasProperty(BlockStateProperties.FACING))
            yRot = AngleHelper.horizontalAngle(state.getValue(BlockStateProperties.FACING));
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
            yRot = AngleHelper.horizontalAngle(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
        return VecHelper.rotateCentered(vec, yRot, Direction.Axis.Y);
    }

    public float getScale() {
        return .5f;
    }

    public float getFontScale() {
        return 1 / 64f;
    }

    public static abstract class Dual extends com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform {

        protected boolean first;

        public Dual(boolean first) {
            this.first = first;
        }

        public boolean isFirst() {
            return first;
        }

        public static Pair<com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform, com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform> makeSlots(Function<Boolean, ? extends Dual> factory) {
            return Pair.of(factory.apply(true), factory.apply(false));
        }

        @Override
        public boolean testHit(BlockState state, Vec3 localHit) {
            Vec3 offset = getLocalOffset(state);
            if (offset == null)
                return false;
            return localHit.distanceTo(offset) < scale / 3.5f;
        }

    }

    public static abstract class Sided extends ValueBoxTransform2 {

        protected Direction direction = Direction.UP;

        public Sided fromSide(Direction direction) {
            this.direction = direction;
            return this;
        }

        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            Vec3 location = getSouthLocation();
            location = VecHelper.rotateCentered(location, AngleHelper.horizontalAngle(getSide()), Direction.Axis.Y);
            location = VecHelper.rotateCentered(location, AngleHelper.verticalAngle(getSide()), Direction.Axis.X);
            return location;
        }

        protected abstract Vec3 getSouthLocation();

        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
            float yRot = AngleHelper.horizontalAngle(getSide()) + 180;
            float xRot = getSide() == Direction.UP ? 90 : getSide() == Direction.DOWN ? 270 : 0;
            TransformStack.cast(ms)
                    .rotateY(yRot)
                    .rotateX(xRot);
        }

        @Override
        public boolean shouldRender(LevelAccessor level, BlockPos pos, BlockState state) {
            return super.shouldRender(level, pos, state) && isSideActive(state, getSide());
        }

        @Override
        public boolean testHit(LevelAccessor level, BlockPos pos, BlockState state, Vec3 localHit) {
            return isSideActive(state, getSide()) && super.testHit(level, pos, state, localHit);
        }

        protected boolean isSideActive(BlockState state, Direction direction) {
            return true;
        }

        public Direction getSide() {
            return direction;
        }

    }

}


package moe.paring.createlogisticsbackport.mixin;

import net.minecraft.world.level.block.state.StateHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StateHolder.class)
public interface StateHolderAccessor<O, S> {
    @Accessor
    O getOwner();
}
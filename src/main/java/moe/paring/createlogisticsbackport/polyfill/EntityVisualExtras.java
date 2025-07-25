package moe.paring.createlogisticsbackport.polyfill;

import com.simibubi.create.foundation.utility.Pair;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

public class EntityVisualExtras {
    public static Pair<Integer, Integer> computePackedLight(float partialTick, Entity entity, Level level) {
        BlockPos pos = BlockPos.containing(entity.getLightProbePosition(partialTick));
        int blockLight = entity.isOnFire() ? 15 : level.getBrightness(LightLayer.BLOCK, pos);
        int skyLight = level.getBrightness(LightLayer.SKY, pos);
        return Pair.of(blockLight, skyLight);
    }
}

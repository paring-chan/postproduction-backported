package moe.paring.createlogisticsbackport.foundation.block.connected;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.HashMap;
import java.util.Map;

public class CTSpriteShifter2 {

    private static final Map<String, CTSpriteShiftEntry2> ENTRY_CACHE = new HashMap<>();

    public static CTSpriteShiftEntry2 getCT(CTType2 type, ResourceLocation blockTexture, ResourceLocation connectedTexture) {
        String key = blockTexture + "->" + connectedTexture + "+" + type.getId();
        if (ENTRY_CACHE.containsKey(key))
            return ENTRY_CACHE.get(key);

        CTSpriteShiftEntry2 entry = new CTSpriteShiftEntry2(type);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> entry.set(blockTexture, connectedTexture));
        ENTRY_CACHE.put(key, entry);
        return entry;
    }

}

package moe.paring.createlogisticsbackport.foundation.block.connected;

import com.simibubi.create.foundation.block.render.SpriteShiftEntry;

public class CTSpriteShiftEntry2 extends SpriteShiftEntry {

    protected final CTType2 type;

    public CTSpriteShiftEntry2(CTType2 type) {
        this.type = type;
    }

    public CTType2 getType() {
        return type;
    }

    public float getTargetU(float localU, int index) {
        float uOffset = (index % type.getSheetSize());
        return getTarget().getU(
                (getUnInterpolatedU(getOriginal(), localU) + (uOffset * 16)) / ((float) type.getSheetSize()));
    }

    public float getTargetV(float localV, int index) {
        float vOffset = (index / type.getSheetSize());
        return getTarget().getV(
                (getUnInterpolatedV(getOriginal(), localV) + (vOffset * 16)) / ((float) type.getSheetSize()));
    }

}

package moe.paring.createlogisticsbackport.content.kinetics.chainConveyor;

import com.simibubi.create.foundation.networking.BlockEntityConfigurationPacket;
import com.simibubi.create.foundation.utility.AngleHelper;
import moe.paring.createlogisticsbackport.config.ExtraConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class ChainPackageInteractionPacket extends BlockEntityConfigurationPacket<ChainConveyorBlockEntity> {

    private BlockPos selectedConnection;
    private float chainPosition;
    private ItemStack insertedPackage;

    public ChainPackageInteractionPacket(BlockPos pos, BlockPos selectedConnection, float chainPosition,
                                         ItemStack insertedPackage) {
        super(pos);
        this.selectedConnection = selectedConnection == null ? BlockPos.ZERO : selectedConnection;
        this.chainPosition = chainPosition;
        this.insertedPackage = insertedPackage == null ? ItemStack.EMPTY : insertedPackage;
    }

    public ChainPackageInteractionPacket(FriendlyByteBuf buffer) {
        super(buffer);
    }

    @Override
    protected void writeSettings(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(selectedConnection);
        buffer.writeFloat(chainPosition);
        buffer.writeItem(insertedPackage);
    }

    @Override
    protected void readSettings(FriendlyByteBuf buffer) {
        selectedConnection = buffer.readBlockPos();
        chainPosition = buffer.readFloat();
        insertedPackage = buffer.readItem();
    }

    @Override
    protected int maxRange() {
        return ExtraConfigs.server().kinetics.maxChainConveyorLength.get() + 16;
    }

    @Override
    protected void applySettings(ChainConveyorBlockEntity be) {
    }

    @Override
    protected void applySettings(ServerPlayer player, ChainConveyorBlockEntity be) {
        if (insertedPackage.isEmpty()) {

            float bestDiff = Float.POSITIVE_INFINITY;
            ChainConveyorPackage best = null;
            List<ChainConveyorPackage> list = selectedConnection.equals(BlockPos.ZERO) ? be.loopingPackages
                    : be.travellingPackages.get(selectedConnection);

            if (list == null || list.isEmpty())
                return;

            for (ChainConveyorPackage liftPackage : list) {
                float diff = Math.abs(selectedConnection == null
                        ? AngleHelper.getShortestAngleDiff(liftPackage.chainPosition, chainPosition)
                        : liftPackage.chainPosition - chainPosition);
                if (diff > bestDiff)
                    continue;
                bestDiff = diff;
                best = liftPackage;
            }

            if (best == null)
                return;

            if (player.getMainHandItem()
                    .isEmpty())
                player.setItemInHand(InteractionHand.MAIN_HAND, best.item.copy());
            else
                player.getInventory()
                        .placeItemBackInInventory(best.item.copy());

            list.remove(best);
            be.sendData();

            return;
        }

        ChainConveyorPackage chainConveyorPackage = new ChainConveyorPackage(chainPosition, insertedPackage);
        if (!be.canAcceptPackagesFor(selectedConnection))
            return;

        if (!player.isCreative()) {
            player.getMainHandItem()
                    .shrink(1);
            if (player.getMainHandItem()
                    .isEmpty())
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }

        if (selectedConnection.equals(BlockPos.ZERO))
            be.addLoopingPackage(chainConveyorPackage);
        else
            be.addTravellingPackage(chainConveyorPackage, selectedConnection);

    }

}

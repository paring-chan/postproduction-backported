package moe.paring.createlogisticsbackport.content.logistics.itemHatch;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import moe.paring.createlogisticsbackport.registry.ExtraBlocks;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.eventbus.api.Event.Result;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(modid = Create.ID)
public class ItemHatchHandler {

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void useOnItemHatchIgnoresSneak(RightClickBlock event) {
		if (event.getUseItem() == Result.DEFAULT && ExtraBlocks.ITEM_HATCH.has(event.getLevel()
			.getBlockState(event.getPos())))
			event.setUseBlock(Result.ALLOW);
	}

}

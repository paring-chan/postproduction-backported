package moe.paring.createlogisticsbackport.compat.computercraft.luaObjects;

import dan200.computercraft.api.detail.VanillaDetailRegistries;
import moe.paring.createlogisticsbackport.content.logistics.BigItemStack;

import java.util.Map;

public class LuaBigItemStack implements LuaComparable {

  private final BigItemStack stack;

  public LuaBigItemStack(BigItemStack stack) {
    this.stack = stack;
  }

  @Override
  public Map<?, ?> getTableRepresentation() {
    Map<String, Object> details = VanillaDetailRegistries.ITEM_STACK.getDetails(stack.stack);
    // Add count to the details
    details.put("count", stack.count);
    return details;
  }

}

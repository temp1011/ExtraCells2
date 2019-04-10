package extracells.util

import net.minecraft.item.ItemStack
import net.minecraftforge.items.IItemHandler


object ItemHandlerUtil {

  def insertItemStack(itemHandler: IItemHandler, stack: ItemStack, simulate: Boolean): ItemStack = {
    if (itemHandler == null)
      return stack
    var itemStackRemaining = stack.copy()
    for (i <- 0 until itemHandler.getSlots){
      itemStackRemaining = itemHandler.insertItem(i, itemStackRemaining, simulate)
      if (itemStackRemaining.isEmpty)
        return ItemStack.EMPTY
    }
    itemStackRemaining
  }

}

package extracells.part.gas

import appeng.api.config.Actionable
import appeng.api.storage.IMEMonitor
import appeng.api.storage.data.IAEFluidStack
import extracells.api.gas.IAEGasStack
import extracells.container.{ContainerTerminal, StorageType}
import extracells.gridblock.ECBaseGridBlock
import extracells.gui.GuiTerminal
import extracells.integration.Integration.Mods
import extracells.part.fluid.PartFluidTerminal
import extracells.util.{AEUtils, GasUtil, StorageChannels}
import mekanism.api.gas.GasStack
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fml.common.Optional
import org.apache.commons.lang3.tuple.MutablePair


class PartGasTerminal extends PartFluidTerminal {

  val mekLoaded: Boolean = Mods.MEKANISMGAS.isEnabled
  var doNextFill = false

  override protected def isItemValidForInputSlot(i: Int, itemStack: ItemStack): Boolean = {
    GasUtil.isGasContainer(itemStack)
  }

  override def doWork() {
    if (mekLoaded)
      doWorkGas
  }

  @Optional.Method(modid = "MekanismAPI|gas")
  def doWorkGas()() {
    val secondSlot: ItemStack = this.inventory.getStackInSlot(1)
    if (secondSlot != null && (!secondSlot.isEmpty) && secondSlot.getCount >= secondSlot.getMaxStackSize) return
    var container: ItemStack = this.inventory.getStackInSlot(0)
    if (container == null || container.isEmpty)
      doNextFill = false
    if (!GasUtil.isGasContainer(container)) return
    container = container.copy
    container.setCount(1)
    val gridBlock: ECBaseGridBlock = getGridBlock
    if (gridBlock == null) return
    val monitor: IMEMonitor[IAEGasStack] = gridBlock.getGasMonitor
    if (monitor == null) return
    val gasStack = GasUtil.getGasFromContainer(container)

    if (GasUtil.isEmpty(container) || (gasStack.amount < GasUtil.getCapacity(container) && GasUtil.getFluidStack(gasStack).getFluid == this.currentFluid && doNextFill)) {
      if (this.currentFluid == null) return
      val capacity: Int = GasUtil.getCapacity(container)
      val result: IAEGasStack = monitor.extractItems(StorageChannels.GAS.createStack(new GasStack(GasUtil.getGas(this.currentFluid), capacity)), Actionable.SIMULATE, this.machineSource)
      var proposedAmount: Int = 0
      if (result == null)
        proposedAmount = 0
      else if (gasStack == null)
        proposedAmount = Math.min(capacity, result.getStackSize).toInt
      else
        proposedAmount = Math.min(capacity - gasStack.amount, result.getStackSize).toInt

      val filledContainer: MutablePair[Integer, ItemStack] = GasUtil.fillStack(container, GasUtil.getGasStack(new FluidStack(this.currentFluid, proposedAmount)))
      val gasStack2 = GasUtil.getGasFromContainer(filledContainer.getRight)
      if (gasStack2 == null) {
        doNextFill = false
      } else if (container.getCount == 1 && gasStack2.amount < GasUtil.getCapacity(filledContainer.getRight)) {
        this.inventory.setInventorySlotContents(0, filledContainer.getRight)
        monitor.extractItems(StorageChannels.GAS.createStack(new GasStack(GasUtil.getGas(this.currentFluid), filledContainer.getLeft)), Actionable.MODULATE, this.machineSource)
        doNextFill = true
      } else if (fillSecondSlot(filledContainer.getRight)) {
        monitor.extractItems(StorageChannels.GAS.createStack(new GasStack(GasUtil.getGas(this.currentFluid), filledContainer.getLeft)), Actionable.MODULATE, this.machineSource)
        decreaseFirstSlot()
        doNextFill = false
      }
    }
    else {
      val containerGas = GasUtil.getGasFromContainer(container)

      val drainedContainer: MutablePair[Integer, ItemStack] = GasUtil.drainStack(container.copy(), containerGas)
      val gasStack = containerGas.copy()
      gasStack.amount = drainedContainer.getLeft
      val notInjected: IAEGasStack = monitor.injectItems(StorageChannels.GAS.createStack(gasStack), Actionable.SIMULATE, this.machineSource)
      if (notInjected != null) return
      val emptyContainer: ItemStack = drainedContainer.getRight
      if (emptyContainer != null && (!emptyContainer.isEmpty) && GasUtil.getGasFromContainer(emptyContainer) != null && emptyContainer.getCount == 1) {
        monitor.injectItems(StorageChannels.GAS.createStack(gasStack), Actionable.MODULATE, this.machineSource)
        this.inventory.setInventorySlotContents(0, emptyContainer)
      } else if (emptyContainer == null || emptyContainer.isEmpty || fillSecondSlot(emptyContainer)) {
        monitor.injectItems(StorageChannels.GAS.createStack(containerGas), Actionable.MODULATE, this.machineSource)
        decreaseFirstSlot()
      }
    }
  }

  override def getServerGuiElement(player: EntityPlayer): AnyRef = {
    if (mekLoaded)
      new ContainerTerminal(this, player, StorageType.GAS)
    else
      null
  }

  override def getClientGuiElement(player: EntityPlayer): AnyRef = {
    if (mekLoaded)
      new GuiTerminal(this, player, StorageType.GAS)
    else
      null
  }
}

package extracells.gridblock

import java.util
import java.util.EnumSet

import appeng.api.networking._
import appeng.api.util.{AEColor, DimensionalCoord}
import extracells.tileentity.TileEntityVibrationChamberFluid
import net.minecraft.block.state.IBlockState
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing


class ECGridBlockVibrantChamber(host: TileEntityVibrationChamberFluid) extends IGridBlock {
  protected var grid: IGrid = _
  protected var usedChannels: Int = 0

  override def getConnectableSides: util.EnumSet[EnumFacing] =
    util.EnumSet.of(EnumFacing.DOWN, EnumFacing.UP, EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH,
      EnumFacing.WEST)

  override def getFlags: util.EnumSet[GridFlags] = util.EnumSet.noneOf(classOf[GridFlags])

  override def getGridColor = AEColor.TRANSPARENT

  override def getIdlePowerUsage: Double = host.getPowerUsage

  override def getLocation: DimensionalCoord = host.getLocation

  override def getMachine: IGridHost = host

  override def getMachineRepresentation: ItemStack = {
    val loc: DimensionalCoord = getLocation
    if (loc == null) return null
    val blockState: IBlockState = loc.getWorld.getBlockState(loc.getPos)
    new ItemStack(blockState.getBlock, 1, blockState.getBlock.getMetaFromState(blockState))
  }

  override def gridChanged() {}

  override def isWorldAccessible = true

  override def onGridNotification(notification: GridNotification) {}

  override def setNetworkStatus(_grid: IGrid, _usedChannels: Int) {
    this.grid = _grid
    this.usedChannels = _usedChannels
  }

}

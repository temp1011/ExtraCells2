package extracells.integration.opencomputers

import appeng.api.AEApi
import appeng.api.config.Actionable
import appeng.api.implementations.tiles.IWirelessAccessPoint
import appeng.api.networking.{IGrid, IGridHost, IGridNode, IMachineSet}
import appeng.api.networking.security.IActionHost
import appeng.api.networking.storage.IStorageGrid
import appeng.api.storage.IMEMonitor
import appeng.api.storage.data.{IAEFluidStack, IAEItemStack}
import appeng.api.util.{AEPartLocation, WorldCoord}
import extracells.registries.ItemEnum
import extracells.util.{FluidHelper, MachineSource, StorageChannels}
import li.cil.oc.api.Network
import li.cil.oc.api.internal.{Agent, Database, Drone, Robot}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network._
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import li.cil.oc.server.network.Component
import net.minecraft.item.ItemStack
import net.minecraft.tileentity.TileEntity
import net.minecraftforge.fluids.Fluid

import scala.collection.JavaConversions._


trait NetworkControl[AETile >: Null <: TileEntity with IActionHost with IGridHost] extends AbstractManagedEnvironment{

  def tile: AETile

  def host: EnvironmentHost

  val robot: Robot =
    host match {
      case robot1: Robot => robot1
      case _ => null
    }

  val drone: Drone =
    host match {
      case drone1: Drone => drone1
      case _ => null
    }
  var isActive = false

  val agent: Agent = host.asInstanceOf[Agent]

  def getComponent: ItemStack = {
    if (robot != null)
      return robot.getStackInSlot(robot.componentSlot(node.address))
    else if(drone != null){
      val i = drone.internalComponents.iterator
      while (i.hasNext){
        val item = i.next
        if(item != null && item.getItem == ItemEnum.OCUPGRADE.getItem)
          return item
      }
    }
    null
  }

  def getSecurity: IGridHost = {
    if (host.world.isRemote) return null
    val component = getComponent
    val sec = AEApi.instance.registries.locatable.getLocatableBy(getAEKey(component)).asInstanceOf[IGridHost]
    if(checkRange(component, sec))
      sec
    else
      null
  }
  def checkRange(stack: ItemStack, sec: IGridHost): Boolean = {
    if (sec == null) return false
    val gridNode: IGridNode = sec.getGridNode(AEPartLocation.INTERNAL)
    if (gridNode == null) return false
    val grid = gridNode.getGrid
    if(grid == null) return false
    stack.getItemDamage match{
      case 0 =>
        grid.getMachines(AEApi.instance.definitions.blocks.wirelessAccessPoint().maybeEntity.get.asInstanceOf[Class[_ <: IGridHost]]).iterator.hasNext
      case 1 =>
        val gridBlock = gridNode.getGridBlock
        if (gridBlock == null) return false
        val loc = gridBlock.getLocation
        if (loc == null) return false
        for (node: IGridNode <- grid.getMachines(AEApi.instance.definitions.blocks.wirelessAccessPoint().maybeEntity.get.asInstanceOf[Class[_ <: IGridHost]])) {
          val accessPoint: IWirelessAccessPoint = node.asInstanceOf[IWirelessAccessPoint]
          val distance: WorldCoord = accessPoint.getLocation.subtract(agent.xPosition.toInt, agent.yPosition.toInt, agent.zPosition.toInt)
          val squaredDistance: Int = distance.x * distance.x + distance.y * distance.y + distance.z * distance.z
          val range = accessPoint.getRange
          if (squaredDistance <= range * range) return true
        }
        false
      case _ =>
        val gridBlock = gridNode.getGridBlock
        if (gridBlock == null) return false
        val loc = gridBlock.getLocation
        if (loc == null) return false
        for (node: IGridNode <- grid.getMachines(AEApi.instance.definitions.blocks.wirelessAccessPoint().maybeEntity.get.asInstanceOf[Class[_ <: IGridHost]])) {
          val accessPoint: IWirelessAccessPoint = node.asInstanceOf[IWirelessAccessPoint]
          val distance: WorldCoord = accessPoint.getLocation.subtract(agent.xPosition.toInt, agent.yPosition.toInt, agent.zPosition.toInt)
          val squaredDistance: Int = distance.x * distance.x + distance.y * distance.y + distance.z * distance.z
          val range = accessPoint.getRange / 2
          if (squaredDistance <= range * range) return true
        }
        false
    }
  }

  def getGrid: IGrid = {
    if (host.world.isRemote) return null
    val securityTerminal = getSecurity
    if (securityTerminal == null) return null
    val gridNode: IGridNode = securityTerminal.getGridNode(AEPartLocation.INTERNAL)
    if (gridNode == null) return null
    gridNode.getGrid
  }

  def getAEKey(stack: ItemStack): Long = {
    try {
      return WirelessHandlerUpgradeAE.getEncryptionKey(stack).toLong
    }
    catch {
      case _: Throwable =>
    }
    0L
  }

  def getFluidInventory: IMEMonitor[IAEFluidStack] = {
    val grid = getGrid
    if (grid == null) return null
    val storage: IStorageGrid = grid.getCache(classOf[IStorageGrid])
    if (storage == null) return null
    storage.getInventory(StorageChannels.FLUID)
  }

  def getItemInventory: IMEMonitor[IAEItemStack] = {
    val grid = getGrid
    if (grid == null) return null
    val storage: IStorageGrid = grid.getCache(classOf[IStorageGrid])
    if (storage == null) return null
    storage.getInventory(StorageChannels.ITEM)
  }

  @Callback(doc = "function([number:amount]):number -- Transfer selected items to your ae system.")
  def sendItems(context: Context, args: Arguments): Array[AnyRef] = {
    val selected = agent.selectedSlot
    val invRobot = agent.mainInventory
    if (invRobot.getSizeInventory <= 0) return Array(0.underlying)
    val stack = invRobot.getStackInSlot(selected)
    val inv = getItemInventory
    if (stack == null || stack.isEmpty || inv == null) return Array(0.underlying)
    val amount = Math.min(args.optInteger(0, 64), stack.getCount)
    val stack2 = stack.copy
    stack2.setCount(amount)
    val notInjectet = inv.injectItems(StorageChannels.ITEM.createStack(stack2), Actionable.MODULATE, new MachineSource(tile))
    if (notInjectet == null){
      stack.setCount(stack.getCount - amount)
      if (stack.getCount <= 0)
        invRobot.setInventorySlotContents(selected, ItemStack.EMPTY)
      else
        invRobot.setInventorySlotContents(selected, stack)
      Array(amount.underlying)
    }else{
      stack.setCount(stack.getCount - amount + notInjectet.getStackSize.toInt)
      if (stack.getCount <= 0)
        invRobot.setInventorySlotContents(selected, ItemStack.EMPTY)
      else
        invRobot.setInventorySlotContents(selected, stack)
      Array((stack2.getCount - notInjectet.getStackSize).underlying)
    }
  }

  @Callback(doc = "function(database:address, entry:number[, number:amount]):number -- Get items from your ae system.")
  def requestItems(context: Context, args: Arguments): Array[AnyRef] = {

    val address = args.checkString(0)
    val entry = args.checkInteger(1)
    val amount = args.optInteger(2, 64)
    val selected = agent.selectedSlot
    val invRobot = agent.mainInventory
    if (invRobot.getSizeInventory <= 0) return Array(0.underlying)
    val inv = getItemInventory
    if (inv == null) return Array(0.underlying)
    val n: Node = node.network.node(address)
    if (n == null) throw new IllegalArgumentException("no such component")
    if (!n.isInstanceOf[Component]) throw new IllegalArgumentException("no such component")
    val env: Environment = n.host
    if (!env.isInstanceOf[Database]) throw new IllegalArgumentException("not a database")
    val database: Database = env.asInstanceOf[Database]
    val sel = invRobot.getStackInSlot(selected)
    val inSlot =
      if (sel == null)
        0
      else
        sel.getCount
    val maxSize =
      if (sel == null)
        64
      else
        sel.getMaxStackSize
    val stack = database.getStackInSlot(entry - 1)
    if(stack == null) return Array(0.underlying)
    stack.setCount(Math.min(amount, maxSize - inSlot))
    val stack2 = stack.copy
    stack2.setCount(1)
    val sel2 =
      if (sel != null) {
        val sel3 = sel.copy
        sel3.setCount(1)
        sel3
      }else
        null
    if(sel != null && !ItemStack.areItemStacksEqual(sel2, stack2) && !sel2.isEmpty) return Array(0.underlying)
    val extracted = inv.extractItems(StorageChannels.ITEM.createStack(stack), Actionable.MODULATE, new MachineSource(tile))
    if(extracted == null) return Array(0.underlying)
    val ext = extracted.getStackSize.toInt
    stack.setCount(inSlot + ext)
    invRobot.setInventorySlotContents(selected, stack)
    Array(ext.underlying)
  }

  @Callback(doc = "function([number:amount]):number -- Transfer selecte fluid to your ae system.")
  def sendFluids(context: Context, args: Arguments): Array[AnyRef] = {
    val selected = agent.selectedTank
    val tanks = agent.tank
    if (tanks.tankCount <= 0) return Array(0.underlying)
    val tank = tanks.getFluidTank(selected)
    val inv = getFluidInventory
    if (tank == null || inv == null || tank.getFluid == null) return Array(0.underlying)
    val amount = Math.min(args.optInteger(0, tank.getCapacity), tank.getFluidAmount)
    val fluid = tank.getFluid
    val fluid2 = fluid.copy
    fluid2.amount = amount
    val notInjectet = inv.injectItems(StorageChannels.FLUID.createStack(fluid2), Actionable.MODULATE, new MachineSource(tile))
    if (notInjectet == null){
      tank.drain(amount, true)
      Array(amount.underlying)
    }else{
      tank.drain(amount - notInjectet.getStackSize.toInt, true)
      Array((amount - notInjectet.getStackSize).underlying)
    }
  }

  @Callback(doc = "function(database:address, entry:number[, number:amount]):number -- Get fluid from your ae system.")
  def requestFluids(context: Context, args: Arguments): Array[AnyRef] = {
    val address = args.checkString(0)
    val entry = args.checkInteger(1)
    val amount = args.optInteger(2, Fluid.BUCKET_VOLUME)
    val tanks = agent.tank
    val selected = agent.selectedTank
    if (tanks.tankCount <= 0) return Array(0.underlying)
    val tank = tanks.getFluidTank(selected)
    val inv = getFluidInventory
    if (tank == null || inv == null) return Array(0.underlying)
    val n: Node = node.network.node(address)
    if (n == null) throw new IllegalArgumentException("no such component")
    if (!n.isInstanceOf[Component]) throw new IllegalArgumentException("no such component")
    val env: Environment = n.host
    if (!env.isInstanceOf[Database]) throw new IllegalArgumentException("not a database")
    val database: Database = env.asInstanceOf[Database]
    val fluid = FluidHelper.getFluidFromContainer(database.getStackInSlot(entry - 1))
    fluid.amount = amount
    val fluid2 = fluid.copy()
    fluid2.amount = tank.fill(fluid, false)
    if (fluid2.amount == 0) return Array(0.underlying)
    val extracted = inv.extractItems(StorageChannels.FLUID.createStack(fluid2), Actionable.MODULATE, new MachineSource(tile))
    if (extracted == 0) return Array(0.underlying)
    Array(tank.fill(extracted.getFluidStack, true).underlying)
  }


  @Callback(doc = "function():boolean -- Return true if the card is linket to your ae network.")
  def isLinked(context: Context, args: Arguments): Array[AnyRef] = {
    val isLinked = getGrid != null
    Array(boolean2Boolean(isLinked))
  }

  override def update() {
    super.update()
    if (host.world.getTotalWorldTime % 10 == 0 && isActive) {
      if (!node.asInstanceOf[Connector].tryChangeBuffer(-getEnergy)) {
        isActive = false
      }
    }
  }

  def getEnergy: Double = {
    val c = getComponent
    if (c == null)
      .0
    else
      c.getItemDamage match{
        case 0 => .6
        case 1 => .3
        case _ => .05
      }
  }

  override def onMessage(message: Message) {
    super.onMessage(message)
    if (message.name == "computer.stopped") {
      isActive = false
    }
    else if (message.name == "computer.started") {
      isActive = true
    }
  }

}

package extracells.network

import appeng.api.config.SecurityPermissions
import appeng.api.networking.energy.IEnergyGrid
import appeng.api.networking.security.{IActionHost, ISecurityGrid}
import appeng.api.parts.{IPart, IPartHost}
import appeng.api.storage.IMEMonitor
import appeng.api.storage.data.IAEFluidStack
import appeng.api.util.AEPartLocation
import extracells.ExtraCells
import extracells.api._
import extracells.api.gas.IAEGasStack
import extracells.block.TGuiBlock
import extracells.container.fluid.ContainerFluidStorage
import extracells.container.gas.ContainerGasStorage
import extracells.gui._
import extracells.integration.mekanism.gas.MEMonitorFluidGasWrapper
import extracells.part.PartECBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.{EnumFacing, EnumHand}
import net.minecraft.world.World
import net.minecraftforge.fml.common.network.IGuiHandler
import net.minecraftforge.fml.relauncher.{Side, SideOnly}

object GuiHandler extends IGuiHandler {

  def getContainer(ID: Int, player: EntityPlayer, args: Array[Any]): Any = {
    ID match {
      case 0 =>
        val fluidInventory = args.apply(0).asInstanceOf[IMEMonitor[IAEFluidStack]]
        new ContainerFluidStorage(fluidInventory, player, hand)
      case 1 =>
        val fluidInventory2 = args.apply(0).asInstanceOf[IMEMonitor[IAEFluidStack]]
        val handler = args.apply(1).asInstanceOf[IWirelessFluidTermHandler]
        new ContainerFluidStorage(fluidInventory2, player, handler, hand)
      case 3 =>
        val fluidInventory3 = args.apply(0).asInstanceOf[IMEMonitor[IAEFluidStack]]
        val storageCell = args.apply(1).asInstanceOf[IPortableFluidStorageCell]
        new ContainerFluidStorage(fluidInventory3, player, storageCell, hand)
      case 4 =>
        val fluidInventory = new MEMonitorFluidGasWrapper(args.apply(0).asInstanceOf[IMEMonitor[IAEGasStack]])
        new ContainerGasStorage(fluidInventory, player, hand)
      case 5 =>
        val fluidInventory2 = new MEMonitorFluidGasWrapper(args.apply(0).asInstanceOf[IMEMonitor[IAEGasStack]])
        val handler = args.apply(1).asInstanceOf[IWirelessGasTermHandler]
        new ContainerGasStorage(fluidInventory2, player, handler, hand)
      case 6 =>
        val fluidInventory3 = new MEMonitorFluidGasWrapper(args.apply(0).asInstanceOf[IMEMonitor[IAEGasStack]])
        val storageCell = args.apply(1).asInstanceOf[IPortableGasStorageCell]
        new ContainerGasStorage(fluidInventory3, player, storageCell, hand)
      case _ =>
        null
    }
  }

  @SideOnly(Side.CLIENT)
  def getGui(ID: Int, player: EntityPlayer): Any = {
    ID match {
      case 0 =>
        new GuiStorage(new ContainerFluidStorage(player, hand), "extracells.part.fluid.terminal.name");
      case 1 =>
        new GuiStorage(new ContainerFluidStorage(player, hand), "extracells.part.fluid.terminal.name");
      case 3 =>
        new GuiStorage(new ContainerFluidStorage(player, hand), "extracells.item.storage.fluid.portable.name");
      case 4 =>
        new GuiStorage(new ContainerGasStorage(player, hand), "extracells.part.gas.terminal.name");
      case 5 =>
        new GuiStorage(new ContainerGasStorage(player, hand), "extracells.part.gas.terminal.name");
      case 6 =>
        new GuiStorage(new ContainerGasStorage(player, hand), "extracells.item.storage.gas.portable.name");
      case _ =>
        null;
    }
  }

  def getGuiId(guiId: Int): Int = guiId + 6

  def getGuiId(part: PartECBase): Int = part.getFacing.ordinal()

  def getPartContainer(side: EnumFacing, player: EntityPlayer, world: World, x: Int, y: Int, z: Int): Any =
    world.getTileEntity(new BlockPos(x, y, z)).asInstanceOf[IPartHost].getPart(side).asInstanceOf[PartECBase]
      .getServerGuiElement(player)


  def getPartGui(side: EnumFacing, player: EntityPlayer, world: World, x: Int, y: Int, z: Int): Any =
    world.getTileEntity(new BlockPos(x, y, z)).asInstanceOf[IPartHost].getPart(side).asInstanceOf[PartECBase]
      .getClientGuiElement(player)

  def launchGui(ID: Int, player: EntityPlayer, hand: EnumHand, args: Array[Any]) {
    temp = args
    this.hand = hand
    player.openGui(ExtraCells.instance, ID, player.getEntityWorld, player.posX.asInstanceOf[Int], player.posY.asInstanceOf[Int], player.posZ.asInstanceOf[Int])
  }

  def launchGui(ID: Int, player: EntityPlayer, world: World, x: Int, y: Int, z: Int): Any = {
    player.openGui(ExtraCells.instance, ID, world, x, y, z)
  }

  def hasPermissions(pos: BlockPos, side: AEPartLocation, player: EntityPlayer): Boolean = {
    val world: World = player.getEntityWorld
    val tileEntity: TileEntity = world.getTileEntity(pos)
    if (tileEntity == null) {
      return true
    } else tileEntity match {
      case _: IGuiProvider =>
        return securityCheck(tileEntity, player)
      case _ => tileEntity match {
        case host: IPartHost =>
          val part: IPart = host.getPart(side)
          if (part != null) {
            return securityCheck(part, player)
          }
        case _ =>
      }
    }
    false
  }

  private def securityCheck(te: Any, player: EntityPlayer): Boolean = {
    te match {
      case host: IActionHost =>
        val gn = host.getActionableNode
        if (gn != null) {
          val g = gn.getGrid
          if (g != null) {
            val requirePower = false
            if (requirePower) {
              val eg: IEnergyGrid = g.getCache(classOf[IEnergyGrid])
              if (!eg.isNetworkPowered) return false
            }
            val sg: ISecurityGrid = g.getCache(classOf[ISecurityGrid])
            if (sg.hasPermission(player, SecurityPermissions.BUILD)) return true
          }
        }
        return false
      case _ =>
    }
    true
  }

  var temp: Array[Any] = Array[Any]()
  var hand: EnumHand = _

  override def getClientGuiElement(ID: Int, player: EntityPlayer, world: World, x: Int, y: Int, z: Int): AnyRef = {
    val gui = getGuiBlockElement(player, world, x, y, z)
    if (gui != null)
      return gui.asInstanceOf[AnyRef]
    var side: EnumFacing = null
    if (ID <= 5) side = EnumFacing.getFront(ID)
    val pos = new BlockPos(x, y, z)
    val tileEntity = world.getTileEntity(pos)
    if (tileEntity == null)
      if(ID>=6)
        return getGui(ID - 6, player).asInstanceOf[AnyRef]
      else
        return null
    else tileEntity match {
      case provider: IGuiProvider => return provider.getClientGuiElement(player)
      case _: IPartHost =>
        if (world != null && side != null) {
          return getPartGui(side, player, world, x, y, z).asInstanceOf[AnyRef]
        }
        if (ID >= 6)
          return getGui(ID - 6, player).asInstanceOf[AnyRef]
      case _ =>
    }
    null
  }

  override def getServerGuiElement(ID: Int, player: EntityPlayer, world: World, x: Int, y: Int, z: Int): AnyRef = {
    val con: Any = getContainerBlockElement(player, world, x, y, z)
    if (con != null)
      return con.asInstanceOf[AnyRef]
    var side: EnumFacing = null
    if (ID <= 5) {
      side = EnumFacing.getFront(ID)
    }
    val pos = new BlockPos(x, y, z)
    val tileEntity = world.getTileEntity(pos)
    if (tileEntity == null)
      if(ID >= 6)
        return getContainer(ID - 6, player, temp).asInstanceOf[AnyRef]
      else
        return null
    else tileEntity match {
      case provider: IGuiProvider => return provider.getServerGuiElement(player)
      case _: IPartHost =>
        if (world != null && side != null) {
          return getPartContainer(side, player, world, x, y, z).asInstanceOf[AnyRef]
        }
        if (ID >= 6)
          return getContainer(ID - 6, player, temp).asInstanceOf[AnyRef]
      case _ =>
    }
    null
  }

  def getGuiBlockElement(player: EntityPlayer, world: World, x: Int, y: Int, z: Int): Any = {
    if (world == null || player == null)
      return null
    val pos = new BlockPos(x, y, z)
    val block = world.getBlockState(pos).getBlock
    if (block == null)
      return null
    block match {
      case guiBlock: TGuiBlock => guiBlock.getClientGuiElement(player, world, pos)
      case _ => null
    }
  }

  def getContainerBlockElement(player: EntityPlayer, world: World, x: Int, y: Int, z: Int): Any = {
    if (world == null || player == null) {
      return null
    }
    val pos = new BlockPos(x, y, z)
    val block = world.getBlockState(pos).getBlock
    if (block == null) {
      return null
    }
    block match {
      case guiBlock: TGuiBlock => guiBlock.getServerGuiElement(player, world, pos)
      case _ => null
    }
  }
}

package extracells.block

import java.util.Random
import javax.annotation.Nullable

import appeng.api.AEApi
import appeng.api.config.SecurityPermissions
import appeng.api.implementations.items.IAEWrench
import appeng.api.networking.IGridNode
import appeng.api.util.AEPartLocation
import extracells.models.ModelManager
import extracells.network.GuiHandler
import extracells.tileentity.{IListenerTile, TileEntityFluidInterface}
import extracells.util.{PermissionUtil, WrenchUtil}
import net.minecraft.block.material.Material
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.IInventory
import net.minecraft.item.{Item, ItemStack}
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.{EnumFacing, EnumHand}
import net.minecraft.util.math.{BlockPos, RayTraceResult, Vec3d}
import net.minecraft.world.World

object BlockFluidInterface extends BlockEC(Material.IRON, 2.0F, 10.0F) {

  //Only needed because BlockEnum is in java. not in scala
  val instance: BlockFluidInterface.type = this

  override def registerModel(item: Item, manager: ModelManager): Unit = manager.registerItemModel(item, 0, "fluid_interface")

  def createNewTileEntity(world: World, meta: Int): TileEntity = new TileEntityFluidInterface

  private def dropPatter(world: World, pos: BlockPos) {
    val rand: Random = new Random
    val x: Int = pos.getX
    val y: Int = pos.getY
    val z: Int = pos.getZ
    val tileEntity: TileEntity = world.getTileEntity(pos)
    if (!tileEntity.isInstanceOf[TileEntityFluidInterface]) {
      return
    }
    val inventory: IInventory = tileEntity.asInstanceOf[TileEntityFluidInterface].inventory
    var i: Int = 0
    while (i < inventory.getSizeInventory) {
      {
        val item: ItemStack = inventory.getStackInSlot(i)
        if (item != null && item.getCount > 0) {
          val rx: Float = rand.nextFloat * 0.8F + 0.1F
          val ry: Float = rand.nextFloat * 0.8F + 0.1F
          val rz: Float = rand.nextFloat * 0.8F + 0.1F
          val entityItem: EntityItem = new EntityItem(world, x + rx, y + ry, z + rz, item.copy)
          if (item.hasTagCompound) {
            entityItem.getItem.setTagCompound(item.getTagCompound.copy)
          }
          val factor: Float = 0.05F
          entityItem.motionX = rand.nextGaussian * factor
          entityItem.motionY = rand.nextGaussian * factor + 0.2F
          entityItem.motionZ = rand.nextGaussian * factor
          world.spawnEntity(entityItem)
          item.setCount(0)
        }
      }
      {
        i += 1
        i - 1
      }
    }
  }

  override def onBlockActivated(world: World, pos: BlockPos, state: IBlockState, player: EntityPlayer, hand: EnumHand, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Boolean = {
    if (world.isRemote) return true
    val tile: TileEntity = world.getTileEntity(pos)
    tile match {
      case interface: TileEntityFluidInterface => if (!PermissionUtil.hasPermission(player, SecurityPermissions.BUILD, interface.getGridNode(null))) return false
      case _ =>
    }
    val current = player.getHeldItem(hand)
    if (player.isSneaking) {
      val rayTraceResult = new RayTraceResult(new Vec3d(hitX, hitY, hitZ), side, pos)
      val wrenchHandler = WrenchUtil.getHandler(current, player, rayTraceResult, hand)
      if (wrenchHandler != null) {
        val block = new ItemStack(this)
        if (tile != null && tile.isInstanceOf[TileEntityFluidInterface]) block.setTagCompound(tile.asInstanceOf[TileEntityFluidInterface].writeFilter(new NBTTagCompound))
        dropBlockAsItem(world, pos, state, 0)
        world.setBlockToAir(pos)
        wrenchHandler.wrenchUsed(current, player, rayTraceResult, hand)
        return true
      }
    }
    GuiHandler.launchGui(0, player, world, pos.getX, pos.getY, pos.getZ)
    true
  }

  override def onBlockPlacedBy(world: World, pos: BlockPos, state: IBlockState, entity: EntityLivingBase, stack: ItemStack) {
    if (world.isRemote) return

    val tile = world.getTileEntity(pos)
    if (tile != null) {
      tile match {
        case interface: TileEntityFluidInterface =>
          val node = interface.getGridNode(null)
          if (entity != null && entity.isInstanceOf[EntityPlayer]) {
            val player = entity.asInstanceOf[EntityPlayer]
            node.setPlayerID(AEApi.instance.registries.players.getID(player))
          }
          node.updateState
        case _ =>
      }
      tile match {
        case tile1: IListenerTile => tile1.registerListener
        case _ =>
      }
    }
  }

  override def breakBlock(world: World, pos: BlockPos, state: IBlockState) {
    if (world.isRemote){
      super.breakBlock(world, pos, state)
      return
    }
    dropPatter(world, pos)
    val tile: TileEntity = world.getTileEntity(pos)
    if (tile != null) {
      tile match {
        case interface: TileEntityFluidInterface =>
          val node: IGridNode = interface.getGridNode(AEPartLocation.INTERNAL)
          if (node != null) {
            node.destroy
          }
        case _ =>
      }
    }
    super.breakBlock(world, pos, state)
  }

}